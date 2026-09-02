# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在此仓库中工作时提供指导。

## 项目概览

企业级智能协作平台 —— 一个全栈的**智能 RAG** 问答系统。用户将文档上传到知识库，然后基于该语料库进行提问，回答会附带引用来源和可见的"思考过程"。

相比普通向量检索 RAG，本系统的核心升级在于一条由职责单一的 **Advisor** 组成的流水线：意图路由、查询重写、混合检索（Milvus 向量 + Elasticsearch BM25，通过 RRF 融合）、BGE 重排序、带引用编号的上下文组装，以及安全校验。

## 仓库结构

- `backend/ai-orchestrator-zhy/` — Spring Boot 单体应用（Java 21、Spring Boot 3.5.14、Maven）
- `frontend/` — Vue 3 + TypeScript + Vite + Pinia + Ant Design Vue
- `docker-compose.yml` — 基础设施：MySQL 8、Redis 7、RabbitMQ 3.13、Milvus 2.5.9（+ etcd + MinIO）、Elasticsearch 8.12.2 + Kibana、bge-rerank-v2-m3 重排序服务、Attu（Milvus 管理界面）
- `prd/` — 产品需求文档（主 PRD + 各模块 PRD，中文）
- `docs/superpowers/` — 设计规格与实现计划

## 常用命令

### 后端 —— 在 `backend/ai-orchestrator-zhy/` 下

```bash
./mvnw spring-boot:run            # 启动开发服务（端口 8080）
./mvnw clean compile -DskipTests  # 编译
./mvnw test                       # 运行全部测试
./mvnw test -Dtest=RagServiceTest # 运行单个测试类
./start-backend.sh                # 清理构建并启动
./test-api.sh                     # 认证接口的 curl 冒烟测试
```

使用 `./mvnw`（bash）或 `mvnw.cmd`（cmd/PowerShell）。测试位于 `src/test/java/com/zhy/workflow/ai/`。

### 前端 —— 在 `frontend/` 下

```bash
npm install
npm run dev      # 开发服务，端口 3000；Vite 将 /api 代理到 http://127.0.0.1:8080
npm run build    # vue-tsc 类型检查 + vite 构建
npm run lint     # eslint --fix
```

### 基础设施

```bash
docker-compose up -d   # 启动 MySQL、Redis、RabbitMQ、Milvus、ES、重排序服务等
```

## 架构

### RAG 流水线（系统核心）

`RagService`（`backend/.../service/RagService.java`）将问答流程编排为一系列注入的 Advisor 组件（`backend/.../advisor/`），每个组件对应流水线的一个阶段：

1. `IntentionRouterAdvisor` — 意图分类；闲聊/指令类回复会跳过检索直接返回。
2. `MemoryAdvisor` — 加载短期对话记忆（基于 Redis）。
3. `QueryRewriteAdvisor` — 指代消解 + 多跳问题拆解为子问题。
4. `HybridRetrievalAdvisor` — Milvus（向量）+ Elasticsearch（BM25），由 `RrfFusion` 融合；主问题与各子问题分别检索后按 `doc_id` 去重。
5. `RerankAdvisor` — 通过 `RerankerClient` 调用 BGE 重排序。
6. `ContextEnrichAdvisor` — 组装 system prompt 并分配引用编号。
7. `SafetyGuardAdvisor` — 依据引用校验回答。
8. `MemoryAdvisor` — 保存本轮对话。

`ask()`（同步 JSON）与 `askStream()`（SSE）执行同一条链路；`askStream` 会发出 `thinking` / `token` / `done` / `error` 事件，由前端的 `ThinkingIndicator` 消费。

### 文档生命周期

上传（`RagService.uploadDocument`）：文件 → MinIO（原始文件）+ Apache Tika 解析 → `TokenTextSplitter` 分块 → 双写索引到 Milvus + Elasticsearch → 在 MySQL 注册元数据（`DocumentRecord`）。删除会级联清理 ES + Milvus + MinIO + MySQL（`DocumentLifecycleService`）；重索引与一致性校验在 `KnowledgeService` 中。

### 包结构（`com.zhy.workflow.ai`）

- `advisor/` — 上述流水线各阶段
- `retrieval/` — `ElasticsearchRetriever`、`RrfFusion`
- `service/` — `RagService`（编排）、`KnowledgeService`、`DocumentLifecycleService`、`ConversationMemoryService`、`AuthService`、`VerifyCodeService`
- `controller/` — REST 接口，位于 `/api/{rag,knowledge,auth,verify,user-settings}`
- `entity/` + `repository/` — JPA 实体与 Spring Data 仓储（`User`、`Conversation`、`Message`、`DocumentRecord`、`VerifyCode`）
- `security/` — JWT 认证（`JwtUtil`、`JwtAuthenticationFilter`、`SecurityConfig`）
- `config/` — `CorsConfig`、`ElasticsearchConfig`、`MinioConfig`
- `dto/` — 请求/响应记录（`AskRequest`、`AskResponse`、`Citation`、`ThinkingProcess`）

`WorkflowOrchestrator` / `WorkflowController`（`/api/workflow`）是早期的 LangGraph4j 实验 —— `@PostConstruct` 自动运行已注释掉，与 RAG 主链路基本脱节。

### 前端结构

- `src/api/` — 按领域划分的 axios 客户端（`auth.ts`、`rag.ts`、`verify.ts`）
- `src/stores/` — Pinia 状态（`auth`、`chat`、`knowledge`、`ui`）
- `src/composables/useChat.ts` — 聊天 + SSE 消费逻辑
- `src/views/` — `LoginView`、`RegisterView`、`ResetPasswordView`、`AppLayout`（认证外壳）、`DashboardView`、`ChatPage`、`KnowledgePage`
- `src/components/chat/` — `ChatMain`、`ChatSidebar`、`CitationPanel`、`InputBar`、`MessageItem`、`ThinkingIndicator`、`ReindexProgressDrawer`
- `src/utils/request.ts` — axios 封装（注入认证 token）
- `src/styles/dark-neon.css` — 自定义深色主题

路由守卫会将未认证用户重定向到 `/login`。

## 配置

- 后端：`backend/ai-orchestrator-zhy/src/main/resources/application.yml` — 数据源（MySQL `workflow_db`）、RabbitMQ、Redis、Spring AI OpenAI（通过 DashScope 兼容模式接入 Qwen）、Milvus、Elasticsearch、MinIO、重排序服务 URL、JWT、RAG 记忆设置。**此文件包含真实的 API key/密钥。**
- 前端：`frontend/.env.local` — `VITE_API_BASE_URL`；开发代理配置在 `vite.config.ts`。
- LLM：Qwen `qwen3.6-plus` 聊天模型 + `text-embedding-v3`（1024 维）嵌入模型，通过 `https://dashscope.aliyuncs.com/compatible-mode` 接入。

## 约定

- 代码注释与产品文档为中文；标识符和 API 路径为英文。
- 后端使用 Lombok；JPA `ddl-auto: update` 加 `schema.sql` —— 表结构变更在启动时应用，无迁移工具。
