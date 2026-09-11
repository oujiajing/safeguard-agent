package com.safeguard.agent;

import com.mzt.logapi.starter.annotation.EnableLogRecord;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ragent 核心应用启动类
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
        "com.safeguard.agent.sample.dao.mapper",
        "com.safeguard.agent.agent.dao.mapper"
})
public class RagentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagentApplication.class, args);
    }
}
