package com.safeguard.agent;

import com.mzt.logapi.starter.annotation.EnableLogRecord;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * rag 模块测试启动配置，注解镜像 bootstrap 的 SafeGuardApplication
 * 差异：agent.dao.mapper 不在 rag 测试类路径上，故不镜像该扫描包
 */
@SpringBootApplication
@EnableScheduling
@EnableLogRecord(tenant = "safeguard", proxyTargetClass = true)
@MapperScan(basePackages = {
        "com.safeguard.agent.rag.dao.mapper",
        "com.safeguard.agent.ingestion.dao.mapper",
        "com.safeguard.agent.knowledge.dao.mapper",
        "com.safeguard.agent.user.dao.mapper",
        "com.safeguard.agent.audit.dao.mapper",
        "com.safeguard.agent.sample.dao.mapper"
})
public class TestSafeGuardApplication {
}
