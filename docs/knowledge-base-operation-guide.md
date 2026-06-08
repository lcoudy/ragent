# 知识库操作指南

本文按管理后台入口、后端接口和后台处理链路梳理知识库、文档、Chunk 与定时刷新相关功能，方便本地调试和二次开发时快速定位。

## 功能入口

管理后台的知识库功能分三层页面：

| 页面 | 前端路由 | 主要文件 | 用途 |
|---|---|---|---|
| 知识库列表 | `/admin/knowledge` | `frontend/src/pages/admin/knowledge/KnowledgeListPage.tsx` | 创建、搜索、重命名、删除知识库，查看知识库统计信息。 |
| 文档列表 | `/admin/knowledge/:kbId` | `frontend/src/pages/admin/knowledge/KnowledgeDocumentsPage.tsx` | 上传文档、筛选文档状态、启动分块、查看分块日志、编辑文档处理配置。 |
| Chunk 列表 | `/admin/knowledge/:kbId/docs/:docId` | `frontend/src/pages/admin/knowledge/KnowledgeChunksPage.tsx` | 查看、创建、编辑、删除、启用或禁用文档 Chunk。 |

前端接口统一封装在 `frontend/src/services/knowledgeService.ts`。如果页面行为和后端接口不一致，优先从这个文件核对请求方法、路径和参数名。

## 知识库管理

后端入口：`bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/controller/KnowledgeBaseController.java`

| 操作 | 方法与路径 | 说明 |
|---|---|---|
| 创建知识库 | `POST /knowledge-base` | 创建知识库并返回知识库 ID。前端创建弹窗会提交名称、Embedding 模型等字段。 |
| 重命名知识库 | `PUT /knowledge-base/{kb-id}` | 更新知识库名称。当前前端主要用于重命名。 |
| 删除知识库 | `DELETE /knowledge-base/{kb-id}` | 删除知识库。调试时要同时关注关联文档、Chunk 和向量数据清理逻辑。 |
| 查询详情 | `GET /knowledge-base/{kb-id}` | 文档列表页加载标题和 Collection 信息时使用。 |
| 分页查询 | `GET /knowledge-base` | 支持 `current`、`size`、`name` 等分页和名称过滤参数。 |
| 分块策略 | `GET /knowledge-base/chunk-strategies` | 返回可见的 `ChunkingMode` 及默认配置，供上传和编辑文档时选择。 |

知识库的核心字段包括 `name`、`embeddingModel`、`collectionName` 和 `documentCount`。其中 `collectionName` 对应向量库逻辑空间，文档分块写入向量库时会通过知识库 ID 反查这个字段。

## 文档管理

后端入口：`bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/controller/KnowledgeDocumentController.java`

| 操作 | 方法与路径 | 说明 |
|---|---|---|
| 上传文档 | `POST /knowledge-base/{kb-id}/docs/upload` | `multipart/form-data` 请求。支持本地文件和远程 URL 两种来源。 |
| 启动分块 | `POST /knowledge-base/docs/{doc-id}/chunk` | 将文档状态置为 `running`，通过事务消息提交异步分块任务。 |
| 删除文档 | `DELETE /knowledge-base/docs/{doc-id}` | 删除文档、Chunk、分块日志、调度记录，并清理向量和存储文件。 |
| 查询详情 | `GET /knowledge-base/docs/{docId}` | Chunk 页面和文档编辑弹窗会读取文档详情。 |
| 更新文档 | `PUT /knowledge-base/docs/{docId}` | 更新名称、处理模式、分块参数、Pipeline、远程 URL 和定时刷新配置。 |
| 分页查询 | `GET /knowledge-base/{kb-id}/docs` | 支持 `current`、`size`、`status`、`keyword`。 |
| 全局搜索 | `GET /knowledge-base/docs/search` | 用于按文档名搜索，最多返回 20 条。 |
| 启用/禁用 | `PATCH /knowledge-base/docs/{docId}/enable?value=true|false` | 禁用时删除文档向量；启用时重新 Embedding 并写回向量库。 |
| 分块日志 | `GET /knowledge-base/docs/{docId}/chunk-logs` | 查看最近分块任务的阶段耗时、状态和错误信息。 |

文档有两个关键维度：

| 维度 | 可选值 | 影响 |
|---|---|---|
| 来源类型 `sourceType` | `file`、`url` | 本地文件直接上传；远程 URL 会先拉取并存储，且只有 URL 文档支持定时刷新。 |
| 处理模式 `processMode` | `chunk`、`pipeline` | `chunk` 走内置解析和分块策略；`pipeline` 走摄取 Pipeline 编排。 |

文档状态常见值：

| 状态 | 含义 |
|---|---|
| `pending` | 已上传或已创建记录，但尚未完成分块。 |
| `running` | 正在分块或通过 Pipeline 处理。运行中禁止删除、编辑和重复启动分块。 |
| `success` | 分块、Embedding、Chunk 持久化和向量写入成功。 |
| `failed` | 分块链路失败，可通过分块日志查看错误信息。 |

## 分块与向量写入

直接分块模式由 `KnowledgeDocumentServiceImpl` 串起：

1. 读取文档存储文件。
2. 通过 `DocumentParserSelector` 选择解析器抽取文本。
3. 根据 `chunkStrategy` 和 `chunkConfig` 构造 `ChunkingOptions`。
4. 使用 `ChunkingStrategyFactory` 执行分块。
5. 调用 `ChunkEmbeddingService` 生成向量。
6. 在事务内删除旧 Chunk、写入新 Chunk、清理旧向量、写入新向量，并更新文档状态和分块数。

Pipeline 模式会读取文件字节，构造 `IngestionContext`，交给 `IngestionEngine` 执行指定 Pipeline。这里会设置 `skipIndexerWrite=true`，最终仍由文档服务统一持久化 Chunk 和向量，避免 Pipeline 和知识库文档链路重复写索引。

分块日志保存在 `knowledge_document_chunk_log` 相关实体中，字段会记录解析、分块、Embedding、持久化和总耗时。排查分块慢或失败时，优先打开文档列表里的日志入口，再对照 `KnowledgeDocumentServiceImpl#runChunkTask`。

## Chunk 管理

后端入口：`bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/controller/KnowledgeChunkController.java`

| 操作 | 方法与路径 | 说明 |
|---|---|---|
| 分页查询 | `GET /knowledge-base/docs/{doc-id}/chunks` | 支持 `current`、`size`、`enabled` 过滤。 |
| 新增 Chunk | `POST /knowledge-base/docs/{doc-id}/chunks` | 手动新增 Chunk，可指定序号。 |
| 更新 Chunk | `PUT /knowledge-base/docs/{doc-id}/chunks/{chunk-id}` | 修改 Chunk 内容。 |
| 删除 Chunk | `DELETE /knowledge-base/docs/{doc-id}/chunks/{chunk-id}` | 删除单条 Chunk，并清理对应向量。 |
| 单条启停 | `PATCH /knowledge-base/docs/{doc-id}/chunks/{chunk-id}/enable?value=true|false` | 控制单条 Chunk 是否参与检索。 |
| 批量启停 | `PATCH /knowledge-base/docs/{doc-id}/chunks/batch-enable?value=true|false` | 请求体传 `chunkIds`，用于列表批量操作。 |

手动维护 Chunk 时要注意两点：

1. 禁用 Chunk 通常用于临时排除低质量内容，不等于删除原始文档。
2. 修改 Chunk 内容后，需要确认服务层是否同步更新向量；如果检索结果仍是旧内容，应从 Chunk 服务和向量库写入路径继续排查。

## 定时刷新

定时刷新只面向远程 URL 文档。上传或编辑 URL 文档时，如果开启 `scheduleEnabled` 并填写 `scheduleCron`，文档服务会调用 `KnowledgeDocumentScheduleService#upsertSchedule` 创建或更新调度记录。

刷新执行入口在 `ScheduleRefreshProcessor`，核心流程是：

1. 获取调度锁并启动心跳，避免多实例重复执行同一刷新任务。
2. 检查文档是否存在、是否删除、是否启用、是否仍是 URL 来源。
3. 校验 cron 并计算下一次执行时间。
4. 使用 `RemoteFileFetcher` 根据 ETag、Last-Modified 或内容哈希判断远程文件是否变化。
5. 文件未变化时记录 skipped；文件变化时先占用文档运行权。
6. 上传新文件，调用文档分块链路重新生成 Chunk 和向量。
7. 分块成功后切换文档文件元数据，并写回调度执行状态。

定时刷新失败时先看调度执行记录，再看文档分块日志。如果文档处于 `running`，刷新会跳过或等待下一次调度，避免和人工分块互相覆盖。

## 常见排查

| 现象 | 优先检查 |
|---|---|
| 知识库列表为空 | 确认登录用户是管理员；检查 `GET /knowledge-base` 是否返回分页数据。 |
| 上传 URL 文档失败 | 检查 `sourceLocation` 是否为空、远程地址是否可访问、文件存储配置是否可用。 |
| 开启定时刷新失败 | 检查 cron 表达式是否合法，以及是否低于最小调度间隔。 |
| 分块一直 running | 查看事务消息消费、`KnowledgeDocumentChunkConsumer`、分块日志和文档状态更新。 |
| 分块 failed | 打开分块日志，按解析、分块、Embedding、持久化阶段定位异常。 |
| 检索不到新文档 | 确认文档状态为 `success`、文档和 Chunk 均启用、向量库 collection 与知识库 `collectionName` 一致。 |
| 禁用后仍命中 | 检查文档或 Chunk 禁用时是否成功清理向量，以及检索端是否过滤 `enabled` 状态。 |

