# API 响应契约指南

本文说明 Ragent 后端统一响应格式、错误码约定、全局异常处理，以及前端 Axios 如何解包和处理认证失效。

## 后端统一响应

统一响应对象位于：

```text
framework/src/main/java/com/nageoffer/ai/ragent/framework/convention/Result.java
```

字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | `String` | 状态码，`"0"` 表示成功，其他值表示错误。 |
| `message` | `String` | 响应说明，失败时为错误原因。 |
| `data` | 泛型 | 业务数据，失败时通常为 `null`。 |
| `requestId` | `String` | 请求追踪 ID，便于排查。 |

成功响应由 `Results.success()` 或 `Results.success(data)` 构造。前端判断成功时必须按字符串 `"0"` 判断，不要按数字 `0` 判断。

## 错误码

基础错误码位于：

```text
framework/src/main/java/com/nageoffer/ai/ragent/framework/errorcode/BaseErrorCode.java
```

当前按 A/B/C 分类：

- `A000001`：用户端错误，例如参数错误、未登录、权限不足。
- `B000001`：系统执行错误，默认兜底异常。
- `C000001`：第三方服务错误，例如外部模型或远程服务调用失败。

业务异常继承 `AbstractException`，全局异常处理器会读取异常里的 `errorCode` 和 `errorMessage` 并包装为 `Result`。

## 全局异常处理

全局处理器位于：

```text
framework/src/main/java/com/nageoffer/ai/ragent/framework/web/GlobalExceptionHandler.java
```

主要规则：

- 参数校验失败：返回 `A000001` 和首个字段错误信息。
- `AbstractException`：返回异常携带的错误码和错误信息。
- 未登录：返回 `A000001`，message 为“未登录或登录已过期”。
- 角色不足：返回 `A000001`，message 为“权限不足”。
- 上传大小超限：返回 `A000001` 和文件大小限制说明。
- 未捕获异常：返回 `B000001` 和“系统执行出错”。

Controller 层应尽量返回 `Result<T>`，不要混用其他响应包装结构。

## 前端解包规则

共享 Axios 实例位于：

```text
frontend/src/services/api.ts
```

响应拦截器规则：

1. 如果响应体是对象且包含 `code` 字段，则按后端统一响应处理。
2. `code === "0"` 时返回 `payload.data`。
3. `code !== "0"` 时抛出 `Error(message)`。
4. 如果 message 包含“未登录”，前端会清理认证信息并跳转 `/login`。
5. 如果响应不是统一响应对象，则直接返回原始 payload。

因此 service 函数通常直接拿到业务数据，不需要再访问 `.data.data`。

## 认证失效

前端有两类认证失效处理：

- 后端统一响应：`message` 包含“未登录”。
- HTTP 状态码：`response.status === 401`。

两种情况都会清理本地认证信息，并在当前页面不是 `/login` 时跳转登录页。

## 编写新接口的建议

- 后端成功时统一返回 `Results.success(data)`。
- 业务错误使用明确的 `ClientException`、`ServiceException` 或 `RemoteException`。
- 错误消息面向用户或管理员可读，避免只返回异常类名。
- 前端 service 只描述业务 API，不重复写 token、统一响应解包和跳转逻辑。
- 页面组件只处理业务状态和交互，不直接依赖 `{ code, message, data }`。

## 常见问题

### 前端拿不到 `code`

这是正常现象。统一响应已在 Axios 拦截器中解包，页面和 service 拿到的是 `data`。

### 前端错误提示重复

避免在 service 层捕获后再次 toast。统一网络错误和后端 message 已由 `api.ts` 的响应错误分支处理。

### 后端返回数字 0 导致前端失败

`Result.SUCCESS_CODE` 是字符串 `"0"`。如果某个接口手写响应并返回数字 `0`，前端会判定为失败或行为不一致。

### 认证过期没有跳转登录

确认后端 message 是否包含“未登录”，或 HTTP 状态码是否为 401。当前前端依赖这两个信号清理认证状态。
