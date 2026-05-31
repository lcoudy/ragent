# 模型路由与故障切换说明

本文档基于 `infra-ai` 中的模型路由实现，说明 Chat、Embedding、Rerank 三类模型如何选择候选、健康检查、熔断降级和流式首包探测。

## 相关入口

模型路由集中在 `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/`：

- `config/AIModelProperties.java`：绑定 `ai.*` 配置，包括 provider、模型组、候选模型和熔断参数。
- `model/ModelSelector.java`：按模型组选择可用候选，并根据默认模型、深度思考模型和优先级排序。
- `model/ModelRoutingExecutor.java`：同步调用的统一降级执行器，Chat、Embedding、Rerank 共用。
- `model/ModelHealthStore.java`：维护每个模型的健康状态，提供 CLOSED、OPEN、HALF_OPEN 三态熔断。
- `chat/RoutingLLMService.java`：Chat 路由服务，流式接口在这里额外做首包探测。
- `embedding/RoutingEmbeddingService.java`：Embedding 路由服务。
- `rerank/RoutingRerankService.java`：Rerank 路由服务。

默认配置位于 `bootstrap/src/main/resources/application.yaml` 的 `ai:` 节点。

## 候选模型配置

每类能力都有独立的模型组：

```yaml
ai:
  selection:
    failure-threshold: 2
    open-duration-ms: 30000

  chat:
    default-model: qwen3-max
    deep-thinking-model: qwen3-max
    candidates:
      - id: qwen-plus
        provider: bailian
        model: qwen-plus-latest
        priority: 1
      - id: qwen3-max
        provider: bailian
        model: qwen3-max
        supports-thinking: true
        priority: 3
      - id: glm-4.7
        provider: siliconflow
        model: Pro/zai-org/GLM-4.7
        supports-thinking: true
        priority: 0
```

候选字段含义：

- `id`：业务侧使用的模型唯一标识。未配置时，代码会用 `provider::model` 生成。
- `provider`：供应商标识，例如 `bailian`、`siliconflow`、`ollama`、`noop`。
- `model`：实际传给供应商 API 的模型名。
- `url`：可选，配置后优先使用候选自己的完整 URL。
- `dimension`：Embedding 模型维度。
- `priority`：候选优先级，数值越小越靠前。
- `enabled`：是否启用，默认 `true`。
- `supports-thinking`：是否支持深度思考模式。

Provider 的基础地址、API Key 和各能力端点配置在 `ai.providers` 下。HTTP 客户端会通过 `ModelUrlResolver` 优先使用候选 `url`，否则使用 provider `url` 加对应能力 endpoint。

## 候选选择顺序

`ModelSelector` 的选择规则是：

1. 过滤空候选和 `enabled: false` 的候选。
2. 如果是 Chat 深度思考请求，只保留 `supports-thinking: true` 的候选。
3. 把 `default-model` 或 `deep-thinking-model` 对应的候选放到第一位。
4. 其余候选按 `priority` 从小到大排序，再按 `id` 排序。
5. 过滤当前处于不可用状态的模型。
6. 过滤缺少 provider 配置的模型，但 `noop` provider 例外。

这意味着默认模型会优先于 `priority`。例如当前 Chat 默认模型是 `qwen3-max`，即使 `glm-4.7` 的 `priority` 更小，普通和深度思考请求仍会先尝试 `qwen3-max`，失败后再按优先级降级到后续候选。

## 同步调用降级流程

同步 Chat、Embedding 和 Rerank 都通过 `ModelRoutingExecutor#executeWithFallback` 执行：

```text
业务服务调用模型能力
  -> ModelSelector 选择候选列表
  -> 按顺序遍历 ModelTarget
      -> 找 provider client
      -> healthStore.allowCall(modelId)
      -> 调用具体供应商 client
      -> 成功：markSuccess 并返回
      -> 失败：markFailure 并尝试下一个候选
  -> 所有候选失败：抛出 RemoteException
```

不同能力接入同一个执行器：

- Chat：`RoutingLLMService#chat`
- Embedding：`RoutingEmbeddingService#embed` 和 `embedBatch`
- Rerank：`RoutingRerankService#rerank`

指定 `modelId` 的调用会先解析出单个 `ModelTarget`，再进入执行器。它仍会经过健康检查和失败计数，但不会横向切换到其他候选模型。

## 熔断状态机

`ModelHealthStore` 为每个模型 `id` 独立维护健康状态。

```text
CLOSED
  成功：保持 CLOSED，失败计数清零
  失败：consecutiveFailures + 1
  失败次数达到 failure-threshold：进入 OPEN

OPEN
  openUntil 未到：拒绝调用，路由跳过该模型
  openUntil 到期：进入 HALF_OPEN，并允许一个探测请求

HALF_OPEN
  探测进行中：拒绝并发探测
  探测成功：回到 CLOSED
  探测失败：回到 OPEN，重新计算 openUntil
```

默认参数：

- `ai.selection.failure-threshold: 2`
- `ai.selection.open-duration-ms: 30000`

因此同一模型连续失败 2 次会熔断 30 秒。冷却结束后只允许一个半开探测请求，避免大量请求同时打到刚恢复的模型。

## 流式 Chat 首包探测

流式 Chat 没有直接复用 `ModelRoutingExecutor`，因为启动流式连接成功不代表模型已经真正产出内容。`RoutingLLMService#streamChat` 会对每个候选执行额外的首包探测：

```text
选择 Chat 候选
  -> healthStore.allowCall(modelId)
  -> client.streamChat(request, ProbeStreamBridge, target)
  -> 等待首包，超时时间 60 秒
      -> 收到 content/thinking：markSuccess，提交缓冲内容，返回取消句柄
      -> 收到 error：markFailure，取消当前流，尝试下一个模型
      -> 先 complete 但无内容：markFailure，取消当前流，尝试下一个模型
      -> 超时：markFailure，取消当前流，尝试下一个模型
  -> 所有候选失败：callback.onError 并抛出 RemoteException
```

`ProbeStreamBridge` 会在探测成功前缓存下游事件。只有收到 `onContent` 或 `onThinking` 后，才把已缓存事件提交给原始 callback。这样可以避免一个失败模型先向前端写入半截内容，再切换到另一个模型时造成响应混乱。

## 常见排查路径

### 候选模型没有被选中

优先检查：

- `ai.<capability>.candidates` 是否存在该候选。
- 候选是否被设置为 `enabled: false`。
- Chat 深度思考请求是否要求 `supports-thinking: true`。
- Provider 是否在 `ai.providers` 下配置；`noop` 以外的 provider 缺失会被过滤。
- 该 `id` 是否正处于 OPEN 或 HALF_OPEN 探测中。

### 请求总是走不到预期优先级

先确认 `default-model` 或 `deep-thinking-model`。默认模型会被固定排到第一位，然后才比较其他候选的 `priority`。

### 模型失败后没有立刻重试同一个模型

这是熔断设计。达到失败阈值后，`ModelHealthStore` 会在 `open-duration-ms` 期间跳过该模型。冷却结束后只放行一个半开探测请求，探测成功才恢复。

### 流式请求连接成功但仍切换模型

流式请求以首包结果为准。连接启动后，如果 60 秒内没有收到内容或思考片段，或者只收到完成事件但没有内容，当前模型会被判定失败并切换到下一个候选。

### Rerank 失败后仍有结果

当前默认 Rerank 候选包含 `rerank-noop`。当外部 Rerank 模型不可用时，路由可以降级到 noop 实现，按原候选顺序截取结果，避免检索链路因为精排服务异常整体失败。
