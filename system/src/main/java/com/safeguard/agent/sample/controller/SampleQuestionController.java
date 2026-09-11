package com.safeguard.agent.sample.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.safeguard.agent.framework.convention.Result;
import com.safeguard.agent.framework.web.Results;
import com.safeguard.agent.sample.controller.request.SampleQuestionCreateRequest;
import com.safeguard.agent.sample.controller.request.SampleQuestionPageRequest;
import com.safeguard.agent.sample.controller.request.SampleQuestionUpdateRequest;
import com.safeguard.agent.sample.controller.vo.SampleQuestionVO;
import com.safeguard.agent.sample.service.SampleQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 示例问题控制器
 * 前台各引擎欢迎页取随机若干条，后台走标准 CRUD
 */
@RestController
@RequestMapping("/sample-questions")
@RequiredArgsConstructor
public class SampleQuestionController {

    private final SampleQuestionService sampleQuestionService;

    /**
     * 随机获取示例问题列表，条数由调用方决定
     * 字面量段优先于 /{id} 模板匹配，两者不会打架
     */
    @GetMapping("/random")
    public Result<List<SampleQuestionVO>> listRandom(@RequestParam(defaultValue = "3") int limit) {
        return Results.success(sampleQuestionService.listRandomQuestions(limit));
    }

    /**
     * 分页查询示例问题列表
     */
    @GetMapping
    public Result<IPage<SampleQuestionVO>> pageQuery(SampleQuestionPageRequest requestParam) {
        return Results.success(sampleQuestionService.pageQuery(requestParam));
    }

    /**
     * 查询示例问题详情
     */
    @GetMapping("/{id}")
    public Result<SampleQuestionVO> queryById(@PathVariable String id) {
        return Results.success(sampleQuestionService.queryById(id));
    }

    /**
     * 创建示例问题
     */
    @PostMapping
    public Result<String> create(@RequestBody SampleQuestionCreateRequest requestParam) {
        return Results.success(sampleQuestionService.create(requestParam));
    }

    /**
     * 更新示例问题
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody SampleQuestionUpdateRequest requestParam) {
        sampleQuestionService.update(id, requestParam);
        return Results.success();
    }

    /**
     * 删除示例问题
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        sampleQuestionService.delete(id);
        return Results.success();
    }
}
