package io.github.trustrag.admin;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusInfrastructureMemoryMetricSourceTest {

    @Test
    void urlEncodesPromQlLabelSelectorsBeforeRequestingPrometheus() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/query", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = """
                    {"status":"success","data":{"resultType":"vector","result":[
                    {"metric":{"container_label_com_docker_compose_service":"milvus"},"value":[1,"42"]}
                    ]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            InfrastructureMemoryProperties properties = new InfrastructureMemoryProperties(
                    true, "http://127.0.0.1:" + server.getAddress().getPort(),
                    "container_label_com_docker_compose_service", "milvus", "opensearch",
                    60, 1440, 3000);
            PrometheusInfrastructureMemoryMetricSource source =
                    new PrometheusInfrastructureMemoryMetricSource(properties);

            Map<String, Long> values = source.instantValues("container_memory_working_set_bytes");

            assertThat(values).containsEntry("milvus", 42L);
            assertThat(rawQuery.get()).contains("%7B").contains("%7D");
        } finally {
            server.stop(0);
        }
    }
}
