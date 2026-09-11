# SafeGuard Agent

面向建筑施工安全场景的垂直领域 Agent，覆盖隐患理解、法规证据检索、整改建议、人工确认和整改任务闭环。

## 核心能力

- 施工安全隐患分析与证据引用。
- Retrieval、Embedding、可选 Rerank 和 Citation。
- Human-in-the-loop 确认门禁。
- Safe-team REST 集成与任务幂等。
- Agent Trace、Tool Calling 和技能渐进式加载。
- Demo Adapter：无需企业业务系统即可体验完整闭环。

## Demo Runtime

默认 Demo 使用 PostgreSQL/pgvector、Redis、RocketMQ、S3 兼容对象存储和 TEI Embedding。体验者可以通过本地环境变量提供模型 API Key；凭据不会进入 Git。

```text
SAFEGUARD_DEMO_MODE=true
SAFEGUARD_POSTGRES_PASSWORD=<local password>
SAFEGUARD_REDIS_PASSWORD=<local password>
SAFEGUARD_RUSTFS_ACCESS_KEY_ID=<local access key>
SAFEGUARD_RUSTFS_SECRET_ACCESS_KEY=<local secret key>
```

运行所需的数据库、缓存、对象存储和模型服务通过本地环境变量配置，凭据不会进入 Git。

## 安全边界

高风险业务写入必须经过明确确认；Safe-team 负责权限、状态、数据范围和最终业务事实，Agent 不直连 Safe-team 数据库。

## 当前范围

Rerank、Milvus、Elasticsearch、MinerU、OSS 和 Skill Admin 作为可选或管理能力保留。法规评测体系、法规 Gold 数据、Corpus Benchmark 和评测专用运行材料不属于本发布版本。

## 状态

当前为 SafeGuard Agent 独立公开版本，持续通过 CI、安全扫描和 Fresh Clone 验证。
