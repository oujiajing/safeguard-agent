-- Phase 2A-1 Legal cleaned TXT ingestion MVP
-- Additive-only migration. Safe to re-run; generic knowledge documents remain nullable on every legal column.

ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS doc_title VARCHAR(256);
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS doc_type VARCHAR(32);
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS standard_no VARCHAR(64);
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS issuing_authority VARCHAR(256);
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS publish_date DATE;
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS effective_date DATE;
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS source_format VARCHAR(32);
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS file_hash VARCHAR(64);
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS parser_version VARCHAR(64);
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS ingestion_stage VARCHAR(16);
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS ingestion_run_id VARCHAR(64);
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS quality_status VARCHAR(16);

CREATE INDEX IF NOT EXISTS idx_knowledge_document_standard_no ON t_knowledge_document (standard_no);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_file_hash ON t_knowledge_document (kb_id, file_hash);
CREATE UNIQUE INDEX IF NOT EXISTS uk_legal_document_hash_parser
    ON t_knowledge_document (kb_id, file_hash, parser_version)
    WHERE file_hash IS NOT NULL AND parser_version IS NOT NULL AND deleted = 0;

ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS parent_clause_id VARCHAR(64);
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS chunk_type VARCHAR(32);
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS chapter_no VARCHAR(64);
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS chapter_title VARCHAR(256);
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS section_no VARCHAR(64);
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS section_title VARCHAR(256);
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS clause_no VARCHAR(64);
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS hierarchy_path VARCHAR(1024);
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS child_range VARCHAR(128);
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS content_role VARCHAR(32);
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS page_start INTEGER;
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS page_end INTEGER;
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS metadata JSONB;
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS index_eligible BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS duplicate_of_clause_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_parent_clause ON t_knowledge_chunk (parent_clause_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_clause_role ON t_knowledge_chunk (doc_id, clause_no, content_role);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_metadata ON t_knowledge_chunk USING gin(metadata);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_index_eligible ON t_knowledge_chunk (index_eligible);

ALTER TABLE t_legal_clause ADD COLUMN IF NOT EXISTS provenance JSONB;
ALTER TABLE t_legal_clause ADD COLUMN IF NOT EXISTS index_eligible BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE t_legal_clause ADD COLUMN IF NOT EXISTS duplicate_of_clause_id VARCHAR(64);

CREATE TABLE IF NOT EXISTS t_legal_document_element (
    id               VARCHAR(64)  NOT NULL PRIMARY KEY,
    document_id      VARCHAR(20)  NOT NULL,
    element_index    INTEGER      NOT NULL,
    raw_text         TEXT         NOT NULL,
    normalized_text  TEXT         NOT NULL,
    structure_type   VARCHAR(32)  NOT NULL,
    content_role     VARCHAR(32)  NOT NULL,
    canonical_number VARCHAR(64),
    page_start       INTEGER,
    page_end         INTEGER,
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_legal_element_order UNIQUE (document_id, element_index)
);
CREATE INDEX IF NOT EXISTS idx_legal_element_document ON t_legal_document_element (document_id);

CREATE TABLE IF NOT EXISTS t_legal_clause (
    id                 VARCHAR(64)  NOT NULL PRIMARY KEY,
    document_id        VARCHAR(20)  NOT NULL,
    content_role       VARCHAR(32)  NOT NULL,
    structure_type     VARCHAR(32)  NOT NULL,
    chapter_no         VARCHAR(64),
    chapter_title      VARCHAR(256),
    section_no         VARCHAR(64),
    section_title      VARCHAR(256),
    clause_no          VARCHAR(64)  NOT NULL,
    hierarchy_path     VARCHAR(1024),
    raw_text           TEXT         NOT NULL,
    normalized_text    TEXT         NOT NULL,
    children_json      JSONB,
    first_element_id   VARCHAR(64)  NOT NULL,
    last_element_id    VARCHAR(64)  NOT NULL,
    page_start         INTEGER,
    page_end           INTEGER,
    provenance         JSONB,
    index_eligible     BOOLEAN      NOT NULL DEFAULT TRUE,
    duplicate_of_clause_id VARCHAR(64),
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_legal_clause_document ON t_legal_clause (document_id);
CREATE INDEX IF NOT EXISTS idx_legal_clause_number_role ON t_legal_clause (document_id, clause_no, content_role);
CREATE INDEX IF NOT EXISTS idx_legal_clause_index_eligible ON t_legal_clause (index_eligible);

CREATE TABLE IF NOT EXISTS t_legal_quality_report (
    id                           VARCHAR(20)  NOT NULL PRIMARY KEY,
    document_id                  VARCHAR(20)  NOT NULL,
    page_count                   INTEGER,
    table_count                  INTEGER      NOT NULL DEFAULT 0,
    parsed_text_length           INTEGER      NOT NULL DEFAULT 0,
    chapter_count                INTEGER      NOT NULL DEFAULT 0,
    section_count                INTEGER      NOT NULL DEFAULT 0,
    clause_count                 INTEGER      NOT NULL DEFAULT 0,
    normative_clause_count       INTEGER      NOT NULL DEFAULT 0,
    commentary_clause_count      INTEGER      NOT NULL DEFAULT 0,
    supplementary_count          INTEGER      NOT NULL DEFAULT 0,
    appendix_count               INTEGER      NOT NULL DEFAULT 0,
    unknown_role_count           INTEGER      NOT NULL DEFAULT 0,
    unstructured_paragraph_count INTEGER      NOT NULL DEFAULT 0,
    duplicate_clause_count       INTEGER      NOT NULL DEFAULT 0,
    chunk_count                  INTEGER      NOT NULL DEFAULT 0,
    oversized_chunk_count        INTEGER      NOT NULL DEFAULT 0,
    empty_chunk_count            INTEGER      NOT NULL DEFAULT 0,
    quality_status               VARCHAR(16)  NOT NULL,
    warnings                     JSONB,
    create_time                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_legal_quality_document ON t_legal_quality_report (document_id, create_time);

-- Reserved for Phase 2B. Cleaned TXT never inserts rows here.
CREATE TABLE IF NOT EXISTS t_legal_table (
    id               VARCHAR(64) NOT NULL PRIMARY KEY,
    document_id      VARCHAR(20) NOT NULL,
    element_id       VARCHAR(64),
    parent_clause_id VARCHAR(64),
    table_no         VARCHAR(64),
    table_title      VARCHAR(512),
    table_text       TEXT,
    table_html       TEXT,
    cells_json       JSONB,
    page_start       INTEGER,
    page_end         INTEGER,
    content_hash     VARCHAR(64),
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_legal_table_document ON t_legal_table (document_id);
