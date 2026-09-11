package com.safeguard.agent.audit.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.safeguard.agent.audit.controller.request.BizChangeLogPageRequest;
import com.safeguard.agent.audit.controller.vo.BizChangeLogVO;
import com.safeguard.agent.audit.service.BizChangeLogService;
import com.safeguard.agent.framework.convention.Result;
import com.safeguard.agent.framework.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BizChangeLogController {

    private final BizChangeLogService bizChangeLogService;

    @GetMapping("/biz-change-logs")
    public Result<IPage<BizChangeLogVO>> page(BizChangeLogPageRequest requestParam) {
        return Results.success(bizChangeLogService.page(requestParam));
    }

    @GetMapping("/biz-change-logs/{id}")
    public Result<BizChangeLogVO> get(@PathVariable String id) {
        return Results.success(bizChangeLogService.get(id));
    }
}
