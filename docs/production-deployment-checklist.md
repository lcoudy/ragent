# 生产部署检查清单

这份清单用于把 Ragent 从本地联调推进到生产或预发环境。它不替代具体公司的发布流程，重点覆盖本项目容易遗漏的配置、依赖和验证点。

## 环境基线

- JDK 版本与 Maven 构建环境保持一致，优先使用仓库当前 Maven wrapper 执行构建。
- 前端使用 `frontend/package-lock.json` 锁定依赖，发布包由 `npm run build` 生成。
- 主服务默认端口是 `9090`，上下文路径是 `/api/ragent`；MCP server 默认端口是 `9099`。
- 生产环境不要直接复用本地默认密码，尤其是 PostgreSQL、Redis、对象存储和模型供应商 API Key。

## 必备中间件

| 组件 | 用途 | 发布前检查 |
|---|---|---|
| PostgreSQL | 业务数据、pgvector 向量数据 | 已执行 `schema_pg.sql`、`init_data_pg.sql` 和必要升级脚本 |
| Redis | 缓存、限流、幂等和状态缓存 | 密码、库编号、连接池和过期策略符合环境规范 |
| RocketMQ | 文档分块异步处理 | NameServer、topic、consumer group 和重试策略可用 |
| S3 兼容存储 | 文档原文件和解析产物 | bucket、endpoint、access key 和权限策略已配置 |
| Milvus | 可选向量库 | 仅在 `rag.vector.type=milvus` 时要求可用 |

## 配置检查

发布前重点核对 `bootstrap/src/main/resources/application.yaml` 中对应的环境变量或外部化配置：

1. `spring.datasource.*` 指向目标 PostgreSQL，不使用本地默认库。
2. `spring.data.redis.*` 指向目标 Redis，并开启认证。
3. `rocketmq.name-server` 指向目标 RocketMQ NameServer。
4. `rag.vector.type` 与实际向量库一致，切换向量库后已重建索引。
5. `rag.mcp.server-url` 指向已部署的 MCP server。
6. `BAILIAN_API_KEY`、`SILICONFLOW_API_KEY` 等模型密钥由环境变量注入。
7. 前端 `VITE_API_BASE_URL` 指向后端公开地址，避免构建产物请求到错误同源。

## 发布前验证

后端建议至少执行：

```powershell
./mvnw.cmd -pl bootstrap test
./mvnw.cmd -pl infra-ai test
```

前端建议至少执行：

```powershell
npm --prefix frontend run build
```

如果只发布文档或局部测试补充，可以用 `git diff --check` 做轻量检查；如果改动涉及 RAG 编排、模型路由、入库 Pipeline 或前端管理台，应选择对应模块测试或构建。

## 冒烟流程

1. 登录前端，确认认证失败会跳转 `/login`，登录成功可进入 `/chat`。
2. 创建或选择知识库，上传一个小文档，确认文档状态进入可检索状态。
3. 发送一个知识库问题，确认 SSE 持续输出且最终回答包含检索上下文。
4. 打开管理后台 Trace 页面，确认本次问答链路可查询。
5. 触发一个 MCP 工具类问题，确认工具发现、参数提取和调用结果正常。
6. 检查后端日志中没有连续模型失败、RocketMQ 消费失败或数据库连接异常。

## 回滚准备

- 保留上一个可用后端包和前端静态资源包。
- 数据库升级脚本发布前先备份，涉及不可逆迁移时先在预发验证。
- 模型供应商切换或向量库切换要预留开关，优先通过配置恢复。
- 每次发布记录当前 Git commit、数据库脚本版本、前端构建时间和关键配置版本。

## 常见风险

- 前端 `VITE_API_BASE_URL` 未设置，导致生产页面请求静态资源同源。
- pgvector 未安装或维度不一致，导致文档入库后无法检索。
- RocketMQ topic 或 consumer group 未初始化，文档状态长时间停在处理中。
- MCP server 未部署或地址错误，工具类意图只能返回普通模型回答。
- API Key 只在本地 shell 中存在，服务进程启动后读取不到环境变量。
