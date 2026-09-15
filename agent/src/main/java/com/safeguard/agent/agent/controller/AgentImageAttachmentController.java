package com.safeguard.agent.agent.controller;

import com.safeguard.agent.agent.attachment.AgentImageAttachmentService;
import com.safeguard.agent.agent.attachment.AgentImageAttachmentService.ImageAttachment;
import com.safeguard.agent.agent.config.ConditionalOnAgentEngine;
import com.safeguard.agent.framework.context.UserContext;
import com.safeguard.agent.framework.convention.Result;
import com.safeguard.agent.framework.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@ConditionalOnAgentEngine
@RequiredArgsConstructor
public class AgentImageAttachmentController {

    private final AgentImageAttachmentService attachmentService;

    @PostMapping(value = "/agent/v1/attachments/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadResult> upload(@RequestPart("file") MultipartFile file) {
        ImageAttachment attachment = attachmentService.store(UserContext.requireUser().getUserId(), file);
        return Results.success(new UploadResult(
                attachment.id(), attachment.filename(), attachment.contentType(), attachment.bytes().length));
    }

    public record UploadResult(String attachmentId, String filename, String contentType, long size) {}
}
