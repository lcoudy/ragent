# 模型供应商配置指南

本文说明 Ragent 中聊天、Embedding、Rerank 模型的配置方式、默认候选、环境变量和常见配置错误。相关配置位于主服务的 `application.yaml`，绑定到 `infra-ai` 模块中的 `AIModelProperties`。

## 1. 配置入口

配置文件：

```text
bootstrap/src/main/resources/application.yaml
```

配置类：

```text
infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/config/AIModelProperties.java
```

模型能力分为三类：

| 能力 | 配置节点 | 说明 |
|---|---|---|
| Chat | `ai.chat` | 聊天、问答和流式输出。 |
| Embedding | `ai.embedding` | 文档分块和问题向量化。 |
| Rerank | `ai.rerank` | 对检索结果重排序。 |

供应商统一配置在：

```yaml
ai:
  providers:
```

模型组和候选模型配置在：

```yaml
ai:
  chat:
  embedding:
  rerank:
```

## 2. 支持的供应商

供应商枚举位于：

```text
infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/enums/ModelProvider.java
```

当前支持：

| Provider ID | 说明 | API Key |
|---|---|---|
| `ollama` | 本地 Ollama OpenAI 兼容服务。 | 不需要。 |
| `bailian` | 阿里云百炼。 | 需要 `BAILIAN_API_KEY`。 |
| `siliconflow` | 硅基流动。 | 需要 `SILICONFLOW_API_KEY`。 |
| `noop` | 空实现，主要用于 Rerank 兜底。 | 不需要。 |

## 3. Provider 配置

Provider 配置包含：

| 字段 | 说明 |
|---|---|
| `url` | 供应商基础地址。 |
| `api-key` | 供应商 API Key，可通过环境变量注入。 |
| `endpoints` | 不同能力对应的 endpoint 路径，例如 `chat`、`embedding`、`rerank`。 |

默认配置示例：

```yaml
ai:
  providers:
    ollama:
      url: http://localhost:11434
      endpoints:
        chat: /v1/chat/completions
        embedding: /v1/embeddings
    bailian:
      url: https://dashscope.aliyuncs.com
      api-key: ${BAILIAN_API_KEY:}
      endpoints:
        chat: /compatible-mode/v1/chat/completions
        rerank: /api/v1/services/rerank/text-rerank/text-rerank
    siliconflow:
      url: https://api.siliconflow.cn
      api-key: ${SILICONFLOW_API_KEY:}
      endpoints:
        chat: /v1/chat/completions
        embedding: /v1/embeddings
```

URL 解析逻辑位于：

```text
infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/http/ModelUrlResolver.java
```

解析优先级：

```text
候选模型 url > provider.url + provider.endpoints[能力]
```

如果单个候选模型配置了 `url`，会优先使用候选模型自己的完整 URL。

## 4. 模型候选配置

每个模型组都有相同结构：

```yaml
default-model: xxx
deep-thinking-model: xxx
candidates:
  - id: xxx
    provider: xxx
    model: xxx
    priority: 1
    enabled: true
    supports-thinking: false
```

字段说明：

| 字段 | 说明 |
|---|---|
| `id` | 项目内使用的模型唯一标识。 |
| `provider` | 供应商 ID，必须和 `ai.providers` 中的 key 匹配，`noop` 除外。 |
| `model` | 传给供应商 API 的模型名称。 |
| `url` | 可选，候选模型专属完整 URL。 |
| `dimension` | Embedding 模型维度。 |
| `priority` | 候选优先级，数字越小越靠前。 |
| `enabled` | 是否启用该候选，默认 `true`。 |
| `supports-thinking` | Chat 候选是否支持深度思考。 |

## 5. Chat 配置

默认 Chat 配置：

```yaml
ai:
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
      - id: qwen3-local
        provider: ollama
        model: qwen3:8b-fp16
        priority: 2
```

Chat 调用入口：

```text
infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/chat/RoutingLLMService.java
```

客户端实现：

| Provider | 客户端 |
|---|---|
| `bailian` | `BaiLianChatClient` |
| `siliconflow` | `SiliconFlowChatClient` |
| `ollama` | `OllamaChatClient` |

深度思考模式会优先使用 `deep-thinking-model`，并过滤掉 `supports-thinking=false` 的候选。

## 6. Embedding 配置

默认 Embedding 配置：

```yaml
ai:
  embedding:
    default-model: qwen-emb-8b
    candidates:
      - id: qwen-emb-8b
        provider: siliconflow
        model: Qwen/Qwen3-Embedding-8B
        dimension: ${rag.default.dimension}
        priority: 1
      - id: qwen-emb-local
        provider: ollama
        model: qwen3-embedding:8b-fp16
        dimension: ${rag.default.dimension}
        priority: 2
```

Embedding 调用入口：

```text
infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/embedding/RoutingEmbeddingService.java
```

客户端实现：

| Provider | 客户端 |
|---|---|
| `siliconflow` | `SiliconFlowEmbeddingClient` |
| `ollama` | `OllamaEmbeddingClient` |

注意：

- `dimension` 应与 `rag.default.dimension` 和向量库 collection 维度一致。
- 如果切换 Embedding 维度，已有向量索引通常需要重建。
- Ollama Embedding 不需要 API Key。

## 7. Rerank 配置

默认 Rerank 配置：

```yaml
ai:
  rerank:
    default-model: qwen3-rerank
    candidates:
      - id: qwen3-rerank
        provider: bailian
        model: qwen3-rerank
        priority: 1
      - id: rerank-noop
        provider: noop
        model: noop
        priority: 100
```

Rerank 调用入口：

```text
infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/rerank/RoutingRerankService.java
```

客户端实现：

| Provider | 客户端 |
|---|---|
| `bailian` | `BaiLianRerankClient` |
| `noop` | `NoopRerankClient` |

`noop` 是兜底实现，不调用远程模型，只按原顺序截取 topN。它适合本地开发或 Rerank 服务不可用时保持主流程可跑。

## 8. 模型选择与故障切换

选择器：

```text
infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/model/ModelSelector.java
```

执行器：

```text
infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/model/ModelRoutingExecutor.java
```

选择规则：

1. 过滤 `enabled=false` 的候选。
2. 深度思考模式下过滤 `supports-thinking=false` 的 Chat 候选。
3. 优先选择 `default-model` 或 `deep-thinking-model` 对应的候选。
4. 其余候选按 `priority` 从小到大排序。
5. 跳过健康状态不可用的模型。
6. 执行失败后标记失败并尝试下一个候选。

熔断配置：

```yaml
ai:
  selection:
    failure-threshold: 2
    open-duration-ms: 30000
```

含义：

| 字段 | 说明 |
|---|---|
| `failure-threshold` | 连续失败达到阈值后进入不可用状态。 |
| `open-duration-ms` | 不可用状态持续时间，过期后可再次尝试。 |

## 9. 环境变量

本地启动前建议设置：

```powershell
$env:BAILIAN_API_KEY="your-bailian-key"
$env:SILICONFLOW_API_KEY="your-siliconflow-key"
```

如果只使用 Ollama，本地 Chat 和 Embedding 可以不配置远程 API Key，但需要确认：

- Ollama 服务正在运行。
- `ai.providers.ollama.url` 指向正确地址。
- 本地模型已经拉取。
- 候选模型 `model` 名称与 Ollama 中模型名一致。

## 10. 常见问题

### Provider 配置缺失

日志中出现 `Provider配置缺失` 时，检查候选模型的 `provider` 是否能在 `ai.providers` 下找到同名配置。

### API Key 为空

百炼和硅基流动客户端会校验 API Key。检查：

- 环境变量是否设置。
- Spring Boot 启动进程是否能读取该环境变量。
- `application.yaml` 中是否仍保留 `${BAILIAN_API_KEY:}` 或 `${SILICONFLOW_API_KEY:}`。

### Provider endpoint 缺失

`ModelUrlResolver` 会按能力读取 endpoint：

| 能力 | endpoint key |
|---|---|
| Chat | `chat` |
| Embedding | `embedding` |
| Rerank | `rerank` |

如果某个 provider 缺少对应 endpoint，又没有在候选模型上配置完整 `url`，调用会失败。

### Embedding 维度不一致

如果模型输出维度和向量库 collection 维度不一致，入库或检索会失败。切换 Embedding 模型时要同步确认：

- `rag.default.dimension`
- `ai.embedding.candidates[].dimension`
- 已存在向量 collection 的维度

### 深度思考模式没有候选

如果开启深度思考但没有 `supports-thinking=true` 的 Chat 候选，选择器会返回空候选。检查 `deep-thinking-model` 和候选模型配置。

### Rerank 不想调用远程服务

可以把 `rerank-noop` 作为默认模型：

```yaml
ai:
  rerank:
    default-model: rerank-noop
```

这样可以保留后处理链路，但不调用外部 Rerank 服务。
