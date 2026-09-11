package com.safeguard.agent.rag.core.retrieval.channel;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safeguard.agent.knowledge.dao.entity.KnowledgeBaseDO;
import com.safeguard.agent.knowledge.dao.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 有效知识库 collection 提供者
 * <p>
 * 全局检索（向量 / 关键词）的唯一「全库范围」来源：只返回未删除（deleted=0）知识库的 collection
 * 两路全局检索共用此处，保证「全局」语义一致——都以知识库表为准，
 * 而非各自用通配（如 ES 的 kb_*），后者会命中已删除库残留、测试库、旧 schema 等无效索引
 */
@Component
@RequiredArgsConstructor
public class KbCollectionProvider {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    /**
     * 返回所有有效知识库的 collection 名称（去重、去空）
     */
    public List<String> listActiveCollections() {
        List<KnowledgeBaseDO> kbList = knowledgeBaseMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                        .select(KnowledgeBaseDO::getCollectionName)
                        .eq(KnowledgeBaseDO::getDeleted, 0)
        );
        return kbList.stream()
                .map(KnowledgeBaseDO::getCollectionName)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
    }
}
