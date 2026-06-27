package io.github.trustrag.opensearch;

import io.github.trustrag.core.model.KeywordHit;
import io.github.trustrag.core.model.KeywordSearchRequest;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.spi.KnowledgeKeywordStore;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenSearch 关键词索引适配器。
 *
 * <p>它提供 BM25 召回能力，并与 Milvus 向量召回一起进入核心层的 RRF 融合。</p>
 */
public final class OpenSearchKnowledgeKeywordStore
        implements KnowledgeKeywordStore, AutoCloseable {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(OpenSearchKnowledgeKeywordStore.class);

    private final OpenSearchClient client;
    private final OpenSearchTransport transport;
    private final OpenSearchSettings settings;
    private final OpenSearchIndexDefinition indexDefinition;
    private final OpenSearchQueryFactory queryFactory;
    private volatile boolean initialized;

    public OpenSearchKnowledgeKeywordStore(
            OpenSearchClient client,
            OpenSearchSettings settings) {
        this(client, null, settings);
    }

    private OpenSearchKnowledgeKeywordStore(
            OpenSearchClient client,
            OpenSearchTransport transport,
            OpenSearchSettings settings) {
        this.client = client;
        this.transport = transport;
        this.settings = settings;
        this.indexDefinition = new OpenSearchIndexDefinition();
        this.queryFactory = new OpenSearchQueryFactory();
    }

    public static OpenSearchKnowledgeKeywordStore connect(OpenSearchSettings settings) {
        HttpHost[] hosts = settings.uris().stream()
                .map(OpenSearchKnowledgeKeywordStore::httpHost)
                .toArray(HttpHost[]::new);
        ApacheHttpClient5TransportBuilder builder =
                ApacheHttpClient5TransportBuilder.builder(hosts)
                        .setMapper(new JacksonJsonpMapper());
        if (settings.username() != null && !settings.username().isBlank()) {
            BasicCredentialsProvider credentials = new BasicCredentialsProvider();
            credentials.setCredentials(
                    new AuthScope(null, -1),
                    new UsernamePasswordCredentials(
                            settings.username(),
                            settings.password() == null
                                    ? new char[0]
                                    : settings.password().toCharArray()));
            builder.setHttpClientConfigCallback(
                    httpClient -> httpClient.setDefaultCredentialsProvider(credentials));
        }
        OpenSearchTransport transport = builder.build();
        return new OpenSearchKnowledgeKeywordStore(
                new OpenSearchClient(transport), transport, settings);
    }

    @Override
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        try {
            boolean exists = client.indices()
                    .exists(request -> request.index(settings.index()))
                    .value();
            if (!exists) {
                if (!settings.autoCreateIndex()) {
                    throw new IllegalStateException(
                            "OpenSearch index does not exist: " + settings.index());
                }
                client.indices().create(indexDefinition.createRequest(settings.index()));
            }
            initialized = true;
        } catch (IOException exception) {
            throw new IllegalStateException("OpenSearch index initialization failed", exception);
        }
    }

    @Override
    public void upsert(KnowledgeItem knowledge) {
        ensureInitialized();
        if (knowledge.id() == null) {
            throw new IllegalArgumentException(
                    "Knowledge id is required for OpenSearch upsert");
        }
        try {
            client.index(request -> request
                    .index(settings.index())
                    .id(Long.toString(knowledge.id()))
                    .refresh(Refresh.WaitFor)
                    .document(KeywordIndexDocument.from(knowledge)));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "OpenSearch upsert failed for knowledge " + knowledge.id(),
                    exception);
        }
    }

    @Override
    public void delete(long knowledgeId) {
        ensureInitialized();
        try {
            client.delete(request -> request
                    .index(settings.index())
                    .id(Long.toString(knowledgeId))
                    .refresh(Refresh.WaitFor));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "OpenSearch delete failed for knowledge " + knowledgeId,
                    exception);
        }
    }

    @Override
    public List<KeywordHit> search(KeywordSearchRequest request) {
        ensureInitialized();
        try {
            // 查询构造器负责注入状态、可信等级和作用域条件，避免只按文本相似度召回。
            SearchResponse<KeywordIndexDocument> response = client.search(
                    search -> search
                            .index(settings.index())
                            .size(request.topK())
                            .query(queryFactory.create(request)),
                    KeywordIndexDocument.class);
            List<KeywordHit> hits = new ArrayList<>();
            for (Hit<KeywordIndexDocument> hit : response.hits().hits()) {
                KeywordIndexDocument source = hit.source();
                long knowledgeId = source == null
                        ? Long.parseLong(hit.id())
                        : source.knowledgeId();
                hits.add(new KeywordHit(
                        knowledgeId,
                        hit.score() == null ? 0.0 : hit.score()));
            }
            return hits;
        } catch (IOException exception) {
            throw new IllegalStateException("OpenSearch keyword search failed", exception);
        }
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void close() {
        if (transport == null) {
            return;
        }
        try {
            transport.close();
        } catch (Exception exception) {
            LOGGER.warn("Failed to close OpenSearch transport", exception);
        }
    }

    private static HttpHost httpHost(String uri) {
        try {
            return HttpHost.create(uri);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid OpenSearch URI: " + uri, exception);
        }
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }
}
