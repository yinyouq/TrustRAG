ALTER TABLE rag_trace ADD COLUMN trace_type VARCHAR(32) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE rag_trace ADD COLUMN eval_run_id BIGINT;
ALTER TABLE rag_trace ADD COLUMN eval_case_id BIGINT;
CREATE INDEX idx_rag_trace_eval
    ON rag_trace(trace_type, eval_run_id, eval_case_id);

CREATE TABLE eval_dataset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    tenant_id VARCHAR(64),
    project_id VARCHAR(64),
    created_by VARCHAR(64),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_eval_dataset_scope(tenant_id, project_id, enabled)
);

CREATE TABLE eval_case (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    question TEXT NOT NULL,
    expected_answer TEXT,
    tenant_id VARCHAR(64),
    project_id VARCHAR(64),
    user_id VARCHAR(64),
    conversation_id VARCHAR(64),
    tags TEXT,
    difficulty VARCHAR(32),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_eval_case_dataset(dataset_id, enabled, id),
    CONSTRAINT fk_eval_case_dataset FOREIGN KEY (dataset_id) REFERENCES eval_dataset(id)
);

CREATE TABLE eval_case_expected_knowledge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    eval_case_id BIGINT NOT NULL,
    knowledge_id BIGINT NOT NULL,
    relevance_grade INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_eval_expected_case_knowledge(eval_case_id, knowledge_id),
    CONSTRAINT fk_eval_expected_case FOREIGN KEY (eval_case_id) REFERENCES eval_case(id),
    CONSTRAINT fk_eval_expected_knowledge FOREIGN KEY (knowledge_id) REFERENCES knowledge_item(id)
);

CREATE TABLE eval_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    run_name VARCHAR(255),
    run_type VARCHAR(32) NOT NULL,
    before_after_group VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(32) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    engine_config_snapshot TEXT,
    model_config_snapshot TEXT,
    knowledge_snapshot_time DATETIME(6),
    started_at DATETIME(6),
    finished_at DATETIME(6),
    error_message TEXT,
    created_by VARCHAR(64),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_eval_run_dataset(dataset_id, created_at),
    INDEX idx_eval_run_status(status, created_at),
    CONSTRAINT fk_eval_run_dataset FOREIGN KEY (dataset_id) REFERENCES eval_dataset(id)
);

CREATE TABLE eval_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    eval_run_id BIGINT NOT NULL,
    eval_case_id BIGINT NOT NULL,
    trace_id VARCHAR(64),
    question TEXT NOT NULL,
    expected_answer TEXT,
    answer TEXT,
    retrieved_count INT NOT NULL DEFAULT 0,
    expected_knowledge_count INT NOT NULL DEFAULT 0,
    recall_at_5 DECIMAL(8,6),
    recall_at_10 DECIMAL(8,6),
    precision_at_5 DECIMAL(8,6),
    precision_at_10 DECIMAL(8,6),
    mrr DECIMAL(8,6),
    ndcg_at_5 DECIMAL(8,6),
    ndcg_at_10 DECIMAL(8,6),
    faithfulness DECIMAL(8,6),
    answer_correctness DECIMAL(8,6),
    answer_relevance DECIMAL(8,6),
    hallucination_score DECIMAL(8,6),
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_eval_result_run(eval_run_id, eval_case_id),
    CONSTRAINT fk_eval_result_run FOREIGN KEY (eval_run_id) REFERENCES eval_run(id),
    CONSTRAINT fk_eval_result_case FOREIGN KEY (eval_case_id) REFERENCES eval_case(id)
);

CREATE TABLE eval_judge_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    eval_result_id BIGINT NOT NULL,
    eval_run_id BIGINT NOT NULL,
    eval_case_id BIGINT NOT NULL,
    judge_type VARCHAR(64) NOT NULL,
    model VARCHAR(128),
    prompt TEXT,
    raw_output TEXT,
    score DECIMAL(8,6),
    passed BOOLEAN,
    reason TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_eval_judge_result(eval_result_id, judge_type),
    CONSTRAINT fk_eval_judge_result FOREIGN KEY (eval_result_id) REFERENCES eval_result(id)
);

CREATE TABLE eval_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    eval_run_id BIGINT NOT NULL UNIQUE,
    dataset_id BIGINT NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    avg_recall_at_5 DECIMAL(8,6),
    avg_recall_at_10 DECIMAL(8,6),
    avg_precision_at_5 DECIMAL(8,6),
    avg_precision_at_10 DECIMAL(8,6),
    avg_mrr DECIMAL(8,6),
    avg_ndcg_at_5 DECIMAL(8,6),
    avg_ndcg_at_10 DECIMAL(8,6),
    avg_faithfulness DECIMAL(8,6),
    avg_answer_correctness DECIMAL(8,6),
    avg_answer_relevance DECIMAL(8,6),
    avg_hallucination_score DECIMAL(8,6),
    avg_latency_ms DECIMAL(12,3),
    p90_latency_ms DECIMAL(12,3),
    summary_json TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_eval_report_run FOREIGN KEY (eval_run_id) REFERENCES eval_run(id)
);

CREATE TABLE eval_compare_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    before_run_id BIGINT NOT NULL,
    after_run_id BIGINT NOT NULL,
    recall_at_10_delta DECIMAL(8,6),
    mrr_delta DECIMAL(8,6),
    faithfulness_delta DECIMAL(8,6),
    answer_correctness_delta DECIMAL(8,6),
    hallucination_score_delta DECIMAL(8,6),
    conclusion TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE eval_governance_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id VARCHAR(64),
    project_id VARCHAR(64),
    snapshot_date DATE NOT NULL,
    total_candidate_count BIGINT NOT NULL DEFAULT 0,
    approved_candidate_count BIGINT NOT NULL DEFAULT 0,
    rejected_candidate_count BIGINT NOT NULL DEFAULT 0,
    candidate_approval_rate DECIMAL(8,6),
    knowledge_reuse_rate DECIMAL(8,6),
    contamination_rate DECIMAL(8,6),
    privacy_leakage_rate DECIMAL(8,6),
    gap_resolve_rate DECIMAL(8,6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_eval_governance_scope_date(tenant_id, project_id, snapshot_date)
);

CREATE TABLE privacy_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(64),
    knowledge_id BIGINT,
    tenant_id VARCHAR(64),
    project_id VARCHAR(64),
    event_type VARCHAR(64) NOT NULL,
    risk_level VARCHAR(32),
    description TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_privacy_event_scope_time(tenant_id, project_id, created_at)
);
