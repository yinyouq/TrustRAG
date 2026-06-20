package io.github.trustrag.opensearch;

import org.opensearch.client.opensearch._types.mapping.DynamicMapping;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;

public final class OpenSearchIndexDefinition {

    public CreateIndexRequest createRequest(String index) {
        return new CreateIndexRequest.Builder()
                .index(index)
                .settings(settings -> settings
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                        .analysis(analysis -> analysis
                                .analyzer(
                                        "trust_rag_text_analyzer",
                                        analyzer -> analyzer.standard(value -> value))))
                .mappings(mapping -> mapping
                        .dynamic(DynamicMapping.Strict)
                        .properties("knowledge_id", property -> property.long_(value -> value))
                        .properties("title", property -> property.text(
                                value -> value.analyzer("trust_rag_text_analyzer")))
                        .properties("claim", property -> property.text(
                                value -> value.analyzer("trust_rag_text_analyzer")))
                        .properties("content", property -> property.text(
                                value -> value.analyzer("trust_rag_text_analyzer")))
                        .properties("tags", property -> property.keyword(value -> value))
                        .properties("trust_level", property -> property.keyword(value -> value))
                        .properties("scope_type", property -> property.keyword(value -> value))
                        .properties("user_id", property -> property.keyword(value -> value))
                        .properties("conversation_id", property -> property.keyword(value -> value))
                        .properties("project_id", property -> property.keyword(value -> value))
                        .properties("tenant_id", property -> property.keyword(value -> value))
                        .properties("status", property -> property.keyword(value -> value))
                        .properties("source_type", property -> property.keyword(value -> value))
                        .properties("source_ref", property -> property.keyword(value -> value))
                        .properties("version", property -> property.integer(value -> value))
                        .properties("created_at", property -> property.date(value -> value))
                        .properties("updated_at", property -> property.date(value -> value)))
                .build();
    }
}
