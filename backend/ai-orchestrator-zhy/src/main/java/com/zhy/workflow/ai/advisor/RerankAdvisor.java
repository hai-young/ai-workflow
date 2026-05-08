package com.zhy.workflow.ai.advisor;

import com.zhy.workflow.ai.client.RerankerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 重排序 Advisor：对融合后的候选文档调用 BGE-Reranker-v2-m3 精排。
 */
@Component
public class RerankAdvisor {

    private static final Logger log = LoggerFactory.getLogger(RerankAdvisor.class);

    private final RerankerClient rerankerClient;
    private int topK = 5;
    private double minScore = 0.3;

    public RerankAdvisor(RerankerClient rerankerClient) {
        this.rerankerClient = rerankerClient;
    }

    /**
     * 执行重排序。
     * @param query 用户问题
     * @param documents 候选文档列表（来自混合检索）
     * @return 重排序结果 + 元信息
     */
    public RerankResult rerank(String query, List<Document> documents) {
        RerankResult result = new RerankResult();

        if (documents == null || documents.isEmpty()) {
            result.documents = List.of();
            return result;
        }

        List<Document> ranked = rerankerClient.rerank(query, documents, Math.min(topK, documents.size()));
        result.documents = ranked;
        result.rerankedCount = ranked.size();
        result.rerankerAvailable = rerankerClient.isAvailable();

        if (!result.rerankerAvailable) {
            result.degraded = true;
            result.status = "degraded";
            // 取原始 RRF 结果前 topK 条
            if (ranked.isEmpty()) {
                result.documents = documents.subList(0, Math.min(topK, documents.size()));
                result.rerankedCount = result.documents.size();
            }
            log.debug("Reranker 不可用，降级使用 RRF 分数排序");
        } else {
            result.status = "success";
            // 过滤低分文档（但至少保留 1 条）
            List<Document> filtered = ranked.stream()
                    .filter(d -> {
                        Object score = d.getMetadata().get("rerank_score");
                        return score != null && ((Number) score).doubleValue() >= minScore;
                    })
                    .toList();
            if (!filtered.isEmpty()) {
                result.documents = filtered;
                result.rerankedCount = filtered.size();
            }
        }

        return result;
    }

    public void setTopK(int topK) { this.topK = topK; }
    public void setMinScore(double minScore) { this.minScore = minScore; }

    public static class RerankResult {
        public List<Document> documents = List.of();
        public int rerankedCount;
        public boolean rerankerAvailable = true;
        public boolean degraded;
        public String status = "success";
    }
}
