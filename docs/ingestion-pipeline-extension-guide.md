# 入库 Pipeline 扩展指南

本文说明如何在 Ragent 中扩展文档入库 Pipeline 节点，包括节点接口、节点配置、条件表达式、上下文传递、节点日志和调试检查。

## 核心文件

| 文件 | 作用 |
|---|---|
| `IngestionNode.java` | 节点接口，所有 Pipeline 节点都实现它。 |
| `IngestionEngine.java` | Pipeline 执行引擎，负责校验链路、选择起点、执行节点和记录日志。 |
| `IngestionContext.java` | 入库上下文，承载原始字节、文本、分块、增强结果、元数据和日志。 |
| `PipelineDefinition.java` | Pipeline 定义，包含节点列表。 |
| `NodeConfig.java` | 单个节点配置，包含节点 ID、类型、settings、condition 和 nextNodeId。 |
| `NodeResult.java` | 节点执行结果，决定成功、失败、跳过或终止。 |
| `ConditionEvaluator.java` | 节点条件表达式评估器。 |
| `NodeOutputExtractor.java` | 将上下文里的节点输出提取到节点日志。 |

## 已有节点

节点类型定义在 `IngestionNodeType`：

| 类型 | 说明 |
|---|---|
| `fetcher` | 获取文档字节和来源信息。 |
| `parser` | 解析原始文档为文本或结构化文档。 |
| `enhancer` | 对全文做 AI 增强，提取关键词、问题或元数据。 |
| `chunker` | 将文本切分成 `VectorChunk`，并生成 embedding。 |
| `enricher` | 对 chunk 做增强处理。 |
| `indexer` | 将 chunk 写入向量库；知识库文档链路可设置 `skipIndexerWrite=true` 跳过直接写入。 |

新增节点时优先沿用小写 snake_case 类型名，例如 `metadata_extractor`。

## 新增节点步骤

实现 `IngestionNode`：

```java
@Component
@RequiredArgsConstructor
public class MetadataExtractorNode implements IngestionNode {

    private final ObjectMapper objectMapper;

    @Override
    public String getNodeType() {
        return "metadata_extractor";
    }

    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        MetadataSettings settings = objectMapper.convertValue(config.getSettings(), MetadataSettings.class);
        if (context.getRawText() == null || context.getRawText().isBlank()) {
            return NodeResult.fail(new ClientException("可抽取文本为空"));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", settings.getSource());
        context.setMetadata(metadata);
        return NodeResult.ok("已抽取元数据");
    }
}
```

注意点：

- 必须注册为 Spring Bean，`IngestionEngine` 会从 `List<IngestionNode>` 收集节点。
- `getNodeType()` 必须和 Pipeline 配置里的 `nodeType` 一致。
- 节点只读写 `IngestionContext` 中自己负责的字段。
- settings 使用专门的 settings class 解析，避免直接散落读取 JSON 字段。
- 失败返回 `NodeResult.fail(...)`，不要吞掉异常后返回成功。

## NodeResult 语义

| 方法 | success | shouldContinue | 适用场景 |
|---|---|---|---|
| `NodeResult.ok()` | true | true | 节点成功，继续后续节点。 |
| `NodeResult.ok(message)` | true | true | 节点成功，并记录可读消息。 |
| `NodeResult.skip(reason)` | true | true | 条件不满足或主动跳过，继续后续节点。 |
| `NodeResult.fail(error)` | false | false | 节点失败，Pipeline 状态变为 `FAILED`。 |
| `NodeResult.terminate(reason)` | true | false | 节点成功但主动终止后续链路。 |

如果节点抛出异常，`IngestionEngine` 会捕获异常、写节点日志，并转换成失败结果。

## Pipeline 配置

`PipelineDefinition` 由多个 `NodeConfig` 构成：

```json
{
  "id": "pdf-basic",
  "name": "PDF 基础入库",
  "nodes": [
    {
      "nodeId": "fetcher-1",
      "nodeType": "fetcher",
      "settings": {},
      "nextNodeId": "parser-1"
    },
    {
      "nodeId": "parser-1",
      "nodeType": "parser",
      "settings": {},
      "nextNodeId": "chunker-1"
    },
    {
      "nodeId": "chunker-1",
      "nodeType": "chunker",
      "settings": {
        "strategy": "fixed_size",
        "chunkSize": 512,
        "overlapSize": 128
      }
    }
  ]
}
```

执行引擎会做以下校验：

- `nextNodeId` 引用的节点必须存在。
- 链路不能出现环。
- 起始节点是没有被任何 `nextNodeId` 引用的节点。
- 找不到起始节点时抛出 `ClientException`。

当前执行模型是线性链路，不支持一个节点分叉到多个下游节点。需要分支时，用条件控制某些节点跳过，并保持单条 `nextNodeId` 链。

## 条件表达式

`NodeConfig#condition` 支持多种格式。

布尔值：

```json
true
```

SpEL 字符串：

```json
"#ctx.mimeType == 'application/pdf'"
```

组合条件：

```json
{
  "all": [
    { "field": "mimeType", "operator": "eq", "value": "application/pdf" },
    { "field": "rawText", "operator": "exists" }
  ]
}
```

字段规则：

```json
{
  "field": "rawText",
  "operator": "contains",
  "value": "合同"
}
```

支持的 operator：

| operator | 说明 |
|---|---|
| `eq` | 默认，相等比较。 |
| `ne` | 不相等。 |
| `in` | 左值或右值为列表时判断包含。 |
| `contains` | 字符串包含或列表包含。 |
| `regex` | 正则匹配。 |
| `gt`/`gte` | 数字大于/大于等于。 |
| `lt`/`lte` | 数字小于/小于等于。 |
| `exists` | 字段不为 null。 |
| `not_exists` | 字段为 null。 |

条件不满足时，引擎会写一条成功的 skip 日志，并继续执行后续节点。

## 上下文字段选择

常见字段使用建议：

| 字段 | 写入节点 | 使用建议 |
|---|---|---|
| `rawBytes` | fetcher | 只存原始文件字节，避免后续节点重复下载。 |
| `mimeType` | fetcher/parser | 条件判断和解析分支使用。 |
| `rawText` | parser | 后续 enhancer/chunker 的默认输入。 |
| `document` | parser | 结构化解析结果，适合保留标题、段落、表格等结构。 |
| `enhancedText` | enhancer | chunker 会优先使用增强文本。 |
| `chunks` | chunker/enricher | indexer 和知识库持久化链路使用。 |
| `metadata` | enhancer/custom | 存放扩展字段，避免新增大量上下文属性。 |
| `logs` | engine | 引擎统一写入，节点通常不要直接改。 |

如果新增节点只产生扩展信息，优先写入 `metadata`，并在日志输出或后续节点中按 key 读取。

## 节点日志

每个节点执行后，`IngestionEngine` 会写入 `NodeLog`：

- `nodeId`
- `nodeType`
- `message`
- `durationMs`
- `success`
- `error`
- `output`

`output` 由 `NodeOutputExtractor` 从 `IngestionContext` 提取。新增节点如果需要更清晰的输出，可以扩展 `NodeOutputExtractor#genericOutput` 或增加新类型分支。

注意不要在日志中输出大体积字节、敏感凭据或完整外部响应。当前 fetcher 输出会包含 `rawBytesBase64`，调试完成后应关注日志体积。

## 与知识库文档链路的关系

知识库文档入库会复用 Pipeline，但可能设置：

```java
context.setSkipIndexerWrite(true);
```

这表示 `IndexerNode` 只做校验，不直接写向量库。最终由知识库文档服务在事务内统一删除旧 chunk、写新 chunk、清理旧向量并写入新向量，避免 Pipeline 和文档服务重复写索引。

扩展 `indexer` 或新增写库节点时，要明确它是否会被知识库文档链路调用，并尊重 `skipIndexerWrite`。

## 测试建议

优先写纯单元测试，不依赖数据库、Redis、RocketMQ、Milvus 或真实模型：

- 用 fake `IngestionNode` 验证引擎执行顺序。
- 覆盖环路、缺失节点、无起始节点等校验失败。
- 覆盖条件命中、条件跳过和 `NodeResult.terminate`。
- 验证节点执行后 `IngestionContext` 字段和 `NodeLog` 输出。

已有参考：

- `IngestionEngineValidationTest`
- `IngestionEngineConditionTest`

## 排查路径

| 现象 | 检查点 |
|---|---|
| 启动后找不到节点类型 | 节点是否是 Spring Bean，`getNodeType()` 是否和配置一致。 |
| Pipeline 未找到起点 | 是否所有节点都被某个 `nextNodeId` 引用，或存在环。 |
| 执行顺序不对 | 检查每个 `nextNodeId`，不要只看 nodes 数组顺序。 |
| 节点被跳过 | 查看 `condition` 和日志里的 `Skipped: 条件未满足`。 |
| chunker 报可分块文本为空 | parser 是否写入 `rawText`，enhancer 是否清空了 `enhancedText`。 |
| 写向量重复 | 检查 `skipIndexerWrite` 和知识库文档服务的统一持久化路径。 |
| 日志输出过大 | 检查 `NodeOutputExtractor` 是否输出了大字段或 base64 字节。 |
