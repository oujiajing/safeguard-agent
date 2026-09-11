package com.safeguard.agent.rag.core.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitationMarkupTest {

    @Test
    void stripsOnlyInlineCitationLinks() {
        String content = "制度要求如下。[1](#cite-1)[2](#cite-2) 详情见[员工手册](https://example.com)。";

        assertEquals("制度要求如下。 详情见[员工手册](https://example.com)。",
                CitationMarkup.strip(content));
    }
}
