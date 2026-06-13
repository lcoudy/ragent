# 前端管理后台操作指南

本文说明 Ragent 前端管理后台的入口、页面职责、API 调用约定和本地联调方式。前端位于 `frontend/`，技术栈为 Vite、React 18、TypeScript、Zustand 和 Axios。

## 启动与访问

```powershell
cd frontend
npm install
npm run dev
```

默认情况下，前端请求使用当前页面同源地址。如果后端在本地 `9090` 端口启动，建议配置：

```powershell
$env:VITE_API_BASE_URL="http://localhost:9090/api/ragent"
npm run dev
```

登录成功后访问 `/admin`，路由会自动跳转到 `/admin/dashboard`。非登录用户会跳转 `/login`，非管理员用户会跳转 `/chat`。

## 路由与权限

管理后台路由集中在 `frontend/src/router.tsx`：

- `RequireAuth`：保护普通聊天页。
- `RequireAdmin`：保护 `/admin` 下所有管理页面。
- `RedirectIfAuth`：已登录用户访问 `/login` 时跳转 `/chat`。
- `/admin`：使用 `AdminLayout` 作为后台布局，子路由承载各业务页面。

当前后台页面包括：

- `/admin/dashboard`：统计看板。
- `/admin/knowledge`：知识库列表。
- `/admin/knowledge/:kbId`：知识库文档列表。
- `/admin/knowledge/:kbId/docs/:docId`：文档分块列表。
- `/admin/intent-tree`、`/admin/intent-list`：意图树和意图节点维护。
- `/admin/ingestion`：入库 Pipeline 管理。
- `/admin/traces`、`/admin/traces/:traceId`：RAG Trace 列表和详情。
- `/admin/settings`：系统设置。
- `/admin/sample-questions`：示例问题维护。
- `/admin/mappings`：查询术语映射维护。
- `/admin/users`：用户管理。

## API 调用约定

共享 Axios 实例位于 `frontend/src/services/api.ts`。

- `VITE_API_BASE_URL` 存在时作为 `baseURL`。
- 请求拦截器会从本地存储读取 token，并写入 `Authorization` header。
- 后端返回 `{ code, message, data }` 时，响应拦截器会自动解包 `data`。
- 当 `code !== "0"` 时，service 会收到 rejected error。
- 错误信息包含“未登录”或 HTTP 401 时，会清理本地认证信息并跳转 `/login`。

因此业务 service 通常只返回后端 `data`，页面组件不需要重复处理统一响应结构。

## Service 分层

后台相关 API 按业务域拆分在 `frontend/src/services/`：

- `dashboardService.ts`：看板统计。
- `knowledgeService.ts`：知识库、文档、分块。
- `ingestionService.ts`：入库 Pipeline 和任务。
- `intentTreeService.ts`：意图树和意图节点。
- `ragTraceService.ts`：Trace run 和节点详情。
- `settingsService.ts`：系统设置。
- `queryTermMappingService.ts`：术语映射。
- `sampleQuestionService.ts`：示例问题。
- `userService.ts`：用户管理。

新增后台页面时，优先沿用“页面组件 -> domain service -> api 实例”的调用方式，避免在组件里直接拼 Axios 细节。

## 本地联调检查

1. 后端主服务是否启动在 `http://localhost:9090/api/ragent`。
2. `VITE_API_BASE_URL` 是否指向后端上下文路径，而不是裸端口。
3. 浏览器 Network 面板里接口路径是否带 `/api/ragent`。
4. 登录接口是否成功写入 token，本地存储里是否存在认证信息。
5. 当前用户 `role` 是否为 `admin`，否则会被 `/admin` 守卫跳回 `/chat`。
6. 后端统一响应 `code` 是否为字符串 `"0"`；非零 code 会走 error 分支。

## 常见问题

### 访问 `/admin` 后回到 `/chat`

说明已登录但不是管理员。检查登录用户的 `role` 字段和后端用户初始化数据。

### 接口 404

优先检查 `VITE_API_BASE_URL`。如果配置成 `http://localhost:9090`，请求会缺少 `/api/ragent` 上下文路径。

### 登录后仍提示未登录

检查响应 token 是否写入本地存储，以及请求拦截器是否带上 `Authorization`。如果后端返回 message 包含“未登录”，前端会主动清理认证并跳转登录页。

### 页面拿到的数据结构不对

确认 service 是否已经经过 Axios 拦截器解包。页面组件通常拿到的是 `data` 本身，不是 `{ code, message, data }`。

### 本地跨域失败

推荐配置 `VITE_API_BASE_URL` 直连后端；如果使用同源代理，需要确认 Vite proxy 和后端上下文路径一致。
