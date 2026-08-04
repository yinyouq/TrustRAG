ALTER TABLE knowledge_item ADD COLUMN promotion_stage VARCHAR(32);
ALTER TABLE knowledge_item ADD COLUMN promotion_score DECIMAL(5,4);
ALTER TABLE knowledge_item ADD COLUMN llm_pre_review_result TEXT;
ALTER TABLE knowledge_item ADD COLUMN normalized_claim TEXT;
ALTER TABLE knowledge_item ADD COLUMN claim_hash VARCHAR(64);
ALTER TABLE knowledge_item ADD COLUMN llm_score DECIMAL(5,4);
ALTER TABLE knowledge_item ADD COLUMN source_score DECIMAL(5,4);
ALTER TABLE knowledge_item ADD COLUMN evidence_score DECIMAL(5,4);
ALTER TABLE knowledge_item ADD COLUMN feedback_score DECIMAL(5,4);
ALTER TABLE knowledge_item ADD COLUMN usage_score DECIMAL(5,4);
ALTER TABLE knowledge_item ADD COLUMN general_value_score DECIMAL(5,4);
ALTER TABLE knowledge_item ADD COLUMN privacy_risk DECIMAL(5,4) NOT NULL DEFAULT 0;
ALTER TABLE knowledge_item ADD COLUMN conflict_risk DECIMAL(5,4) NOT NULL DEFAULT 0;
ALTER TABLE knowledge_item ADD COLUMN stale_risk DECIMAL(5,4) NOT NULL DEFAULT 0;
ALTER TABLE knowledge_item ADD COLUMN applicable_version VARCHAR(128);
ALTER TABLE knowledge_item ADD COLUMN valid_from DATETIME(6);
ALTER TABLE knowledge_item ADD COLUMN valid_to DATETIME(6);
ALTER TABLE knowledge_item ADD COLUMN source_time DATETIME(6);
ALTER TABLE knowledge_item ADD COLUMN last_verified_at DATETIME(6);
ALTER TABLE knowledge_item ADD COLUMN usage_count INT NOT NULL DEFAULT 0;
ALTER TABLE knowledge_item ADD COLUMN positive_feedback_count INT NOT NULL DEFAULT 0;
ALTER TABLE knowledge_item ADD COLUMN negative_feedback_count INT NOT NULL DEFAULT 0;
ALTER TABLE knowledge_item ADD COLUMN tags_json TEXT;
ALTER TABLE knowledge_item ADD COLUMN previous_trust_level VARCHAR(32);
ALTER TABLE knowledge_item ADD COLUMN previous_status VARCHAR(32);

UPDATE knowledge_item
SET status = CASE
    WHEN status = 'ENABLED' AND trust_level = 'HIGH' THEN 'HIGH_ENABLED'
    WHEN status = 'ENABLED' AND trust_level = 'MEDIUM' THEN 'HUMAN_REVIEW_PENDING'
    WHEN status = 'ENABLED' AND trust_level = 'LOW' THEN 'LOW_ENABLED'
    WHEN status = 'PENDING_REVIEW' THEN 'LOW_PENDING'
    ELSE status
END;

CREATE INDEX idx_knowledge_claim_hash ON knowledge_item(claim_hash);
CREATE INDEX idx_knowledge_promotion ON knowledge_item(status, trust_level, promotion_score);
CREATE INDEX idx_knowledge_validity ON knowledge_item(valid_to, expires_at);

CREATE TABLE promotion_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at DATETIME(6),
    finished_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_promotion_runnable(status, retry_count, created_at),
    INDEX idx_promotion_knowledge(knowledge_id, task_type, status),
    CONSTRAINT fk_promotion_knowledge FOREIGN KEY (knowledge_id) REFERENCES knowledge_item(id)
);

UPDATE review_task
SET status = 'REJECTED',
    reviewer_id = 'trust-rag-migration',
    review_action = 'migrated-to-promotion',
    review_comment = 'Legacy low-pool review task replaced by promotion governance',
    reviewed_at = CURRENT_TIMESTAMP(6)
WHERE status = 'PENDING'
  AND knowledge_id IN (
      SELECT id FROM knowledge_item WHERE trust_level = 'LOW'
  );

INSERT INTO promotion_task (
    knowledge_id, status, task_type, retry_count, created_at
)
SELECT id, 'PENDING', 'LOW_TO_MEDIUM', 0, CURRENT_TIMESTAMP(6)
FROM knowledge_item
WHERE trust_level = 'LOW'
  AND status IN ('LOW_PENDING', 'PROMOTION_PENDING');

INSERT INTO review_task (
    knowledge_id, status, created_at
)
SELECT item.id, 'PENDING', CURRENT_TIMESTAMP(6)
FROM knowledge_item item
WHERE item.trust_level = 'MEDIUM'
  AND item.status = 'HUMAN_REVIEW_PENDING'
  AND NOT EXISTS (
      SELECT 1 FROM review_task task
      WHERE task.knowledge_id = item.id AND task.status = 'PENDING'
  );

CREATE TABLE conflict_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    candidate_knowledge_id BIGINT NOT NULL,
    existing_knowledge_id BIGINT NOT NULL,
    conflict_type VARCHAR(32) NOT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    reason TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_conflict_candidate(candidate_knowledge_id, created_at),
    CONSTRAINT fk_conflict_candidate FOREIGN KEY (candidate_knowledge_id) REFERENCES knowledge_item(id),
    CONSTRAINT fk_conflict_existing FOREIGN KEY (existing_knowledge_id) REFERENCES knowledge_item(id)
);

CREATE TABLE knowledge_lineage (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_id BIGINT NOT NULL,
    parent_knowledge_id BIGINT,
    source_trace_id VARCHAR(64),
    source_feedback_id BIGINT,
    action VARCHAR(128) NOT NULL,
    operator_type VARCHAR(32) NOT NULL,
    operator_id VARCHAR(64),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_lineage_knowledge(knowledge_id, created_at),
    CONSTRAINT fk_lineage_knowledge FOREIGN KEY (knowledge_id) REFERENCES knowledge_item(id),
    CONSTRAINT fk_lineage_parent FOREIGN KEY (parent_knowledge_id) REFERENCES knowledge_item(id)
);
