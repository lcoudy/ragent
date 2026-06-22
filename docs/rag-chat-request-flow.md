# RAG 问答请求链路说明

本文按一次流式问答请求的真实调用路径，串联前端请求、后端 SSE、会话记忆、问题改写、意图解析、检索、Prompt 组装、模型流式输出和停止任务接口。

## 入口接口

主入口位于：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/controller/RAGChatController.java
```

| 接口 | 方法 | 说明 |
|---|---|---|
| `/rag/v3/chat` | `GET` | 发起 SSE 流式问答，参数包括 `question`、`conversationId` 和 `deepThinking`。 |
| `/rag/v3/stop` | `POST` | 根据 `taskId` 停止正在输出的流式任务。 |

`/rag/v3/chat` 返回 `SseEmitter`，超时时间来自 `RAGDefaultProperties#getSseTimeoutMs`。接口上有 `@IdempotentSubmit`，用于限制同一个用户重复提交正在处理的会话请求。

## 服务编排

`RAGChatServiceImpl#streamChat` 负责把 Controller 请求转成流水线上下文：

1. 如果前端没有传 `conversationId`，生成新的会话 ID。
2. 如果当前 trace 上下文没有 `taskId`，生成新的任务 ID。
3. 通过 `StreamCallbackFactory` 创建 SSE 回调处理器。
4. 构造 `StreamChatContext`，写入问题、会话、用户、任务、深度思考开关和回调。
5. 调用 `StreamChatPipeline#execute` 执行业务链路。

异常会统一进入 `callback.onError(e)`，由 SSE 发送端结束连接。

## 流水线阶段

核心编排位于：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/pipeline/StreamChatPipeline.java
```

执行顺序：

| 阶段 | 调用点 | 作用 |
|---|---|---|
| 会话记忆 | `ConversationMemoryService#loadAndAppend` | 先持久化用户问题，再加载摘要和最近历史。 |
| 问题改写 | `QueryRewriteService#rewriteWithSplit` | 结合历史改写问题，并拆分多子问题。 |
| 意图解析 | `IntentResolver#resolve` | 将改写结果匹配到意图节点。 |
| 歧义引导 | `IntentGuidanceService#detectAmbiguity` | 置信度不足时直接返回澄清提示并短路。 |
| 系统意图 | `handleSystemOnly` | 如果全部是系统类意图，跳过检索，直接调用模型回答。 |
| 检索 | `RetrievalEngine#retrieve` | 根据意图执行 KB/MCP/混合检索与工具调用。 |
| 空结果处理 | `handleEmptyRetrieval` | 没有任何上下文时返回固定提示。 |
| Prompt 组装 | `RAGPromptService#buildStructuredMessages` | 组合 system、history、证据和用户问题。 |
| 模型输出 | `LLMService#streamChat` | 走模型路由与流式输出。 |

## 会话记忆

默认实现位于：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/memory/DefaultConversationMemoryService.java
```

加载时并行读取两类数据：

- 最新会话摘要。
- 最近历史消息。

如果历史为空，即使有摘要也返回空列表，避免只有摘要没有上下文时污染新问题。追加消息后会调用 `ConversationMemorySummaryService#compressIfNeeded`，在历史过长时触发摘要压缩。

## 检索与工具

`RetrievalEngine#retrieve` 返回 `RetrievalContext`，上层不直接关心具体来源，只判断：

- `hasKb()`：是否有知识库上下文。
- `hasMcp()`：是否有 MCP 工具上下文。
- `getIntentChunks()`：按意图组织的检索结果，用于 Prompt 规划。

多通道检索、去重和 Rerank 细节可继续参考：

- `docs/multi-channel-retrieval.md`
- `docs/retrieval-troubleshooting.md`
- `docs/mcp-tool-extension-guide.md`

## Prompt 组装

`RAGPromptService` 按场景选择模板：

| 场景 | 条件 | 默认模板 |
|---|---|---|
| KB only | 只有知识库上下文 | `answer-chat-kb.st` |
| MCP only | 只有工具上下文 | `answer-chat-mcp.st` |
| Mixed | 同时有 KB 和 MCP | `answer-chat-mcp-kb-mixed.st` |

最终消息结构是：

1. `system`：系统提示词。
2. `history`：会话摘要和最近历史。
3. `user`：检索证据、工具结果和当前问题合并后的内容。

多子问题会通过 `context-format.st` 的 `multi-questions` section 格式化；单问题使用 `single-question` section。

## SSE 事件

回调处理器位于：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/handler/StreamChatEventHandler.java
```

主要事件：

| 事件 | 载荷 | 说明 |
|---|---|---|
| `meta` | `MetaPayload` | 第一个事件，包含 `conversationId` 和 `taskId`。 |
| `message` | `MessageDelta` | 模型输出片段，`type` 为 `response` 或 `think`。 |
| `finish` | `CompletionPayload` | 回答完成，包含消息 ID 和可能生成的会话标题。 |
| `done` | `[DONE]` | SSE 流结束信号。 |

输出分片大小来自 `AIModelProperties.Stream#messageChunkSize`，默认至少为 1。完成时会把助手回答写入会话记忆；如果有深度思考内容，也会随消息一起保存。

## 停止任务

`/rag/v3/stop` 调用 `StreamTaskManager#cancel`。任务取消后：

1. 已绑定的模型流式句柄会被取消。
2. 如果已经生成部分回答，取消回调会尝试持久化这部分内容。
3. SSE 返回完成载荷并释放任务注册信息。

排查停止无效时，优先确认 `meta` 事件里的 `taskId` 是否被前端正确保存，并检查任务是否已经绑定了模型返回的 `StreamCancellationHandle`。

## 排查顺序

| 现象 | 优先检查 |
|---|---|
| 请求没有进入后端 | 前端 `VITE_API_BASE_URL`、后端 context path、浏览器 Network。 |
| SSE 立刻结束 | `RAGChatServiceImpl` 日志、`callback.onError`、接口幂等限制。 |
| 总是提示未检索到文档 | `RetrievalContext#isEmpty`、知识库文档状态、向量库配置。 |
| 回答没有历史上下文 | `ConversationMemoryService#loadAndAppend`、用户 ID、历史保留轮数。 |
| 停止按钮无效 | `taskId` 是否来自 `meta` 事件、`StreamTaskManager` 是否注册和绑定句柄。 |
