# RAG Trace 阅读指南

本文说明 Ragent 中一次 RAG 请求的链路追踪如何采集、如何落库、如何通过接口和管理后台查看，以及排查慢节点和失败节点时应该看哪些字段。

## 1. Trace 相关模块

RAG Trace 分布在三个位置：

| 位置 | 说明 |
|---|---|
| `framework/src/main/java/com/nageoffer/ai/ragent/framework/trace/` | Trace 注解和上下文。 |
| `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/aop/RagTraceAspect.java` | 注解式 Trace 采集切面。 |
| `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/impl/` | Trace 记录和查询服务。 |
| `frontend/src/pages/admin/traces/` | 管理后台链路追踪列表页和详情页。 |

配置项位于：

```yaml
rag:
  trace:
    enabled: true
    max-error-length: 1000
```

对应配置类：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/config/RagTraceProperties.java
```

## 2. 核心概念

RAG Trace 分为两层：

| 概念 | 注解或表 | 说明 |
|---|---|---|
| Run | `@RagTraceRoot` / `t_rag_trace_run` | 一次完整 RAG 请求。 |
| Node | `@RagTraceNode` / `t_rag_trace_node` | 请求中的一个阶段，例如问题重写、意图识别、检索。 |

`RagTraceContext` 使用 `TransmittableThreadLocal` 保存当前 `traceId`、`taskId` 和节点栈，让异步线程池中的子节点也能挂到同一条链路下。

## 3. 采集流程

入口切面：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/aop/RagTraceAspect.java
```

采集流程：

```text
@RagTraceRoot 入口方法
  -> 创建 traceId
  -> 写入 t_rag_trace_run，状态 RUNNING
  -> 设置 RagTraceContext
  -> 执行业务逻辑
    -> @RagTraceNode 方法被调用
      -> 写入 t_rag_trace_node，状态 RUNNING
      -> 节点入栈
      -> 执行节点逻辑
      -> 更新节点 SUCCESS / ERROR、耗时和错误信息
      -> 节点出栈
  -> 更新 run SUCCESS / ERROR、耗时和错误信息
  -> 清理 RagTraceContext
```

如果 `rag.trace.enabled=false`，切面会直接放行业务方法，不写 Trace 数据。

## 4. Trace 注解

根节点注解：

```text
framework/src/main/java/com/nageoffer/ai/ragent/framework/trace/RagTraceRoot.java
```

常用字段：

| 字段 | 说明 |
|---|---|
| `name` | 链路名称。 |
| `conversationIdArg` | 从入口方法参数中提取会话 ID 的参数名，默认 `conversationId`。 |
| `taskIdArg` | 从入口方法参数中提取任务 ID 的参数名，默认 `taskId`。 |

普通节点注解：

```text
framework/src/main/java/com/nageoffer/ai/ragent/framework/trace/RagTraceNode.java
```

常用字段：

| 字段 | 说明 |
|---|---|
| `name` | 节点展示名称。 |
| `type` | 节点类型，例如 `REWRITE`、`INTENT`、`RETRIEVE`、`RETRIEVE_CHANNEL`。 |

当前代码中已有的节点示例：

| 节点 | 位置 |
|---|---|
| `query-rewrite` / `query-rewrite-and-split` | `MultiQuestionRewriteService` |
| `intent-resolve` | `IntentResolver` |
| `retrieval-engine` | `RetrievalEngine` |
| `multi-channel-retrieval` | `MultiChannelRetrievalEngine` |

## 5. 数据表

Trace 表在 `resources/database/schema_pg.sql` 中定义：

```text
t_rag_trace_run
t_rag_trace_node
```

`t_rag_trace_run` 关注一次请求整体：

| 字段 | 说明 |
|---|---|
| `trace_id` | 全局链路 ID。 |
| `trace_name` | 链路名称。 |
| `entry_method` | 入口方法。 |
| `conversation_id` | 会话 ID。 |
| `task_id` | 流式任务 ID。 |
| `user_id` | 当前用户。 |
| `status` | `RUNNING`、`SUCCESS`、`ERROR`。 |
| `duration_ms` | 整体耗时。 |
| `error_message` | 错误摘要。 |

`t_rag_trace_node` 关注阶段明细：

| 字段 | 说明 |
|---|---|
| `trace_id` | 所属链路 ID。 |
| `node_id` | 节点 ID。 |
| `parent_node_id` | 父节点 ID，用于还原层级。 |
| `depth` | 节点深度。 |
| `node_type` | 节点类型。 |
| `node_name` | 节点名称。 |
| `class_name` / `method_name` | 采集到的 Java 方法。 |
| `status` | 节点状态。 |
| `duration_ms` | 节点耗时。 |
| `error_message` | 节点错误摘要。 |

## 6. 后端查询接口

Controller：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/controller/RagTraceController.java
```

接口路径会叠加主服务上下文路径 `/api/ragent`。

| 接口 | 说明 |
|---|---|
| `GET /api/ragent/rag/traces/runs` | 分页查询 Trace run。支持 `traceId`、`conversationId`、`taskId`、`status` 过滤。 |
| `GET /api/ragent/rag/traces/runs/{traceId}` | 查询单次链路详情，包含 run 和 nodes。 |
| `GET /api/ragent/rag/traces/runs/{traceId}/nodes` | 只查询节点列表。 |

查询服务：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/impl/RagTraceQueryServiceImpl.java
```

列表默认按 `startTime` 倒序；节点详情按 `startTime` 和 `id` 升序，便于前端还原时间线。

## 7. 管理后台入口

前端服务：

```text
frontend/src/services/ragTraceService.ts
```

页面：

| 页面 | 说明 |
|---|---|
| `frontend/src/pages/admin/traces/RagTracePage.tsx` | Trace 列表页，支持按 Trace Id 查询，展示成功率、平均耗时、P95 等指标。 |
| `frontend/src/pages/admin/traces/RagTraceDetailPage.tsx` | Trace 详情页，展示运行信息、节点瀑布图、慢节点和失败节点。 |

管理后台中点击某条运行记录，可以进入详情页查看该次请求的节点时间线。

## 8. 排查一次请求

推荐顺序：

1. 在管理后台 Trace 列表中搜索 `traceId`。
2. 查看 run 状态，如果是 `ERROR`，先读 `errorMessage` 和 `entryMethod`。
3. 进入详情页，看瀑布图中耗时最长的节点。
4. 对照节点的 `nodeType` 和 `methodName` 定位后端类。
5. 如果节点失败，优先查看节点 `errorMessage`，再结合后端日志中的同一时间段日志。
6. 如果整体失败但节点都成功，检查入口方法之后的流式发送、消息持久化或回调逻辑。

常见判断：

| 现象 | 优先检查 |
|---|---|
| 列表没有记录 | `rag.trace.enabled` 是否开启；入口方法是否有 `@RagTraceRoot`；当前请求是否走到了被注解的方法。 |
| 只有 run 没有 node | 子流程方法是否有 `@RagTraceNode`；是否发生了自调用导致 Spring AOP 未生效。 |
| 节点层级不正确 | 异步线程是否透传 `RagTraceContext`；是否使用了未包装的线程池。 |
| 错误信息被截断 | 查看 `rag.trace.max-error-length` 配置。 |
| 节点耗时异常高 | 对照 `nodeType` 判断是重写、意图、检索、重排还是模型调用慢。 |

## 9. 扩展 Trace 节点建议

新增可观测节点时，优先选择业务阶段边界清晰的方法，例如：

- 问题重写。
- 意图识别。
- MCP 参数提取。
- MCP 工具调用。
- 向量检索。
- 多通道结果合并。
- Rerank。
- Prompt 构建。
- 模型调用。

添加节点时注意：

1. `name` 使用稳定、可读的英文短名。
2. `type` 使用少量稳定枚举风格的值，方便前端聚合。
3. 不要在极高频、极细粒度方法上滥用 Trace，避免写库过多。
4. 对异步流程，确认上下文能透传到目标线程。
