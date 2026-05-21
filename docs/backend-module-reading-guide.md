# 后端模块源码阅读路线

本文面向第一次阅读 Ragent 后端源码的贡献者，帮助你按模块和调用链快速定位入口。建议先从启动类和接口层看起，再向 service、core、infra 逐层下钻。

## 1. 先看整体启动与配置

从仓库根目录的 Maven 多模块结构开始：

- `pom.xml`：确认 `bootstrap`、`framework`、`infra-ai`、`mcp-server` 四个模块的依赖关系。
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/RagentApplication.java`：主服务启动类，也是 MyBatis mapper 扫描配置的入口。
- `bootstrap/src/main/resources/application.yaml`：默认端口、数据库、Redis、RocketMQ、对象存储、向量库和模型供应商配置都集中在这里。

阅读顺序建议：

```text
pom.xml
  -> bootstrap/.../RagentApplication.java
  -> bootstrap/src/main/resources/application.yaml
  -> resources/database/schema_pg.sql
```

这样可以先建立“应用如何启动、依赖哪些中间件、数据表如何组织”的整体印象。

## 2. `bootstrap`：业务主流程入口

`bootstrap` 是主应用模块，适合从 controller 进入，再沿着 service 和 core 逻辑追踪。

重点包：

- `rag`：聊天、RAG 编排、检索、意图识别、会话记忆、链路追踪。
  - 入口可从 `rag/controller/RAGChatController.java` 看起。
  - 检索扩展点可看 `rag/core/retrieve/channel/SearchChannel.java`。
  - Trace 相关可看 `rag/controller/RagTraceController.java` 以及 trace mapper/entity。
- `knowledge`：知识库、文档、分块、调度刷新和索引维护。
  - 入口可从 `knowledge/controller/KnowledgeDocumentController.java`、`KnowledgeBaseController.java` 看起。
- `ingestion`：文档摄取 Pipeline。
  - 入口可从 `ingestion/controller/IngestionPipelineController.java`、`IngestionTaskController.java` 看起。
  - 节点扩展点可看 `ingestion/node/IngestionNode.java`。
- `core`：文档解析、分块等底层能力，通常被 `knowledge` 和 `ingestion` 调用。
- `admin`、`user`：管理后台统计、认证和用户管理。

建议按一个用户请求来追踪，例如：

```text
RAGChatController
  -> rag service / orchestration
  -> retrieve channel / post processor
  -> infra-ai chat / embedding / rerank abstraction
  -> rag trace persistence
```

## 3. `framework`：通用基础设施

`framework` 提供跨业务模块复用的能力。业务代码遇到通用封装时，可以回到这里查定义。

重点关注：

- 统一响应、异常和错误码。
- Web 全局处理、用户上下文。
- Redis/cache 工具、分布式 ID、幂等切面。
- RocketMQ producer 支持。
- SSE 工具。
- RAG Trace 注解和上下文能力。

阅读建议：不要一开始逐个类通读，优先在 `bootstrap` 中遇到相关工具或注解时再回到 `framework` 查实现。

## 4. `infra-ai`：模型供应商与路由抽象

`infra-ai` 是 AI 能力的基础设施层，负责把业务模块和具体模型供应商解耦。

重点包：

- `chat`：聊天模型客户端抽象与供应商实现。
- `embedding`：向量化客户端抽象与实现。
- `rerank`：重排客户端抽象与实现。
- `token`：token 计数能力。
- `model`：模型配置、健康状态、路由和故障转移。
  - 可重点看 `ModelRoutingExecutor.java` 和 `ModelHealthStore.java`。
- `http`、`config`：HTTP 调用与配置绑定。

阅读顺序建议：

```text
业务 service 调用的模型接口
  -> chat / embedding / rerank 抽象
  -> provider-specific client
  -> model routing / health / fallback
```

如果要新增模型供应商，应优先在本模块扩展，不要把供应商 HTTP 细节写进 `bootstrap` 业务代码。

## 5. `mcp-server`：独立 MCP 工具服务

`mcp-server` 是独立 Spring Boot 应用，默认和主服务分开启动。

阅读入口：

- `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/McpServerApplication.java`：MCP 服务启动类。
- `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/executor/`：示例工具执行器，例如天气、票务、销售等工具。
- `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/config/`：MCP 服务配置。

建议先独立理解 MCP server 提供了哪些工具，再回到 `bootstrap` 中查看主服务如何配置和调用 MCP server。

## 6. 推荐阅读路径

如果目标是理解一次完整 RAG 问答：

```text
RagentApplication
  -> RAGChatController
  -> rag 编排 service
  -> SearchChannel / post processor
  -> infra-ai chat / embedding / rerank
  -> RagTraceController / trace persistence
```

如果目标是理解文档入库：

```text
KnowledgeDocumentController
  -> ingestion pipeline / task controller
  -> IngestionNode
  -> core parser / chunker
  -> knowledge chunk persistence
  -> vector store configuration
```

如果目标是做小型贡献：

1. 先选一个 controller 对应的用户场景。
2. 找到对应 service 和 mapper。
3. 查看是否已有测试或文档。
4. 优先补文档、边界测试或小范围 bugfix。
