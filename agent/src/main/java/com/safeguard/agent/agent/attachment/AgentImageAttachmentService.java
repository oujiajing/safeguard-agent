package com.safeguard.agent.agent.attachment;

import com.safeguard.agent.framework.exception.ClientException;
import com.safeguard.agent.rag.eval.VisualHazardProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Short-lived, user-bound image handoff between the browser and an Agent tool call. */
@Service
@RequiredArgsConstructor
public class AgentImageAttachmentService {

    private static final Duration TTL = Duration.ofMinutes(30);

    private final VisualHazardProperties properties;
    private final Map<String, ImageAttachment> attachments = new ConcurrentHashMap<>();

    public ImageAttachment store(String userId, MultipartFile file) {
        purgeExpired();
        if (file == null || file.isEmpty()) {
            throw new ClientException("图片内容为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new ClientException("只支持图片附件");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > properties.getMaxImageBytes()) {
                throw new ClientException("图片大小超过限制");
            }
            ImageAttachment attachment = new ImageAttachment(
                    UUID.randomUUID().toString(), userId, file.getOriginalFilename(), contentType, bytes, Instant.now());
            attachments.put(attachment.id(), attachment);
            return attachment;
        } catch (ClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ClientException("图片读取失败");
        }
    }

    public ImageAttachment requireOwned(String attachmentId, String userId) {
        purgeExpired();
        ImageAttachment attachment = attachments.get(attachmentId);
        if (attachment == null || !attachment.userId().equals(userId)) {
            throw new ClientException("图片附件不存在或已过期");
        }
        return attachment;
    }

    private void purgeExpired() {
        Instant threshold = Instant.now().minus(TTL);
        attachments.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(threshold));
    }

    public record ImageAttachment(String id, String userId, String filename, String contentType,
                                  byte[] bytes, Instant createdAt) {}
}
