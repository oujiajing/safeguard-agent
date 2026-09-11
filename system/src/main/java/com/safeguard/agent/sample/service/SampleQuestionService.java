package com.safeguard.agent.sample.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.safeguard.agent.sample.controller.request.SampleQuestionCreateRequest;
import com.safeguard.agent.sample.controller.request.SampleQuestionPageRequest;
import com.safeguard.agent.sample.controller.request.SampleQuestionUpdateRequest;
import com.safeguard.agent.sample.controller.vo.SampleQuestionVO;

import java.util.List;

public interface SampleQuestionService {

    /**
     * 创建示例问题
     */
    String create(SampleQuestionCreateRequest requestParam);

    /**
     * 更新示例问题
     */
    void update(String id, SampleQuestionUpdateRequest requestParam);

    /**
     * 删除示例问题
     */
    void delete(String id);

    /**
     * 查询示例问题详情
     */
    SampleQuestionVO queryById(String id);

    /**
     * 分页查询示例问题列表
     */
    IPage<SampleQuestionVO> pageQuery(SampleQuestionPageRequest requestParam);

    /**
     * 随机获取示例问题列表
     *
     * @param limit 期望条数，越界按上下限收敛
     */
    List<SampleQuestionVO> listRandomQuestions(int limit);
}
