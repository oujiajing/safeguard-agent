package com.safeguard.agent.agent.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.safeguard.agent.rag.eval.VisualHazardProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AgentImageAttachmentServiceTest {

    @Test
    void attachmentIsBoundToUploader() {
        VisualHazardProperties properties = new VisualHazardProperties();
        AgentImageAttachmentService service = new AgentImageAttachmentService(properties);
        MockMultipartFile file = new MockMultipartFile("file", "site.png", "image/png", new byte[] {1, 2, 3});

        AgentImageAttachmentService.ImageAttachment saved = service.store("user-1", file);

        assertThat(service.requireOwned(saved.id(), "user-1").filename()).isEqualTo("site.png");
        assertThatThrownBy(() -> service.requireOwned(saved.id(), "user-2"))
                .hasMessageContaining("不存在或已过期");
    }

    @Test
    void rejectsNonImageContent() {
        AgentImageAttachmentService service = new AgentImageAttachmentService(new VisualHazardProperties());
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", new byte[] {1});

        assertThatThrownBy(() -> service.store("user-1", file)).hasMessageContaining("只支持图片");
    }
}
