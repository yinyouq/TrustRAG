ALTER TABLE retrieval_log ADD COLUMN vector_rank INT;
ALTER TABLE retrieval_log ADD COLUMN keyword_rank INT;
ALTER TABLE retrieval_log ADD COLUMN rrf_score DECIMAL(8,6);

CREATE TABLE index_sync_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_id BIGINT NOT NULL,
    target_index VARCHAR(32) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    target_status VARCHAR(32),
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at DATETIME(6),
    finished_at DATETIME(6),
    INDEX idx_index_sync_runnable(status, retry_count, created_at),
    INDEX idx_index_sync_knowledge(knowledge_id, target_index, status),
    CONSTRAINT fk_index_sync_knowledge
        FOREIGN KEY (knowledge_id) REFERENCES knowledge_item(id)
);
