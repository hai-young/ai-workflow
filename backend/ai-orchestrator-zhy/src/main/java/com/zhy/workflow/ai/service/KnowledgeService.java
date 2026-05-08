package com.zhy.workflow.ai.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhy.workflow.ai.retrieval.ElasticsearchRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库管理服务：索引状态、文档管理、一致性校验、重索引、错误日志。
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String INDEX_NAME = "knowledge_base";
    private static final String ERROR_LOG_KEY = "knowledge:error-logs";
    private static final String REINDEX_KEY_PREFIX = "knowledge:reindex:";
    private static final int MAX_ERROR_LOGS = 100;

    private final ElasticsearchClient esClient;
    private final ElasticsearchRetriever esRetriever;
    private final VectorStore vectorStore;
    private final StringRedisTemplate redis;
    private final DocumentLifecycleService documentLifecycleService;

    public KnowledgeService(ElasticsearchClient esClient,
                            ElasticsearchRetriever esRetriever,
                            VectorStore vectorStore,
                            StringRedisTemplate redis,
                            DocumentLifecycleService documentLifecycleService) {
        this.esClient = esClient;
        this.esRetriever = esRetriever;
        this.vectorStore = vectorStore;
        this.redis = redis;
        this.documentLifecycleService = documentLifecycleService;
    }

    // ═══════════════════════════════════════════════════════════
    // 索引状态
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取 Milvus 和 Elasticsearch 的索引状态及文档数量。
     */
    public Map<String, Object> getIndexStatus() {
        Map<String, Object> result = new LinkedHashMap<>();

        // ── Milvus 状态 ──
        Map<String, Object> milvusStatus = new LinkedHashMap<>();
        long milvusDocCount = 0;
        boolean milvusAvailable = false;
        String milvusError = null;
        try {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("a")
                            .topK(1)
                            .similarityThreshold(0.0)
                            .build());
            milvusAvailable = true;
            milvusDocCount = estimateMilvusCount();
        } catch (Exception e) {
            milvusError = e.getMessage();
            log.warn("Milvus 状态检查失败: {}", milvusError);
        }
        milvusStatus.put("available", milvusAvailable);
        milvusStatus.put("docCount", milvusDocCount);
        if (milvusError != null) {
            milvusStatus.put("error", milvusError);
        }

        // ── Elasticsearch 状态 ──
        Map<String, Object> esStatus = new LinkedHashMap<>();
        long esDocCount = 0;
        boolean esAvailable = false;
        String esError = null;
        try {
            esRetriever.ensureIndex();
            esDocCount = esClient.count(c -> c.index(INDEX_NAME)).count();
            esAvailable = true;
        } catch (Exception e) {
            esError = e.getMessage();
            log.warn("Elasticsearch 状态检查失败: {}", esError);
        }
        esStatus.put("available", esAvailable);
        esStatus.put("docCount", esDocCount);
        if (esError != null) {
            esStatus.put("error", esError);
        }

        // ── 一致性概览 ──
        Map<String, Object> consistency = new LinkedHashMap<>();
        consistency.put("milvusCount", milvusDocCount);
        consistency.put("esCount", esDocCount);
        if (milvusAvailable && esAvailable) {
            consistency.put("consistent", milvusDocCount == esDocCount);
        } else {
            consistency.put("consistent", false);
            consistency.put("note", "部分服务不可用，无法判断一致性");
        }

        result.put("milvus", milvusStatus);
        result.put("elasticsearch", esStatus);
        result.put("consistency", consistency);
        return result;
    }

    /**
     * 估算 Milvus 中的文档数量。
     * 通过尝试多组相似度搜索来估计（受 topK 限制，结果为近似值）。
     */
    private long estimateMilvusCount() {
        try {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("document")
                            .topK(10000)
                            .similarityThreshold(0.0)
                            .build());
            // 按 doc_id 去重
            Set<String> uniqueDocIds = docs.stream()
                    .map(d -> (String) d.getMetadata().getOrDefault("doc_id", ""))
                    .filter(id -> !id.isEmpty())
                    .collect(Collectors.toSet());
            return uniqueDocIds.size();
        } catch (Exception e) {
            log.debug("Milvus 文档计数失败: {}", e.getMessage());
            return 0;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 文档列表
    // ═══════════════════════════════════════════════════════════

    /**
     * 查询文档列表，MySQL 为主数据源、ES 为补充（关键词搜索时优先 ES）。
     */
    public Map<String, Object> getDocuments(int page, int pageSize, String keyword, String fileType, String indexStatus) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 有关键词搜索时走 ES 全文检索
        if (hasFilter(keyword) && esRetriever.isAvailable()) {
            try {
                esRetriever.ensureIndex();
                SearchResponse<Map> response = esClient.search(s -> {
                    s.index(INDEX_NAME).size(0);
                    s.query(q -> q.multiMatch(mm -> mm
                            .query(keyword.trim())
                            .fields("title", "content")));
                    if (hasFilter(fileType)) {
                        s.query(q -> q.bool(b -> b
                                .must(m -> m.multiMatch(mt -> mt.query(keyword.trim()).fields("title", "content")))
                                .filter(f -> f.term(t -> t.field("file_type").value(fileType.trim())))));
                    }
                    s.aggregations("by_doc_id", a -> a
                            .terms(t -> t.field("doc_id").size(10000))
                            .aggregations("latest", sa -> sa
                                    .topHits(th -> th.size(1)
                                            .sort(srt -> srt.field(f -> f.field("upload_time").order(SortOrder.Desc)))
                                            .source(sf -> sf.filter(f -> f.includes(
                                                    List.of("doc_id", "chunk_id", "title", "content",
                                                            "file_name", "file_type", "upload_time")))))));
                    return s;
                }, Map.class);

                StringTermsAggregate termsAgg = response.aggregations().get("by_doc_id").sterms();
                List<StringTermsBucket> buckets = termsAgg.buckets().array();

                List<String> esDocIds = buckets.stream()
                        .map(b -> b.key().toString())
                        .collect(Collectors.toList());

                // 用 ES 的 docIds 从 MySQL 补全元数据
                List<Map<String, Object>> docs = esDocIds.stream()
                        .map(docId -> {
                            var record = documentLifecycleService.getByDocId(docId);
                            if (record.isPresent()) {
                                return buildDocEntryFromRecord(record.get());
                            }
                            // MySQL 中没有记录，从 ES 聚合构造
                            var bucket = buckets.stream()
                                    .filter(b -> b.key().toString().equals(docId))
                                    .findFirst().orElse(null);
                            return buildDocumentEntry(docId,
                                    bucket != null ? bucket.docCount() : 0,
                                    bucket != null ? extractTopHitSource(bucket) : Collections.emptyMap());
                        })
                        .collect(Collectors.toList());

                return paginateDocs(docs, page, pageSize, result);
            } catch (Exception e) {
                log.warn("ES 关键词搜索失败，降级到 MySQL: {}", e.getMessage());
            }
        }

        // ── 主路径：MySQL 分页查询 ──
        org.springframework.data.domain.Page<com.zhy.workflow.ai.entity.DocumentRecord> mysqlPage =
                documentLifecycleService.listDocuments(page, pageSize);

        List<Map<String, Object>> docs = mysqlPage.getContent().stream()
                .map(this::buildDocEntryFromRecord)
                .collect(Collectors.toList());

        // 按 indexStatus 过滤
        if (hasFilter(indexStatus)) {
            docs = docs.stream()
                    .filter(d -> indexStatus.trim().equalsIgnoreCase((String) d.get("indexStatus")))
                    .collect(Collectors.toList());
        }
        // 按 fileType 过滤
        if (hasFilter(fileType) && !hasFilter(keyword)) {
            docs = docs.stream()
                    .filter(d -> fileType.trim().equalsIgnoreCase((String) d.get("fileType")))
                    .collect(Collectors.toList());
        }

        result.put("documents", docs);
        result.put("total", (int) mysqlPage.getTotalElements());
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("totalPages", Math.max(1, mysqlPage.getTotalPages()));
        return result;
    }

    private Map<String, Object> buildDocEntryFromRecord(com.zhy.workflow.ai.entity.DocumentRecord record) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", record.getDocId());
        doc.put("docId", record.getDocId());
        doc.put("fileName", record.getFileName());
        doc.put("fileType", record.getFileType());
        doc.put("fileSize", record.getFileSize());
        doc.put("chunkCount", record.getChunkCount());
        doc.put("uploadTime", record.getCreatedAt() != null ? record.getCreatedAt().toString() : "");
        doc.put("esStatus", record.getEsStatus() != null ? record.getEsStatus() : "pending");
        doc.put("milvusStatus", record.getMilvusStatus() != null ? record.getMilvusStatus() : "pending");

        // 计算综合状态
        boolean esOk = "indexed".equals(record.getEsStatus());
        boolean milOk = "indexed".equals(record.getMilvusStatus());
        if (esOk && milOk) doc.put("indexStatus", "completed");
        else if (!esOk && !milOk) doc.put("indexStatus", "pending");
        else doc.put("indexStatus", "partial");
        return doc;
    }

    private Map<String, Object> paginateDocs(List<Map<String, Object>> allDocs, int page, int pageSize,
                                              Map<String, Object> result) {
        int total = allDocs.size();
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> pageDocs = from < total ? allDocs.subList(from, to) : Collections.emptyList();
        result.put("documents", pageDocs);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("totalPages", Math.max(1, (int) Math.ceil((double) total / pageSize)));
        return result;
    }

    private Map<String, Object> extractTopHitSource(StringTermsBucket bucket) {
        try {
            TopHitsAggregate topHits = bucket.aggregations().get("latest").topHits();
            List<Hit<JsonData>> hitsData = topHits.hits().hits();
            if (!hitsData.isEmpty() && hitsData.get(0).source() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> source = hitsData.get(0).source().to(Map.class);
                return source;
            }
        } catch (Exception e) {
            log.debug("提取聚合 top hit 失败, bucket={}: {}", bucket.key(), e.getMessage());
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> buildDocumentEntry(String docId, long chunkCount, Map<String, Object> source) {
        Map<String, Object> doc = new LinkedHashMap<>();
        String fileName = safeString(source.get("file_name"), "未知");
        String fileType = safeString(source.get("file_type"), "");
        String uploadTime = safeString(source.get("upload_time"), "");

        doc.put("id", docId);
        doc.put("docId", docId);
        doc.put("fileName", fileName);
        doc.put("fileType", fileType);
        doc.put("fileSize", source.getOrDefault("file_size", 0));
        doc.put("uploadTime", uploadTime);
        doc.put("chunkCount", chunkCount);

        // 三态状态标记
        boolean esAvail = esRetriever.isAvailable();
        boolean milvusAvail = isMilvusAvailable();
        doc.put("esStatus", esAvail ? "indexed" : "pending");
        doc.put("milvusStatus", milvusAvail ? "indexed" : "pending");

        if (esAvail && milvusAvail) {
            doc.put("indexStatus", "completed");
        } else if (!esAvail && !milvusAvail) {
            doc.put("indexStatus", "pending");
        } else {
            doc.put("indexStatus", "partial");
        }
        return doc;
    }

    // ═══════════════════════════════════════════════════════════
    // 一致性校验
    // ═══════════════════════════════════════════════════════════

    /**
     * 比较 ES 和 Milvus 中的 doc_id 集合，返回匹配/差异统计。
     */
    public Map<String, Object> checkConsistency() {
        Map<String, Object> result = new LinkedHashMap<>();
        Set<String> esDocIds = Collections.emptySet();
        Set<String> milvusDocIds = Collections.emptySet();
        boolean esAvailable = false;
        boolean milvusAvailable = false;
        String esError = null;
        String milvusError = null;

        // ── ES: 通过聚合获取所有唯一的 doc_id ──
        try {
            esDocIds = collectEsDocIds();
            esAvailable = true;
        } catch (Exception e) {
            esError = e.getMessage();
            log.warn("一致性校验 - ES 查询失败: {}", esError);
        }

        // ── Milvus: 通过向量搜索获取所有文档 ──
        try {
            milvusDocIds = collectMilvusDocIds();
            milvusAvailable = true;
        } catch (Exception e) {
            milvusError = e.getMessage();
            log.warn("一致性校验 - Milvus 查询失败: {}", milvusError);
        }

        // ── 计算差异 ──
        Set<String> matched = new HashSet<>(esDocIds);
        matched.retainAll(milvusDocIds);

        Set<String> esOnly = new HashSet<>(esDocIds);
        esOnly.removeAll(milvusDocIds);

        Set<String> milvusOnly = new HashSet<>(milvusDocIds);
        milvusOnly.removeAll(esDocIds);

        result.put("matched", matched.size());
        result.put("matchedIds", new ArrayList<>(matched));
        result.put("esOnly", esOnly.size());
        result.put("esOnlyIds", new ArrayList<>(esOnly));
        result.put("milvusOnly", milvusOnly.size());
        result.put("milvusOnlyIds", new ArrayList<>(milvusOnly));
        result.put("esAvailable", esAvailable);
        result.put("milvusAvailable", milvusAvailable);

        if (esError != null) {
            result.put("esError", esError);
        }
        if (milvusError != null) {
            result.put("milvusError", milvusError);
        }

        log.info("一致性校验完成: matched={}, esOnly={}, milvusOnly={}",
                matched.size(), esOnly.size(), milvusOnly.size());
        return result;
    }

    private Set<String> collectEsDocIds() {
        try {
            esRetriever.ensureIndex();
            SearchResponse<Map> response = esClient.search(s -> s
                            .index(INDEX_NAME)
                            .size(0)
                            .aggregations("unique_docs", a -> a
                                    .terms(t -> t.field("doc_id").size(10000))),
                    Map.class);
            StringTermsAggregate termsAgg = response.aggregations().get("unique_docs").sterms();
            return termsAgg.buckets().array().stream()
                    .map(b -> b.key().toString())
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("获取 ES 文档 ID 列表失败: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    private Set<String> collectMilvusDocIds() {
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("document")
                        .topK(10000)
                        .similarityThreshold(0.0)
                        .build());
        return docs.stream()
                .map(d -> (String) d.getMetadata().getOrDefault("doc_id", ""))
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toSet());
    }

    // ═══════════════════════════════════════════════════════════
    // 重索引
    // ═══════════════════════════════════════════════════════════

    /**
     * 触发异步重索引任务，将进度存入 Redis。
     */
    public Map<String, Object> reindex(String target) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("taskId", taskId);
        progress.put("target", target);
        progress.put("status", "pending");
        progress.put("milvusProgress", 0);
        progress.put("milvusTotal", 0);
        progress.put("esProgress", 0);
        progress.put("esTotal", 0);
        progress.put("startTime", LocalDateTime.now().toString());
        progress.put("endTime", null);
        progress.put("error", null);

        try {
            redis.opsForValue().set(REINDEX_KEY_PREFIX + taskId,
                    mapper.writeValueAsString(progress),
                    Duration.ofHours(24));
        } catch (Exception e) {
            log.error("重索引任务创建失败: {}", e.getMessage(), e);
            Map<String, Object> errResult = new LinkedHashMap<>();
            errResult.put("success", false);
            errResult.put("error", "任务创建失败: " + e.getMessage());
            return errResult;
        }

        // 异步执行
        final String finalTarget = target != null ? target.trim().toLowerCase() : "all";
        new Thread(() -> executeReindex(taskId, finalTarget), "reindex-" + taskId).start();

        log.info("重索引任务已创建: taskId={}, target={}", taskId, finalTarget);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("taskId", taskId);
        result.put("target", finalTarget);
        result.put("message", "重索引任务已提交");
        return result;
    }

    private void executeReindex(String taskId, String target) {
        try {
            updateReindexStatus(taskId, "running", null);

            // ── 收集 ES 中的文档信息作为索引依据 ──
            Set<String> allDocIds;
            try {
                allDocIds = collectEsDocIds();
            } catch (Exception e) {
                log.warn("重索引 - 无法获取 ES 文档列表, 尝试从 Milvus 获取: {}", e.getMessage());
                allDocIds = collectMilvusDocIds();
            }

            List<String> docIdList = new ArrayList<>(allDocIds);
            int totalDocs = docIdList.size();

            if (totalDocs == 0) {
                updateReindexStatus(taskId, "completed", "无可索引的文档");
                return;
            }

            if ("elasticsearch".equals(target) || "all".equals(target)) {
                // 重索引到 ES：从 ES/Milvus 获取每个 doc 的 chunks，重新写入 ES
                reindexToEs(taskId, docIdList);
            }

            if ("milvus".equals(target) || "all".equals(target)) {
                // 重索引到 Milvus：从 ES 获取每个 doc 的 chunks，重新写入 Milvus
                reindexToMilvus(taskId, docIdList);
            }

            updateReindexStatus(taskId, "completed", null);

        } catch (Exception e) {
            log.error("重索引任务异常: taskId={}, {}", taskId, e.getMessage(), e);
            updateReindexStatus(taskId, "failed", e.getMessage());
        }
    }

    /**
     * 重建 ES 索引：一次拉取 Milvus 全部 chunk，按 docId 分组后写入 ES。
     */
    private void reindexToEs(String taskId, List<String> docIdList) {
        int total = docIdList.size();
        int processed = 0;

        try {
            Map<String, Object> prog = readReindexProgress(taskId);
            prog.put("esTotal", total);
            prog.put("esProgress", 0);
            writeReindexProgress(taskId, prog);
        } catch (Exception e) {
            log.warn("更新 ES 重索引进度失败: {}", e.getMessage());
        }

        // 一次拉取 Milvus 全部 chunk，避免 filterExpression 兼容问题
        Set<String> targetSet = new HashSet<>(docIdList);
        Map<String, List<Document>> chunksByDoc = new HashMap<>();
        try {
            List<Document> allChunks = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("dummy")
                            .topK(10000)
                            .similarityThreshold(0.0)
                            .build());
            for (Document doc : allChunks) {
                String did = (String) doc.getMetadata().getOrDefault("doc_id", "");
                if (!did.isEmpty() && targetSet.contains(did)) {
                    chunksByDoc.computeIfAbsent(did, k -> new ArrayList<>()).add(doc);
                }
            }
        } catch (Exception e) {
            log.error("重索引 ES - 无法从 Milvus 读取 chunk: {}", e.getMessage());
            updateReindexStatus(taskId, "failed", "Milvus 读取失败: " + e.getMessage());
            return;
        }

        for (String docId : docIdList) {
            try {
                List<Document> chunks = chunksByDoc.getOrDefault(docId, Collections.emptyList());
                if (chunks.isEmpty()) {
                    log.warn("重索引 ES - 文档 {} 在 Milvus 中无数据，跳过", docId);
                    processed++;
                    updateEsProgress(taskId, processed, total);
                    continue;
                }

                esRetriever.ensureIndex();
                for (Document doc : chunks) {
                    String chunkId = (String) doc.getMetadata().getOrDefault("chunk_id", doc.getId());
                    String content = doc.getText();
                    String fileName = (String) doc.getMetadata().getOrDefault("file_name", "");
                    String fileType = (String) doc.getMetadata().getOrDefault("file_type", "");
                    String title = (String) doc.getMetadata().getOrDefault("title", fileName);

                    esRetriever.indexDocument(docId, chunkId, content, fileName, fileType, title);
                }

                documentLifecycleService.updateStatus(docId, "indexed", null);
                processed++;
                updateEsProgress(taskId, processed, total);
                log.info("重索引 ES 进度: {}/{} docId={}", processed, total, docId);
            } catch (Exception e) {
                log.warn("重索引 ES - 文档 {} 失败: {}", docId, e.getMessage());
                logError("reindex-es-doc-failed", "docId=" + docId + ", error=" + e.getMessage());
            }
        }
    }

    /**
     * 重建 Milvus 索引：从 ES 读取 chunk（含全文内容），重新 embedding 写入 Milvus。
     */
    private void reindexToMilvus(String taskId, List<String> docIdList) {
        int total = docIdList.size();
        int processed = 0;

        try {
            Map<String, Object> prog = readReindexProgress(taskId);
            prog.put("milvusTotal", total);
            prog.put("milvusProgress", 0);
            writeReindexProgress(taskId, prog);
        } catch (Exception e) {
            log.warn("更新 Milvus 重索引进度失败: {}", e.getMessage());
        }

        for (String docId : docIdList) {
            try {
                esRetriever.ensureIndex();
                // 从 ES 读取该文档所有 chunk
                SearchResponse<Map> response = esClient.search(s -> s
                                .index(INDEX_NAME)
                                .query(q -> q.term(t -> t.field("doc_id").value(docId)))
                                .size(10000),
                        Map.class);

                List<Document> chunks = new ArrayList<>();
                for (Hit<Map> hit : response.hits().hits()) {
                    Map<String, Object> source = hit.source();
                    if (source == null) continue;

                    String content = (String) source.getOrDefault("content", "");
                    if (content == null || content.isEmpty()) continue;

                    Document doc = new Document(content);
                    doc.getMetadata().put("doc_id", docId);
                    doc.getMetadata().put("chunk_id", source.getOrDefault("chunk_id", hit.id()));
                    doc.getMetadata().put("file_name", source.getOrDefault("file_name", ""));
                    doc.getMetadata().put("file_type", source.getOrDefault("file_type", ""));
                    doc.getMetadata().put("upload_time", source.getOrDefault("upload_time", ""));
                    doc.getMetadata().put("title", source.getOrDefault("title", ""));
                    doc.getMetadata().put("chunk_index", source.getOrDefault("chunk_index", 0));
                    chunks.add(doc);
                }

                if (!chunks.isEmpty()) {
                    vectorStore.add(chunks);
                }

                // 更新 MySQL 状态
                documentLifecycleService.updateStatus(docId, null, "indexed");

                processed++;
                updateMilvusProgress(taskId, processed, total);
            } catch (Exception e) {
                log.warn("重索引 Milvus - 文档 {} 失败: {}", docId, e.getMessage());
                logError("reindex-milvus-doc-failed", "docId=" + docId + ", error=" + e.getMessage());
            }
        }
    }

    private void updateReindexStatus(String taskId, String status, String error) {
        try {
            Map<String, Object> prog = readReindexProgress(taskId);
            prog.put("status", status);
            if (status.equals("completed") || status.equals("failed")) {
                prog.put("endTime", LocalDateTime.now().toString());
            }
            if (error != null) {
                prog.put("error", error);
            }
            writeReindexProgress(taskId, prog);
        } catch (Exception e) {
            log.warn("更新重索引状态失败: {}", e.getMessage());
        }
    }

    private void updateEsProgress(String taskId, int processed, int total) {
        try {
            Map<String, Object> prog = readReindexProgress(taskId);
            prog.put("esProgress", processed);
            prog.put("esTotal", total);
            writeReindexProgress(taskId, prog);
        } catch (Exception e) {
            log.debug("写入 ES 进度失败: {}", e.getMessage());
        }
    }

    private void updateMilvusProgress(String taskId, int processed, int total) {
        try {
            Map<String, Object> prog = readReindexProgress(taskId);
            prog.put("milvusProgress", processed);
            prog.put("milvusTotal", total);
            writeReindexProgress(taskId, prog);
        } catch (Exception e) {
            log.debug("写入 Milvus 进度失败: {}", e.getMessage());
        }
    }

    /**
     * 查询重索引任务进度。
     */
    public Map<String, Object> getReindexStatus(String taskId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (taskId == null || taskId.isBlank()) {
            result.put("success", false);
            result.put("error", "taskId 不能为空");
            return result;
        }

        try {
            Map<String, Object> progress = readReindexProgress(taskId);
            if (progress.isEmpty()) {
                result.put("success", false);
                result.put("error", "任务不存在或已过期");
                return result;
            }
            result.put("success", true);
            result.putAll(progress);
        } catch (Exception e) {
            log.warn("查询重索引进度失败: taskId={}, {}", taskId, e.getMessage());
            result.put("success", false);
            result.put("error", "查询失败: " + e.getMessage());
        }
        return result;
    }

    private Map<String, Object> readReindexProgress(String taskId) {
        try {
            String json = redis.opsForValue().get(REINDEX_KEY_PREFIX + taskId);
            if (json == null || json.isEmpty()) return new LinkedHashMap<>();
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private void writeReindexProgress(String taskId, Map<String, Object> progress) {
        try {
            redis.opsForValue().set(REINDEX_KEY_PREFIX + taskId,
                    mapper.writeValueAsString(progress),
                    Duration.ofHours(24));
        } catch (JsonProcessingException e) {
            log.warn("序列化重索引进度失败: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 错误日志
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取分页的错误日志。
     */
    public Map<String, Object> getErrorLogs(int page, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Long total = redis.opsForList().size(ERROR_LOG_KEY);
            long totalCount = total != null ? total : 0;

            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize - 1, (int) totalCount - 1);

            List<String> logs;
            if (start < totalCount) {
                logs = redis.opsForList().range(ERROR_LOG_KEY, start, end);
            } else {
                logs = Collections.emptyList();
            }
            if (logs == null) logs = Collections.emptyList();

            // 解析 JSON 日志
            List<Map<String, Object>> parsedLogs = new ArrayList<>();
            for (String entry : logs) {
                try {
                    Map<String, Object> logEntry = mapper.readValue(entry,
                            new TypeReference<Map<String, Object>>() {});
                    parsedLogs.add(logEntry);
                } catch (Exception e) {
                    Map<String, Object> raw = new LinkedHashMap<>();
                    raw.put("raw", entry);
                    parsedLogs.add(raw);
                }
            }

            result.put("logs", parsedLogs);
            result.put("total", totalCount);
            result.put("page", page);
            result.put("pageSize", pageSize);
            result.put("totalPages", Math.max(1, (int) Math.ceil((double) totalCount / pageSize)));
        } catch (Exception e) {
            log.warn("获取错误日志失败: {}", e.getMessage());
            result.put("logs", Collections.emptyList());
            result.put("total", 0);
            result.put("page", page);
            result.put("pageSize", pageSize);
            result.put("totalPages", 0);
        }
        return result;
    }

    /**
     * 记录一条错误日志到 Redis，最多保留 {@value #MAX_ERROR_LOGS} 条。
     */
    public void logError(String message, String detail) {
        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", LocalDateTime.now().toString());
            entry.put("message", message);
            entry.put("detail", detail != null ? detail : "");

            String json = mapper.writeValueAsString(entry);
            redis.opsForList().leftPush(ERROR_LOG_KEY, json);
            redis.opsForList().trim(ERROR_LOG_KEY, 0, MAX_ERROR_LOGS - 1);
        } catch (Exception e) {
            log.warn("记录错误日志失败: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════

    private boolean isMilvusAvailable() {
        try {
            vectorStore.similaritySearch(
                    SearchRequest.builder().query("a").topK(1).similarityThreshold(0.0).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasFilter(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safeString(Object value, String defaultVal) {
        if (value == null) return defaultVal;
        String s = value.toString();
        return s.isEmpty() ? defaultVal : s;
    }
}
