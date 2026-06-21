# 发布与贡献流程指南

本文总结 Ragent 的队列式贡献流程、每日发布脚本、检查命令、失败恢复和进度维护规则。适用于“先在本地准备多天真实改进，再每天发布一条 commit”的工作方式。

## 分支模型

- `main`：正式发布分支，每天从队列中发布一条真实改进并推送到 GitHub。
- `contribution-queue`：本地队列分支，每个 commit 对应一个完整任务。
- `Progress.md`：贡献状态的唯一事实来源，记录正式发布、已完成未发布和未完成任务。

队列 commit 要保持小而完整，不要把多天内容混成一个 commit，也不要创建空提交或时间戳提交。

## 准备本地队列

推荐流程：

```powershell
git checkout contribution-queue
git status --short --branch
```

每完成一项任务：

1. 实现真实改动。
2. 运行相关轻量检查。
3. 更新 `Progress.md`。
4. 创建独立 commit。

提交信息尽量沿用任务表里的建议，例如：

```text
docs: add retrieval troubleshooting guide
test: cover session memory boundaries
```

## 每日发布脚本

脚本位于仓库根目录：

```text
publish_queued_commit.ps1
```

预览下一条待发布 commit：

```powershell
./publish_queued_commit.ps1 -DryRun
```

发布下一条队列 commit：

```powershell
./publish_queued_commit.ps1
```

带检查命令发布：

```powershell
./publish_queued_commit.ps1 -CheckCommand "./mvnw.cmd -pl bootstrap -Dtest=ModelHealthStoreTest test"
```

脚本会使用 `git cherry` 跳过已经等价应用到 `main` 的队列 commit，选择最早一条未发布 commit，先应用到 `main` 工作区，可选执行检查，检查通过后提交并 push。

## 检查命令选择

按改动范围选择最小有效检查：

- 文档改动：`git diff --check`。
- 单个后端测试：`./mvnw.cmd -pl bootstrap -Dtest=ClassName test`。
- framework 模块测试：`./mvnw.cmd -pl framework -Dtest=ClassName test`。
- 前端改动：`npm --prefix frontend run build` 或 `npm --prefix frontend run lint`。

如果检查依赖本地中间件，优先选择不需要 PostgreSQL、Redis、RocketMQ 或真实模型 Key 的纯单元测试。

## 失败恢复

发布脚本在检查失败时应恢复到发布前的干净工作区。排查步骤：

1. 查看失败命令输出，确认是代码问题、环境问题还是依赖服务未启动。
2. 保持 `main` 干净，不在发布失败状态下继续叠加新改动。
3. 回到 `contribution-queue` 修复对应队列 commit。
4. 重新运行 `-DryRun` 和带检查的发布命令。

不要用空提交补贡献记录，也不要为了通过发布跳过必要的真实检查。

## Progress.md 维护

每次开始新任务前先校准：

1. 阅读 `Progress.md` 当前状态。
2. 查看 `git log --oneline main..contribution-queue`。
3. 查看 `git cherry -v main contribution-queue`。
4. 已等价进入 `main` 的任务移动到“已完成并已正式提交到 GitHub”。
5. 本地仍为 `+` 的任务保留在“已完成但未正式提交到 GitHub”。
6. 队列为空时，将未发布表改为“暂无”。

`Progress.md` 的状态修正应和真实改动一起提交，或者作为明确的进度维护 commit；不要创建空提交。

## 发布前清单

- 当前分支是 `main`，且工作区干净。
- `contribution-queue` 存在待发布的 `+` commit。
- `publish_queued_commit.ps1 -DryRun` 输出符合预期。
- 已选择与改动范围匹配的检查命令。
- Git 作者身份为 `lcoudy <1020246530@qq.com>`。
- 发布后 `git status --short --branch` 显示 `main...origin/main` 对齐。
