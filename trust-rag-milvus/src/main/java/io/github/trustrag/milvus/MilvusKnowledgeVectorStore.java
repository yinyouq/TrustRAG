package io.github.trustrag.milvus;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MilvusKnowledgeVectorStore implements KnowledgeVectorStore, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MilvusKnowledgeVectorStore.class);
    private static final String PRIMARY_FIELD = "id";
    private static final String VECTOR_FIELD = "embedding";

    private final MilvusClientV2 client;
    private final MilvusSettings settings;
    private final MilvusFilterBuilder filterBuilder;
    private final Gson gson = new Gson();

    public MilvusKnowledgeVectorStore(
            MilvusClientV2 client,
            MilvusSettings settings,
            MilvusFilterBuilder filterBuilder) {
        this.client = client;
        this.settings = settings;
        this.filterBuilder = filterBuilder;
    }

    @Override
    public void initialize() {
        boolean exists = client.hasCollection(HasCollectionReq.builder()
                .databaseName(settings.database())
                .collectionName(settings.collection())
                .build());
        if (!exists) {
            if (!settings.autoCreateCollection()) {
                throw new IllegalStateException("Milvus collection does not exist: " + settings.collection());
            }
            createCollection();
        } else {
            validateCollection();
        }
        client.loadCollection(LoadCollectionReq.builder()
                .databaseName(settings.database())
                .collectionName(settings.collection())
                .build());
    }

    @Override
    public void upsert(KnowledgeItem knowledge, List<Float> vector) {
        if (knowledge.id() == null) {
            throw new IllegalArgumentException("Knowledge id is required for Milvus upsert");
        }
        if (vector == null || vector.size() != settings.dimension()) {
            throw new IllegalArgumentException(
                    "Vector dimension mismatch: expected " + settings.dimension()
                            + " but got " + (vector == null ? 0 : vector.size()));
        }
        JsonObject row = new JsonObject();
        row.addProperty(PRIMARY_FIELD, knowledge.id());
        row.addProperty("knowledge_id", knowledge.id());
        row.add(VECTOR_FIELD, gson.toJsonTree(vector));
        row.addProperty("trust_level", knowledge.trustLevel().name());
        row.addProperty("scope_type", knowledge.scopeType().name());
        row.addProperty("user_id", nonNull(knowledge.userId()));
        row.addProperty("conversation_id", nonNull(knowledge.conversationId()));
        row.addProperty("project_id", nonNull(knowledge.projectId()));
        row.addProperty("tenant_id", nonNull(knowledge.tenantId()));
        row.addProperty("status", knowledge.status().name());
        row.addProperty("created_at", knowledge.createdAt().toEpochMilli());

        client.upsert(UpsertReq.builder()
                .databaseName(settings.database())
                .collectionName(settings.collection())
                .data(List.of(row))
                .build());
    }

    @Override
    public void delete(long knowledgeId) {
        client.delete(DeleteReq.builder()
                .databaseName(settings.database())
                .collectionName(settings.collection())
                .ids(List.of(knowledgeId))
                .build());
    }

    @Override
    public List<VectorHit> search(VectorSearchRequest request) {
        if (request.vector().size() != settings.dimension()) {
            throw new IllegalArgumentException(
                    "Search vector dimension mismatch: expected " + settings.dimension()
                            + " but got " + request.vector().size());
        }
        MilvusFilter filter = filterBuilder.build(request);
        SearchResp response = client.search(SearchReq.builder()
                .databaseName(settings.database())
                .collectionName(settings.collection())
                .annsField(VECTOR_FIELD)
                .metricType(metricType())
                .filter(filter.expression())
                .filterTemplateValues(filter.templateValues())
                .outputFields(List.of("knowledge_id"))
                .data(List.of(new FloatVec(request.vector())))
                .limit(request.topK())
                .build());
        if (response.getSearchResults() == null || response.getSearchResults().isEmpty()) {
            return List.of();
        }

        List<VectorHit> hits = new ArrayList<>();
        for (SearchResp.SearchResult result : response.getSearchResults().get(0)) {
            double score = result.getScore() == null ? 0.0 : result.getScore();
            if (score < request.minScore()) {
                continue;
            }
            Object knowledgeId = result.getEntity() == null ? null : result.getEntity().get("knowledge_id");
            if (knowledgeId == null) {
                knowledgeId = result.getId();
            }
            hits.add(new VectorHit(asLong(knowledgeId), score));
        }
        return hits;
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (Exception exception) {
            LOGGER.warn("Failed to close Milvus client", exception);
        }
    }

    private void createCollection() {
        CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema()
                .addField(field(PRIMARY_FIELD, DataType.Int64, true, null, null))
                .addField(field("knowledge_id", DataType.Int64, false, null, null))
                .addField(field(VECTOR_FIELD, DataType.FloatVector, false, null, settings.dimension()))
                .addField(field("trust_level", DataType.VarChar, false, 32, null))
                .addField(field("scope_type", DataType.VarChar, false, 32, null))
                .addField(field("user_id", DataType.VarChar, false, 128, null))
                .addField(field("conversation_id", DataType.VarChar, false, 128, null))
                .addField(field("project_id", DataType.VarChar, false, 128, null))
                .addField(field("tenant_id", DataType.VarChar, false, 128, null))
                .addField(field("status", DataType.VarChar, false, 32, null))
                .addField(field("created_at", DataType.Int64, false, null, null));
        IndexParam vectorIndex = IndexParam.builder()
                .fieldName(VECTOR_FIELD)
                .indexName("idx_trust_rag_embedding")
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(metricType())
                .build();
        client.createCollection(CreateCollectionReq.builder()
                .databaseName(settings.database())
                .collectionName(settings.collection())
                .description("TrustRAG knowledge vectors")
                .collectionSchema(schema)
                .indexParams(List.of(vectorIndex))
                .enableDynamicField(false)
                .build());
    }

    private AddFieldReq field(
            String name,
            DataType type,
            boolean primary,
            Integer maxLength,
            Integer dimension) {
        AddFieldReq.AddFieldReqBuilder<?> builder = AddFieldReq.builder()
                .fieldName(name)
                .dataType(type)
                .isPrimaryKey(primary)
                .autoID(false);
        if (maxLength != null) {
            builder.maxLength(maxLength);
        }
        if (dimension != null) {
            builder.dimension(dimension);
        }
        return builder.build();
    }

    private void validateCollection() {
        DescribeCollectionResp response = client.describeCollection(DescribeCollectionReq.builder()
                .databaseName(settings.database())
                .collectionName(settings.collection())
                .build());
        CreateCollectionReq.FieldSchema vectorField = response.getCollectionSchema().getField(VECTOR_FIELD);
        if (vectorField == null || vectorField.getDataType() != DataType.FloatVector) {
            throw new IllegalStateException("Milvus collection has no FloatVector field named " + VECTOR_FIELD);
        }
        if (!Integer.valueOf(settings.dimension()).equals(vectorField.getDimension())) {
            throw new IllegalStateException(
                    "Milvus collection dimension mismatch: configured " + settings.dimension()
                            + ", actual " + vectorField.getDimension());
        }
    }

    private IndexParam.MetricType metricType() {
        try {
            return IndexParam.MetricType.valueOf(settings.metricType().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported Milvus metric type: " + settings.metricType(), exception);
        }
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String nonNull(String value) {
        return value == null ? "" : value;
    }
}
