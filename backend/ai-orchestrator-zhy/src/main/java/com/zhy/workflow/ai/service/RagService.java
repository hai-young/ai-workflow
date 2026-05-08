package com.zhy.workflow.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhy.workflow.ai.advisor.*;
import com.zhy.workflow.ai.dto.AskResponse;
import com.zhy.workflow.ai.dto.Citation;
import com.zhy.workflow.ai.dto.ThinkingProcess;
import com.zhy.workflow.ai.retrieval.ElasticsearchRetriever;
import com.zhy.workflow.ai.service.DocumentLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final HybridRetrievalAdvisor hybridRetrievalAdvisor;
    private final RerankAdvisor rerankAdvisor;
    private final ContextEnrichAdvisor contextEnrichAdvisor;
    private final ElasticsearchRetriever esRetriever;
    private final QueryRewriteAdvisor queryRewriteAdvisor;
    private final MemoryAdvisor memoryAdvisor;
    private final IntentionRouterAdvisor intentionRouterAdvisor;
    private final SafetyGuardAdvisor safetyGuardAdvisor;
    private final MinioClient minioClient;
    private final DocumentLifecycleService documentLifecycleService;

    @org.springframework.beans.factory.annotation.Value("${minio.bucket-documents:rag-documents}")
    private String minioBucket;

    public RagService(VectorStore vectorStore,
                      ChatClient.Builder chatClientBuilder,
                      HybridRetrievalAdvisor hybridRetrievalAdvisor,
                      RerankAdvisor rerankAdvisor,
                      ContextEnrichAdvisor contextEnrichAdvisor,
                      ElasticsearchRetriever esRetriever,
                      QueryRewriteAdvisor queryRewriteAdvisor,
                      MemoryAdvisor memoryAdvisor,
                      IntentionRouterAdvisor intentionRouterAdvisor,
                      SafetyGuardAdvisor safetyGuardAdvisor,
                      MinioClient minioClient,
                      DocumentLifecycleService documentLifecycleService) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.hybridRetrievalAdvisor = hybridRetrievalAdvisor;
        this.rerankAdvisor = rerankAdvisor;
        this.contextEnrichAdvisor = contextEnrichAdvisor;
        this.esRetriever = esRetriever;
        this.queryRewriteAdvisor = queryRewriteAdvisor;
        this.memoryAdvisor = memoryAdvisor;
        this.intentionRouterAdvisor = intentionRouterAdvisor;
        this.safetyGuardAdvisor = safetyGuardAdvisor;
        this.minioClient = minioClient;
        this.documentLifecycleService = documentLifecycleService;
    }

    /**
     * 上传文档并持久化到 MinIO + 双索引到 Milvus + ES。
     */
    public void uploadDocument(MultipartFile file) throws IOException {
        String docId = UUID.randomUUID().toString().substring(0, 8);
        String fileName = file.getOriginalFilename();
        String fileType = fileName != null && fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1) : "unknown";

        // ── Step 0: 持久化原始文件到 MinIO ──
        try (InputStream inputStream = file.getInputStream()) {
            String objectPath = docId + "/original." + fileType;
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioBucket)
                    .object(objectPath)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            log.info("原始文件已持久化到 MinIO: {}/{}", minioBucket, objectPath);
        } catch (Exception e) {
            log.warn("MinIO 文件持久化失败（不影响索引流程）: docId={}, {}", docId, e.getMessage());
        }

        TikaDocumentReader reader = new TikaDocumentReader(file.getResource());
        List<Document> documents = reader.get();
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(documents);

        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            String chunkId = docId + "-" + i;
            chunk.getMetadata().put("doc_id", docId);
            chunk.getMetadata().put("chunk_id", chunkId);
            chunk.getMetadata().put("chunk_index", i);
            chunk.getMetadata().put("file_name", fileName);
            chunk.getMetadata().put("upload_time", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")));

            try {
                esRetriever.indexDocument(docId, chunkId, chunk.getText(), fileName, fileType, fileName);
            } catch (Exception e) {
                log.warn("ES 索引写入失败，chunkId={}: {}", chunkId, e.getMessage());
            }
        }

        vectorStore.add(chunks);

        // ── 注册到 MySQL ──
        String fileHash = computeSha256(file);
        String minioPath = docId + "/original." + fileType;
        documentLifecycleService.register(docId, fileName, fileType, file.getSize(),
                fileHash, chunks.size(), minioPath);

        log.info("文档上传完成: {} → {} 个 chunk (docId={})", fileName, chunks.size(), docId);
    }

    private String computeSha256(MultipartFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream is = file.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    md.update(buf, 0, n);
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            log.warn("SHA-256 计算失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 执行智能 RAG 问答（完整 Advisor 链编排）。
     *
     * 流程:
     *   0. IntentionRouterAdvisor  — 意图路由（闲聊/指令直接回复，跳过检索）
     *   1. MemoryAdvisor           — 加载短期记忆上下文
     *   2. QueryRewriteAdvisor     — 指代消解 + 多跳拆解
     *   3. HybridRetrievalAdvisor  — Milvus + ES → RRF 融合
     *   4. RerankAdvisor           — BGE-Reranker 精排
     *   5. ContextEnrichAdvisor    — 上下文组装 + 引用编号
     *   6. ChatClient              — LLM 生成回答
     *   7. SafetyGuardAdvisor      — 安全护栏与引用校验
     *   8. MemoryAdvisor           — 保存本轮对话到 Redis
     */
    public AskResponse ask(String question, String sessionId) {
        ThinkingProcess thinking = new ThinkingProcess();
        long startTime = System.currentTimeMillis();

        try {
            // ── Step 0: 意图路由 ──
            IntentionRouterAdvisor.RouteResult route =
                    intentionRouterAdvisor.route(question);
            thinking.setIntention(route.intent);

            if (route.degraded) {
                thinking.addFallback("router_degraded");
            }

            // 闲聊/指令：无需检索，直接返回
            if (route.skipRetrieval) {
                String answer = route.directResponse != null ? route.directResponse
                        : chatClient.prompt().user(question).call().content();
                thinking.setSafetyStatus("pass");

                try {
                    memoryAdvisor.saveRound(sessionId, question, answer);
                } catch (Exception e) {
                    log.warn("记忆保存失败: {}", e.getMessage());
                }

                return AskResponse.ok(answer, thinking, List.of(), sessionId, null);
            }

            // ── Step 1: 加载对话记忆 ──
            MemoryAdvisor.MemoryResult memory = memoryAdvisor.loadMemory(sessionId);
            String chatHistory = memory.context;

            // ── Step 2: 查询重写 ──
            QueryRewriteAdvisor.RewriteResult rewrite =
                    queryRewriteAdvisor.rewrite(question, chatHistory);
            String searchQuery = rewrite.rewrittenQuestion;
            thinking.setRewrittenQuery(searchQuery);
            thinking.setSubQueries(rewrite.subQueries);

            if (rewrite.rewriteFailed) {
                thinking.addFallback("rewrite_failed");
            }

            // ── Step 3: 混合检索（主问题 + 各子问题独立检索后合并去重） ──
            List<Document> allFusedDocs = new java.util.ArrayList<>();
            List<String> allFallbacks = new java.util.ArrayList<>();
            int totalRetrieved = 0;

            // 检索主问题
            HybridRetrievalAdvisor.RetrievalResult mainRetrieval =
                    hybridRetrievalAdvisor.retrieve(searchQuery);
            allFusedDocs.addAll(mainRetrieval.fusedDocs);
            totalRetrieved += mainRetrieval.fusionCount;
            if (mainRetrieval.getFallbacks() != null) allFallbacks.addAll(mainRetrieval.getFallbacks());

            // 各子问题独立检索
            for (String subQuery : rewrite.subQueries) {
                HybridRetrievalAdvisor.RetrievalResult subRetrieval =
                        hybridRetrievalAdvisor.retrieve(subQuery);
                allFusedDocs.addAll(subRetrieval.fusedDocs);
                totalRetrieved += subRetrieval.fusionCount;
                if (subRetrieval.getFallbacks() != null) allFallbacks.addAll(subRetrieval.getFallbacks());
            }

            // 按 doc_id 去重（保留 RRF 分数最高的那条）
            Map<String, Document> deduped = new java.util.LinkedHashMap<>();
            for (Document doc : allFusedDocs) {
                String key = (String) doc.getMetadata().getOrDefault("doc_id", doc.getId());
                Document existing = deduped.get(key);
                if (existing == null) {
                    deduped.put(key, doc);
                } else {
                    double curScore = getDocScore(doc);
                    double existScore = getDocScore(existing);
                    if (curScore > existScore) deduped.put(key, doc);
                }
            }
            List<Document> fusedDocs = new java.util.ArrayList<>(deduped.values());
            // 限制融合数量
            if (fusedDocs.size() > 30) fusedDocs = fusedDocs.subList(0, 30);

            thinking.setRetrievedCount(totalRetrieved);
            allFallbacks.forEach(thinking::addFallback);

            if (fusedDocs.isEmpty()) {
                return AskResponse.fail("检索服务暂时不可用，请稍后重试");
            }

            // ── Step 4: 重排序 ──
            RerankAdvisor.RerankResult rerankResult =
                    rerankAdvisor.rerank(searchQuery, fusedDocs);
            thinking.setRerankedCount(rerankResult.rerankedCount);
            thinking.setRerankStatus(rerankResult.status);
            if (rerankResult.degraded) {
                thinking.addFallback("reranker_degraded");
            }

            // ── Step 5: 上下文组装（注入记忆上下文） ──
            ContextEnrichAdvisor.EnrichResult enrichResult =
                    contextEnrichAdvisor.enrich(searchQuery, rerankResult.documents);

            // 拼接记忆上下文到 system prompt（如果存在）
            String enhancedSystemPrompt = enrichResult.systemPrompt;
            if (!chatHistory.isEmpty()) {
                enhancedSystemPrompt = enhancedSystemPrompt + "\n\n对话历史背景：\n" + chatHistory;
            }

            // ── Step 6: LLM 生成回答 ──
            String answer = chatClient.prompt()
                    .system(enhancedSystemPrompt)
                    .user(enrichResult.userQuestion)
                    .call()
                    .content();

            // ── Step 7: 安全护栏与引用校验 ──
            SafetyGuardAdvisor.GuardResult guard =
                    safetyGuardAdvisor.guard(answer, enrichResult.citations, enhancedSystemPrompt);
            thinking.setSafetyStatus(guard.status);
            if (!guard.passed) {
                log.warn("安全护栏拦截: {}", guard.reason);
                return AskResponse.fail("回答未通过安全校验: " + guard.reason);
            }

            // ── Step 8: 保存本轮对话到记忆 ──
            try {
                memoryAdvisor.saveRound(sessionId, question, answer);
            } catch (Exception e) {
                log.warn("记忆保存失败（不影响主链路）: {}", e.getMessage());
            }

            long totalMs = System.currentTimeMillis() - startTime;
            log.info("RAG 问答完成: original=\"{}\" → rewritten=\"{}\", docs={}, reranked={}, citations={}, latency={}ms, safety={}",
                    question.substring(0, Math.min(40, question.length())),
                    searchQuery.substring(0, Math.min(40, searchQuery.length())),
                    totalRetrieved, rerankResult.rerankedCount,
                    enrichResult.citations.size(), totalMs, guard.status);

            return AskResponse.ok(answer, thinking, enrichResult.citations, sessionId, null);

        } catch (Exception e) {
            log.error("RAG 问答异常: {}", e.getMessage(), e);
            return AskResponse.fail("生成服务暂时不可用: " + e.getMessage());
        }
    }

    /**
     * SSE 流式问答。通过 SseEmitter 实时推送思考过程和生成 token。
     *
     * 事件协议:
     *   event:thinking  data:{"stage":"intent","intent":"qa"}
     *   event:thinking  data:{"stage":"rewrite","rewritten_query":"..."}
     *   event:thinking  data:{"stage":"retrieval","count":20}
     *   event:thinking  data:{"stage":"rerank","count":5}
     *   event:token     data:{"token":"JWT"}
     *   event:done      data:{"answer":"...","citations":[...]}
     *   event:error     data:{"error":"..."}
     */
    public SseEmitter askStream(String question, String sessionId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时
        emitter.onCompletion(() -> log.debug("SSE 流式推送完成: session={}", sessionId));
        emitter.onTimeout(() -> log.warn("SSE 流式推送超时: session={}", sessionId));

        new Thread(() -> {
            ThinkingProcess thinking = new ThinkingProcess();
            try {
                // ── Step 0: 意图路由 ──
                IntentionRouterAdvisor.RouteResult route = intentionRouterAdvisor.route(question);
                thinking.setIntention(route.intent);
                emitThinking(emitter, "intent", Map.of("intent", route.intent));

                // 闲聊/指令：无需检索
                if (route.skipRetrieval) {
                    String answer = route.directResponse != null ? route.directResponse
                            : chatClient.prompt().user(question).call().content();
                    emitToken(emitter, answer);
                    emitDone(emitter, answer, List.of(), sessionId);
                    try { memoryAdvisor.saveRound(sessionId, question, answer); } catch (Exception e) { /* ok */ }
                    emitter.complete();
                    return;
                }

                // ── Step 1: 加载对话记忆 ──
                MemoryAdvisor.MemoryResult memory = memoryAdvisor.loadMemory(sessionId);
                String chatHistory = memory.context;

                // ── Step 2: 查询重写 ──
                QueryRewriteAdvisor.RewriteResult rewrite = queryRewriteAdvisor.rewrite(question, chatHistory);
                thinking.setRewrittenQuery(rewrite.rewrittenQuestion);
                thinking.setSubQueries(rewrite.subQueries);
                emitThinking(emitter, "rewrite", Map.of(
                        "rewritten_query", rewrite.rewrittenQuestion,
                        "sub_queries", rewrite.subQueries,
                        "has_coreference", rewrite.hasCoreference));

                // ── Step 3: 混合检索（主问题 + 各子问题独立检索后合并去重） ──
                java.util.List<Document> allFusedDocs = new java.util.ArrayList<>();
                int totalRetrieved = 0;

                HybridRetrievalAdvisor.RetrievalResult mainRetrieval = hybridRetrievalAdvisor.retrieve(rewrite.rewrittenQuestion);
                allFusedDocs.addAll(mainRetrieval.fusedDocs);
                totalRetrieved += mainRetrieval.fusionCount;

                for (String subQuery : rewrite.subQueries) {
                    HybridRetrievalAdvisor.RetrievalResult subRetrieval = hybridRetrievalAdvisor.retrieve(subQuery);
                    allFusedDocs.addAll(subRetrieval.fusedDocs);
                    totalRetrieved += subRetrieval.fusionCount;
                }

                java.util.Map<String, Document> deduped = new java.util.LinkedHashMap<>();
                for (Document doc : allFusedDocs) {
                    String key = (String) doc.getMetadata().getOrDefault("doc_id", doc.getId());
                    Document existing = deduped.get(key);
                    if (existing == null) {
                        deduped.put(key, doc);
                    } else {
                        double curScore = getDocScore(doc);
                        double existScore = getDocScore(existing);
                        if (curScore > existScore) deduped.put(key, doc);
                    }
                }
                java.util.List<Document> fusedDocs = new java.util.ArrayList<>(deduped.values());
                if (fusedDocs.size() > 30) fusedDocs = fusedDocs.subList(0, 30);

                thinking.setRetrievedCount(totalRetrieved);
                emitThinking(emitter, "retrieval", Map.of("count", totalRetrieved));

                if (fusedDocs.isEmpty()) {
                    emitError(emitter, "检索服务暂时不可用，请稍后重试");
                    emitter.complete();
                    return;
                }

                // ── Step 4: 重排序 ──
                RerankAdvisor.RerankResult rerankResult = rerankAdvisor.rerank(rewrite.rewrittenQuestion, fusedDocs);
                thinking.setRerankedCount(rerankResult.rerankedCount);
                emitThinking(emitter, "rerank", Map.of("count", rerankResult.rerankedCount));

                // ── Step 5: 上下文组装 ──
                ContextEnrichAdvisor.EnrichResult enrichResult = contextEnrichAdvisor.enrich(rewrite.rewrittenQuestion, rerankResult.documents);
                final String basePrompt = enrichResult.systemPrompt;
                final String enhancedSystemPrompt = chatHistory.isEmpty()
                        ? basePrompt
                        : basePrompt + "\n\n对话历史背景：\n" + chatHistory;

                // ── Step 6: LLM 流式生成 ──
                final StringBuilder fullAnswer = new StringBuilder();
                final List<Citation> citations = enrichResult.citations; // capture for done event
                Flux<String> tokenFlux = chatClient.prompt()
                        .system(enhancedSystemPrompt)
                        .user(enrichResult.userQuestion)
                        .stream()
                        .content();

                tokenFlux.doOnNext(token -> {
                    fullAnswer.append(token);
                    emitToken(emitter, token);
                }).doOnComplete(() -> {
                    String answer = fullAnswer.toString();

                    // ── Step 7: 安全护栏 ──
                    SafetyGuardAdvisor.GuardResult guard = safetyGuardAdvisor.guard(answer, citations, enhancedSystemPrompt);
                    thinking.setSafetyStatus(guard.status);

                    if (!guard.passed) {
                        emitError(emitter, "回答未通过安全校验: " + guard.reason);
                        emitter.complete();
                        return;
                    }

                    // ── Step 8: 保存记忆 ──
                    try { memoryAdvisor.saveRound(sessionId, question, answer); } catch (Exception e) { /* ok */ }

                    emitDone(emitter, answer, citations, sessionId);
                    emitter.complete();
                }).doOnError(e -> {
                    log.error("LLM 流式生成失败: {}", e.getMessage());
                    emitError(emitter, "生成服务暂时不可用: " + e.getMessage());
                    emitter.complete();
                }).subscribe();

            } catch (Exception e) {
                log.error("SSE 问答异常: {}", e.getMessage(), e);
                emitError(emitter, "生成服务暂时不可用: " + e.getMessage());
                emitter.complete();
            }
        }).start();

        return emitter;
    }

    private void emitThinking(SseEmitter emitter, String stage, Map<String, Object> data) {
        try {
            Map<String, Object> event = new HashMap<>(data);
            event.put("stage", stage);
            emitter.send(SseEmitter.event()
                    .name("thinking")
                    .data(mapper.writeValueAsString(event)));
        } catch (Exception e) {
            log.debug("SSE thinking 事件发送失败: {}", e.getMessage());
        }
    }

    private void emitToken(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event()
                    .name("token")
                    .data(mapper.writeValueAsString(Map.of("token", token))));
        } catch (Exception e) {
            log.debug("SSE token 事件发送失败: {}", e.getMessage());
        }
    }

    private void emitDone(SseEmitter emitter, String answer, List<Citation> citations, String conversationId) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("answer", answer);
            data.put("citations", citations);
            data.put("conversationId", conversationId);
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(mapper.writeValueAsString(data)));
        } catch (Exception e) {
            log.warn("SSE done 事件发送失败: {}", e.getMessage());
        }
    }

    private void emitError(SseEmitter emitter, String error) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(mapper.writeValueAsString(Map.of("error", error))));
        } catch (Exception e) {
            log.warn("SSE error 事件发送失败: {}", e.getMessage());
        }
    }

    private double getDocScore(Document doc) {
        Object rrf = doc.getMetadata().get("rrf_score");
        if (rrf instanceof Number n) return n.doubleValue();
        Object bm25 = doc.getMetadata().get("bm25_score");
        if (bm25 instanceof Number n) return n.doubleValue() / 100.0;
        return 0.0;
    }

    /**
     * 兼容旧版 API：纯向量检索 RAG。
     * @deprecated 使用 {@link #ask(String, String)} 替代
     */
    @Deprecated
    public String buildRagPrompt(String question) {
        List<Document> relevantDocs = vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query(question)
                        .topK(5)
                        .build());

        if (relevantDocs.isEmpty()) return question;

        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        return """
                请根据以下参考资料回答用户的问题。如果参考资料不足以回答问题，请说明无法回答。

                参考资料：
                %s

                用户问题：%s

                请给出简洁准确的回答：
                """.formatted(context, question);
    }
}
