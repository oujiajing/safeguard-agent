package com.safeguard.agent.knowledge.schedule;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safeguard.agent.framework.exception.ClientException;
import com.safeguard.agent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.safeguard.agent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.safeguard.agent.knowledge.enums.DocumentStatus;
import com.safeguard.agent.rag.dto.StoredFileDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentStatusHelper {

    private static final String SYSTEM_USER = "system";

    private final KnowledgeDocumentMapper documentMapper;

    public boolean tryMarkRunning(String docId) {
        // Wrapper 更新不触发 updateTime 自动填充, 显式刷新, 使卡死恢复以分块开始时刻为基准
        return documentMapper.update(
                Wrappers.lambdaUpdate(KnowledgeDocumentDO.class)
                        .set(KnowledgeDocumentDO::getStatus, DocumentStatus.RUNNING.getCode())
                        .set(KnowledgeDocumentDO::getUpdatedBy, SYSTEM_USER)
                        .set(KnowledgeDocumentDO::getUpdateTime, new Date())
                        .eq(KnowledgeDocumentDO::getId, docId)
                        .eq(KnowledgeDocumentDO::getDeleted, 0)
                        .eq(KnowledgeDocumentDO::getEnabled, 1)
                        .ne(KnowledgeDocumentDO::getStatus, DocumentStatus.RUNNING.getCode())
        ) > 0;
    }

    public void markFailedIfRunning(String docId) {
        documentMapper.update(
                Wrappers.lambdaUpdate(KnowledgeDocumentDO.class)
                        .set(KnowledgeDocumentDO::getStatus, DocumentStatus.FAILED.getCode())
                        .set(KnowledgeDocumentDO::getUpdatedBy, SYSTEM_USER)
                        .eq(KnowledgeDocumentDO::getId, docId)
                        .eq(KnowledgeDocumentDO::getStatus, DocumentStatus.RUNNING.getCode())
        );
    }

    public void applyRefreshedFileMetadata(String docId, StoredFileDTO stored) {
        KnowledgeDocumentDO update = KnowledgeDocumentDO.builder()
                .id(docId)
                .docName(stored.getOriginalFilename())
                .fileUrl(stored.getUrl())
                .fileType(stored.getDetectedType())
                .fileSize(stored.getSize())
                .updatedBy(SYSTEM_USER)
                .build();
        int updated = documentMapper.updateById(update);
        if (updated == 0) {
            throw new ClientException("文档不存在");
        }
    }

    public StuckRecoveryResult recoverStuckRunning(long timeoutMinutes) {
        long safeTimeout = Math.max(timeoutMinutes, 10);
        Date threshold = new Date(System.currentTimeMillis() - safeTimeout * 60 * 1000);

        List<String> stuckDocIds = documentMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                        .select(KnowledgeDocumentDO::getId)
                        .eq(KnowledgeDocumentDO::getStatus, DocumentStatus.RUNNING.getCode())
                        .eq(KnowledgeDocumentDO::getEnabled, 1)
                        .lt(KnowledgeDocumentDO::getUpdateTime, threshold)
        ).stream().map(KnowledgeDocumentDO::getId).toList();

        if (stuckDocIds.isEmpty()) {
            return new StuckRecoveryResult(List.of(), 0);
        }

        int updated = documentMapper.update(
                Wrappers.lambdaUpdate(KnowledgeDocumentDO.class)
                        .set(KnowledgeDocumentDO::getStatus, DocumentStatus.FAILED.getCode())
                        .set(KnowledgeDocumentDO::getUpdatedBy, SYSTEM_USER)
                        .in(KnowledgeDocumentDO::getId, stuckDocIds)
                        .eq(KnowledgeDocumentDO::getStatus, DocumentStatus.RUNNING.getCode())
        );

        if (updated != stuckDocIds.size()) {
            log.warn("卡死文档恢复时部分候选状态已变化: 候选 {} 个, 实际重置 {} 个",
                    stuckDocIds.size(), updated);
        }

        return new StuckRecoveryResult(stuckDocIds, updated);
    }

    public record StuckRecoveryResult(List<String> stuckDocIds, int actualRecovered) {
    }
}
