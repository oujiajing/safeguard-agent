package com.safeguard.agent.audit.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safeguard.agent.audit.controller.request.BizChangeLogPageRequest;
import com.safeguard.agent.audit.controller.vo.BizChangeLogVO;
import com.safeguard.agent.audit.dao.entity.BizChangeLogDO;
import com.safeguard.agent.audit.dao.mapper.BizChangeLogMapper;
import com.safeguard.agent.audit.service.BizChangeLogService;
import com.safeguard.agent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BizChangeLogServiceImpl implements BizChangeLogService {

    private final BizChangeLogMapper bizChangeLogMapper;

    @Override
    public IPage<BizChangeLogVO> page(BizChangeLogPageRequest requestParam) {
        Page<BizChangeLogDO> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        LambdaQueryWrapper<BizChangeLogDO> queryWrapper = Wrappers.lambdaQuery(BizChangeLogDO.class)
                .eq(StringUtils.hasText(requestParam.getBizType()), BizChangeLogDO::getBizType, requestParam.getBizType())
                .like(StringUtils.hasText(requestParam.getBizId()), BizChangeLogDO::getBizId, requestParam.getBizId())
                .eq(StringUtils.hasText(requestParam.getOperationType()), BizChangeLogDO::getOperationType, requestParam.getOperationType())
                .eq(StringUtils.hasText(requestParam.getOperatorId()), BizChangeLogDO::getOperatorId, requestParam.getOperatorId())
                .like(StringUtils.hasText(requestParam.getOperatorName()), BizChangeLogDO::getOperatorName, requestParam.getOperatorName())
                .eq(requestParam.getSuccess() != null, BizChangeLogDO::getSuccess, requestParam.getSuccess())
                .ge(requestParam.getBeginTime() != null, BizChangeLogDO::getCreateTime, requestParam.getBeginTime())
                .le(requestParam.getEndTime() != null, BizChangeLogDO::getCreateTime, requestParam.getEndTime())
                .orderByDesc(BizChangeLogDO::getCreateTime);
        return bizChangeLogMapper.selectPage(page, queryWrapper)
                .convert(each -> BeanUtil.toBean(each, BizChangeLogVO.class));
    }

    @Override
    public BizChangeLogVO get(String id) {
        BizChangeLogDO record = bizChangeLogMapper.selectById(id);
        if (record == null) {
            throw new ClientException("变更审计日志不存在");
        }
        return BeanUtil.toBean(record, BizChangeLogVO.class);
    }
}
