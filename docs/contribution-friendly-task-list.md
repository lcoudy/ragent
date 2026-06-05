# 适合继续贡献的任务清单

本文整理适合后续持续贡献的小任务。目标是保证每次改动真实、有用、范围清晰，并且可以独立提交，方便通过队列脚本按天发布。

## 1. 选任务原则

优先选择满足以下条件的任务：

- 能在 30 到 90 分钟内完成。
- 不需要完整启动所有中间件。
- 改动边界清楚，方便单独 commit。
- 能补充文档、测试、排查说明或轻量体验优化。
- 能帮助理解后端 AI 工程链路，例如检索、模型路由、文档入库、MCP、Trace。

暂时不建议优先做：

- 大规模重构。
- 换技术栈。
- 引入新中间件。
- 一次性修改多个业务域。
- 需要真实模型 key、真实向量库和完整环境才能验证的重功能。

## 2. 文档任务

| 任务 | 建议提交信息 | 入口文件 | 价值 |
|---|---|---|---|
| 模型供应商配置说明 | `docs: add model provider configuration guide` | `bootstrap/src/main/resources/application.yaml`、`infra-ai/` | 帮助理解 chat、embedding、rerank 的配置关系。 |
| 知识库操作说明 | `docs: add knowledge base operation guide` | `knowledge/controller/`、`frontend/src/pages/admin/knowledge/` | 串联知识库、文档、分块和前端页面。 |
| 检索排查指南 | `docs: add retrieval troubleshooting guide` | `rag/core/retrieve/` | 梳理无结果、不准、Rerank 未生效等常见问题。 |
| RAG 问答链路图文说明 | `docs: explain RAG chat request flow` | `RAGChatController`、`StreamChatPipeline` | 适合后续写简历和面试复盘。 |
| MCP 工具扩展说明 | `docs: add MCP tool extension guide` | `mcp-server/executor/`、`rag/core/mcp/` | 说明如何新增工具和参数 schema。 |
| 本地问题排查 FAQ | `docs: add local troubleshooting FAQ` | `docs/quick-start.md`、`CLAUDE.md` | 记录 Java、Maven、Node、数据库常见启动问题。 |

## 3. 测试任务

| 任务 | 建议提交信息 | 入口文件 | 验证重点 |
|---|---|---|---|
| 模型路由失败切换测试 | `test: cover model routing fallback behavior` | `infra-ai/src/main/java/.../model/` | 第一个模型失败后继续尝试下一个候选。 |
| 问题重写工具测试 | `test: cover query rewrite utility behavior` | `rag/core/rewrite/` | 空输入、无映射、多映射命中。 |
| Rerank 后处理测试 | `test: cover rerank post processor behavior` | `rag/core/retrieve/postprocessor/` | 开关关闭、空结果、重排异常兜底。 |
| Prompt 模板加载测试 | `test: cover prompt template loading` | `rag/core/prompt/` | 模板存在、缺失、变量替换。 |
| 文档类型识别测试 | `test: cover file type detection` | `core/parser/`、`ingestion/util/` | 文件名、MIME、未知类型。 |
| MCP 参数提取边界测试 | `test: cover MCP parameter extraction edge cases` | `rag/core/mcp/` | 空参数、非法 JSON、缺失字段。 |

测试类优先写成纯单元测试，不依赖数据库、Redis、RocketMQ、Milvus 或真实模型服务。

## 4. 小型 bugfix 任务

| 任务 | 建议提交信息 | 入口文件 | 注意点 |
|---|---|---|---|
| 修正文档链接或路径 | `docs: fix broken documentation links` | `README.md`、`docs/` | 只改坏链接，不重写大段内容。 |
| 统一接口示例中的端口和 context path | `docs: align API examples with local defaults` | `docs/examples/` | 默认后端是 `9090` 和 `/api/ragent`。 |
| 补充异常日志关键字段 | `fix: improve retrieval error logging` | `rag/core/retrieve/` | 控制日志量，不打印敏感内容。 |
| 补充空值保护 | `fix: guard empty search context` | `rag/core/retrieve/channel/` | 配合测试证明行为。 |
| 修正前端 loading 或空状态文案 | `fix: improve admin empty states` | `frontend/src/pages/admin/` | 改动要小，不引入设计重构。 |

bugfix 必须能说明“原问题是什么、改后行为是什么”。如果只是风格调整，优先归为文档或体验优化。

## 5. 前端体验优化任务

| 任务 | 建议提交信息 | 入口文件 | 验证方式 |
|---|---|---|---|
| Trace 列表筛选状态保持 | `feat: persist trace list filters` | `frontend/src/pages/admin/traces/` | 切详情返回后筛选不丢。 |
| 知识库文档空状态优化 | `fix: improve knowledge document empty state` | `frontend/src/pages/admin/knowledge/` | 空数据时有明确操作入口。 |
| 入库任务失败原因展示 | `feat: show ingestion failure summary` | `frontend/src/pages/admin/ingestion/` | 失败任务能快速看到原因。 |
| 管理后台表格刷新体验 | `fix: improve admin table refresh feedback` | `frontend/src/pages/admin/` | 刷新中按钮状态明确。 |

前端任务应避免大面积改样式。优先做状态、空数据、错误提示、筛选和返回体验。

## 6. 后端 AI 工程任务

这些任务更适合后续准备简历时深入做，不建议一次做太大。

| 任务 | 建议提交信息 | 入口文件 | 可讲点 |
|---|---|---|---|
| 检索命中统计埋点 | `feat: record retrieval channel metrics` | `rag/core/retrieve/` | 多通道召回耗时、结果数、命中率。 |
| Prompt 构建过程可观测 | `feat: trace prompt build details` | `rag/core/prompt/` | Prompt 片段、上下文数量、模板场景。 |
| 模型路由健康状态说明接口 | `feat: expose model health summary` | `infra-ai/model/`、`rag/controller/` | 故障降级和候选状态可视化。 |
| 文档入库节点耗时统计 | `feat: record ingestion node duration` | `ingestion/` | 解析、切分、向量化、持久化耗时。 |
| 检索结果评分归一化 | `feat: normalize retrieval channel scores` | `rag/core/retrieve/postprocessor/` | 多通道结果融合策略。 |

这类任务要配套测试或文档，否则面试时不容易讲清楚。

## 7. 推荐执行顺序

如果没有明确目标，建议按这个顺序继续：

1. 先补文档，建立模块地图。
2. 再补纯单元测试，理解关键分支。
3. 做小型 bugfix，练习读代码和改代码。
4. 最后做后端 AI 工程增强，沉淀简历亮点。

每次开始前先看 `Progress.md`，选择第一个未完成任务。完成后同步更新任务状态，保持一个任务一个 commit。
