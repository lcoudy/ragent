# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Ragent AI is a full-stack Agentic RAG platform. It includes document ingestion, parsing, chunking, embedding, vector retrieval, reranking, intent recognition, chat/session memory, model routing/failover, MCP tool integration, tracing, and an admin console.

The repository is split into a Maven multi-module Java backend and a Vite/React frontend.

## Common commands

### Backend

Run commands from the repository root unless noted otherwise.

```powershell
# Build all Maven modules
./mvnw clean package

# Compile all backend modules; Spotless license header formatting is bound to compile
./mvnw compile

# Run all backend tests
./mvnw test

# Run tests for one module
./mvnw -pl bootstrap test

# Run one test class
./mvnw -pl bootstrap -Dtest=ScheduleRefreshProcessorTest test

# Run one test method
./mvnw -pl bootstrap -Dtest=ScheduleRefreshProcessorTest#methodName test

# Start the main Ragent service
./mvnw -pl bootstrap spring-boot:run

# Start the MCP server
./mvnw -pl mcp-server spring-boot:run
```

The main backend service listens on port `9090` with context path `/api/ragent`. The MCP server listens on port `9099`.

### Frontend

Run commands from `frontend/`.

```powershell
npm install
npm run dev
npm run build
npm run lint
npm run preview
npm run format
```

The frontend reads `VITE_API_BASE_URL` for the API base URL. If unset, requests are relative to the current origin.

## Runtime dependencies and configuration

Primary backend configuration is in `bootstrap/src/main/resources/application.yaml`.

Default local dependencies/configuration include:

- PostgreSQL database `ragent` at `127.0.0.1:5432`, username/password `postgres`/`postgres`.
- Redis at `127.0.0.1:6379`, password `123456`.
- RocketMQ name server at `127.0.0.1:9876`.
- Optional Milvus at `http://localhost:19530`; `rag.vector.type` defaults to `pg` and can be `pg` or `milvus`.
- RustFS/S3-compatible object storage at `http://localhost:9000` with default credentials in the YAML.
- MCP server URL defaults to `http://localhost:9099`.
- AI provider keys are supplied through `BAILIAN_API_KEY` and `SILICONFLOW_API_KEY`; Ollama defaults to `http://localhost:11434`.

Database scripts live under `resources/database/`:

- `schema_pg.sql` and `init_data_pg.sql` initialize the PostgreSQL schema/data.
- `upgrade_v1.0_to_v1.1.sql` and `upgrade_v1.1_to_v1.2.sql` are upgrade scripts.

Docker compose files for Milvus and RocketMQ are under `resources/docker/`.

## Backend architecture

The root `pom.xml` defines four Maven modules:

- `bootstrap`: Spring Boot application and product/business modules. Entry point: `com.nageoffer.ai.ragent.RagentApplication`.
- `framework`: shared infrastructure: result wrappers, exceptions/error codes, global web handling, user context, Redis/cache utilities, distributed IDs, idempotency aspects, RocketMQ producer support, SSE helper, and RAG trace annotations/context.
- `infra-ai`: AI provider abstraction layer. It defines chat, embedding, rerank, token counting, model health, model selection, routing, fallback, stream parsing, and provider-specific clients for Bailian, SiliconFlow, Ollama, and noop rerank.
- `mcp-server`: standalone Spring Boot MCP server with sample executors such as weather, ticket, and sales tools. Entry point: `com.nageoffer.ai.ragent.mcp.McpServerApplication`.

`bootstrap` depends on `framework` and `infra-ai`; business code should call the abstractions exposed by those modules rather than duplicating provider or infrastructure logic.

Important backend domains in `bootstrap/src/main/java/com/nageoffer/ai/ragent/` include:

- `rag`: chat/RAG orchestration, retrieval, intent handling, query rewrite, memory, vector-store use, and traceable answer generation.
- `knowledge`: knowledge base, documents, chunks, storage, and scheduled refresh/indexing.
- `ingestion`: configurable document ingestion pipelines. `IngestionEngine` discovers `IngestionNode` Spring beans by node type, validates a linear `PipelineDefinition`, executes nodes in order, evaluates node conditions, and records node logs in the ingestion context.
- `core`: lower-level document parsing and chunking utilities used by ingestion/knowledge flows.
- `admin`: dashboard/admin-facing service and controller code.
- `user`: authentication/user management.

MyBatis mapper scanning is configured in `RagentApplication` for `rag`, `ingestion`, `knowledge`, and `user` mapper packages.

## Model and RAG extension points

The README describes these as intentional extension points:

- Add a retrieval channel by implementing `SearchChannel` and registering it as a Spring bean.
- Add a search post-processor by implementing `SearchResultPostProcessor`.
- Add an MCP tool by implementing an `MCPToolExecutor`-style executor and registering/discovering it in the relevant MCP registry/server path.
- Add an ingestion pipeline node by implementing `IngestionNode`.
- Add a model provider in `infra-ai` by implementing the relevant `ChatClient`, `EmbeddingClient`, or `RerankClient` abstraction and adding it to model configuration/candidate routing.

Model routing and failover are centralized in `infra-ai`, especially `ModelRoutingExecutor`, `ModelHealthStore`, and the routing services. Keep provider-specific HTTP/API behavior in `infra-ai` rather than in business services.

## Frontend architecture

The frontend is a Vite React 18 TypeScript app under `frontend/`.

Key structure:

- `src/router.tsx` defines routes: `/login`, `/chat`, `/chat/:sessionId`, and admin routes under `/admin`.
- `src/services/api.ts` creates the shared Axios instance, injects the stored auth token, unwraps backend `Result` payloads, and redirects to `/login` when auth expires.
- `src/services/*Service.ts` files are API clients by domain.
- `src/stores/` contains Zustand stores for auth, chat, and theme state.
- `src/pages/` contains route-level pages; admin pages are under `src/pages/admin/`.
- `src/components/` contains chat, session, layout, admin, common, and UI components.

Backend responses using a `{ code, message, data }` shape are unwrapped by the Axios response interceptor, so frontend service functions generally receive `data` directly.

## Contribution plan workflow

A daily contribution task plan is maintained in `docs/weekly-contribution-plan.md`.

When asked to continue the contribution plan or complete the next daily task:

1. Read `docs/weekly-contribution-plan.md` and pick the first task whose status is `Todo`, unless the user specifies a day.
2. Implement a real, useful change that matches the selected task.
3. Prefer small, independent changes that can be committed separately.
4. Update the selected task status from `Todo` to `Done` after the work is complete.
5. Run the most relevant lightweight checks when practical.
6. Only create a git commit if the user explicitly asks to commit.

Do not create empty commits or meaningless timestamp-only changes for contribution activity.
