package com.safeguard.agent.rag.core.memory;

import com.safeguard.agent.framework.convention.ChatMessage;
import com.safeguard.agent.framework.convention.ChatRequest;
import com.safeguard.agent.infra.chat.LLMService;
import com.safeguard.agent.infra.enums.Tier;
import com.safeguard.agent.rag.config.MemoryProperties;
import com.safeguard.agent.rag.core.prompt.AgentPromptResolver;
import com.safeguard.agent.rag.core.prompt.AgentPromptSlot;
import com.safeguard.agent.rag.core.prompt.PromptTemplateLoader;
import com.safeguard.agent.rag.dao.entity.ConversationMessageDO;
import com.safeguard.agent.rag.dao.entity.ConversationSummaryDO;
import com.safeguard.agent.rag.service.ConversationGroupService;
import com.safeguard.agent.rag.service.ConversationMessageService;
import com.safeguard.agent.rag.service.bo.ConversationSummaryBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcConversationMemorySummaryServiceTest {

    private static final String CONVERSATION_ID = "conversation-1";
    private static final String USER_ID = "user-1";

    @Mock
    private ConversationGroupService conversationGroupService;

    @Mock
    private ConversationMessageService conversationMessageService;

    @Mock
    private LLMService llmService;

    @Mock
    private PromptTemplateLoader promptTemplateLoader;

    @Mock
    private AgentPromptResolver agentPromptResolver;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    private JdbcConversationMemorySummaryService service;

    @BeforeEach
    void setUp() {
        MemoryProperties memoryProperties = new MemoryProperties();
        memoryProperties.setSummaryEnabled(true);
        memoryProperties.setSummaryStartTurns(5);
        memoryProperties.setHistoryKeepTurns(4);
        memoryProperties.setSummaryMaxChars(200);

        Executor directExecutor = Runnable::run;
        service = new JdbcConversationMemorySummaryService(
                conversationGroupService,
                conversationMessageService,
                memoryProperties,
                llmService,
                promptTemplateLoader,
                agentPromptResolver,
                redissonClient,
                directExecutor
        );

        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void firstSummaryOverlapsHalfOfTheHistoryWindow() {
        stubSummaryGeneration();
        when(conversationGroupService.countUserMessages(CONVERSATION_ID, USER_ID)).thenReturn(5L);
        when(conversationGroupService.listLatestUserOnlyMessages(CONVERSATION_ID, USER_ID, 4))
                .thenReturn(latestUserTurns());
        when(conversationGroupService.listMessagesBetweenIds(CONVERSATION_ID, USER_ID, null, "40"))
                .thenReturn(List.of(
                        message("10", "user"),
                        message("39", "assistant")
                ));

        service.compressIfNeeded(CONVERSATION_ID, USER_ID, ChatMessage.assistant("answer"));

        verify(conversationGroupService)
                .listMessagesBetweenIds(CONVERSATION_ID, USER_ID, null, "40");
        ArgumentCaptor<ConversationSummaryBO> summaryCaptor = ArgumentCaptor.forClass(ConversationSummaryBO.class);
        verify(conversationMessageService).addMessageSummary(summaryCaptor.capture());
        assertEquals("39", summaryCaptor.getValue().getLastMessageId());
    }

    @Test
    void doesNotRefreshWhileSummaryCoverageStillOverlapsTheHistoryWindow() {
        when(conversationGroupService.countUserMessages(CONVERSATION_ID, USER_ID)).thenReturn(6L);
        when(conversationGroupService.findLatestSummary(CONVERSATION_ID, USER_ID))
                .thenReturn(ConversationSummaryDO.builder()
                        .content("existing summary")
                        .lastMessageId("35")
                        .build());
        when(conversationGroupService.listLatestUserOnlyMessages(CONVERSATION_ID, USER_ID, 4))
                .thenReturn(latestUserTurns());

        service.compressIfNeeded(CONVERSATION_ID, USER_ID, ChatMessage.assistant("answer"));

        verifyNoInteractions(llmService, conversationMessageService);
    }

    @Test
    void refreshesFromPreviousCoverageOnceItFallsBehindTheHistoryWindow() {
        stubSummaryGeneration();
        when(conversationGroupService.countUserMessages(CONVERSATION_ID, USER_ID)).thenReturn(8L);
        when(conversationGroupService.findLatestSummary(CONVERSATION_ID, USER_ID))
                .thenReturn(ConversationSummaryDO.builder()
                        .content("existing summary")
                        .lastMessageId("15")
                        .build());
        when(conversationGroupService.listLatestUserOnlyMessages(CONVERSATION_ID, USER_ID, 4))
                .thenReturn(latestUserTurns());
        when(conversationGroupService.listMessagesBetweenIds(CONVERSATION_ID, USER_ID, "15", "40"))
                .thenReturn(List.of(
                        message("16", "user"),
                        message("39", "assistant")
                ));

        service.compressIfNeeded(CONVERSATION_ID, USER_ID, ChatMessage.assistant("answer"));

        verify(conversationGroupService)
                .listMessagesBetweenIds(CONVERSATION_ID, USER_ID, "15", "40");
        ArgumentCaptor<ConversationSummaryBO> summaryCaptor = ArgumentCaptor.forClass(ConversationSummaryBO.class);
        verify(conversationMessageService).addMessageSummary(summaryCaptor.capture());
        assertEquals("39", summaryCaptor.getValue().getLastMessageId());
    }

    private void stubSummaryGeneration() {
        when(agentPromptResolver.render(eq(AgentPromptSlot.CONVERSATION_SUMMARY), anyMap())).thenReturn("summary prompt");
        when(llmService.chat(any(ChatRequest.class), eq(Tier.FAST))).thenReturn("updated summary");
    }

    private List<ConversationMessageDO> latestUserTurns() {
        return List.of(
                message("50", "user"),
                message("40", "user"),
                message("30", "user"),
                message("20", "user")
        );
    }

    private ConversationMessageDO message(String id, String role) {
        return ConversationMessageDO.builder()
                .id(id)
                .role(role)
                .content(role + "-" + id)
                .build();
    }
}
