# 前端 API Service 分层指南

本文说明前端 `src/services` 的使用约定，适合新增接口、排查认证失效、理解后端 `Result` 解包时参考。

## 分层职责

| 文件 | 职责 |
|---|---|
| `src/services/api.ts` | 创建共享 Axios 实例，统一 baseURL、token 注入、响应解包和认证失效处理。 |
| `src/services/*Service.ts` | 按业务域封装 API 方法，例如知识库、会话、Trace、设置和用户。 |
| `src/types/index.ts` | 维护前端共享类型，避免页面组件重复声明接口结构。 |
| `src/stores/*Store.ts` | 维护跨页面状态，service 不直接持有 UI 状态。 |

页面组件应调用业务 service，而不是直接调用 Axios。这样可以把后端路径、请求参数和响应结构集中维护。

## 响应解包

后端统一返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {}
}
```

`api.ts` 的响应拦截器会解包 `data`，所以业务 service 通常直接得到业务对象，而不是完整 `Result`。新增 service 时不要再手动读取 `response.data.data`，否则会多解一层。

## 认证处理

请求拦截器会从本地存储读取 token 并写入请求头。认证失效时响应拦截器负责：

- 清理本地认证信息。
- 跳转到 `/login`。
- 把错误交给调用方或全局提示处理。

页面层只需要关心“当前调用失败后如何反馈用户”，不要在每个页面重复写 token 注入逻辑。

## 新增 Service 约定

新增业务域接口时建议：

1. 在 `src/types/index.ts` 补充请求和响应类型。
2. 在 `src/services` 新增或扩展对应 `*Service.ts`。
3. 方法名使用业务动作，例如 `listKnowledgeBases`、`createConversation`。
4. 请求路径只在 service 中出现一次，页面组件不拼 URL。
5. 分页接口统一返回后端分页结构，组件再转换为表格状态。
6. 文件上传接口使用 `FormData`，不要手动设置 multipart boundary。

## 错误处理边界

推荐边界如下：

- `api.ts`：处理认证失效、统一错误对象和网络错误基础归一化。
- service：不吞异常，只做请求参数和响应类型约束。
- store：处理状态更新、加载标记和可恢复业务错误。
- page/component：处理提示文案、空态和局部重试入口。

这样可以避免同一个错误既弹 toast 又被页面二次展示。

## 本地联调检查

如果前端接口请求异常，按顺序检查：

1. `VITE_API_BASE_URL` 是否指向后端公开地址。
2. 浏览器 Network 里的请求路径是否包含 `/api/ragent`。
3. 请求头是否带有 token。
4. 后端返回是否仍是 `{ code, message, data }` 结构。
5. service 是否错误地二次解包。
6. 登录失效后是否被拦截器重定向。

## 常见反模式

- 页面组件里直接写 `axios.get(...)`。
- service 返回完整 Axios response，导致调用方依赖传输层细节。
- 同一个接口在多个 service 中重复定义。
- 在组件里拼接后端上下文路径。
- 每个页面单独处理 401 跳转。
