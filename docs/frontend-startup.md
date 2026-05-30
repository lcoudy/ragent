# 前端启动与接口地址配置

本文档用于本地启动 `frontend/` 前端项目，并排查接口地址、代理和登录跳转相关问题。

## 启动前检查

前端是 Vite + React 18 + TypeScript 项目，目录位于 `frontend/`。建议使用 Node.js 18 及以上版本，优先使用当前 LTS 版本。

```powershell
cd frontend
node -v
npm -v
npm install
```

后端主服务默认监听 `http://localhost:9090`，并使用 `/api/ragent` 作为 context path。启动前端前，先确认主服务已经启动：

```powershell
./mvnw -pl bootstrap spring-boot:run
```

未登录时访问受保护接口返回未登录错误是正常现象，说明请求已经到达后端：

```powershell
curl http://localhost:9090/api/ragent/user/me
```

## 本地开发启动

默认配置已经适合本地前后端联调：

- `frontend/.env` 设置 `VITE_API_BASE_URL=/api/ragent`。
- `frontend/vite.config.ts` 将 `/api` 代理到 `http://localhost:9090`。
- 前端开发服务器默认端口为 `5173`，端口被占用时 Vite 会提示或尝试其他端口。

启动命令：

```powershell
cd frontend
npm run dev
```

浏览器访问：

```text
http://localhost:5173
```

本地开发时，浏览器看到的请求地址类似：

```text
http://localhost:5173/api/ragent/auth/login
```

Vite 会把它代理到：

```text
http://localhost:9090/api/ragent/auth/login
```

## VITE_API_BASE_URL 取值

`frontend/src/services/api.ts` 和 `frontend/src/stores/chatStore.ts` 都会读取 `VITE_API_BASE_URL`。普通接口和流式聊天接口必须使用同一个 API 前缀。

| 场景 | 建议值 | 说明 |
|---|---|---|
| 本地 Vite 开发 | `/api/ragent` | 通过 Vite proxy 转发到 `localhost:9090`，避免浏览器跨域问题。 |
| 前端直连本地后端 | `http://localhost:9090/api/ragent` | 不走 Vite proxy，适合临时验证接口地址。 |
| 生产部署 | 由部署网关决定，例如 `/api/ragent` | 需要确保网关把该路径转发到后端主服务。 |

如果修改了 `.env` 或 `vite.config.ts`，需要重启 `npm run dev`，Vite 才会重新读取配置。

## 登录跳转排查

前端路由会根据登录态自动跳转：

- 未登录访问 `/`、`/chat` 或 `/admin` 会跳到 `/login`。
- 已登录访问 `/login` 会跳到 `/chat`。
- 已登录但角色不是 `admin` 时访问 `/admin` 会回到 `/chat`。

登录接口路径为：

```text
POST /api/ragent/auth/login
```

初始化数据库脚本 `resources/database/init_data_pg.sql` 中包含默认管理员账号：

```text
username: admin
password: admin
role: admin
```

如果登录后又立即跳回 `/login`，按下面顺序检查：

1. 浏览器 Network 中确认 `/api/ragent/auth/login` 返回成功，并且响应体包含 token。
2. 确认 `/api/ragent/user/me` 没有返回 401 或“未登录”。
3. 确认 `frontend/.env` 中 `VITE_API_BASE_URL` 没有漏掉 `/api/ragent`。
4. 如果刚改过 `.env`、`vite.config.ts` 或后端端口，重启前端开发服务器。
5. 如果只在管理后台跳转，检查当前用户 `role` 是否为 `admin`。

## 常见现象

### 出现 `No static resource api/ragent/...`

请求没有被代理到后端，通常是前端 dev server 没有重启，或 `vite.config.ts` 中 `/api` 代理没有生效。重启 `npm run dev` 后再检查 Network 请求地址。

### 前端提示网络错误

先确认后端主服务是否在 `9090` 端口运行，再确认 `VITE_API_BASE_URL` 是否与后端 context path 匹配。

### 接口返回 401 或未登录

这是未登录或 token 过期的正常表现。重新登录即可；如果反复出现，清理浏览器本地存储后重新登录。
