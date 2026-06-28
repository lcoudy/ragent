# 问题改写与术语映射指南

本文说明 Ragent 在 RAG 问答前如何处理用户问题：先做术语归一化，再按配置决定是否调用 LLM 做问题改写与多问句拆分，最后把结果交给意图解析和检索链路。

## 调用位置

流式问答流水线位于：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/pipeline/StreamChatPipeline.java
```

问题预处理发生在两个阶段：

| 阶段 | 方法 | 说明 |
|---|---|---|
| 会话记忆加载 | `loadMemory` | 先把当前用户问题写入会话，再加载摘要和最近历史。 |
| 问题改写 | `rewriteQuery` | 调用 `QueryRewriteService#rewriteWithSplit(question, history)`。 |

改写结果是 `RewriteResult`，包含：

- `rewrittenQuestion`：用于后续意图解析和检索的主问题。
- `subQuestions`：多问句拆分结果，用于多意图和多块证据组织。

## 开关配置

配置位于：

```yaml
rag:
  query-rewrite:
    enabled: true
```

绑定类：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/config/RAGConfigProperties.java
```

行为差异：

| 开关 | 行为 |
|---|---|
| `true` | 先做术语归一化，再调用 LLM 按模板返回 `rewrite` 和 `sub_questions`。 |
| `false` | 只做术语归一化，再用规则按标点拆分问题，不调用 LLM。 |

无论开关是否开启，术语归一化都会先执行。

## 术语映射

运行时服务：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/rewrite/QueryTermMappingService.java
```

管理接口：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/controller/QueryTermMappingController.java
```

前端页面：

```text
frontend/src/pages/admin/query-term-mapping/QueryTermMappingPage.tsx
```

接口清单：

| 接口 | 方法 | 说明 |
|---|---|---|
| `/mappings` | `GET` | 分页查询映射规则。 |
| `/mappings/{id}` | `GET` | 查询单条规则详情。 |
| `/mappings` | `POST` | 新增映射规则。 |
| `/mappings/{id}` | `PUT` | 更新映射规则。 |
| `/mappings/{id}` | `DELETE` | 删除映射规则。 |

映射规则核心字段：

| 字段 | 说明 |
|---|---|
| `sourceTerm` | 用户问题中可能出现的原始词。 |
| `targetTerm` | 归一化后的目标词。 |
| `matchType` | 匹配类型，当前运行时只执行 `1` 精确匹配。 |
| `priority` | 优先级，数据库加载后按优先级和原始词长度排序。 |
| `enabled` | 是否启用。 |
| `remark` | 备注。 |

注意：前端页面展示了精确、前缀、正则、整词等匹配类型，但当前 `QueryTermMappingService#normalize` 只处理 `matchType=1`。其他类型是扩展预留，配置后不会影响运行时归一化。

## 缓存策略

缓存管理器：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/rewrite/QueryTermMappingCacheManager.java
```

规则：

1. 先从 Redis key `ragent:query-term:mappings` 读取映射列表。
2. 缓存未命中时从数据库查询启用规则。
3. 查询结果按优先级和原始词长度排序后写回 Redis。
4. 新增、更新、删除规则后清理缓存。
5. 缓存默认 7 天过期。

如果修改规则后没有立即生效，优先确认管理接口是否成功清理了 Redis 缓存。

## 替换规则

底层替换工具：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/rewrite/QueryTermMappingUtil.java
```

规则要点：

- 空文本或空 `sourceTerm` 直接返回原文。
- 从左到右查找 `sourceTerm` 并替换为 `targetTerm`。
- 如果命中位置本来已经是 `targetTerm`，不会重复替换。
- 多条映射会按加载顺序依次应用。

示例：

| 原文 | source | target | 结果 |
|---|---|---|---|
| `阿里使用钉钉` | `钉钉` | `DingTalk` | `阿里使用DingTalk` |
| `平安保司理赔` | `平安` | `平安保司` | `平安保司理赔` |

第二个例子中，命中位置已经是目标词开头，因此不会变成 `平安保司保司理赔`。

## LLM 改写与拆分

默认实现：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/rewrite/MultiQuestionRewriteService.java
```

模板：

```text
bootstrap/src/main/resources/prompt/user-question-rewrite.st
```

调用流程：

1. 对原始问题做术语归一化，得到 `normalizedQuestion`。
2. 构造 ChatRequest，system message 使用改写模板。
3. 只保留最近 1 到 2 轮 user/assistant 历史，过滤 system 摘要。
4. 调用 `LLMService#chat`。
5. 解析 LLM 返回 JSON。
6. 解析失败或调用异常时，回退到归一化问题。

期望 JSON 结构：

```json
{
  "rewrite": "归一化后的检索问题",
  "sub_questions": ["子问题1", "子问题2"]
}
```

`sub_questions` 为空时，会退化为单个 `rewrite`。

## 规则拆分兜底

当 `rag.query-rewrite.enabled=false`，或者 LLM 调用失败时，会使用规则兜底：

- 按 `?`、`？`、`。`、`；`、`;`、换行拆分。
- 去掉空白片段。
- 每个子问题补 `？` 结尾。
- 如果拆不出有效片段，返回原问题。

这保证关闭 LLM 改写后，多问句仍能进入后续意图解析。

## 与后续链路的关系

| 下游 | 使用字段 | 影响 |
|---|---|---|
| 意图解析 | `RewriteResult` | 根据改写后的问题和子问题匹配意图节点。 |
| 检索 | 子问题和意图 | 多子问题会影响检索上下文组织。 |
| Prompt | `rewrittenQuestion` 和 `subQuestions` | 多问题会用 `multi-questions` section 格式化。 |
| Trace | `@RagTraceNode` | 改写阶段会记录为 `query-rewrite-and-split`。 |

改写结果不只是展示文本，它直接影响召回范围、意图数量和最终 Prompt 结构。

## 排查路径

| 现象 | 检查点 |
|---|---|
| 术语没有被替换 | 规则是否启用、`matchType` 是否为 `1`、Redis 缓存是否清理。 |
| 替换顺序不符合预期 | 检查 `priority` 和 `sourceTerm` 长度排序。 |
| 关闭改写后仍有变化 | 术语归一化仍会执行，这是预期行为。 |
| LLM 返回后没有拆分 | 检查 `sub_questions` 是否是 JSON array，元素是否是字符串。 |
| 改写失败回退原问题 | 查看模型调用异常、模板路径、JSON fence 和解析日志。 |
| 多问句回答结构异常 | 检查 `RewriteResult#subQuestions`、意图解析结果和 `context-format.st` 的 `multi-questions` section。 |

相关测试：

- `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/rewrite/QueryTermMappingUtilTest.java`
- `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/rewrite/MultiQuestionRewriteServiceTests.java`
