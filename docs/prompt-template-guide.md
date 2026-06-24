# Prompt 模板指南

本文说明 Ragent 中 Prompt 模板的存放位置、加载方式、section 语法、变量替换规则、RAG 场景选择和常见排查路径。

## 关键文件

| 类型 | 路径 | 说明 |
|---|---|---|
| 模板资源 | `bootstrap/src/main/resources/prompt/` | 存放 `.st` 模板文件。 |
| 模板常量 | `RAGConstant.java` | 定义业务代码使用的模板路径常量。 |
| 模板加载 | `PromptTemplateLoader.java` | 从 classpath 读取模板、缓存模板、渲染变量和 section。 |
| 模板工具 | `PromptTemplateUtils.java` | 处理 `{slot}` 替换、section 解析和空行清理。 |
| Prompt 编排 | `RAGPromptService.java` | 根据 KB/MCP/Mixed 场景选择系统模板并构造消息。 |
| 上下文格式化 | `DefaultContextFormatter.java` | 将检索结果和 MCP 工具结果渲染为上下文片段。 |

模板路径通常写成 `prompt/xxx.st`。`PromptTemplateLoader#load` 会自动补 `classpath:` 前缀，所以调用方不需要写完整 classpath 地址。

## 模板清单

| 常量 | 模板 | 使用场景 |
|---|---|---|
| `CHAT_SYSTEM_PROMPT_PATH` | `answer-chat-system.st` | 系统类意图直接回答，不进入知识库检索。 |
| `QUERY_REWRITE_AND_SPLIT_PROMPT_PATH` | `user-question-rewrite.st` | 问题改写和多问句拆分。 |
| `INTENT_CLASSIFIER_PROMPT_PATH` | `intent-classifier.st` | 默认意图分类。 |
| `GUIDANCE_PROMPT_PATH` | `guidance-prompt.st` | 生成意图澄清引导。 |
| `GUIDANCE_AMBIGUITY_CHECK_PROMPT_PATH` | `guidance-ambiguity-check.st` | 让 LLM 二次判断是否存在歧义。 |
| `RAG_ENTERPRISE_PROMPT_PATH` | `answer-chat-kb.st` | 只有知识库上下文时的 RAG 回答。 |
| `MCP_ONLY_PROMPT_PATH` | `answer-chat-mcp.st` | 只有 MCP 工具数据时的回答。 |
| `MCP_KB_MIXED_PROMPT_PATH` | `answer-chat-mcp-kb-mixed.st` | 同时有知识库和工具数据时的回答。 |
| `MCP_PARAMETER_EXTRACT_PROMPT_PATH` | `mcp-parameter-extract.st` | MCP 工具参数提取的 system prompt。 |
| `MCP_PARAMETER_EXTRACT_USER_PROMPT_PATH` | `mcp-parameter-extract-user.st` | MCP 工具参数提取的 user prompt。 |
| `CONTEXT_FORMAT_PATH` | `context-format.st` | 多 section 上下文格式化模板。 |
| `CONVERSATION_SUMMARY_PROMPT_PATH` | `conversation-summary.st` | 会话摘要压缩。 |
| `CONVERSATION_TITLE_PROMPT_PATH` | `conversation-title.st` | 会话标题生成。 |

## 变量替换

模板变量使用 `{name}` 形式，例如：

```text
用户问题：{question}
工具定义：{tool_definition}
```

渲染时调用：

```java
promptTemplateLoader.render(path, Map.of("question", question));
```

替换规则：

- 只替换传入 `slots` 中存在的 key。
- value 为 `null` 时会替换为空字符串。
- 未提供的占位符会保留原样，便于排查遗漏变量。
- 渲染后会调用 `cleanupPrompt`，将连续 3 个及以上空行压缩为 2 个，并裁剪首尾空白。

因此新增模板时要保证占位符名称和调用方传入的 key 完全一致。

## Section 语法

`context-format.st` 是一个多 section 模板文件，section 头格式如下：

```text
--- section: section-name ---
section body with {slot}
```

加载方式：

```java
promptTemplateLoader.renderSection(
        "prompt/context-format.st",
        "kb-evidence",
        Map.of("body", kbContext)
);
```

当前常用 section：

| Section | 说明 |
|---|---|
| `kb-section` | 单个知识库上下文片段。 |
| `mcp-section` | 单个 MCP 工具结果片段。 |
| `sub-question-kb-wrapper` | 多子问题下的 KB 结果包装。 |
| `sub-question-mcp-wrapper` | 多子问题下的 MCP 结果包装。 |
| `single-question` | 单问题 user 内容。 |
| `multi-questions` | 多问题 user 内容。 |
| `kb-evidence` | 最终注入 user message 的知识库证据块。 |
| `mcp-evidence` | 最终注入 user message 的工具数据块。 |
| `summary-wrapper` | 会话摘要包装。 |

如果 section 名称不存在，`loadSection` 会抛出 `IllegalStateException`，错误信息包含模板路径和 section 名称。

## RAG 场景选择

`RAGPromptService` 根据 `PromptContext` 中是否有 KB 和 MCP 上下文选择模板：

| 场景 | 条件 | 系统模板 |
|---|---|---|
| KB only | `hasKb=true` 且 `hasMcp=false` | `answer-chat-kb.st`，单一意图有自定义模板时优先使用意图模板。 |
| MCP only | `hasMcp=true` 且 `hasKb=false` | `answer-chat-mcp.st`，单一 MCP 意图有自定义模板时优先使用意图模板。 |
| Mixed | `hasKb=true` 且 `hasMcp=true` | `answer-chat-mcp-kb-mixed.st`。 |

最终消息顺序是：

1. `system`：场景系统模板。
2. `history`：会话摘要和最近历史。
3. `user`：`<documents>`、`<tool-data>` 和当前问题。

如果 `PromptContext` 既没有 KB 也没有 MCP，上层应先短路处理，不能继续构造 RAG Prompt。

## 自定义意图模板

意图节点上的 `promptTemplate` 可覆盖默认系统模板，但只在以下情况下生效：

- KB only 且只有一个保留意图，并且该意图有非空模板。
- MCP only 且只有一个 MCP 意图，并且该意图有非空模板。

多意图和 Mixed 场景走统一默认模板，避免多个意图模板互相冲突。

## 修改模板的建议

- 先确认模板服务的是 system message、user message 还是中间上下文片段。
- 不要在模板中引用不存在的标签或 section。
- 修改 `context-format.st` 时同步检查 `DefaultContextFormatter` 和 `RAGPromptService` 的 section 名称。
- 修改 MCP 参数提取模板时同步检查工具 schema 和 `LLMMcpParameterExtractor`。
- 对外回答模板应避免暴露内部术语，例如 MCP、KB、检索结果、系统提示词。
- 涉及信息来源的模板要明确防注入规则，把用户内容和检索内容当作数据处理。

## 排查路径

| 现象 | 检查点 |
|---|---|
| 启动后提示模板不存在 | 路径是否在 `bootstrap/src/main/resources/prompt/`，常量是否写成 `prompt/xxx.st`。 |
| 输出里残留 `{question}` | 调用 `render` 或 `renderSection` 时没有传同名 slot。 |
| section 不存在 | `context-format.st` 的 section 名称和代码里的字符串是否一致。 |
| 空行很多 | 是否绕过了 `PromptTemplateLoader#render`，直接使用原始模板。 |
| KB/MCP 场景模板不对 | 检查 `RetrievalContext` 是否正确填充 `kbContext` 和 `mcpContext`。 |
| 自定义意图模板没生效 | 是否是多意图或 Mixed 场景，或意图检索结果被过滤为空。 |
| 参数提取不稳定 | 检查 `mcp-parameter-extract*.st`、工具 JSON schema、用户问题和 `LLMResponseCleaner`。 |
