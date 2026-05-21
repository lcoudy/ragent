# Ragent 每日贡献计划

本文档用于规划一周以上的真实改进任务。每个任务都应尽量保持独立、范围小、可验证，适合每天完成一个并提交到 GitHub。

## 使用方式

1. 每天选择一个未完成任务。
2. 让 Claude Code 按任务说明阅读相关文件、实现改动、运行必要检查。
3. 确认无误后提交一个独立 commit。
4. 完成后在本文档中把任务状态改为 `Done`。

建议提交格式：

```text
docs: clarify local setup dependencies
fix: align ingestion API examples with service port
test: cover ingestion pipeline validation
```

## 任务列表

| Day | 状态 | 类型 | 建议提交信息 | 任务说明 |
|---|---|---|---|---|
| 1 | Done | docs | `docs: clarify local dependency setup` | 梳理 README、CLAUDE.md 和配置文件中的本地依赖说明，补充 PostgreSQL、Redis、RocketMQ、RustFS、AI Key 的启动前检查清单。 |
| 2 | Done | docs | `docs: align ingestion API examples with service port` | 检查文档中的 `/api/ragent` 接口示例端口是否与项目默认端口一致，把不一致的示例统一或注明差异。 |
| 3 | Done | docs | `docs: add backend module reading guide` | 增加一段后端源码阅读路线，说明 `bootstrap`、`framework`、`infra-ai`、`mcp-server` 分别适合从哪些入口开始看。 |
| 4 | Todo | docs | `docs: explain multi-channel retrieval flow` | 在多通道检索文档中补充更清晰的执行时序，说明意图定向检索、全局向量检索、去重和 Rerank 的关系。 |
| 5 | Todo | docs | `docs: add ingestion pipeline troubleshooting notes` | 为文档摄取 Pipeline 补充常见问题排查说明，例如循环依赖、节点不存在、无起始节点、任务失败后的检查路径。 |
| 6 | Todo | test | `test: cover ingestion pipeline validation errors` | 查找入库 Pipeline 校验逻辑，补充循环依赖、缺失节点、无起始节点等边界场景的单元测试。 |
| 7 | Todo | docs | `docs: add frontend startup notes` | 补充前端启动说明，包括 Node/npm 依赖、`VITE_API_BASE_URL`、本地后端地址和常见登录跳转问题。 |
| 8 | Todo | docs | `docs: document model routing failover behavior` | 阅读 `infra-ai` 中模型路由、健康检查、熔断降级相关代码，为文档补充核心流程说明。 |
| 9 | Todo | test | `test: cover retrieval deduplication behavior` | 查找检索结果去重处理器，为重复 chunk、不同通道相同 chunk、空结果等场景补充测试。 |
| 10 | Todo | docs | `docs: add MCP server usage notes` | 补充 MCP server 的启动方式、默认端口、示例工具和主服务如何调用 MCP server 的说明。 |
| 11 | Todo | docs | `docs: add database initialization guide` | 基于 `resources/database/` 下的脚本，整理 PostgreSQL 初始化、升级脚本执行顺序和注意事项。 |
| 12 | Todo | docs | `docs: add RAG trace reading guide` | 阅读 Trace 相关代码和管理后台说明，补充如何理解一次 RAG 请求的链路追踪记录。 |
| 13 | Todo | test | `test: cover search channel enablement rules` | 为检索通道启用条件补充测试，例如意图置信度低时启用全局检索、意图明确时优先定向检索。 |
| 14 | Todo | docs | `docs: add contribution-friendly task list` | 整理适合初学者继续贡献的任务清单，包括文档、测试、小型 bugfix、前端体验优化等方向。 |

## 执行建议

### 文档任务

文档任务优先选择，风险低、容易验证，适合没有完整本地依赖环境时完成。完成后至少检查：

- Markdown 格式是否正常。
- 示例命令是否与项目配置一致。
- 路径、端口、模块名是否准确。

### 测试任务

测试任务含金量更高，但需要先确认本地能运行 Maven 测试。完成后建议运行：

```powershell
./mvnw test
```

如果只改了某个模块，可以优先运行：

```powershell
./mvnw -pl bootstrap test
```

### 每日提交流程

```powershell
git status
git add <changed-files>
git commit -m "docs: clarify local dependency setup"
git push
```

实际提交前应根据当天改动调整 commit message。

## 注意事项

- 不要为了绿格提交空改动。
- 不要一天把所有任务全部提交到 `main`，应保持每天一个独立、有意义的提交。
- 如果某个任务改动较大，可以拆成两个任务，但每个 commit 都应保持独立可读。
- 如果任务执行过程中发现文档与代码不一致，以当前代码和配置文件为准。
