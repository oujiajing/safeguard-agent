package com.safeguard.agent.agent.dao.entity;

import lombok.Data;

import java.util.Date;

/**
 * 每用户一行的长期记忆控制面，读写全走注解 Mapper（要 ON CONFLICT 与 FOR UPDATE）
 */
@Data
public class AgentMemoryControlDO {

    private String userId;

    /**
     * 记忆集版本号，提交期与水位一同双校验
     */
    private Long revision;

    /**
     * 建行时刻兼抽取下界，接入之前的历史消息永不倒灌
     */
    private Date createTime;

    private Date updateTime;
}
