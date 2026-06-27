package io.github.trustrag.milvus;

import java.util.Map;

/**
 * MilvusFilter 负责过滤或约束输入内容，降低不合规数据进入知识库的风险。
 */
public record MilvusFilter(String expression, Map<String, Object> templateValues) {

    public MilvusFilter {
        templateValues = Map.copyOf(templateValues);
    }
}
