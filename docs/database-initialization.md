# 数据库初始化指南

本文说明 Ragent 本地 PostgreSQL 数据库的初始化步骤、脚本执行顺序、默认连接配置和常见问题。

## 1. 默认数据库配置

主服务默认数据源配置位于：

```text
bootstrap/src/main/resources/application.yaml
```

默认连接信息：

```yaml
spring:
  datasource:
    username: postgres
    password: postgres
    url: jdbc:postgresql://127.0.0.1:5432/ragent?client_encoding=UTF8
```

本地初始化时建议保持：

| 项 | 默认值 |
|---|---|
| 数据库类型 | PostgreSQL |
| Host | `127.0.0.1` |
| Port | `5432` |
| Database | `ragent` |
| Username | `postgres` |
| Password | `postgres` |

## 2. 脚本位置

PostgreSQL 初始化脚本位于：

```text
resources/database/
```

主要文件：

| 文件 | 说明 |
|---|---|
| `schema_pg.sql` | 创建 PostgreSQL 表结构、索引、注释和 pgvector 扩展。 |
| `init_data_pg.sql` | 初始化基础数据，目前包含默认管理员用户。 |
| `upgrade_v1.0_to_v1.1.sql` | 从 v1.0 升级到 v1.1 的结构变更。 |
| `upgrade_v1.1_to_v1.2.sql` | 从 v1.1 升级到 v1.2 的结构变更。 |
| `backups/` | 历史备份脚本，通常不作为新环境初始化入口。 |

## 3. 新环境初始化顺序

如果是全新的本地环境，推荐顺序如下：

```text
1. 创建 ragent 数据库
2. 执行 schema_pg.sql
3. 执行 init_data_pg.sql
4. 启动后端服务
```

示例命令：

```powershell
psql -U postgres -h 127.0.0.1 -p 5432 -c "CREATE DATABASE ragent;"
psql -U postgres -h 127.0.0.1 -p 5432 -d ragent -f resources/database/schema_pg.sql
psql -U postgres -h 127.0.0.1 -p 5432 -d ragent -f resources/database/init_data_pg.sql
```

如果数据库已经存在，创建数据库命令会失败。可以先确认数据库是否存在，或直接跳过创建数据库步骤。

## 4. pgvector 扩展

`schema_pg.sql` 开头会执行：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

这意味着 PostgreSQL 实例需要提前安装 pgvector 扩展。如果执行脚本时报 `extension "vector" is not available`，说明当前 PostgreSQL 没有安装 pgvector。

排查方向：

- 确认使用的 PostgreSQL 镜像或本地安装包是否包含 pgvector。
- 如果使用 Docker，优先选择带 pgvector 的 PostgreSQL 镜像。
- 如果使用本机 PostgreSQL，需要按系统环境安装 pgvector 后再执行 schema。

## 5. 初始化数据

`init_data_pg.sql` 当前会插入默认管理员用户：

```text
username: admin
password: admin
role: admin
```

该账号适合本地开发和首次登录验证。生产环境或公开演示环境应修改默认密码。

## 6. 表结构范围

`schema_pg.sql` 覆盖的主要业务表包括：

| 领域 | 表 |
|---|---|
| 用户与会话 | `t_user`、`t_conversation`、`t_conversation_summary`、`t_message`、`t_message_feedback` |
| 示例问题 | `t_sample_question` |
| 知识库 | `t_knowledge_base`、`t_knowledge_document`、`t_knowledge_chunk`、`t_knowledge_document_chunk_log` |
| 定时刷新 | `t_knowledge_document_schedule`、`t_knowledge_document_schedule_exec` |
| 意图与术语 | `t_intent_node`、`t_query_term_mapping` |
| RAG Trace | `t_rag_trace_run`、`t_rag_trace_node` |
| 文档摄取 Pipeline | `t_ingestion_pipeline`、`t_ingestion_pipeline_node`、`t_ingestion_task`、`t_ingestion_task_node` |
| 向量存储 | `t_knowledge_vector` |

## 7. 升级脚本顺序

如果不是新库，而是从旧版本升级，按版本顺序执行：

```text
upgrade_v1.0_to_v1.1.sql
upgrade_v1.1_to_v1.2.sql
```

不要跳过中间版本脚本。升级脚本一般只包含增量变更，依赖旧版本已经存在的表和字段。

当前升级内容：

| 脚本 | 变更 |
|---|---|
| `upgrade_v1.0_to_v1.1.sql` | 调整分块日志耗时字段，将 `embedding_duration` 改为 `embed_duration`，新增 `persist_duration`。 |
| `upgrade_v1.1_to_v1.2.sql` | 为 `t_message` 新增 `thinking_content` 和 `thinking_duration`。 |

## 8. 启动前检查

启动主服务前建议确认：

1. PostgreSQL 正在监听 `127.0.0.1:5432`。
2. 已创建 `ragent` 数据库。
3. `schema_pg.sql` 执行完成且没有错误。
4. `init_data_pg.sql` 已插入默认用户。
5. `application.yaml` 中数据库用户名、密码和端口与本地环境一致。
6. 如果使用 `rag.vector.type: pg`，确认 pgvector 扩展可用。

## 9. 常见问题

### FATAL: database "ragent" does not exist

说明数据库还没创建。先执行：

```powershell
psql -U postgres -h 127.0.0.1 -p 5432 -c "CREATE DATABASE ragent;"
```

### password authentication failed

说明 `application.yaml` 中密码和本地 PostgreSQL 密码不一致。修改配置或调整本地数据库用户密码。

### extension "vector" is not available

说明 PostgreSQL 没有安装 pgvector。需要换用支持 pgvector 的镜像或安装扩展。

### relation already exists

说明重复执行了建表脚本。新环境初始化只需要执行一次 `schema_pg.sql`。如果需要重建库，建议先备份数据，再删除并重建数据库。

### 默认 admin 用户无法登录

检查：

- `init_data_pg.sql` 是否执行。
- `t_user` 表中是否存在 `username = 'admin'` 的记录。
- 后端是否连接到了同一个 `ragent` 数据库。
