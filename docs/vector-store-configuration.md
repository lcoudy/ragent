# 向量库配置指南

Ragent 当前支持 PostgreSQL pgvector 和 Milvus 两类向量库。默认配置使用 PostgreSQL，适合本地开发和中小规模验证；Milvus 适合独立向量服务和更明确的向量检索运维场景。

## 配置入口

主配置文件：

```text
bootstrap/src/main/resources/application.yaml
```

关键配置：

```yaml
milvus:
  uri: http://localhost:19530

rag:
  vector:
    type: pg  # 可选 pg / milvus

  default:
    collection-name: rag_default_store
    dimension: 1536
```

`rag.vector.type` 决定启用哪一组向量存储和检索 Bean。切换类型后，需要确认目标向量库中已有对应 collection 和文档向量数据。

## PostgreSQL pgvector

使用 pgvector 时，向量数据和业务数据都在 PostgreSQL 中，默认本地配置为：

- 地址：`127.0.0.1:5432`
- 数据库：`ragent`
- 用户名/密码：`postgres` / `postgres`
- 初始化脚本：`resources/database/schema_pg.sql`

检查点：

1. PostgreSQL 已安装 pgvector 扩展。
2. 已执行 `schema_pg.sql` 和 `init_data_pg.sql`。
3. 向量表字段维度与 embedding 模型输出一致。
4. 文档入库完成后，chunk 表和向量表都有对应数据。
5. 检索服务实际启用的是 pg 相关实现。

pgvector 的优势是本地开发简单、事务和业务表靠近；代价是高并发和大规模向量检索需要额外关注索引、连接池和数据库负载。

## Milvus

使用 Milvus 时，业务数据仍在 PostgreSQL，向量数据写入 Milvus。

配置示例：

```yaml
milvus:
  uri: http://localhost:19530

rag:
  vector:
    type: milvus
```

Docker Compose 文件位于：

```text
resources/docker/
```

检查点：

1. Milvus 服务可访问，端口映射正确。
2. collection 名称与知识库或默认配置一致。
3. collection schema 的向量维度与 embedding 模型输出一致。
4. 切换到 Milvus 后重新执行文档入库或重建索引。
5. 检索异常时同时查看主服务日志和 Milvus 服务日志。

## 切换向量库

切换 `rag.vector.type` 不是只改配置，还要处理已有数据：

1. 停止主服务。
2. 修改 `rag.vector.type`。
3. 确认目标向量库依赖已启动。
4. 初始化目标库 schema 或 collection。
5. 重新入库或触发知识库刷新，让 chunk embedding 写入目标向量库。
6. 使用固定问题验证检索结果和 RAG Trace。

如果只切配置但没有重新写入向量，检索通常会表现为无结果。

## Embedding 维度

向量维度由 embedding 模型决定。常见错误是文档向量使用旧模型生成，运行时查询向量切到新模型。

排查方式：

- 查看 `ai.embedding` 候选配置中的 `dimension`。
- 查看向量库 collection 或表字段维度。
- 确认文档入库时和查询时使用同一 embedding 模型。
- 更换 embedding 模型后，重新生成所有文档向量。

## 常见问题

### 检索无结果

先确认文档是否完成入库，再确认目标向量库中是否有数据。RAG Trace 中如果通道执行但候选为空，通常是 collection 为空、collection 名称错误或向量维度不匹配。

### 启动时找不到向量存储 Bean

检查 `rag.vector.type` 是否只写 `pg` 或 `milvus`。如果写错值，条件装配不会命中。

### pgvector 查询慢

检查向量索引、topK、过滤条件和数据库连接池。文档量增长后，应关注 PostgreSQL 的执行计划和向量索引配置。

### Milvus 写入成功但查询失败

确认查询时使用的 collection、分区、字段名和向量维度与写入时一致。还要确认 Milvus 服务版本和 Java client 兼容。

### 结果质量突然变差

优先确认 embedding 模型是否发生变化，以及是否只重建了部分文档向量。不同 embedding 模型的向量空间通常不能混用。
