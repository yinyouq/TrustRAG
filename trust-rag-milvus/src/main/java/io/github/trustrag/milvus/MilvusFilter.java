package io.github.trustrag.milvus;

import java.util.Map;

public record MilvusFilter(String expression, Map<String, Object> templateValues) {

    public MilvusFilter {
        templateValues = Map.copyOf(templateValues);
    }
}
