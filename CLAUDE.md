# CLAUDE.md

本文件用于指导 Claude Code 在本仓库中工作。后续 AI 继续贡献、排查或提交时，应优先阅读本文件和根目录的 `Progress.md`。

## 项目概览

Ragent AI 是一个全栈 Agentic RAG 平台，覆盖文档入库、解析、分块、向量化、向量检索、重排、意图识别、聊天会话记忆、模型路由与故障切换、MCP 工具集成、链路追踪和管理后台。

仓库由 Maven 多模块 Java 后端和 Vite/React 前端组成。

## 常用命令

### 后端

除非特别说明，后端命令都在仓库根目录运行。

```powershell
# 构建所有 Maven 模块
./mvnw clean package

# 编译所有后端模块；Spotless 许可证头格式化绑定在 compile 阶段
./mvnw compile

# 运行所有后端测试
./mvnw test

# 运行单个模块测试
./mvnw -pl bootstrap test

# 运行单个测试类
./mvnw -pl bootstrap -Dtest=ScheduleRefreshProcessorTest test

# 运行单个测试方法
./mvnw -pl bootstrap -Dtest=ScheduleRefreshProcessorTest#methodName test

# 启动主 Ragent 服务
./mvnw -pl bootstrap spring-boot:run

# 启动 MCP server
./mvnw -pl mcp-server spring-boot:run
```

主后端服务默认监听 `9090` 端口，上下文路径为 `/api/ragent`。MCP server 默认监听 `9099` 端口。

### 前端

前端命令在 `frontend/` 目录运行。

```powershell
npm install
npm run dev
npm run build
npm run lint
npm run preview
npm run format
```

前端通过 `VITE_API_BASE_URL` 读取 API 基础地址。如果未设置，请求会使用当前页面同源地址。

## 运行依赖与配置

主后端配置文件位于 `bootstrap/src/main/resources/application.yaml`。

默认本地依赖和配置包括：

- PostgreSQL：数据库 `ragent`，地址 `127.0.0.1:5432`，用户名/密码 `postgres`/`postgres`。
- Redis：地址 `127.0.0.1:6379`，密码 `123456`。
- RocketMQ：NameServer 地址 `127.0.0.1:9876`。
- Milvus：可选，地址 `http://localhost:19530`；`rag.vector.type` 默认是 `pg`，可选 `pg` 或 `milvus`。
- RustFS/S3 兼容对象存储：地址 `http://localhost:9000`，默认凭据在 YAML 中。
- MCP server：默认地址 `http://localhost:9099`。
- AI provider key：通过 `BAILIAN_API_KEY` 和 `SILICONFLOW_API_KEY` 提供；Ollama 默认地址为 `http://localhost:11434`。

数据库脚本位于 `resources/database/`：

- `schema_pg.sql` 和 `init_data_pg.sql` 用于初始化 PostgreSQL 表结构和基础数据。
- `upgrade_v1.0_to_v1.1.sql` 和 `upgrade_v1.1_to_v1.2.sql` 是升级脚本。

Milvus 和 RocketMQ 的 Docker Compose 文件位于 `resources/docker/`。

## 后端架构

根目录 `pom.xml` 定义了四个 Maven 模块：

- `bootstrap`：Spring Boot 主应用和产品业务模块。启动类是 `com.nageoffer.ai.ragent.RagentApplication`。
- `framework`：通用基础设施，包括统一响应、异常和错误码、全局 Web 处理、用户上下文、Redis/cache 工具、分布式 ID、幂等切面、RocketMQ producer 支持、SSE 工具和 RAG Trace 注解/上下文。
- `infra-ai`：AI provider 抽象层，包括 chat、embedding、rerank、token 计数、模型健康状态、模型选择、模型路由、故障切换、流式解析，以及百炼、硅基流动、Ollama、noop rerank 等供应商实现。
- `mcp-server`：独立 Spring Boot MCP server，包含天气、票务、销售等示例工具。启动类是 `com.nageoffer.ai.ragent.mcp.McpServerApplication`。

`bootstrap` 依赖 `framework` 和 `infra-ai`。业务代码应优先调用这些模块暴露的抽象，不要在业务服务里重复实现 provider HTTP 细节或通用基础设施逻辑。

`bootstrap/src/main/java/com/nageoffer/ai/ragent/` 下的重要业务域包括：

- `rag`：聊天/RAG 编排、检索、意图处理、问题改写、会话记忆、向量库使用和可追踪回答生成。
- `knowledge`：知识库、文档、分块、存储、定时刷新和索引维护。
- `ingestion`：可配置文档入库 Pipeline。`IngestionEngine` 按节点类型发现 `IngestionNode` Spring Bean，校验线性 `PipelineDefinition`，按顺序执行节点，评估节点条件，并在入库上下文中记录节点日志。
- `core`：文档解析、文本清理和分块等底层工具，被 `ingestion` 和 `knowledge` 流程复用。
- `admin`：管理后台统计和管理接口。
- `user`：认证和用户管理。

MyBatis mapper 扫描配置在 `RagentApplication` 中，覆盖 `rag`、`ingestion`、`knowledge` 和 `user` 的 mapper 包。

## 模型与 RAG 扩展点

README 中描述的主要扩展点包括：

- 新增检索通道：实现 `SearchChannel` 并注册为 Spring Bean。
- 新增检索后处理器：实现 `SearchResultPostProcessor`。
- 新增 MCP 工具：主服务侧可通过 `McpToolExecutor` 注册工具执行器；独立 MCP server 侧可暴露 `McpServerFeatures.SyncToolSpecification`。
- 新增入库 Pipeline 节点：实现 `IngestionNode`。
- 新增模型供应商：在 `infra-ai` 中实现对应的 `ChatClient`、`EmbeddingClient` 或 `RerankClient` 抽象，并加入模型配置和候选路由。

模型路由和故障切换集中在 `infra-ai`，重点关注 `ModelRoutingExecutor`、`ModelHealthStore` 和相关 routing service。供应商 HTTP/API 差异应放在 `infra-ai`，不要写进 `bootstrap` 业务服务。

## 前端架构

前端是位于 `frontend/` 下的 Vite React 18 TypeScript 应用。

关键结构：

- `src/router.tsx` 定义路由：`/login`、`/chat`、`/chat/:sessionId`，以及 `/admin` 下的管理后台路由。
- `src/services/api.ts` 创建共享 Axios 实例，注入本地存储的认证 token，解包后端 `Result` 响应，并在认证失效时跳转 `/login`。
- `src/services/*Service.ts` 是按业务域拆分的 API 客户端。
- `src/stores/` 存放 Zustand 状态，包括 auth、chat 和 theme。
- `src/pages/` 存放页面级组件，管理后台页面位于 `src/pages/admin/`。
- `src/components/` 存放 chat、session、layout、admin、common 和 UI 组件。

后端返回 `{ code, message, data }` 结构时，前端 Axios 响应拦截器会解包，所以 service 函数通常直接拿到 `data`。

## 贡献计划流程

每日贡献计划和进度记录统一维护在根目录 `Progress.md`。
`Progress.md` 是已完成任务、待完成任务、最近提交和下一步执行说明的唯一事实来源。旧的 `process.md` 和 `docs/weekly-contribution-plan.md` 已废弃并移除，不再使用。

## 每日队列式贡献流程

当用户希望现在先准备一批真实改进、后续每天自动发布一部分时，使用本地队列分支，不要把半成品留在工作区。

推荐分支模型：

1. `main` 保持为每天推送并产生贡献记录的分支。
2. 创建本地队列分支，通常命名为 `contribution-queue`。
3. 在 `contribution-queue` 上每个 commit 对应一个完整、有意义的改进。
4. 每天通过脚本把 `contribution-queue` 中下一条尚未等价应用到 `main` 的 commit 发布到 `main`，运行轻量检查，然后 push。
5. 不要为了贡献记录创建空提交、时间戳提交、回填日期提交或无意义文件改动。

重要规则：自动发布脚本只按 commit 顺序工作，不会理解或拆分“几天的工作量”。因此，一天要发布的内容必须提前整理成一个独立 commit。如果三天内容被合并成一个 commit，脚本会在一天内一次性发布；如果希望连续三天发布，就必须提前拆成三个独立 commit。

每日发布脚本位于根目录：`publish_queued_commit.ps1`。

典型用法：

```powershell
# 预览下一条队列 commit，不做任何修改
./publish_queued_commit.ps1 -DryRun

# 发布下一条队列 commit 并推送 main
./publish_queued_commit.ps1

# 如果确实要保留队列 commit 原始 AuthorDate
./publish_queued_commit.ps1 -PreserveAuthorDate

# 只有目标后端测试通过才发布
./publish_queued_commit.ps1 -CheckCommand "./mvnw.cmd -pl bootstrap -Dtest=DeduplicationPostProcessorTest test"

# 只有前端构建通过才发布
./publish_queued_commit.ps1 -CheckCommand "npm --prefix frontend run build"
```

真实发布时脚本要求工作区干净，默认对 `main` 执行 `pull --ff-only`，用 `git cherry` 跳过已经等价应用到 `main` 的队列 commit，选择最早一条未发布 commit，先应用但不提交，可选执行 `-CheckCommand`，检查通过后再提交并 push。默认会把新提交的 AuthorDate 重置为发布时间，确保贡献图按当天归属；如果要保留队列 commit 原始 AuthorDate，显式传 `-PreserveAuthorDate`。检查失败时，脚本会恢复到发布前的干净工作区。
`-DryRun` 只读取本地分支并打印下一条队列 commit；它不要求工作区干净，也不会 fetch、pull、切换分支、cherry-pick、运行检查或 push。

## Git 贡献身份

本仓库贡献提交使用以下 Git 身份：

- GitHub 用户名：`lcoudy`
- Git 作者邮箱：`1020246530@qq.com`

当用户要求继续贡献计划或完成下一个每日任务时：

1. 先阅读 `Progress.md`，了解当前进度。
2. 选择第一个状态为 `Todo` 的任务，除非用户指定某一天或某个任务。
3. 实现一个真实、有用、符合该任务范围的改动。
4. 优先保持小而独立的改动，方便单独提交。
5. 完成后把对应任务状态从 `Todo` 更新为 `Done`。
6. 更新 `Progress.md`，记录完成内容、最近提交信息和下一步计划。
7. 在可行时运行最相关的轻量检查。
8. 每日任务完成并检查后，默认创建聚焦 commit 并 push 到远程，除非用户明确要求不要提交或不要推送。

不要创建空提交，也不要用无意义的时间戳改动制造贡献记录。
