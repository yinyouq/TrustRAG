package io.github.trustrag.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 检索基础设施内存监控配置。
 *
 * <p>数据源是 Prometheus/cAdvisor，而不是宿主 JVM。因此该能力只统计
 * Milvus 与 OpenSearch 两个检索引擎容器，不会把接入 TrustRAG 的 Java
 * 应用、关系库或模型服务纳入结果。</p>
 */
@ConfigurationProperties(prefix = "trust-rag.infrastructure-memory")
public record InfrastructureMemoryProperties(
        boolean enabled,
        String prometheusUrl,
        String serviceLabel,
        String milvusService,
        String opensearchService,
        Integer defaultWindowMinutes,
        Integer maxWindowMinutes,
        Integer requestTimeoutMillis) {

    public InfrastructureMemoryProperties {
        prometheusUrl = defaultText(prometheusUrl, "http://localhost:9090");
        serviceLabel = defaultText(serviceLabel, "container_label_com_docker_compose_service");
        milvusService = defaultText(milvusService, "milvus");
        opensearchService = defaultText(opensearchService, "opensearch");
        defaultWindowMinutes = defaultPositive(defaultWindowMinutes, 60);
        maxWindowMinutes = Math.max(defaultPositive(maxWindowMinutes, 1440), defaultWindowMinutes);
        requestTimeoutMillis = defaultPositive(requestTimeoutMillis, 3_000);
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int defaultPositive(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }
}
