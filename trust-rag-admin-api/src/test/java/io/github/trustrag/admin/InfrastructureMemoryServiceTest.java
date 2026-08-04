package io.github.trustrag.admin;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InfrastructureMemoryServiceTest {

    @Test
    void aggregatesOnlyMilvusAndOpenSearchAtEachTimePoint() {
        InfrastructureMemoryMetricSource source = new InfrastructureMemoryMetricSource() {
            @Override
            public Map<String, Long> instantValues(String metric) {
                if (metric.equals("container_memory_working_set_bytes")) {
                    return Map.of("milvus", 120L, "opensearch", 210L, "mysql", 999L);
                }
                return Map.of("milvus", 100L, "opensearch", 180L, "mysql", 999L);
            }

            @Override
            public Map<String, List<InfrastructureMemorySnapshot.MemoryPoint>> rangeValues(
                    String metric, Instant start, Instant end, int stepSeconds) {
                Instant first = Instant.parse("2026-08-04T00:00:00Z");
                Instant second = first.plusSeconds(15);
                return Map.of(
                        "milvus", List.of(
                                new InfrastructureMemorySnapshot.MemoryPoint(first, 100L),
                                new InfrastructureMemorySnapshot.MemoryPoint(second, 130L)),
                        "opensearch", List.of(
                                new InfrastructureMemorySnapshot.MemoryPoint(first, 200L),
                                new InfrastructureMemorySnapshot.MemoryPoint(second, 220L)),
                        "mysql", List.of(new InfrastructureMemorySnapshot.MemoryPoint(second, 999L)));
            }
        };
        InfrastructureMemoryService service = new InfrastructureMemoryService(properties(true), source);

        InfrastructureMemorySnapshot snapshot = service.snapshot(60);

        assertThat(snapshot.status()).isEqualTo("AVAILABLE");
        assertThat(snapshot.workingSetBytes()).isEqualTo(330L);
        assertThat(snapshot.rssBytes()).isEqualTo(280L);
        assertThat(snapshot.peakWorkingSetBytes()).isEqualTo(350L);
        assertThat(snapshot.services()).extracting(InfrastructureMemorySnapshot.ServiceMemory::service)
                .containsExactly("milvus", "opensearch");
        assertThat(snapshot.workingSetTrend())
                .extracting(InfrastructureMemorySnapshot.MemoryPoint::workingSetBytes)
                .containsExactly(300L, 350L);
    }

    @Test
    void reportsDisabledWithoutQueryingMetricSource() {
        InfrastructureMemoryMetricSource source = new InfrastructureMemoryMetricSource() {
            @Override
            public Map<String, Long> instantValues(String metric) {
                throw new AssertionError("disabled monitoring must not query Prometheus");
            }

            @Override
            public Map<String, List<InfrastructureMemorySnapshot.MemoryPoint>> rangeValues(
                    String metric, Instant start, Instant end, int stepSeconds) {
                throw new AssertionError("disabled monitoring must not query Prometheus");
            }
        };

        InfrastructureMemorySnapshot snapshot = new InfrastructureMemoryService(properties(false), source)
                .snapshot(null);

        assertThat(snapshot.status()).isEqualTo("DISABLED");
        assertThat(snapshot.workingSetBytes()).isZero();
    }

    @Test
    void rejectsWindowsOutsideConfiguredRetention() {
        InfrastructureMemoryService service = new InfrastructureMemoryService(properties(true), new EmptySource());

        assertThatThrownBy(() -> service.snapshot(1_441))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("windowMinutes");
    }

    private InfrastructureMemoryProperties properties(boolean enabled) {
        return new InfrastructureMemoryProperties(
                enabled, "http://localhost:9090", "container_label_com_docker_compose_service",
                "milvus", "opensearch", 60, 1440, 1000);
    }

    private static final class EmptySource implements InfrastructureMemoryMetricSource {
        @Override
        public Map<String, Long> instantValues(String metric) {
            return Map.of();
        }

        @Override
        public Map<String, List<InfrastructureMemorySnapshot.MemoryPoint>> rangeValues(
                String metric, Instant start, Instant end, int stepSeconds) {
            return Map.of();
        }
    }
}
