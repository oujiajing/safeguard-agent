-- Legal Chunk Review Workspace. Review state is deliberately separate from Quality Gate/index eligibility.
CREATE TABLE IF NOT EXISTS t_legal_review_signal (
    id VARCHAR(64) PRIMARY KEY,
    document_id VARCHAR(20) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    target_id VARCHAR(64),
    signal_type VARCHAR(64) NOT NULL,
    stable_key VARCHAR(512) NOT NULL,
    related_clause_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    related_chunk_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    message TEXT NOT NULL,
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    detector_version VARCHAR(32) NOT NULL,
    input_fingerprint VARCHAR(128) NOT NULL,
    lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    review_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_REVIEW',
    review_reason VARCHAR(1000),
    reviewed_by VARCHAR(128),
    reviewed_at TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_legal_review_signal_stable UNIQUE (stable_key)
);
CREATE INDEX IF NOT EXISTS idx_legal_review_signal_document ON t_legal_review_signal (document_id, lifecycle_status);
CREATE INDEX IF NOT EXISTS idx_legal_review_signal_type_status ON t_legal_review_signal (document_id, signal_type, review_status);

CREATE TABLE IF NOT EXISTS t_legal_review_run (
    document_id VARCHAR(20) PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    detector_version VARCHAR(32) NOT NULL,
    signal_count INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(2000),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
