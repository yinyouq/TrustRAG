package io.github.trustrag.admin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 仅聚合 Milvus 和 OpenSearch 的容器运行内存。
 */
public final class InfrastructureMemoryService {

    private static final String WORKING_SET = "container_memory_working_set_bytes";
    private static final String RSS = "container_memory_rss";

    private final InfrastructureMemoryProperties properties;
    private final InfrastructureMemoryMetricSource source;

    InfrastructureMemoryService(
            InfrastructureMemoryProperties properties,
            InfrastructureMemoryMetricSource source) {
        this.properties = properties;
        this.source = source;
    }

    public InfrastructureMemorySnapshot snapshot(Integer requestedWindowMinutes) {
        int windowMinutes = resolveWindow(requestedWindowMinutes);
        Instant sampledAt = Instant.now();
        if (!properties.enabled()) {
            return empty("DISABLED", "未启用基础设施内存监控。请配置 trust-rag.infrastructure-memory.enabled=true。",
                    sampledAt, windowMinutes);
        }

        Map<String, Long> workingSet;
        try {
            workingSet = source.instantValues(WORKING_SET);
        } catch (RuntimeException exception) {
            return empty("UNAVAILABLE", "无法读取 Prometheus 的容器 Working Set 指标。请检查 Prometheus/cAdvisor。",
                    sampledAt, windowMinutes);
        }

        Map<String, Long> rss = safeInstant(RSS);
        Map<String, List<InfrastructureMemorySnapshot.MemoryPoint>> range = safeRange(
                sampledAt.minusSeconds(windowMinutes * 60L), sampledAt, windowMinutes);
        List<InfrastructureMemorySnapshot.MemoryPoint> trend = aggregateTrend(range);
        List<InfrastructureMemorySnapshot.ServiceMemory> services = services(workingSet, rss, range);
        long totalWorkingSet = services.stream()
                .mapToLong(InfrastructureMemorySnapshot.ServiceMemory::workingSetBytes)
                .sum();
        long totalRss = services.stream()
                .mapToLong(InfrastructureMemorySnapshot.ServiceMemory::rssBytes)
                .sum();
        long peak = trend.stream().mapToLong(InfrastructureMemorySnapshot.MemoryPoint::workingSetBytes)
                .max().orElse(totalWorkingSet);
        boolean allAvailable = services.stream().allMatch(InfrastructureMemorySnapshot.ServiceMemory::available);
        boolean anyAvailable = services.stream().anyMatch(InfrastructureMemorySnapshot.ServiceMemory::available);
        String status = allAvailable ? "AVAILABLE" : anyAvailable ? "PARTIAL" : "UNAVAILABLE";
        String message = switch (status) {
            case "AVAILABLE" -> "仅统计 Milvus 与 OpenSearch 容器；不包含宿主 Java、MySQL、etcd、MinIO 或模型服务。";
            case "PARTIAL" -> "部分检索引擎未返回容器指标，请检查 cAdvisor 标签与服务状态。";
            default -> "未发现 Milvus 或 OpenSearch 的容器指标，请检查 Prometheus/cAdvisor 配置。";
        };
        return new InfrastructureMemorySnapshot(
                status, message, sampledAt, windowMinutes,
                totalWorkingSet, totalRss, peak, services, trend);
    }

    private int resolveWindow(Integer requestedWindowMinutes) {
        if (requestedWindowMinutes == null) {
            return properties.defaultWindowMinutes();
        }
        if (requestedWindowMinutes < 1 || requestedWindowMinutes > properties.maxWindowMinutes()) {
            throw new IllegalArgumentException(
                    "windowMinutes must be between 1 and " + properties.maxWindowMinutes());
        }
        return requestedWindowMinutes;
    }

    private Map<String, Long> safeInstant(String metric) {
        try {
            return source.instantValues(metric);
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    private Map<String, List<InfrastructureMemorySnapshot.MemoryPoint>> safeRange(
            Instant start, Instant end, int windowMinutes) {
        try {
            int stepSeconds = Math.max(15, (int) Math.ceil(windowMinutes * 60.0 / 240));
            return source.rangeValues(WORKING_SET, start, end, stepSeconds);
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    private List<InfrastructureMemorySnapshot.ServiceMemory> services(
            Map<String, Long> workingSet,
            Map<String, Long> rss,
            Map<String, List<InfrastructureMemorySnapshot.MemoryPoint>> range) {
        List<InfrastructureMemorySnapshot.ServiceMemory> services = new ArrayList<>();
        services.add(service(properties.milvusService(), "Milvus", workingSet, rss, range));
        services.add(service(properties.opensearchService(), "OpenSearch", workingSet, rss, range));
        return services;
    }

    private InfrastructureMemorySnapshot.ServiceMemory service(
            String service,
            String displayName,
            Map<String, Long> workingSet,
            Map<String, Long> rss,
            Map<String, List<InfrastructureMemorySnapshot.MemoryPoint>> range) {
        long current = workingSet.getOrDefault(service, 0L);
        long peak = range.getOrDefault(service, List.of()).stream()
                .mapToLong(InfrastructureMemorySnapshot.MemoryPoint::workingSetBytes)
                .max().orElse(current);
        return new InfrastructureMemorySnapshot.ServiceMemory(
                service, displayName, workingSet.containsKey(service), current,
                rss.getOrDefault(service, 0L), peak);
    }

    private List<InfrastructureMemorySnapshot.MemoryPoint> aggregateTrend(
            Map<String, List<InfrastructureMemorySnapshot.MemoryPoint>> range) {
        Map<Instant, Long> totals = new TreeMap<>();
        Set<String> retrievalServices = Set.of(properties.milvusService(), properties.opensearchService());
        range.forEach((service, points) -> {
            if (retrievalServices.contains(service)) {
                points.forEach(point -> totals.merge(point.timestamp(), point.workingSetBytes(), Long::sum));
            }
        });
        return totals.entrySet().stream()
                .map(entry -> new InfrastructureMemorySnapshot.MemoryPoint(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(InfrastructureMemorySnapshot.MemoryPoint::timestamp))
                .toList();
    }

    private InfrastructureMemorySnapshot empty(
            String status,
            String message,
            Instant sampledAt,
            int windowMinutes) {
        return new InfrastructureMemorySnapshot(
                status, message, sampledAt, windowMinutes, 0L, 0L, 0L,
                services(Map.of(), Map.of(), Map.of()), List.of());
    }
}
