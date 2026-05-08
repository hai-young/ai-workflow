# 对话持久化与文档管理优化设计

## 概述

将对话记忆从纯 Redis 升级为 MySQL 持久化 + Redis 热缓存，并补全文档生命周期管理（删除级联）。

## 1. 数据库设计

### 1.1 MySQL 新表

#### conversations — 会话表

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | 自增主键 |
| session_id | VARCHAR(36) UNIQUE INDEX | 业务 ID（UUID） |
| title | VARCHAR(200) | 首轮用户问题截断 |
| total_rounds | INT DEFAULT 0 | 对话轮数 |
| summary | TEXT NULLABLE | LLM 压缩摘要 |
| status | VARCHAR(20) DEFAULT 'active' | active / archived |
| created_at | DATETIME | |
| updated_at | DATETIME | |

#### messages — 消息表

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | |
| session_id | VARCHAR(36) INDEX | FK → conversations |
| role | VARCHAR(10) | user / assistant / system |
| content | TEXT | 完整消息 |
| citations | TEXT NULLABLE | JSON 数组 |
| token_count | INT NULLABLE | |
| created_at | DATETIME | |

#### documents — 文档注册表

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | |
| doc_id | VARCHAR(64) UNIQUE INDEX | 业务 UUID |
| file_name | VARCHAR(500) | |
| file_type | VARCHAR(50) | pdf/txt/docx/... |
| file_size | BIGINT | 字节数 |
| file_hash | VARCHAR(64) | SHA-256 |
| chunk_count | INT DEFAULT 0 | |
| minio_path | VARCHAR(500) | `{docId}/original.pdf` |
| es_status | VARCHAR(20) DEFAULT 'pending' | pending / indexed / error |
| milvus_status | VARCHAR(20) DEFAULT 'pending' | pending / indexed / error |
| created_at | DATETIME | |
| updated_at | DATETIME | |

### 1.2 Redis 缓存层

```
chat:cache:{sessionId}  → 最近 5 轮对话 JSON    TTL: 1h
chat:summary:{sessionId} → 压缩摘要               TTL: 与 rag.memory.ttl-seconds 一致
```

读写策略：
- **读**：Redis miss → 回源 MySQL → 写回 Redis
- **写**：同时写 MySQL + 更新 Redis
- **压缩**：更新 MySQL conversations.summary + Redis chat:summary

## 2. API 变更

### 2.1 新增端点

```
DELETE /api/knowledge/documents/{docId}
  → 级联删除 ES + Milvus + MinIO + MySQL
  返回: { success, message, deletedChunks }

GET /api/knowledge/documents/{docId}
  → 文档详情 + chunk 列表
  返回: { success, data: { docId, fileName, fileType, fileSize, fileHash,
          chunkCount, esStatus, milvusStatus, minioPath, chunks } }

DELETE /api/rag/conversations/{sessionId}
  → 删除 MySQL conversations + messages + Redis 缓存
  返回: { success, message }
```

### 2.2 已有端点增强

| 端点 | 变更 |
|---|---|
| GET /api/knowledge/documents | 数据源从纯 ES 聚合改为 MySQL 主查 + ES 补充 chunk 计数 |
| GET /api/rag/conversations | 从 Redis scan 改为 MySQL 分页查询 |
| GET /api/rag/conversations/{sessionId} | messages 从 MySQL 加载，返回结构增加 messages 数组 |
| POST /api/rag/ask | 响应新增 conversationId, messageId |
| POST /api/rag/ask/stream | done 事件新增 conversationId, messageId |

## 3. Service 层重构

### 3.1 新增类

| 类 | 职责 |
|---|---|
| `ConversationRepository` | JPA Repository，会话 CRUD |
| `MessageRepository` | JPA Repository，消息写入/按 session 查询 |
| `DocumentRepository` | JPA Repository，文档注册表 CRUD |
| `DocumentLifecycleService` | 文档删除级联、上传注册、状态更新 |

### 3.2 重构类

| 类 | 变更 |
|---|---|
| `ConversationMemoryService` | 注入 Repository，读写穿透（MySQL + Redis） |
| `KnowledgeService` | 文档 CRUD 委托给 DocumentLifecycleService，只保留索引状态/一致性/重索引/错误日志 |
| `RagService` | uploadDocument 完成后调 register()；问答完成后返回 messageId |

## 4. 删除级联链路

```
DocumentLifecycleService.deleteByDocId(docId)
  ├─ ES: esClient.deleteByQuery(doc_id == X)
  ├─ Milvus: vectorStore.delete("doc_id == X")
  ├─ MinIO: minioClient.removeObject(bucket, minioPath)
  └─ MySQL: documentRepository.deleteByDocId(docId)
```

失败处理：前三步任一失败记录 error 日志但不阻断后续步骤，MySQL 记录最终删除。

## 5. 数据流

### 5.1 对话写入

```
用户提问 → RagService.ask()
  ├─ 创建/更新 conversations 记录（首次创建，后续更新 total_rounds + updated_at）
  ├─ 写入 user message → messages 表
  ├─ LLM 生成回答
  ├─ 写入 assistant message → messages 表
  ├─ 更新 Redis chat:cache:{sessionId}
  └─ 超过阈值 → LLM 摘要 → 更新 conversations.summary + Redis
```

### 5.2 文档上传

```
上传文件 → RagService.uploadDocument()
  ├─ 存 MinIO 原始文件
  ├─ 解析 + 分块
  ├─ ES 索引 chunks
  ├─ Milvus 向量化 + 索引
  └─ DocumentLifecycleService.register() → MySQL INSERT
```

## 6. 实施注意事项

- Hibernate `ddl-auto: update` 自动建表，无需手动 DDL
- Milvus 删除需确认 SDK 2.5.x 支持 `deleteByExpression`（collection 需有 doc_id 标量索引）
- ES 删除用 `deleteByQuery`，需确认索引 mapping 中 doc_id 为 keyword 类型（ElasticsearchRetriever 已配置）
- 上传流程中的 SHA-256 计算使用 `MessageDigest`，不依赖外部库
- 缓存 TTL 1h 为合理默认值，可按需通过配置调整
- 现有 ConversationMemoryService 测试需更新以适配 MySQL Repository Mock
