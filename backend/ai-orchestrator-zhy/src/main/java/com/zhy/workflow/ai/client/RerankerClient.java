package com.zhy.workflow.ai.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * BGE-Reranker-v2-m3 HTTP 客户端。
 * 服务不可用时自动降级，返回原始顺序。
 */
@Component
public class RerankerClient {

    private static final Logger log = LoggerFactory.getLogger(RerankerClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String rerankerUrl;
    private final int timeoutMs;
    private final HttpClient httpClient;
    private volatile boolean available = true;

    public RerankerClient(
            @Value("${reranker.url:http://localhost:6006/v1/rerank}") String rerankerUrl,
            @Value("${reranker.timeout-ms:2000}") int timeoutMs) {
        this.rerankerUrl = rerankerUrl;
        this.timeoutMs = timeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    /**
     * 对候选文档进行重排序。
     * @param query 用户问题
     * @param documents 候选文档列表
     * @param topK 精排后保留数量
     * @return 重排序后的文档列表（Reranker 不可用时返回原始前 topK 条）
     */
    public List<Document> rerank(String query, List<Document> documents, int topK) {
        if (!available || documents.isEmpty()) {
            return documents.subList(0, Math.min(topK, documents.size()));
        }
        if (documents.size() <= topK) {
            return new ArrayList<>(documents);
        }

        try {
            // 构造请求
            List<String> texts = documents.stream().map(Document::getText).toList();
            String body = mapper.writeValueAsString(Map.of(
                    "query", query,
                    "documents", texts));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rerankerUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Reranker 返回非 200 状态码: {}，降级跳过", response.statusCode());
                available = false;
                return documents.subList(0, Math.min(topK, documents.size()));
            }

            RerankResponse rr = mapper.readValue(response.body(), RerankResponse.class);
            List<RerankResult> results = rr.results;
            if (results == null || results.isEmpty()) {
                return documents.subList(0, Math.min(topK, documents.size()));
            }

            // 按 relevance_score 降序，取 topK
            return results.stream()
                    .sorted(Comparator.comparingDouble(RerankResult::getRelevanceScore).reversed())
                    .limit(topK)
                    .map(r -> {
                        Document doc = documents.get(r.getIndex());
                        doc.getMetadata().put("rerank_score", r.getRelevanceScore());
                        return doc;
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("Reranker 调用失败，降级跳过: {}", e.getMessage());
            available = false;
            return documents.subList(0, Math.min(topK, documents.size()));
        }
    }

    public boolean isAvailable() { return available; }

    // ── JSON 映射类 ──

    public static class RerankResponse {
        public List<RerankResult> results;
    }

    public static class RerankResult {
        public int index;
        @JsonProperty("relevance_score")
        public double relevanceScore;

        public int getIndex() { return index; }
        public double getRelevanceScore() { return relevanceScore; }
    }
}
