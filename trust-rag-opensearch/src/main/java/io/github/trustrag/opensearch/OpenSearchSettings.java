package io.github.trustrag.opensearch;

import java.util.List;

/**
 * OpenSearchSettings 保存模块运行配置，避免基础设施参数散落在业务代码中。
 */
public record OpenSearchSettings(
        List<String> uris,
        String username,
        String password,
        String index,
        boolean autoCreateIndex) {

    public OpenSearchSettings {
        uris = uris == null ? List.of() : uris.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
        if (uris.isEmpty()) {
            throw new IllegalArgumentException("At least one OpenSearch URI is required");
        }
        if (index == null || index.isBlank()) {
            throw new IllegalArgumentException("OpenSearch index must not be blank");
        }
    }
}
