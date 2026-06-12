package io.github.trustrag.milvus;

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
