ALTER TABLE knowledge_item ADD COLUMN source_title VARCHAR(512);
ALTER TABLE knowledge_item ADD COLUMN source_url TEXT;
ALTER TABLE knowledge_item ADD COLUMN page_number INT;
ALTER TABLE knowledge_item ADD COLUMN section_path TEXT;
ALTER TABLE knowledge_item ADD COLUMN document_id VARCHAR(64);
ALTER TABLE knowledge_item ADD COLUMN chunk_index INT;
CREATE INDEX idx_knowledge_document
    ON knowledge_item(document_id, page_number, chunk_index);

CREATE TABLE document_import_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL UNIQUE,
    source_kind VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    original_filename VARCHAR(512),
    content_type VARCHAR(255),
    source_uri TEXT,
    storage_path TEXT,
    git_ref VARCHAR(255),
    title VARCHAR(512),
    source_type VARCHAR(64) NOT NULL,
    trust_level VARCHAR(32) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    user_id VARCHAR(64),
    conversation_id VARCHAR(64),
    project_id VARCHAR(64),
    tenant_id VARCHAR(64),
    total_documents INT NOT NULL DEFAULT 0,
    total_sections INT NOT NULL DEFAULT 0,
    imported_count INT NOT NULL DEFAULT 0,
    duplicate_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at DATETIME(6),
    finished_at DATETIME(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_document_import_runnable(status, retry_count, created_at)
);
