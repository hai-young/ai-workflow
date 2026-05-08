package com.zhy.workflow.ai.advisor;

import com.zhy.workflow.ai.retrieval.ElasticsearchRetriever;
import com.zhy.workflow.ai.retrieval.RrfFusion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 混合检索 Advisor：Milvus 向量检索 + Elasticsearch BM25 关键词检索 → RRF 融合。
 */
@Component
public class HybridRetrievalAdvisor {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalAdvisor.class);

    private final VectorStore vectorStore;
    private final ElasticsearchRetriever esRetriever;
    private RrfFusion rrfFusion;

    private int milvusTopK = 30;
    private int esTopK = 30;
    private int fusionTopK = 20;
    private int rrfK = 60;

    public HybridRetrievalAdvisor(VectorStore vectorStore, ElasticsearchRetriever esRetriever) {
        this.vectorStore = vectorStore;
        this.esRetriever = esRetriever;
        this.rrfFusion = new RrfFusion(rrfK);
    }

    /**
     * 执行混合检索。
     * @param query 问题文本
     * @return 检索结果 + 元信息
     */
    public RetrievalResult retrieve(String query) {
        RetrievalResult result = new RetrievalResult();

        // 路 1: Milvus 向量检索
        try {
            List<Document> milvusDocs = vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query(query)
                            .topK(milvusTopK)
                            .build());
            for (Document doc : milvusDocs) {
                doc.getMetadata().put("source", "milvus");
            }
            result.milvusDocs = milvusDocs;
            result.milvusCount = milvusDocs.size();
            log.debug("Milvus 检索返回 {} 条", milvusDocs.size());
        } catch (Exception e) {
            log.warn("Milvus 检索失败: {}", e.getMessage());
            result.milvusDocs = Collections.emptyList();
            result.addFallback("milvus_unavailable");
        }

        // 路 2: ES BM25 检索
        try {
            result.esDocs = esRetriever.search(query, esTopK);
            result.esCount = result.esDocs.size();
            result.esAvailable = esRetriever.isAvailable();
            log.debug("ES 检索返回 {} 条", result.esDocs.size());
        } catch (Exception e) {
            log.warn("ES 检索失败: {}", e.getMessage());
            result.esDocs = Collections.emptyList();
            result.addFallback("es_unavailable");
        }

        if (!result.esAvailable) {
            result.addFallback("es_degraded");
        }

        // RRF 融合
        if (!result.milvusDocs.isEmpty() || !result.esDocs.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Document> fused = rrfFusion.fuse(fusionTopK, result.milvusDocs, result.esDocs);
            result.fusedDocs = fused;
            result.fusionCount = fused.size();
        } else {
            result.fusedDocs = Collections.emptyList();
            result.addFallback("both_unavailable");
        }

        return result;
    }

    // ── 配置方法 ──

    public void setMilvusTopK(int milvusTopK) { this.milvusTopK = milvusTopK; }
    public void setEsTopK(int esTopK) { this.esTopK = esTopK; }
    public void setFusionTopK(int fusionTopK) { this.fusionTopK = fusionTopK; }
    public void setRrfK(int rrfK) { this.rrfK = rrfK; this.rrfFusion = new RrfFusion(rrfK); }

    // ── 内部类 ──

    public static class RetrievalResult {
        public List<Document> milvusDocs = Collections.emptyList();
        public List<Document> esDocs = Collections.emptyList();
        public List<Document> fusedDocs = Collections.emptyList();
        public int milvusCount;
        public int esCount;
        public int fusionCount;
        public boolean esAvailable = true;
        private final java.util.List<String> fallbacks = new java.util.ArrayList<>();

        public List<Document> getFusedDocs() { return fusedDocs; }
        public boolean isDegraded() { return !fallbacks.isEmpty(); }
        public List<String> getFallbacks() { return fallbacks; }
        public void addFallback(String f) { fallbacks.add(f); }
    }
}
