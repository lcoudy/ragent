# 检索排查指南

本文用于排查 Ragent 在知识库问答阶段出现的“无结果、结果不准、Rerank 未生效、向量库配置异常、多通道召回异常”等问题。建议先确认一次请求的 RAG Trace，再按“问题改写 -> 意图识别 -> 通道召回 -> 后置处理 -> 生成上下文”的顺序定位。

## 快速定位顺序

1. 在管理后台或接口日志中找到本次请求的 `traceId`，确认链路是否进入 `query-rewrite`、`intent-resolve`、`multi-channel-retrieval` 和 `retrieval-engine`。
2. 检查改写后的主问题和子问题是否仍保留关键实体、业务条件和知识库相关词。
3. 查看意图识别结果是否命中 KB 意图，以及分数是否达到 `intent-directed.min-intent-score`。
4. 查看启用的检索通道列表，确认 `VectorGlobalSearchChannel` 或 `IntentDirectedSearchChannel` 至少有一个通道被执行。
5. 对比通道原始候选数、去重后候选数和 Rerank 后最终数量，判断问题发生在召回阶段还是后置处理阶段。

## 检索无结果

优先确认是否真的没有候选，而不是候选被后置处理过滤掉。

- 如果所有通道都没有执行：检查 `rag.search.channels.*.enabled`，以及当前意图分数是否满足通道启用条件。
- 如果只执行了意图定向检索：确认意图节点是否绑定了正确的知识库 collection，且意图分数高于 `intent-directed.min-intent-score`。
- 如果全局向量检索未启用：检查最高意图分数是否低于 `vector-global.confidence-threshold`，或单意图分数是否低于 `single-intent-supplement-threshold`。
- 如果通道执行但候选为空：确认知识库文档已完成分块和索引，`t_knowledge_chunk` 与向量表或 Milvus collection 中存在对应数据。
- 如果候选存在但最终为空：检查 `DeduplicationPostProcessor`、Rerank topK 和后续上下文截断逻辑。

## 结果不准

结果不准通常来自查询文本、知识库范围或排序策略不匹配。

- 查询改写丢失实体：查看 `RewriteResult.rewrittenQuestion()` 和 `subQuestions()`，必要时补充术语映射或关闭过度改写。
- 意图识别偏到错误知识库：检查意图树节点描述是否过短、重复或与其他节点语义重叠。
- 全局召回噪声过多：调低 `vector-global.top-k-multiplier`，或提高全局通道启用阈值，减少低置信度兜底候选。
- 定向召回覆盖不足：调高 `intent-directed.top-k-multiplier`，或补充知识库分块的标题、章节和业务标签。
- 去重后保留了较弱版本：检查重复 chunk 的 `id`、文本内容和通道分数，确认同一内容是否被不同切分策略重复索引。

## Rerank 未生效

Rerank 只会处理已经召回并去重后的候选，不负责扩大召回范围。

- 检查 `rag.search.post-processors.rerank.enabled` 是否为 `true`。
- 确认 Rerank 模型候选已配置，并且 API Key、baseUrl、modelName 正确。
- 查看模型路由日志，确认 Rerank 候选没有处于熔断或不可用状态。
- 对比去重后候选数和最终 topK：候选数少于等于 topK 时，Rerank 的排序变化可能不明显。
- 如果 Rerank 调用失败但检索仍返回结果，说明后置处理器走了降级路径，应继续排查 `infra-ai` 的模型路由和供应商异常。

## 向量库配置错误

当前 `rag.vector.type` 支持 `pg` 和 `milvus`。排查时先确认实际启用的 Bean 和数据位置一致。

- 使用 PostgreSQL：确认已安装 pgvector，`schema_pg.sql` 已执行，向量字段维度与 embedding 模型输出一致。
- 使用 Milvus：确认 `rag.vector.type=milvus`，Milvus 地址可访问，collection 已创建且字段定义与写入逻辑一致。
- 切换向量库后无结果：确认新向量库已经重新索引，不能只切配置而复用旧库数据。
- embedding 模型变更后召回变差：不同模型的向量空间不兼容，应重新生成文档 embedding。
- 本地环境连不上向量库：优先检查 `application.yaml`、Docker Compose 端口映射和服务启动顺序。

## 多通道召回异常

多通道检索允许单个通道失败并继续处理其他通道，因此要分别看每个通道的状态。

- 某个通道异常但整体有结果：这是预期降级行为，继续查看异常通道的日志和 `SearchChannelResult` 元数据。
- 意图定向和全局通道结果重复：确认去重处理器是否启用，重复 chunk 是否有稳定 `id`。
- 只想看某个通道效果：临时关闭其他通道，或提高其他通道的启用阈值，再对比候选质量。
- 并行通道耗时过高：查看每个通道耗时，优先优化 collection 数量、topK multiplier 和向量库索引。

## 常用检查点

- 配置文件：`bootstrap/src/main/resources/application.yaml` 和检索相关 profile。
- 检索引擎：`MultiChannelRetrievalEngine`。
- 通道实现：`VectorGlobalSearchChannel`、`IntentDirectedSearchChannel`。
- 后置处理器：`DeduplicationPostProcessor`、`RerankPostProcessor`。
- 模型路由：`ModelRoutingExecutor`、`ModelHealthStore`。
- Trace 查询：`RagTraceController` 与管理后台 Trace 页面。
