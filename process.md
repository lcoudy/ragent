# Ragent 贡献进度记录

本文档用于记录当前仓库的贡献进度、已完成任务、最近提交和下一步计划。后续继续执行贡献计划时，先阅读本文档和 `docs/weekly-contribution-plan.md`，再选择下一个 `Todo` 任务。

## 当前状态

- 当前分支：`main`
- 远程仓库：`https://github.com/lcoudy/ragent.git`
- GitHub 用户名：`lcoudy`
- Git 提交邮箱：`1020246530@qq.com`
- 当前贡献计划进度：Day 1 - Day 8 已完成，下一项为 Day 9。
- 当前未提交改动：无，Day 8 文档改动已按默认流程提交。

## 已完成任务

| Day | 状态 | 提交 | 说明 |
|---|---|---|---|
| 1 | Done | `33ab64e` | 梳理本地依赖、快速启动和文档摄取示例相关说明。 |
| 2 | Done | `33ab64e` | 对齐 `/api/ragent` 接口示例端口和本地配置说明。 |
| 3 | Done | `71ebf81` | 新增 `docs/backend-module-reading-guide.md`，说明 `bootstrap`、`framework`、`infra-ai`、`mcp-server` 的后端源码阅读路线，并在 README 快速导航中增加入口。 |
| 4 | Done | `docs: explain multi-channel retrieval flow` | 在 `docs/multi-channel-retrieval.md` 补充多通道检索执行时序，说明意图定向检索、全局向量检索、去重和 Rerank 的关系。 |
| 5 | Done | `docs: add ingestion pipeline troubleshooting notes` | 新增文档摄取 Pipeline 排查指南，覆盖环路、节点缺失、起点缺失、节点类型和任务节点日志检查路径，并在 README 快速导航中增加入口。 |
| 6 | Done | `test: cover ingestion pipeline validation errors` | 新增 IngestionEngine Pipeline 校验单元测试，覆盖循环依赖、缺失节点、无起始节点和校验失败日志初始化行为。 |
| 7 | Done | `docs: add frontend startup notes` | 新增前端启动与接口地址配置说明，覆盖 Node/npm、`VITE_API_BASE_URL`、本地后端地址、Vite 代理和登录跳转排查。 |
| 8 | Done | `docs: document model routing failover behavior` | 新增模型路由与故障切换说明，覆盖候选选择、同步降级、三态熔断、流式首包探测和常见排查路径。 |

## 最近提交

```text
docs: document model routing failover behavior
docs: add frontend startup notes
test: cover ingestion pipeline validation errors
docs: add ingestion pipeline troubleshooting notes
docs: explain multi-channel retrieval flow
71ebf81 docs: add backend module reading guide
33ab64e docs: align local setup and ingestion examples
4d325d7 Initial commit
```

## 接下来需要完成的任务

优先从 `docs/weekly-contribution-plan.md` 中选择第一个 `Todo` 任务：

- 优先继续 Day 9 测试任务，补充检索结果去重行为测试。

| Day | 类型 | 建议提交信息 | 任务说明 |
|---|---|---|---|
| 9 | test | `test: cover retrieval deduplication behavior` | 查找检索结果去重处理器，为重复 chunk、不同通道相同 chunk、空结果等场景补充测试。 |
| 10 | docs | `docs: add MCP server usage notes` | 补充 MCP server 的启动方式、默认端口、示例工具和主服务如何调用 MCP server 的说明。 |

## 下一次执行建议

1. 先运行 `git status -sb`，确认工作区是否干净。
2. 阅读 `docs/weekly-contribution-plan.md` 和本文件，确认下一个任务。
3. 如果继续 Day 9，重点查看：
   - `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/postprocessor/`
   - `DeduplicationPostProcessor.java`
   - 现有测试目录中与检索或后置处理相关的测试写法
4. 完成后更新：
   - 对应文档内容
   - `docs/weekly-contribution-plan.md` 中的任务状态
   - 本文件的进度、最近提交和下一步任务
5. 完成并检查每日任务后，默认直接创建聚焦 commit 并 push 到远程仓库；除非用户明确要求不要提交或不要推送。

## 注意事项

- 不创建空提交或仅更新时间戳的提交。
- 每次提交应对应一个真实、可读、范围明确的改动。
- 提交前确认 Git 身份为 `lcoudy <1020246530@qq.com>`，确保 GitHub 贡献统计可识别。
- 如果 GitHub 主页没有立即出现绿色，先确认提交已 push、邮箱已绑定 GitHub，并等待贡献图刷新。
