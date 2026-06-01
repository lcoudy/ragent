# Ragent 贡献进度

本文件是本仓库 AI 辅助贡献工作的唯一事实来源。

后续继续贡献任务前，先阅读本文件，再选择第一个状态为 `Todo` 的任务。除非用户明确指定某一天或某个任务，不要跳过前面的待办项。

## 当前状态

- 当前分支：`main`
- 远程仓库：`https://github.com/lcoudy/ragent.git`
- GitHub 用户名：`lcoudy`
- Git 作者邮箱：`1020246530@qq.com`
- 已完成贡献任务：Day 1 到 Day 10
- 下一项任务：Day 11，`docs: add database initialization guide`
- 开始新任务前的工作区预期：干净；如果用户明确要求继续未提交改动，则按用户要求处理

## 每日贡献规则

1. 从任务表中选择第一个状态为 `Todo` 的任务。
2. 做一个真实、有用、范围清晰的改进，并确保它匹配所选任务。
3. 优先做到一个任务对应一个独立 commit。
4. 不创建空提交、时间戳提交或无意义文件改动。
5. 在可行时运行最相关的轻量检查。
6. 任务完成后更新本文件：
   - 将完成的任务标记为 `Done`
   - 补充或更新 commit 标识/提交信息
   - 更新“当前状态”
   - 更新下一项任务
7. 每日任务完成并检查后，默认提交并推送；除非用户明确要求不要提交或不要推送。

## 队列式自动发布

如果要现在提前准备多项真实改进，并在后续每天自动发布一部分，使用本地队列分支。

推荐流程：

```powershell
git checkout -b contribution-queue
# 在 contribution-queue 上每个 commit 做一个完整改进

# 预览下一条队列 commit
./publish_queued_commit.ps1 -DryRun

# 运行指定检查，检查通过后发布下一条队列 commit
./publish_queued_commit.ps1 -CheckCommand "./mvnw.cmd -pl bootstrap -Dtest=DeduplicationPostProcessorTest test"
```

根目录脚本 `publish_queued_commit.ps1` 会从 `main..contribution-queue` 中选择最早的一条 commit，cherry-pick 到 `main`，可选运行 `-CheckCommand` 指定的检查，然后 push `main`。如果检查失败，脚本会自动中止 cherry-pick。

重要规则：脚本只认识 commit，不会自动判断或拆分“几天的工作量”。一天要自动发布的内容必须提前做成一个独立 commit。如果三天内容放在一个 commit 里，脚本会在一天内一次性发布；如果要分三天发布，就需要提前拆成三个 commit。

## 已完成任务

| Day | 状态 | 提交 | 说明 |
|---|---|---|---|
| 1 | Done | `33ab64e` | 梳理本地依赖说明，包括 PostgreSQL、Redis、RocketMQ、RustFS 和 AI Key。 |
| 2 | Done | `33ab64e` | 对齐 `/api/ragent` 接口示例与默认本地服务端口、上下文路径。 |
| 3 | Done | `71ebf81` | 新增 `docs/backend-module-reading-guide.md`，并在 README 快速导航中增加入口。 |
| 4 | Done | `0102a6f` | 补充多通道检索流程，说明意图定向检索、全局向量检索、去重和 Rerank 的关系。 |
| 5 | Done | `edad60d` | 新增文档摄取 Pipeline 排查指南，并在 README 快速导航中增加入口。 |
| 6 | Done | `a43f337` | 新增入库 Pipeline 校验测试，覆盖循环依赖、节点缺失、无起始节点和校验日志初始化。 |
| 7 | Done | `7ed1f60` | 新增前端启动说明，覆盖 Node/npm、`VITE_API_BASE_URL`、本地后端地址、Vite 代理和登录跳转。 |
| 8 | Done | `fed1bb8` | 补充模型路由与故障切换说明，覆盖候选选择、健康状态、流式首包探测和排查路径。 |
| 9 | Done | `1137aad` | 新增检索结果去重测试，覆盖重复 chunk id、跨通道相同文本和空通道结果。 |
| 10 | Done | `queued` | 新增 MCP server 使用说明，覆盖启动方式、默认 endpoint、示例工具、主服务配置和工具发现调用链路。 |

## 待完成任务

| Day | 状态 | 类型 | 建议提交信息 | 任务 |
|---|---|---|---|---|
| 11 | Todo | docs | `docs: add database initialization guide` | 基于 `resources/database/` 整理 PostgreSQL 初始化说明，包括 schema/data 脚本、升级顺序和常见问题。 |
| 12 | Todo | docs | `docs: add RAG trace reading guide` | 结合后端 Trace 记录和管理后台，说明如何理解一次 RAG 请求的链路追踪。 |
| 13 | Todo | test | `test: cover search channel enablement rules` | 为检索通道启用规则补充测试，例如低置信度意图回退和意图定向检索优先级。 |
| 14 | Todo | docs | `docs: add contribution-friendly task list` | 整理适合初学者继续贡献的任务清单，包括文档、测试、小型 bugfix 和前端体验优化。 |
| 15 | Todo | docs | `docs: add model provider configuration guide` | 整理聊天、Embedding、Rerank 模型供应商的配置项、默认模型、环境变量和常见配置错误。 |
| 16 | Todo | test | `test: cover model routing fallback behavior` | 为模型路由故障切换补充轻量测试，覆盖候选模型失败后继续尝试下一个模型的行为。 |
| 17 | Todo | docs | `docs: add knowledge base operation guide` | 补充知识库、文档、分块和定时刷新相关接口的使用说明，帮助定位前后端功能入口。 |
| 18 | Todo | docs | `docs: add retrieval troubleshooting guide` | 整理检索无结果、结果不准、Rerank 未生效、向量库配置错误等常见排查路径。 |
| 19 | Todo | test | `test: cover query rewrite utility behavior` | 为问题重写或术语映射工具补充边界测试，覆盖空输入、无匹配映射和多映射命中的情况。 |
| 20 | Todo | docs | `docs: add resume-oriented project summary` | 以简历复盘为目标，总结项目架构、核心链路、可讲亮点、个人贡献记录和后续改造方向。 |

## 最近提交

```text
queued docs: add MCP server usage notes
1137aad test: cover retrieval deduplication behavior
fed1bb8 docs: document model routing failover behavior
7ed1f60 docs: add frontend startup notes
a43f337 test: cover ingestion pipeline validation errors
edad60d docs: add ingestion pipeline troubleshooting notes
0102a6f docs: explain multi-channel retrieval flow
71ebf81 docs: add backend module reading guide
33ab64e docs: align local setup and ingestion examples
4d325d7 Initial commit
```

## 下一次执行说明

Day 11 建议重点查看：

- `resources/database/schema_pg.sql`
- `resources/database/init_data_pg.sql`
- `resources/database/upgrade_v1.0_to_v1.1.sql`
- `resources/database/upgrade_v1.1_to_v1.2.sql`
- `bootstrap/src/main/resources/application.yaml`

预期产出：一篇聚焦 PostgreSQL 初始化、初始化数据、升级脚本顺序和常见问题的文档；如合适，同步在 README 快速导航中增加入口。
