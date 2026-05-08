package com.zhy.workflow.ai.retrieval;

import org.springframework.ai.document.Document;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Reciprocal Rank Fusion — 多路检索结果融合。
 * 公式: RRF_score(d) = Σ 1/(k + rank_i(d))
 */
public class RrfFusion {

    private final int k;

    public RrfFusion(int k) {
        this.k = k;
    }

    public RrfFusion() {
        this(60);
    }

    /**
     * 融合多路检索结果，按 RRF 分数降序返回。
     * @param resultLists 各路检索结果列表（每路已按相关性从高到低排序）
     * @param topK 融合后保留数量
     */
    @SafeVarargs
    public final List<Document> fuse(int topK, List<Document>... resultLists) {
        Map<String, Document> docMap = new LinkedHashMap<>();
        Map<String, Double> scoreMap = new HashMap<>();

        for (List<Document> resultList : resultLists) {
            for (int rank = 0; rank < resultList.size(); rank++) {
                Document doc = resultList.get(rank);
                String docId = doc.getId() != null ? doc.getId() : String.valueOf(System.identityHashCode(doc));
                docMap.putIfAbsent(docId, doc);
                double rrfScore = scoreMap.getOrDefault(docId, 0.0) + 1.0 / (k + rank + 1);
                scoreMap.put(docId, rrfScore);
            }
        }

        return docMap.keySet().stream()
                .sorted(Comparator.comparingDouble(scoreMap::get).reversed())
                .limit(topK)
                .peek(docId -> {
                    Document doc = docMap.get(docId);
                    doc.getMetadata().put("rrf_score", scoreMap.get(docId));
                })
                .map(docMap::get)
                .collect(Collectors.toList());
    }
}
