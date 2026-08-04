package io.github.trustrag.admin;

import java.time.Instant;
import java.util.List;

/**
 * 面向管理台的检索基础设施内存快照。
 */
public record InfrastructureMemorySnapshot(
        String status,
        String message,
        Instant sampledAt,
        int windowMinutes,
        long workingSetBytes,
        long rssBytes,
        long peakWorkingSetBytes,
        List<ServiceMemory> services,
        List<MemoryPoint> workingSetTrend) {

    /** 单个检索引擎的容器内存。 */
    public record ServiceMemory(
            String service,
            String displayName,
            boolean available,
            long workingSetBytes,
            long rssBytes,
            long peakWorkingSetBytes) {
    }

    /** 聚合后的 Working Set 时序点。 */
    public record MemoryPoint(Instant timestamp, long workingSetBytes) {
    }
}
