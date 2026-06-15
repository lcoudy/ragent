# MCP 工具扩展指南

本文说明如何在 Ragent 中新增 MCP 工具。扩展路径分为两类：在主服务内注册本地工具执行器，或在独立 `mcp-server` 中暴露远程工具并让主服务自动发现。

## 选择扩展方式

- 本地工具：适合只依赖主服务内部 Bean、数据库或业务服务的工具，实现 `McpToolExecutor` 并注册为 Spring Bean。
- 远程工具：适合跨系统、跨语言或独立部署的工具，在 `mcp-server` 中暴露 `McpServerFeatures.SyncToolSpecification`。
- 示例工具：天气、工单和销售查询位于 `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/executor/`。

## 主服务侧本地工具

主服务侧接口位于：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/McpToolExecutor.java
```

一个工具执行器需要提供：

- `getToolId()`：工具唯一 ID。
- `getToolDefinition()`：MCP Tool 定义，包括名称、描述和输入 JSON Schema。
- `execute(Map<String, Object> parameters)`：执行工具并返回 `CallToolResult`。

注册逻辑由 `DefaultMcpToolRegistry` 完成。它会自动注入 Spring 容器中的 `McpToolExecutor` Bean，并把远程发现的工具执行器一起放入 registry。新增本地工具时，重点保证 tool id 唯一，避免覆盖远程同名工具。

## 独立 MCP server 远程工具

远程工具在 `mcp-server` 模块中实现。推荐步骤：

1. 新增一个 `@Component` executor 类。
2. 构造 `McpSchema.Tool`，写清工具名称、描述和输入 schema。
3. 暴露 `McpServerFeatures.SyncToolSpecification` Bean。
4. 在 handler 中读取 `request.arguments()`，执行业务逻辑并返回 `CallToolResult`。
5. 启动 `mcp-server`，确认主服务日志中能发现该工具。

`McpServerConfig` 会收集所有 `SyncToolSpecification`，并统一暴露到 `/mcp` endpoint。

## 主服务发现远程工具

主服务配置在 `bootstrap/src/main/resources/application.yaml`：

```yaml
rag:
  mcp:
    servers:
      - name: default
        url: http://localhost:9099
```

启动时 `McpClientAutoConfiguration` 会：

1. 创建 MCP client。
2. 初始化连接。
3. 调用 `listTools()` 拉取工具定义。
4. 为每个工具创建 `McpClientToolExecutor`。
5. 注册到 `McpToolRegistry`。

如果远程 server 不可用，主服务会记录日志并跳过，不阻塞启动。

## 参数提取

用户问题到工具参数的转换由 `McpParameterExtractor` 负责，默认实现为 `LLMMcpParameterExtractor`。

排查参数问题时重点看：

- Tool input schema 是否完整描述字段、类型、枚举和必填项。
- 工具描述是否足够明确，避免模型抽错 queryType 或 region。
- 自定义 prompt 是否覆盖了默认提参规则。
- 提参失败时是否走了默认参数填充逻辑。

## 联调清单

1. 启动 `mcp-server`，确认 `/mcp` endpoint 可访问。
2. 启动主服务，查看日志中是否出现“返回 N 个工具”和“工具注册成功”。
3. 检查 tool id 是否与意图节点或工具选择逻辑中的 ID 一致。
4. 用固定问题触发工具调用，确认 `McpClientToolExecutor` 日志里有调用参数。
5. 如果返回 `isError=true`，先看远端工具日志，再看主服务封装的失败原因。

## 常见问题

### 主服务发现不到新工具

确认 `SyncToolSpecification` 是 Spring Bean，`mcp-server` 已重启，主服务配置的 URL 指向正确端口，并且主服务也已重启重新发现工具。

### 工具参数为空或不符合预期

优先检查 input schema 和工具描述。Schema 越模糊，LLM 提参越容易输出默认值或遗漏字段。

### 工具调用成功但答案没有使用结果

确认 RAG 编排流程是否把 `CallToolResult` 内容加入最终 prompt，上游意图是否被识别为工具类意图，以及工具返回文本是否足够可读。

### 多个工具 ID 冲突

`DefaultMcpToolRegistry` 会按 tool id 覆盖旧 executor。新增工具时应使用稳定且唯一的 ID，例如 `weather_query`、`ticket_query`。
