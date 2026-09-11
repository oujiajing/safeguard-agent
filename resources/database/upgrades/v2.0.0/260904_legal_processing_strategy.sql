ALTER TABLE t_knowledge_document
    ADD COLUMN IF NOT EXISTS processing_strategy VARCHAR(16) NOT NULL DEFAULT 'GENERAL';

ALTER TABLE t_knowledge_document
    ADD CONSTRAINT ck_knowledge_document_processing_strategy
    CHECK (processing_strategy IN ('GENERAL', 'LEGAL'));

UPDATE t_knowledge_document
SET processing_strategy = 'LEGAL'
WHERE processing_strategy = 'GENERAL'
  AND parser_version = 'legal-pdf-mineru-adapter/2.0.0'
  AND deleted = 0;

COMMENT ON COLUMN t_knowledge_document.processing_strategy
    IS '领域处理策略：GENERAL / LEGAL；独立于 process_mode 和 ingestion_spec';
