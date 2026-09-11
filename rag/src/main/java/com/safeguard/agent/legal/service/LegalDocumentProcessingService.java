package com.safeguard.agent.legal.service;

import com.safeguard.agent.core.ingest.VectorTarget;
import com.safeguard.agent.legal.ingest.LegalPdfImportService;
import com.safeguard.agent.legal.model.CleanedTextImportResult;
import com.safeguard.agent.legal.persistence.LegalCorpusIndexingService;
import com.safeguard.agent.legal.persistence.LegalCorpusPersistenceService;
import com.safeguard.agent.legal.review.LegalReviewService;
import com.safeguard.agent.knowledge.dao.entity.KnowledgeDocumentDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Executes the existing Legal PDF pipeline for an already-uploaded document. */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegalDocumentProcessingService {

    private final LegalPdfImportService legalPdfImportService;
    private final LegalCorpusPersistenceService persistenceService;
    private final LegalCorpusIndexingService indexingService;
    private final LegalReviewService legalReviewService;

    public int process(KnowledgeDocumentDO document, byte[] pdfBytes, VectorTarget target, String actor) {
        CleanedTextImportResult result = legalPdfImportService.dryRun(document.getId(), document.getDocName(), pdfBytes);
        persistenceService.replaceUploadedDocument(document, result, actor == null ? "system" : actor);
        try {
            legalReviewService.audit(document.getId());
        } catch (RuntimeException error) {
            log.warn("Legal Chunk 已完成，但复核检测失败，保留检测失败状态, docId={}", document.getId(), error);
        }
        return indexingService.indexUploadedDocument(document.getKbId(), document.getId(), target);
    }

    public void deleteArtifacts(String documentId) {
        persistenceService.clearUploadedDocumentArtifacts(documentId);
    }

    public int reindex(KnowledgeDocumentDO document, VectorTarget target) {
        return indexingService.indexUploadedDocument(document.getKbId(), document.getId(), target);
    }
}
