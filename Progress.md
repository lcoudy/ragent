# Ragent 贡献进度

本文件是本仓库 AI 辅助贡献工作的唯一事实来源。

后续继续贡献任务前，先阅读本文件，再选择第一个状态为 `Todo` 的任务。除非用户明确指定某一天或某个任务，不要跳过前面的待办项。

## 当前状态

- 发布目标分支：`main`
- 队列分支：`contribution-queue`
- 远程仓库：`https://github.com/lcoudy/ragent.git`
- GitHub 用户名：`lcoudy`
- Git 作者邮箱：`1020246530@qq.com`
- 已完成贡献任务：Day 1 到 Day 36
- 已完成并已正式提交到 GitHub：Day 1 到 Day 23
- 已完成但未正式提交到 GitHub：Day 24 到 Day 36（仅在本地 `contribution-queue` 队列中）
- 未完成且未提交到 GitHub：Day 37 到 Day 40
- 下一项任务：Day 37，`docs: add query rewrite and term mapping guide`
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

根目录脚本 `publish_queued_commit.ps1` 会用 `git cherry` 跳过已经等价应用到 `main` 的队列 commit，选择最早一条未发布 commit，先应用但不提交，可选运行 `-CheckCommand` 指定的检查，检查通过后再提交并 push `main`。默认会把新提交的 AuthorDate 重置为发布时间，确保贡献图按当天归属；如果要保留队列 commit 原始 AuthorDate，显式传 `-PreserveAuthorDate`。如果检查失败，脚本会恢复到发布前的干净工作区。

重要规则：脚本只认识 commit，不会自动判断或拆分“几天的工作量”。一天要自动发布的内容必须提前做成一个独立 commit。如果三天内容放在一个 commit 里，脚本会在一天内一次性发布；如果要分三天发布，就需要提前拆成三个 commit。

## 已完成并已正式提交到 GitHub

| Day | 状态 | GitHub 提交 | 说明 |
|---|---|---|---|
| 1 | Done | `33ab64e` | 梳理本地依赖说明，包括 PostgreSQL、Redis、RocketMQ、RustFS 和 AI Key。 |
| 2 | Done | `33ab64e` | 对齐 `/api/ragent` 接口示例与默认本地服务端口、上下文路径。 |
| 3 | Done | `71ebf81` | 新增 `docs/backend-module-reading-guide.md`，并在 README 快速导航中增加入口。 |
| 4 | Done | `0102a6f` | 补充多通道检索流程，说明意图定向检索、全局向量检索、去重和 Rerank 的关系。 |
| 5 | Done | `edad60d` | 新增文档摄取 Pipeline 排查指南，并在 README 快速导航中增加入口。 |
| 6 | Done | `a43f337` | 新增入库 Pipeline 校验测试，覆盖循环依赖、节点缺失、无起始节点和校验日志初始化。 |
| 7 | Done | `7ed1f60` | 新增前端启动说明，覆盖 Node/npm、`VITE_API_BASE_URL`、本地后端地址、Vite 代理和登录跳转。 |
| 8 | Done | `fed1bb8` | 补充模型路由与故障切换说明，覆盖候选选择、健康检查、熔断降级和首包探测路径。 |
| 9 | Done | `1137aad` | 新增检索结果去重测试，覆盖重复 chunk id、跨通道相同文本和空通道结果。 |
| 10 | Done | `d0d5733` | 新增 MCP server 使用说明，覆盖启动方式、默认 endpoint、示例工具、主服务配置和工具发现调用链路。 |
| 11 | Done | `3235515` | 新增数据库初始化指南，覆盖 PostgreSQL 默认配置、pgvector、schema/data 脚本、升级顺序和常见问题。 |
| 12 | Done | `6d31799` | 新增 RAG Trace 阅读指南，覆盖注解采集、上下文透传、数据表、查询接口、管理后台和排查路径。 |
| 13 | Done | `1729ba0` | 新增检索通道启用规则测试，覆盖全局检索低置信度兜底、单意图补充，以及意图定向检索的 KB 意图过滤规则。 |
| 14 | Done | `fc32b8e` | 新增适合继续贡献的任务清单，按文档、测试、小型 bugfix、前端体验和后端 AI 工程方向分类。 |
| 15 | Done | `b28a97d` | 新增模型供应商配置指南，覆盖 Chat、Embedding、Rerank 模型候选、API Key、优先级、故障切换和常见配置错误。 |
| 16 | Done | `6800eb5` | 新增模型路由故障切换测试，覆盖候选失败后继续尝试下一个模型，以及已熔断候选跳过规则。 |
| 17 | Done | `57e2f71` | 新增知识库操作指南，覆盖知识库、文档、分块、定时刷新、后台入口和常见排查路径。 |
| 18 | Done | `7e8a598` | 新增检索排查指南，覆盖检索无结果、结果不准、Rerank 未生效、向量库配置错误和多通道召回异常。 |
| 19 | Done | `9663630` | 新增术语映射工具单元测试，覆盖空输入、无匹配、多次命中、目标词防重复和多映射顺序命中。 |
| 20 | Done | `2a0dcb4` | 新增面向简历复盘的项目总结，覆盖架构分层、核心链路、可讲贡献点和后续改造方向。 |
| 21 | Done | `c0d39be` | 新增入库引擎条件执行单元测试，覆盖条件跳过、条件命中、节点调用次数和节点日志输出。 |
| 22 | Done | `18bbba7` | 新增前端管理后台操作指南，覆盖后台路由、认证守卫、API 解包、service 分层和本地联调排查。 |
| 23 | Done | `5a1f01c` | 新增 RAG Trace 上下文单元测试，覆盖 trace/task 初始化、嵌套节点栈、空 pop 和 clear 清理。 |

## 已完成但未正式提交到 GitHub

这些任务已在本地 `contribution-queue` 中做成独立 commit，等待 `publish_queued_commit.ps1` 后续按天 cherry-pick 到 `main` 并 push。

| Day | 状态 | 本地队列提交 | 建议提交信息 | 说明 |
|---|---|---|---|---|
| 24 | Done | `d50f10f` | `docs: document MCP tool extension path` | 新增 MCP 工具扩展指南，覆盖主服务本地执行器、独立 MCP server 远程工具、参数提取和联调排查。 |
| 25 | Done | `1389207` | `test: cover model health store behavior` | 新增模型健康状态存储测试，覆盖失败阈值、熔断窗口、半开单次探测、成功恢复和半开失败重开。 |
| 26 | Done | `b5185d2` | `docs: add vector store configuration guide` | 新增向量库配置指南，覆盖 PostgreSQL pgvector、Milvus、切换流程、embedding 维度和常见错误。 |
| 27 | Done | `98a0be8` | `test: cover chunking edge cases` | 新增固定大小分块器边界测试，覆盖空白文本、短文本、重叠窗口、超长段落和禁用切分。 |
| 28 | Done | `cd39a6d` | `docs: add API response contract guide` | 新增 API 响应契约指南，覆盖后端统一响应、错误码、全局异常、前端 Axios 解包和认证失效处理。 |
| 29 | Done | `fb18aa0` | `test: cover session memory boundaries` | 新增会话记忆边界测试，覆盖空参数、空历史、摘要拼接、异常降级、append 压缩和历史截断。 |
| 30 | Done | `797dc17` | `docs: add release and contribution workflow guide` | 新增发布与贡献流程指南，覆盖队列分支、每日发布脚本、检查命令、失败恢复和进度维护规则。 |
| 31 | Done | `70b33db` | `docs: explain RAG chat request flow` | 新增 RAG 问答请求链路说明，串联 SSE、会话记忆、问题改写、意图解析、检索、Prompt、模型输出和停止任务。 |
| 32 | Done | `6833630` | `test: cover rerank post processor behavior` | 新增 Rerank 后处理器单元测试，覆盖空结果跳过、重写问题优先、原始问题兜底、topK 透传和精排结果返回。 |
| 33 | Done | `db81c31` | `docs: add prompt template guide` | 新增 Prompt 模板指南，覆盖模板位置、section 语法、变量替换、RAG 场景选择、自定义意图模板和排查方式。 |
| 34 | Done | `4863cf4` | `test: cover prompt template loading` | 新增 Prompt 模板加载器单元测试，覆盖 classpath 加载缓存、变量渲染、section 渲染和缺失 section 报错。 |
| 35 | Done | `3b837fe` | `docs: add local troubleshooting FAQ` | 新增本地开发排查 FAQ，覆盖 Java、Maven、Node、数据库、中间件、向量库、模型 Key 和 SSE 常见问题。 |
| 36 | Done | `queue HEAD` | `test: cover file type detection` | 新增 MIME 类型识别单元测试，覆盖空字节、PDF magic、纯文本文件和无文件名内容识别。 |

## 未完成且未提交到 GitHub

| Day | 状态 | 类型 | 建议提交信息 | 任务 |
|---|---|---|---|---|
| 37 | Todo | docs | `docs: add query rewrite and term mapping guide` | 新增问题改写与术语映射指南，覆盖改写输入、术语替换、拆分、配置入口和排查路径。 |
| 38 | Todo | test | `test: cover JSON response parser behavior` | 为 LLM JSON 响应解析补充测试，覆盖 markdown fence、正文包裹 JSON、非法输入和类型不匹配。 |
| 39 | Todo | docs | `docs: add ingestion pipeline extension guide` | 新增入库 Pipeline 扩展指南，覆盖节点接口、条件表达式、上下文传递、日志和调试。 |
| 40 | Todo | test | `test: cover LLM response cleaner behavior` | 为 LLM 输出清理工具补充测试，覆盖语言标记代码块、普通代码块、空值和普通文本。 |

## 最近提交

```text
queue HEAD test: cover file type detection (queued)
3b837fe docs: add local troubleshooting FAQ (queued)
4863cf4 test: cover prompt template loading (queued)
db81c31 docs: add prompt template guide (queued)
6833630 test: cover rerank post processor behavior (queued)
70b33db docs: explain RAG chat request flow (queued)
797dc17 docs: add release and contribution workflow guide (queued)
fb18aa0 test: cover session memory boundaries (queued)
cd39a6d docs: add API response contract guide (queued)
98a0be8 test: cover chunking edge cases (queued)
b5185d2 docs: add vector store configuration guide (queued)
1389207 test: cover model health store behavior (queued)
d50f10f docs: document MCP tool extension path (queued)
5a1f01c test: cover trace context propagation
18bbba7 docs: add frontend admin operation guide
c0d39be test: cover ingestion node condition behavior
2a0dcb4 docs: add resume-oriented project summary
9663630 test: cover query rewrite utility behavior
7e8a598 docs: add retrieval troubleshooting guide
57e2f71 docs: add knowledge base operation guide
6800eb5 test: cover model routing fallback behavior
b28a97d docs: add model provider configuration guide
fc32b8e docs: add contribution-friendly task list
0aeb5ff fix: tolerate dirty tree in queued publisher
1729ba0 test: cover search channel enablement rules
6d31799 docs: add RAG trace reading guide
3235515 docs: add database initialization guide
5d9ef0f fix: publish queued commits with current author date
d0d5733 docs: add MCP server usage notes
cc23b54 chore: consolidate contribution workflow updates
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

当前 Day 24 到 Day 36 已在本地 `contribution-queue` 做成独立 commit，Day 37 到 Day 40 已完成规划。下一项任务是 Day 37：`docs: add query rewrite and term mapping guide`。

```powershell
git diff --check
```

完成后更新本文件，并继续保持一个任务一个独立 commit。
