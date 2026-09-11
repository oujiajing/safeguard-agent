package com.safeguard.agent.audit.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BizChangeLogVO {

    private String id;

    private String bizType;

    private String bizId;

    private String operationType;

    private String actionDesc;

    private String beforeSnapshot;

    private String afterSnapshot;

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

    private Date createTime;
}
