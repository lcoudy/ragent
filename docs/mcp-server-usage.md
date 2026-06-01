# MCP server 使用说明

本文说明 Ragent 中独立 MCP server 的启动方式、默认端口、工具 endpoint、示例工具，以及主服务如何发现和调用远程 MCP 工具。

## 1. 模块定位

仓库中与 MCP 相关的代码分为两部分：

- `mcp-server/`：独立 Spring Boot 应用，负责暴露 MCP 工具。
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/`：主服务侧 MCP 客户端、工具注册表和工具执行器。

默认情况下，主服务启动后会读取 `rag.mcp.servers` 配置，连接独立 MCP server，拉取工具列表并注册到本地工具注册表。

## 2. 启动 MCP server

在仓库根目录运行：

```powershell
./mvnw -pl mcp-server spring-boot:run
```

MCP server 默认配置位于：

```text
mcp-server/src/main/resources/application.yml
```

默认端口：

```yaml
server:
  port: 9099
```

MCP transport servlet 在 `McpServerConfig` 中注册：

```text
mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/config/McpServerConfig.java
```

默认 endpoint：

```text
http://localhost:9099/mcp
```

注意：主服务配置中可以写 `http://localhost:9099`，客户端会自动补全 `/mcp`。

## 3. 示例工具

当前 MCP server 内置了三个示例工具，位于：

```text
mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/executor/
```

| 工具 | Tool ID | 说明 |
|---|---|---|
| 天气查询 | `weather_query` | 查询城市当前天气或未来天气预报。 |
| 工单查询 | `ticket_query` | 按地区、状态、优先级、产品、客户等维度查询客户技术支持工单。 |
| 销售查询 | `sales_query` | 按地区、周期、产品、销售人员等维度查询销售数据。 |

每个工具通过 `McpServerFeatures.SyncToolSpecification` 暴露工具定义和调用处理器。工具定义中包含工具名称、描述、输入 JSON Schema 和参数约束。

## 4. 主服务配置

主服务默认配置位于：

```text
bootstrap/src/main/resources/application.yaml
```

MCP server 配置：

```yaml
rag:
  mcp:
    servers:
      - name: default
        url: http://localhost:9099
```

配置类：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/McpClientProperties.java
```

字段说明：

| 字段 | 说明 |
|---|---|
| `name` | MCP server 名称，用于日志区分。 |
| `url` | MCP server 基础地址，可写到端口，也可直接写完整 `/mcp` 地址。 |

## 5. 工具发现流程

主服务侧入口：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/McpClientAutoConfiguration.java
```

启动流程：

1. 读取 `rag.mcp.servers` 配置。
2. 对每个 server 创建 `HttpClientStreamableHttpTransport`。
3. 使用 `McpClient.sync(transport)` 创建同步客户端。
4. 调用 `client.initialize()` 初始化连接。
5. 调用 `client.listTools()` 获取远程工具列表。
6. 为每个远程工具创建 `McpClientToolExecutor`。
7. 注册到 `McpToolRegistry`，后续 RAG 流程即可按 tool id 调用。

如果 MCP server 不可用，主服务会记录错误日志并跳过该 server，不会阻塞主服务启动。

## 6. 工具调用链路

远程工具执行器：

```text
bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/McpClientToolExecutor.java
```

调用流程：

```text
RAG 编排流程
  -> 选择 MCP 工具
  -> McpToolRegistry 获取 McpToolExecutor
  -> McpClientToolExecutor.execute(parameters)
  -> mcpClient.callTool(...)
  -> 远端 mcp-server 执行对应工具
  -> 返回 CallToolResult
```

如果远程调用失败，`McpClientToolExecutor` 会返回带 `isError=true` 的 `CallToolResult`，并将失败原因写入文本内容。

## 7. 常见排查

### 主服务日志提示连接 MCP server 失败

检查：

- `mcp-server` 是否已经启动。
- 端口是否为 `9099`。
- 主服务 `rag.mcp.servers[].url` 是否正确。
- 防火墙或代理是否阻断本地 HTTP 访问。

### 工具列表为空

检查：

- 工具类是否被 Spring 扫描。
- 工具类中是否声明了 `McpServerFeatures.SyncToolSpecification` Bean。
- `McpServerConfig#mcpServer` 是否注入到了工具规格列表。

### 工具能发现但调用失败

检查：

- 参数名称是否和工具 JSON Schema 一致。
- 必填参数是否传入。
- 工具内部是否返回了错误结果。
- 主服务日志中的 `toolId`、`params` 和 `reason`。

## 8. 新增 MCP 工具建议

在 `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/executor/` 下新增工具类，并遵循以下约定：

1. 使用稳定的 tool id，例如 `order_query`。
2. 工具描述写清楚适用场景，方便模型选择。
3. 输入 schema 明确字段类型、枚举值、默认值和必填字段。
4. 对缺失参数和非法参数返回可读错误。
5. 在日志中记录 tool id、关键参数和耗时，方便排查。
