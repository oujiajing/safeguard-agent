package com.safeguard.agent.rag.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safeguard.agent.rag.controller.vo.ConversationMessageVO;
import com.safeguard.agent.rag.dao.entity.ConversationDO;
import com.safeguard.agent.rag.dao.entity.ConversationMessageDO;
import com.safeguard.agent.rag.dao.entity.ConversationSummaryDO;
import com.safeguard.agent.rag.dao.mapper.ConversationMapper;
import com.safeguard.agent.rag.dao.mapper.ConversationMessageMapper;
import com.safeguard.agent.rag.dao.mapper.ConversationSummaryMapper;
import com.safeguard.agent.rag.enums.ConversationMessageOrder;
import com.safeguard.agent.rag.service.MessageFeedbackService;
import com.safeguard.agent.rag.service.ConversationMessageService;
import com.safeguard.agent.rag.service.bo.ConversationMessageBO;
import com.safeguard.agent.rag.service.bo.ConversationSummaryBO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConversationMessageServiceImpl implements ConversationMessageService {

    private final ConversationMessageMapper conversationMessageMapper;
    private final ConversationSummaryMapper conversationSummaryMapper;
    private final ConversationMapper conversationMapper;
    private final MessageFeedbackService feedbackService;

    @Override
    public String addMessage(ConversationMessageBO conversationMessage) {
        ConversationMessageDO messageDO = BeanUtil.toBean(conversationMessage, ConversationMessageDO.class);
        conversationMessageMapper.insert(messageDO);
        return messageDO.getId();
    }

    @Override
    public List<ConversationMessageVO> listMessages(String conversationId, String userId, Integer limit, ConversationMessageOrder order) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            return List.of();
        }

        ConversationDO conversation = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, conversationId)
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getDeleted, 0)
        );
        if (conversation == null) {
            return List.of();
        }

        boolean asc = order == null || order == ConversationMessageOrder.ASC;
        List<ConversationMessageDO> records = conversationMessageMapper.selectList(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getConversationId, conversationId)
                        .eq(ConversationMessageDO::getUserId, userId)
                        .eq(ConversationMessageDO::getDeleted, 0)
                        .orderBy(true, asc, ConversationMessageDO::getCreateTime)
                        .orderBy(true, asc, ConversationMessageDO::getId)
                        .last(limit != null, "limit " + limit)
        );
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        if (!asc) {
            Collections.reverse(records);
        }

        List<String> assistantMessageIds = records.stream()
                .filter(record -> "assistant".equalsIgnoreCase(record.getRole()))
                .map(ConversationMessageDO::getId)
                .toList();
        Map<String, Integer> votesByMessageId = feedbackService.getUserVotes(userId, assistantMessageIds);

        List<ConversationMessageVO> result = new ArrayList<>();
        for (ConversationMessageDO record : records) {
            ConversationMessageVO vo = ConversationMessageVO.builder()
                    .id(String.valueOf(record.getId()))
                    .conversationId(record.getConversationId())
                    .role(record.getRole())
                    .content(record.getContent())
                    .thinkingContent(record.getThinkingContent())
                    .thinkingDuration(record.getThinkingDuration())
                    .vote(votesByMessageId.get(record.getId()))
                    .sources(record.getSources())
                    .recommendedQuestions(record.getRecommendedQuestions())
                    .messageStatus(record.getMessageStatus())
                    .createTime(record.getCreateTime())
                    .build();
            result.add(vo);
        }

        return result;
    }

    @Override
    public void addMessageSummary(ConversationSummaryBO conversationSummary) {
        ConversationSummaryDO conversationSummaryDO = BeanUtil.toBean(conversationSummary, ConversationSummaryDO.class);
        conversationSummaryMapper.insert(conversationSummaryDO);
    }
}
