package com.safeguard.agent.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.safeguard.agent.framework.convention.Result;
import com.safeguard.agent.framework.web.Results;
import com.safeguard.agent.knowledge.controller.request.KnowledgeChunkBatchRequest;
import com.safeguard.agent.knowledge.controller.request.KnowledgeChunkCreateRequest;
import com.safeguard.agent.knowledge.controller.request.KnowledgeChunkPageRequest;
import com.safeguard.agent.knowledge.controller.request.KnowledgeChunkUpdateRequest;
import com.safeguard.agent.knowledge.controller.vo.KnowledgeChunkVO;
import com.safeguard.agent.knowledge.service.KnowledgeChunkService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库 Chunk 管理接口
 */
@RestController
@RequiredArgsConstructor
@Validated
public class KnowledgeChunkController {

    private final KnowledgeChunkService knowledgeChunkService;

    /**
     * 分页查询 Chunk 列表
     */
    @GetMapping("/knowledge-base/docs/{doc-id}/chunks")
    public Result<IPage<KnowledgeChunkVO>> pageQuery(@PathVariable("doc-id") String docId,
                                                     @Validated KnowledgeChunkPageRequest requestParam) {
        return Results.success(knowledgeChunkService.pageQuery(docId, requestParam));
    }

    @GetMapping("/knowledge-base/docs/{doc-id}/chunk-chapters")
    public Result<List<String>> listChapterNos(@PathVariable("doc-id") String docId) {
        return Results.success(knowledgeChunkService.listChapterNos(docId));
    }

    /**
     * 新增 Chunk
     */
    @PostMapping("/knowledge-base/docs/{doc-id}/chunks")
    public Result<KnowledgeChunkVO> create(@PathVariable("doc-id") String docId,
                                           @RequestBody KnowledgeChunkCreateRequest request) {
        return Results.success(knowledgeChunkService.create(docId, request));
    }

    /**
     * 更新 Chunk 内容
     */
    @PutMapping("/knowledge-base/docs/{doc-id}/chunks/{chunk-id}")
    public Result<Void> update(@PathVariable("doc-id") String docId,
                               @PathVariable("chunk-id") String chunkId,
                               @RequestBody KnowledgeChunkUpdateRequest request) {
        knowledgeChunkService.update(docId, chunkId, request);
        return Results.success();
    }

    /**
     * 删除 Chunk
     */
    @DeleteMapping("/knowledge-base/docs/{doc-id}/chunks/{chunk-id}")
    public Result<Void> delete(@PathVariable("doc-id") String docId,
                               @PathVariable("chunk-id") String chunkId) {
        knowledgeChunkService.delete(docId, chunkId);
        return Results.success();
    }

    /**
     * 启用或禁用单条 Chunk
     */
    @PatchMapping("/knowledge-base/docs/{doc-id}/chunks/{chunk-id}/enable")
    public Result<Void> enable(@PathVariable("doc-id") String docId,
                               @PathVariable("chunk-id") String chunkId,
                               @RequestParam("value") boolean enabled) {
        knowledgeChunkService.enableChunk(docId, chunkId, enabled);
        return Results.success();
    }

    /**
     * 批量启用或禁用 Chunk
     */
    @PatchMapping("/knowledge-base/docs/{doc-id}/chunks/batch-enable")
    public Result<Void> batchEnable(@PathVariable("doc-id") String docId,
                                    @RequestParam("value") boolean enabled,
                                    @RequestBody(required = false) KnowledgeChunkBatchRequest request) {
        knowledgeChunkService.batchToggleEnabled(docId, request, enabled);
        return Results.success();
    }
}
