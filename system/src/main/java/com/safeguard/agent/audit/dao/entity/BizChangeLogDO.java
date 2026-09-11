package com.safeguard.agent.audit.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safeguard.agent.framework.database.JsonbTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "t_biz_change_log", autoResultMap = true)
public class BizChangeLogDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String bizType;

    private String bizId;

    private String operationType;

    private String actionDesc;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String beforeSnapshot;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String afterSnapshot;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String changeDiff;

    private String operatorId;

    private String operatorName;

    private String operatorRole;

    private Boolean success;

    private String errorMessage;

    private String className;

    private String methodName;

    private String ip;

    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
