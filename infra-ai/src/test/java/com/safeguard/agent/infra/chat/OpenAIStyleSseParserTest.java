package com.safeguard.agent.infra.chat;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIStyleSseParserTest {

    private static final Gson GSON = new Gson();

    @Test
    void normalContentShouldBeRecognizedAsContent() {
        OpenAIStyleSseParser.ParsedEvent event = OpenAIStyleSseParser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}", GSON, false);
        assertTrue(event.hasContent());
        assertEquals("你好", event.content());
        assertFalse(event.completed());
    }

    @Test
    void emptyContentShouldNotBeRecognizedAsContent() {
        OpenAIStyleSseParser.ParsedEvent event = OpenAIStyleSseParser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"\"}}]}", GSON, false);
        assertFalse(event.hasContent());
    }

    @Test
    void blankContentShouldNotBeRecognizedAsContent() {
        OpenAIStyleSseParser.ParsedEvent event = OpenAIStyleSseParser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"   \"}}]}", GSON, false);
        assertFalse(event.hasContent());
    }

    @Test
    void completionWithoutContentShouldBeMarkedCompleted() {
        OpenAIStyleSseParser.ParsedEvent event = OpenAIStyleSseParser.parseLine(
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}", GSON, false);
        assertFalse(event.hasContent());
        assertTrue(event.completed());
    }

    @Test
    void reasoningOnlyShouldNotBeRecognizedAsContent() {
        OpenAIStyleSseParser.ParsedEvent event = OpenAIStyleSseParser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"思考中\"}}]}", GSON, true);
        assertTrue(event.hasReasoning());
        assertFalse(event.hasContent());
    }

    @Test
    void doneMarkerShouldBeRecognized() {
        OpenAIStyleSseParser.ParsedEvent event = OpenAIStyleSseParser.parseLine("data: [DONE]", GSON, false);
        assertFalse(event.hasContent());
        assertTrue(event.completed());
    }

    @Test
    void blankLineShouldReturnEmptyEvent() {
        OpenAIStyleSseParser.ParsedEvent event = OpenAIStyleSseParser.parseLine("", GSON, false);
        assertFalse(event.hasContent());
        assertFalse(event.hasReasoning());
        assertFalse(event.completed());
    }
}
