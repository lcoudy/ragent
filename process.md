# Ragent 贡献进度记录

本文档用于记录当前仓库的贡献进度、已完成任务、最近提交和下一步计划。后续继续执行贡献计划时，先阅读本文档和 `docs/weekly-contribution-plan.md`，再选择下一个 `Todo` 任务。

## 当前状态

- 当前分支：`main`
- 远程仓库：`https://github.com/lcoudy/ragent.git`
- GitHub 用户名：`lcoudy`
- Git 提交邮箱：`1020246530@qq.com`
- 当前贡献计划进度：Day 1 - Day 5 已完成，下一项为 Day 6。
- 当前未提交改动：无，Day 5 文档改动已按默认流程提交并推送。

## 已完成任务

| Day | 状态 | 提交 | 说明 |
|---|---|---|---|
| 1 | Done | `33ab64e` | 梳理本地依赖、快速启动和文档摄取示例相关说明。 |
| 2 | Done | `33ab64e` | 对齐 `/api/ragent` 接口示例端口和本地配置说明。 |
| 3 | Done | `71ebf81` | 新增 `docs/backend-module-reading-guide.md`，说明 `bootstrap`、`framework`、`infra-ai`、`mcp-server` 的后端源码阅读路线，并在 README 快速导航中增加入口。 |
| 4 | Done | `docs: explain multi-channel retrieval flow` | 在 `docs/multi-channel-retrieval.md` 补充多通道检索执行时序，说明意图定向检索、全局向量检索、去重和 Rerank 的关系。 |
| 5 | Done | `docs: add ingestion pipeline troubleshooting notes` | 新增文档摄取 Pipeline 排查指南，覆盖环路、节点缺失、起点缺失、节点类型和任务节点日志检查路径，并在 README 快速导航中增加入口。 |

## 最近提交

```text
docs: add ingestion pipeline troubleshooting notes
docs: explain multi-channel retrieval flow
71ebf81 docs: add backend module reading guide
33ab64e docs: align local setup and ingestion examples
4d325d7 Initial commit
```

## 接下来需要完成的任务

优先从 `docs/weekly-contribution-plan.md` 中选择第一个 `Todo` 任务：

- 优先继续 Day 6 测试任务，先确认 Maven 测试可运行，再补充 Pipeline 校验错误场景。

| Day | 类型 | 建议提交信息 | 任务说明 |
|---|---|---|---|
| 6 | test | `test: cover ingestion pipeline validation errors` | 查找入库 Pipeline 校验逻辑，补充循环依赖、缺失节点、无起始节点等边界场景的单元测试。 |
| 7 | docs | `docs: add frontend startup notes` | 补充前端启动说明，包括 Node/npm 依赖、`VITE_API_BASE_URL`、本地后端地址和常见登录跳转问题。 |

## 下一次执行建议

1. 先运行 `git status -sb`，确认工作区是否干净。
2. 阅读 `docs/weekly-contribution-plan.md` 和本文件，确认下一个任务。
3. 如果继续 Day 6，重点查看：
   - `bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/engine/IngestionEngine.java`
   - `bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/domain/pipeline/`
   - 现有测试目录和测试依赖，补充 Pipeline 校验错误的单元测试
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
