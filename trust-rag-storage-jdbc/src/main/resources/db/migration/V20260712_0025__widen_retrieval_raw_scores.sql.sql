ALTER TABLE rag_trace
    ALTER COLUMN max_vector_score TYPE NUMERIC(16,6),
    ALTER COLUMN avg_vector_score TYPE NUMERIC(16,6),
    ALTER COLUMN max_rerank_score TYPE NUMERIC(16,6);

ALTER TABLE retrieval_log
    ALTER COLUMN vector_score TYPE NUMERIC(16,6),
    ALTER COLUMN keyword_score TYPE NUMERIC(16,6),
    ALTER COLUMN rerank_score TYPE NUMERIC(16,6);
