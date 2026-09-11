package com.safeguard.agent.audit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.safeguard.agent.audit.controller.request.BizChangeLogPageRequest;
import com.safeguard.agent.audit.controller.vo.BizChangeLogVO;

public interface BizChangeLogService {

    IPage<BizChangeLogVO> page(BizChangeLogPageRequest requestParam);

    BizChangeLogVO get(String id);
}
