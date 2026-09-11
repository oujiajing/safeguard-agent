package com.safeguard.agent.agent.integration.safeteam;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safeguard.agent.agent.config.ConditionalOnAgentEngine;
import com.safeguard.agent.rag.core.intent.IntentTreeCacheManager;
import com.safeguard.agent.rag.dao.entity.IntentNodeDO;
import com.safeguard.agent.rag.dao.mapper.IntentNodeMapper;
import com.safeguard.agent.rag.enums.IntentKind;
import com.safeguard.agent.rag.enums.IntentLevel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/** Idempotently makes the four Phase 1 MCP bindings available in a fresh local safeguard DB. */
@Slf4j
@Component
@ConditionalOnAgentEngine
@RequiredArgsConstructor
public class SafeTeamIntentNodeInitializer {
    private static final String PARENT_CODE = "safeguard-rectification";

    private final IntentNodeMapper mapper;
    private final IntentTreeCacheManager cacheManager;

    @PostConstruct
    void ensureNodes() {
        boolean changed = ensureNode(PARENT_CODE, "隐患整改", IntentLevel.DOMAIN.getCode(), null,
                "查询或通过 Safe-team 开发测试入口操作隐患整改工单");
        changed |= ensureNode("safeguard-rectification-search", "查询整改工单", IntentLevel.CATEGORY.getCode(),
                "search_rectification_orders", "按状态、组织、责任人或业务日期查询隐患整改工单");
        changed |= ensureNode("safeguard-rectification-detail", "查询整改工单详情", IntentLevel.CATEGORY.getCode(),
                "get_rectification_order", "查询指定隐患整改工单的当前状态、明细和版本");
        changed |= ensureNode("safeguard-rectification-create", "创建整改工单", IntentLevel.CATEGORY.getCode(),
                "create_rectification_order", "创建 Safe-team 手工隐患整改工单，仅允许开发测试入口明确执行");
        changed |= ensureNode("safeguard-rectification-issue", "下发整改", IntentLevel.CATEGORY.getCode(),
                "issue_rectification", "下发 Safe-team 待派发整改工单，仅允许开发测试入口明确执行");
        if (changed) {
            cacheManager.clearIntentTreeCache();
            log.info("SafeGuard Phase 1 Intent Tool 节点已确保存在");
        }
    }

    private boolean ensureNode(String intentCode, String name, int level, String toolId, String description) {
        Long count = mapper.selectCount(Wrappers.<IntentNodeDO>lambdaQuery()
                .eq(IntentNodeDO::getIntentCode, intentCode)
                .eq(IntentNodeDO::getDeleted, 0));
        if (count != null && count > 0) {
            return false;
        }
        IntentNodeDO node = IntentNodeDO.builder()
                .id(IdUtil.getSnowflakeNextIdStr())
                .intentCode(intentCode)
                .name(name)
                .level(level)
                .parentCode(PARENT_CODE.equals(intentCode) ? null : PARENT_CODE)
                .description(description)
                .kind(IntentKind.MCP.getCode())
                .mcpToolId(toolId)
                .examples(null)
                .sortOrder(0)
                .enabled(1)
                .deleted(0)
                .build();
        mapper.insert(node);
        return true;
    }
}
