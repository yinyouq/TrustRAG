ALTER TABLE rag_trace
    MODIFY COLUMN max_vector_score DECIMAL(16,6) NULL,
    MODIFY COLUMN avg_vector_score DECIMAL(16,6) NULL,
    MODIFY COLUMN max_rerank_score DECIMAL(16,6) NULL;

ALTER TABLE retrieval_log
    MODIFY COLUMN vector_score DECIMAL(16,6) NULL,
    MODIFY COLUMN keyword_score DECIMAL(16,6) NULL,
    MODIFY COLUMN rerank_score DECIMAL(16,6) NULL;
