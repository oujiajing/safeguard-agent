package com.safeguard.agent.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safeguard.agent.rag.config.MemoryProperties;
import com.safeguard.agent.rag.controller.request.ConversationUpdateRequest;
import com.safeguard.agent.rag.controller.vo.ConversationVO;
import com.safeguard.agent.rag.dao.entity.ConversationDO;
import com.safeguard.agent.rag.dao.entity.ConversationMessageDO;
import com.safeguard.agent.rag.dao.entity.ConversationSummaryDO;
import com.safeguard.agent.rag.dao.mapper.ConversationMapper;
import com.safeguard.agent.rag.dao.mapper.ConversationMessageMapper;
import com.safeguard.agent.rag.dao.mapper.ConversationSummaryMapper;
import com.safeguard.agent.framework.context.UserContext;
import com.safeguard.agent.framework.exception.ClientException;
import com.safeguard.agent.rag.service.ConversationService;
import com.safeguard.agent.rag.service.bo.ConversationCreateBO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话服务实现类
 * 处理会话的创建、更新、重命名和删除等业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final ConversationSummaryMapper summaryMapper;
    private final MemoryProperties memoryProperties;
    private final ConversationTitleGenerator titleGenerator;

    @Override
    public List<ConversationVO> listByUserId(String userId) {
        if (StrUtil.isBlank(userId)) {
            return List.of();
        }

        List<ConversationDO> records = conversationMapper.selectList(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getDeleted, 0)
                        .orderByDesc(ConversationDO::getLastTime)
        );
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        return records.stream()
                .map(item -> ConversationVO.builder()
                        .conversationId(item.getConversationId())
                        .title(item.getTitle())
                        .lastTime(item.getLastTime())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void createOrUpdate(ConversationCreateBO request) {
        String userId = request.getUserId();
        String conversationId = request.getConversationId();
        String question = request.getQuestion();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("用户信息缺失");
        }

        ConversationDO existing = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, conversationId)
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getDeleted, 0)
        );

        if (existing == null) {
            String title = titleGenerator.generate(question);
            ConversationDO record = ConversationDO.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .title(title)
                    .lastTime(request.getLastTime())
                    .build();
            conversationMapper.insert(record);
            return;
        }

        existing.setLastTime(request.getLastTime());
        conversationMapper.updateById(existing);
    }

    @Override
    public void rename(String conversationId, ConversationUpdateRequest request) {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            throw new ClientException("会话信息缺失");
        }

        String title = request.getTitle();
        if (StrUtil.isBlank(title)) {
            throw new ClientException("会话名称不能为空");
        }
        int maxLen = memoryProperties.getTitleMaxLength();
        if (title.length() > maxLen) {
            throw new ClientException("会话名称长度不能超过" + maxLen + "个字符");
        }

        ConversationDO record = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, conversationId)
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getDeleted, 0)
        );
        if (record == null) {
            throw new ClientException("会话不存在");
        }

        record.setTitle(title.trim());
        conversationMapper.updateById(record);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(String conversationId) {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            throw new ClientException("会话信息缺失");
        }

        ConversationDO record = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, conversationId)
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getDeleted, 0)
        );
        if (record == null) {
            throw new ClientException("会话不存在");
        }

        conversationMapper.deleteById(record.getId());
        messageMapper.delete(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getConversationId, conversationId)
                        .eq(ConversationMessageDO::getUserId, userId)
                        .eq(ConversationMessageDO::getDeleted, 0)
        );
        summaryMapper.delete(
                Wrappers.lambdaQuery(ConversationSummaryDO.class)
                        .eq(ConversationSummaryDO::getConversationId, conversationId)
                        .eq(ConversationSummaryDO::getUserId, userId)
                        .eq(ConversationSummaryDO::getDeleted, 0)
        );
    }

}
