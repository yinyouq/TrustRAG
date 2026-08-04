package io.github.trustrag.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 容器内存指标来源的抽象，便于替换 Prometheus 或在测试中提供确定性数据。
 */
interface InfrastructureMemoryMetricSource {

    Map<String, Long> instantValues(String metricName);

    Map<String, List<InfrastructureMemorySnapshot.MemoryPoint>> rangeValues(
            String metricName,
            Instant start,
            Instant end,
            int stepSeconds);
}
