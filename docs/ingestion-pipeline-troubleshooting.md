# 文档摄取 Pipeline 排查指南

本文档用于排查文档摄取 Pipeline 创建、执行和节点日志查看时的常见问题。相关代码入口：

- Pipeline 管理：`bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/controller/IngestionPipelineController.java`
- 任务执行：`bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/controller/IngestionTaskController.java`
- 执行引擎：`bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/engine/IngestionEngine.java`
- 节点配置：`bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/domain/pipeline/NodeConfig.java`
- 节点实现：`bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/node/`

## 快速定位路径

1. 先通过 `GET /api/ragent/ingestion/tasks/{id}` 查看任务整体状态、`errorMessage` 和摘要日志。
2. 再通过 `GET /api/ragent/ingestion/tasks/{id}/nodes` 查看每个节点的状态、耗时、错误和输出快照。
3. 如果任务还没有生成节点日志，优先检查 Pipeline 定义是否能通过 `IngestionEngine` 的结构校验。
4. 如果某个节点日志为 `failed`，从该节点的 `nodeType` 找到对应 `IngestionNode` 实现，继续检查 settings、上游输出和外部依赖。

> 本地默认服务端口为 `9090`，接口前缀为 `/api/ragent`。

## Pipeline 结构校验问题

### 1. 流水线存在环

典型报错：

```text
流水线存在环: <nodeId>
执行节点数超过上限，可能存在死循环
```

原因通常是某个节点的 `nextNodeId` 指回了上游节点，例如 `fetcher -> parser -> fetcher`。执行引擎会沿 `nextNodeId` 链路检查路径，一旦同一条路径内再次遇到已访问节点，就会拒绝执行。

排查建议：

- 列出当前 Pipeline 的全部节点，确认每个 `nodeId` 只出现在一个节点定义中。
- 从预期起点开始手动沿 `nextNodeId` 画出链路，确保最终有一个节点的 `nextNodeId` 为空。
- 修改 Pipeline 后重新查询 `GET /api/ragent/ingestion/pipelines/{id}`，确认持久化后的节点关系与预期一致。

### 2. 找不到下一个节点

典型报错：

```text
找不到下一个节点: <nextNodeId>，被节点 <nodeId> 引用
```

原因是某个节点配置了 `nextNodeId`，但 Pipeline 的节点列表中不存在对应 `nodeId`。

排查建议：

- 检查 `nextNodeId` 是否拼写错误、大小写不一致或仍引用已删除节点。
- 更新 Pipeline 时注意接口会先删除旧节点再写入新节点，确保请求体包含完整节点列表。
- 如果从前端拖拽编辑得到配置，保存后再回读 Pipeline，确认 UI 中删除的节点没有残留连线。

### 3. 流水线未找到起始节点

典型报错：

```text
流水线未找到起始节点
```

执行引擎把“没有被任何 `nextNodeId` 引用的节点”作为起始节点。如果所有节点都被引用，或者节点列表为空，就无法确定起点。

排查建议：

- 确认 Pipeline 至少包含一个节点。
- 确认存在一个明确起点，常见起点是 `fetcher`，且没有其他节点的 `nextNodeId` 指向它。
- 如果所有节点都互相引用，通常同时存在环路问题，先断开最后一个节点的 `nextNodeId`。

### 4. 未知节点类型

典型报错：

```text
未知节点类型: <nodeType>
未找到节点类型: <nodeType>
```

`IngestionPipelineServiceImpl` 会按 `IngestionNodeType` 规范化节点类型，目前支持：`fetcher`、`parser`、`enhancer`、`chunker`、`enricher`、`indexer`。执行时 `IngestionEngine` 还会按节点类型从 Spring Bean 中查找对应 `IngestionNode`。

排查建议：

- 优先使用枚举中的小写类型值，例如 `chunker`，不要使用前端展示名。
- 新增自定义节点时，同时实现 `IngestionNode#getNodeType()`，并确保该实现被 Spring 扫描注册。
- 若创建 Pipeline 时失败，检查 `IngestionNodeType.fromValue` 是否支持该类型；若执行时失败，检查对应节点 Bean 是否存在。

## 节点执行失败排查

### Fetcher 节点

Fetcher 负责根据 `DocumentSource` 获取原始文档。常见失败原因包括：

- `source.type` 为空或与实际来源不匹配。
- URL、S3/RustFS、飞书等外部来源不可访问。
- 上传文件为空、文件名缺失或 MIME 类型识别异常。

排查时先确认任务创建请求中的 `source`，再查看节点日志中的错误信息。上传文件入口是 `POST /api/ragent/ingestion/tasks/upload`，普通来源入口是 `POST /api/ragent/ingestion/tasks`。

### Parser / Chunker 节点

Parser 把原始文档转为结构化文本，Chunker 把文档切分为文本块。常见失败原因包括：

- 上游 Fetcher 没有产出可解析内容。
- 文件类型不受当前解析器支持。
- 分块参数过小、重叠参数异常，导致切分结果为空或不符合预期。

排查时重点看上游节点 `output` 是否已有原始文本、当前节点 settings 是否与文档类型匹配。

### Enhancer / Enricher 节点

这两个节点会调用模型能力做文档级或分块级增强。常见失败原因包括：

- `BAILIAN_API_KEY`、`SILICONFLOW_API_KEY` 等模型 Key 未配置。
- 模型服务不可用或响应格式不符合预期。
- Prompt 模板变量缺失，导致渲染失败或输出无法解析。

如果只是本地验证入库主链路，可以先在 Pipeline 中跳过增强节点，使用 `fetcher -> parser -> chunker -> indexer` 的最小链路确认基础流程。

### Indexer 节点

Indexer 负责向量化并写入向量存储。常见失败原因包括：

- 上游没有生成 chunks。
- Embedding 模型不可用或 Key 未配置。
- PostgreSQL / Milvus 等向量存储配置不正确。
- `vectorSpaceId` 缺失或与目标知识库不一致。

排查时确认 `application.yaml` 中 `rag.vector.type`、数据库连接、Milvus 地址，以及任务请求中的 `vectorSpaceId`。

## 条件跳过不是失败

如果节点配置了 `condition` 且条件不满足，引擎会记录一条成功的跳过日志，任务节点状态会归一化为 `skipped`。这类日志通常以 `Skipped:` 开头，不代表任务失败。

排查建议：

- 如果节点不该被跳过，检查 `condition` 中读取的上下文字段是否由上游节点正确写入。
- 如果节点可以被跳过，确认后续节点不依赖它的输出；否则后续节点可能因为缺少输入而失败。

## 推荐的最小可用链路

排查复杂 Pipeline 时，先退回最小链路，确认基础能力正常：

```text
fetcher -> parser -> chunker -> indexer
```

确认最小链路可以完成后，再逐步加入 `enhancer`、`enricher`、条件表达式和自定义节点。每加入一个节点就执行一次任务，并通过任务节点日志确认该节点的输入、输出、耗时和错误信息。

## 排查检查清单

- Pipeline 节点列表非空，且只有一个清晰起点。
- 每个 `nextNodeId` 都能在当前节点列表中找到。
- 链路最终会结束，没有任何环路。
- `nodeType` 属于当前支持类型，或对应自定义节点已注册为 Spring Bean。
- 任务请求包含 `pipelineId` 和正确的 `source` / 上传文件。
- 模型 Key、PostgreSQL、Redis、RocketMQ、对象存储、向量库等本地依赖已按 README 和 `application.yaml` 配置。
- 任务失败后先看任务详情，再看节点详情，最后回到对应节点实现和外部依赖日志。
