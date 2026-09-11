package com.safeguard.agent.rag.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safeguard.agent.rag.dao.entity.MessageFeedbackDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface MessageFeedbackMapper extends BaseMapper<MessageFeedbackDO> {

    @Insert("""
            INSERT INTO t_message_feedback
                (id, message_id, conversation_id, user_id, vote, reason, comment, create_time, update_time, deleted)
            VALUES
                (#{feedback.id}, #{feedback.messageId}, #{feedback.conversationId}, #{feedback.userId},
                 #{feedback.vote}, #{feedback.reason}, #{feedback.comment},
                 #{feedback.createTime}, #{feedback.updateTime}, 0)
            ON CONFLICT (message_id, user_id) DO UPDATE SET
                conversation_id = EXCLUDED.conversation_id,
                vote = EXCLUDED.vote,
                reason = EXCLUDED.reason,
                comment = EXCLUDED.comment,
                update_time = EXCLUDED.update_time,
                deleted = 0
            WHERE t_message_feedback.update_time < EXCLUDED.update_time
            """)
    int upsertActiveFeedback(@Param("feedback") MessageFeedbackDO feedback);

    /**
     * 写入取消反馈；记录不存在时创建逻辑删除占位，保证重复取消幂等。
     */
    @Insert("""
            INSERT INTO t_message_feedback
                (id, message_id, conversation_id, user_id, vote, reason, comment, create_time, update_time, deleted)
            VALUES
                (#{feedback.id}, #{feedback.messageId}, #{feedback.conversationId}, #{feedback.userId},
                 0, NULL, NULL, #{feedback.createTime}, #{feedback.updateTime}, 1)
            ON CONFLICT (message_id, user_id) DO UPDATE SET
                update_time = EXCLUDED.update_time,
                deleted = 1
            WHERE t_message_feedback.update_time < EXCLUDED.update_time
            """)
    int upsertCancelledFeedback(@Param("feedback") MessageFeedbackDO feedback);
}
