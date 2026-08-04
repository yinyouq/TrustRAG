package io.github.trustrag.admin;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过 Prometheus HTTP API 读取 cAdvisor 导出的容器内存指标。
 */
final class PrometheusInfrastructureMemoryMetricSource
        implements InfrastructureMemoryMetricSource {

    private final InfrastructureMemoryProperties properties;
    private final RestClient client;

    PrometheusInfrastructureMemoryMetricSource(InfrastructureMemoryProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.requestTimeoutMillis());
        requestFactory.setReadTimeout(properties.requestTimeoutMillis());
        this.client = RestClient.builder()
                .baseUrl(properties.prometheusUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Map<String, Long> instantValues(String metricName) {
        JsonNode response = client.get()
                .uri(prometheusUri("/api/v1/query", expression(metricName)))
                .retrieve()
                .body(JsonNode.class);
        return parseInstant(response);
    }

    @Override
    public Map<String, List<InfrastructureMemorySnapshot.MemoryPoint>> rangeValues(
            String metricName,
            Instant start,
            Instant end,
            int stepSeconds) {
        JsonNode response = client.get()
                .uri(prometheusRangeUri(expression(metricName), start, end, stepSeconds))
                .retrieve()
                .body(JsonNode.class);
        return parseRange(response);
    }

    private String expression(String metricName) {
        String services = properties.milvusService() + "|" + properties.opensearchService();
        return "sum by (" + properties.serviceLabel() + ") ("
                + metricName + "{" + properties.serviceLabel() + "=~\"" + services + "\"})";
    }

    private URI prometheusUri(String path, String expression) {
        return URI.create(baseUrl() + path + "?query=" + urlEncode(expression));
    }

    private URI prometheusRangeUri(String expression, Instant start, Instant end, int stepSeconds) {
        return URI.create(baseUrl() + "/api/v1/query_range?query=" + urlEncode(expression)
                + "&start=" + start.getEpochSecond()
                + "&end=" + end.getEpochSecond()
                + "&step=" + stepSeconds);
    }

    private String baseUrl() {
        return properties.prometheusUrl().replaceAll("/+$", "");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Map<String, Long> parseInstant(JsonNode response) {
        JsonNode result = requireResult(response);
        Map<String, Long> values = new LinkedHashMap<>();
        for (JsonNode row : result) {
            String service = service(row);
            JsonNode value = row.path("value");
            if (!service.isBlank() && value.isArray() && value.size() >= 2) {
                parseBytes(value.get(1).asText()).ifPresent(bytes -> values.put(service, bytes));
            }
        }
        return values;
    }

    private Map<String, List<InfrastructureMemorySnapshot.MemoryPoint>> parseRange(JsonNode response) {
        JsonNode result = requireResult(response);
        Map<String, List<InfrastructureMemorySnapshot.MemoryPoint>> values = new LinkedHashMap<>();
        for (JsonNode row : result) {
            String service = service(row);
            if (service.isBlank()) {
                continue;
            }
            List<InfrastructureMemorySnapshot.MemoryPoint> points = new ArrayList<>();
            for (JsonNode value : row.path("values")) {
                if (!value.isArray() || value.size() < 2) {
                    continue;
                }
                parseBytes(value.get(1).asText()).ifPresent(bytes -> points.add(
                        new InfrastructureMemorySnapshot.MemoryPoint(
                                Instant.ofEpochMilli(Math.round(value.get(0).asDouble() * 1_000)),
                                bytes)));
            }
            values.put(service, points);
        }
        return values;
    }

    private JsonNode requireResult(JsonNode response) {
        if (response == null || !"success".equals(response.path("status").asText())) {
            String error = response == null ? "empty response" : response.path("error").asText("unknown error");
            throw new IllegalStateException("Prometheus query failed: " + error);
        }
        return response.path("data").path("result");
    }

    private String service(JsonNode row) {
        return row.path("metric").path(properties.serviceLabel()).asText();
    }

    private java.util.Optional<Long> parseBytes(String value) {
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) && parsed >= 0
                    ? java.util.Optional.of(Math.round(parsed))
                    : java.util.Optional.empty();
        } catch (NumberFormatException exception) {
            return java.util.Optional.empty();
        }
    }
}
