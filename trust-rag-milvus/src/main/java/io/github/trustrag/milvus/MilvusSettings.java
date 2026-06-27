package io.github.trustrag.milvus;

/**
 * MilvusSettings 保存模块运行配置，避免基础设施参数散落在业务代码中。
 */
public record MilvusSettings(
        String database,
        String collection,
        int dimension,
        String metricType,
        boolean autoCreateCollection) {

    public MilvusSettings {
        if (collection == null || collection.isBlank()) {
            throw new IllegalArgumentException("Milvus collection must not be blank");
        }
        if (dimension < 1) {
            throw new IllegalArgumentException("Milvus dimension must be positive");
        }
        database = database == null || database.isBlank() ? "default" : database;
        metricType = metricType == null || metricType.isBlank() ? "COSINE" : metricType;
    }
}
