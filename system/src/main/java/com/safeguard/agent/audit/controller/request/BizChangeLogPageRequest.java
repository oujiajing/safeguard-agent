package com.safeguard.agent.audit.controller.request;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Data
public class BizChangeLogPageRequest extends Page {

    private String bizType;

    private String bizId;

    private String operationType;

    private String operatorId;

    private String operatorName;

    private Boolean success;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date beginTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;
}
