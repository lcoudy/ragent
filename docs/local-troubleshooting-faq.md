# 本地开发排查 FAQ

本文整理 Ragent 本地开发时常见的 Java、Maven、Node、数据库、中间件、模型 Key、向量库和 SSE 问题。目标是先判断问题发生在哪一层，再选择最小检查命令定位。

## Java 与 Maven

### `JAVA_HOME environment variable is not defined correctly`

现象：运行 `./mvnw.cmd test` 或 `./mvnw.cmd compile` 时，Maven wrapper 直接退出。

处理：

```powershell
$env:JAVA_HOME="C:\Users\10202\.jdks\openjdk-26.0.1"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd -v
```

项目源码按 Java 17 编译。优先使用 JDK 17；如果本机只有更高版本 JDK，通常也可以编译 `source/target 17`，但可能出现额外 warning。

### `Could not find artifact framework/infra-ai`

现象：只运行 `-pl bootstrap` 时，Maven 找不到本项目其他模块的 jar。

原因：`bootstrap` 依赖 `framework` 和 `infra-ai`，单独构建 bootstrap 时这些模块可能还没有 install 到本地仓库。

处理：

```powershell
.\mvnw.cmd -pl bootstrap -am -Dtest=ClassName test
```

`-am` 表示 also make，会把依赖模块一起加入当前 reactor。

### 指定测试在依赖模块报 `No tests matching pattern`

现象：使用 `-pl bootstrap -am -Dtest=SomeTest test` 时，`framework` 或 `infra-ai` 先执行 surefire，但它们没有这个测试类。

处理：

```powershell
.\mvnw.cmd -pl bootstrap -am -Dtest=SomeTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

在 PowerShell 中，带点的 `-D` 参数建议加引号，否则可能被解析成错误的 lifecycle phase。

### bootstrap 测试编译阶段失败

现象：还没运行目标测试，`testCompile` 先在旧测试类上失败，例如 `lombok.extern.slf4j.__` 或 `log` 字段找不到。

判断方式：

- 如果错误来自未修改的测试类，说明目标测试被既有 testCompile 问题阻塞。
- 先记录失败类和错误，再决定是否单独修复测试基础设施。
- 不要把 unrelated 测试批量改动混进一个小任务 commit。

文档任务可先运行：

```powershell
git diff --check
```

## 前端启动

### `npm install` 慢或失败

处理顺序：

1. 确认 Node 版本满足前端工程要求。
2. 优先使用仓库已有 `package-lock.json`，不要随意删除 lock 文件。
3. 如果公司网络代理影响 npm，先配置 npm registry 或代理。

常用命令：

```powershell
npm --prefix frontend install
npm --prefix frontend run dev
```

### 前端请求打到错误地址

前端通过 `VITE_API_BASE_URL` 配置 API 前缀。默认后端地址是：

```text
http://localhost:9090/api/ragent
```

排查：

- 浏览器 Network 中确认请求 URL。
- 检查 `.env` 或当前 shell 中的 `VITE_API_BASE_URL`。
- 确认后端 `server.servlet.context-path` 没有被改掉。

更多说明见 `docs/frontend-startup.md`。

## PostgreSQL 与数据初始化

### 后端启动时报数据库连接失败

默认配置：

| 配置 | 默认值 |
|---|---|
| host | `127.0.0.1` |
| port | `5432` |
| database | `ragent` |
| username/password | `postgres/postgres` |

排查：

- 数据库是否启动。
- `ragent` 数据库是否存在。
- `resources/database/schema_pg.sql` 是否已执行。
- `resources/database/init_data_pg.sql` 是否已执行。
- `pgvector` 扩展是否创建成功。

### 表存在但接口查不到数据

先确认是否执行了初始化数据脚本，再检查当前连接的 database/schema 是否和执行脚本时一致。

详细步骤见 `docs/database-initialization.md`。

## Redis、RocketMQ 与对象存储

### Redis 连接失败

默认 Redis 地址是 `127.0.0.1:6379`，密码是 `123456`。如果本机 Redis 无密码，需要同步修改 `application.yaml` 或本地覆盖配置。

### RocketMQ 未启动

文档入库的异步分块依赖 RocketMQ。现象通常是文档状态一直停留在 `pending` 或 `running`。

排查：

- NameServer 地址是否是 `127.0.0.1:9876`。
- broker 是否已注册到 NameServer。
- 消费者日志里是否有消息消费异常。

### S3/RustFS 上传失败

默认对象存储地址是 `http://localhost:9000`。排查 bucket、access key、secret key 和 endpoint 是否与 `application.yaml` 一致。

## 向量库

默认向量库类型是 PostgreSQL pgvector：

```yaml
rag:
  vector:
    type: pg
```

切换到 Milvus 时要同时确认：

- Milvus 服务地址和端口。
- collection 是否创建。
- embedding 维度是否和 collection schema 一致。
- 切换向量库后是否重新写入已有文档向量。

详细说明见 `docs/vector-store-configuration.md`。

## 模型 Key 与模型路由

默认模型配置依赖环境变量：

| 环境变量 | 用途 |
|---|---|
| `BAILIAN_API_KEY` | 百炼 Chat/Embedding/Rerank。 |
| `SILICONFLOW_API_KEY` | 硅基流动 Chat/Embedding/Rerank。 |

如果没有真实 Key，可以优先验证不依赖模型的文档、前端构建和纯单元测试。模型调用失败时，继续看：

- `docs/model-provider-configuration.md`
- `docs/model-routing-failover.md`

## SSE 流式问答

### 浏览器没有持续收到消息

排查顺序：

1. Network 中确认 `/rag/v3/chat` 返回 `text/event-stream`。
2. 确认后端没有被反向代理缓冲。
3. 查看第一个事件是否是 `meta`，其中包含 `conversationId` 和 `taskId`。
4. 查看后端是否触发模型首包超时或 provider 切换。

### 停止按钮无效

前端必须使用 `meta` 事件里的 `taskId` 调用 `/rag/v3/stop`。如果 taskId 丢失，后端无法定位 `StreamTaskManager` 中的任务。

## 建议的最小检查

| 改动范围 | 优先检查 |
|---|---|
| 只改文档 | `git diff --check` |
| 只改前端 | `npm --prefix frontend run build` |
| 只改一个纯单测 | `.\mvnw.cmd -pl <module> -am -Dtest=ClassName "-Dsurefire.failIfNoSpecifiedTests=false" test` |
| 修改 Prompt 文本 | `git diff --check`，再用最小样例请求验证输出 |
| 修改数据库脚本 | 在空库执行 schema/data，并记录执行顺序 |

遇到本地环境问题时，优先把命令、错误首行和失败模块记录下来，再判断是代码问题、环境问题还是既有测试基础设施问题。
