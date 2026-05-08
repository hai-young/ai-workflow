package com.zhy.workflow.ai.retrieval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class ElasticsearchRetriever {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchRetriever.class);
    static final String INDEX_NAME = "knowledge_base";

    private final ElasticsearchClient esClient;
    private volatile boolean available = true;
    private volatile boolean indexEnsured = false;

    public ElasticsearchRetriever(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    /**
     * BM25 关键词检索。首次调用时自动创建索引。
     */
    public List<Document> search(String query, int topK) {
        if (!available) return Collections.emptyList();
        ensureIndex();
        try {
            SearchResponse<Map> response = esClient.search(s -> s
                            .index(INDEX_NAME)
                            .query(q -> q
                                    .multiMatch(mm -> mm
                                            .query(query)
                                            .fields("content", "title", "summary")))
                            .size(topK),
                    Map.class);

            List<Document> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source == null) continue;

                String content = (String) source.getOrDefault("content", "");
                Document doc = new Document(content);
                doc.getMetadata().put("source", "elasticsearch");
                doc.getMetadata().put("doc_id", source.getOrDefault("doc_id", hit.id()));
                doc.getMetadata().put("file_name", source.getOrDefault("file_name", ""));
                doc.getMetadata().put("bm25_score", hit.score() != null ? hit.score() : 0.0);
                doc.getMetadata().put("_es_id", hit.id());
                results.add(doc);
            }
            return results;
        } catch (Exception e) {
            log.warn("Elasticsearch 检索失败，降级跳过: {}", e.getMessage());
            available = false;
            return Collections.emptyList();
        }
    }

    /**
     * 索引文档 chunk 到 ES。
     */
    public void indexDocument(String docId, String chunkId, String content,
                              String fileName, String fileType, String title) {
        if (!available) {
            log.warn("ES 不可用，跳过索引写入: docId={}, chunkId={}", docId, chunkId);
            return;
        }
        ensureIndex();
        try {
            esClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(chunkId)
                    .document(Map.of(
                            "doc_id", docId,
                            "chunk_id", chunkId,
                            "title", title != null ? title : fileName,
                            "content", content,
                            "file_name", fileName,
                            "file_type", fileType,
                            "upload_time", java.time.LocalDateTime.now().toString()
                    )));
        } catch (Exception e) {
            log.warn("ES 索引写入失败 (docId={}, chunkId={}): {}", docId, chunkId, e.getMessage());
        }
    }

    /**
     * 确保索引存在，不存在时自动创建（含 IK 中文分词器配置）。
     */
    public synchronized void ensureIndex() {
        if (indexEnsured) return;
        try {
            boolean exists = esClient.indices().exists(ExistsRequest.of(e -> e.index(INDEX_NAME))).value();
            if (!exists) {
                esClient.indices().create(CreateIndexRequest.of(c -> c
                        .index(INDEX_NAME)
                        .settings(s -> s
                                .numberOfShards("1")
                                .numberOfReplicas("0")
                                .analysis(a -> a
                                        .analyzer("ik_smart_analyzer", az -> az
                                                .custom(cu -> cu.tokenizer("ik_smart")))
                                        .analyzer("ik_max_word_analyzer", az -> az
                                                .custom(cu -> cu.tokenizer("ik_max_word")))))
                        .mappings(m -> m
                                .properties("doc_id", p -> p.keyword(k -> k))
                                .properties("chunk_id", p -> p.keyword(k -> k))
                                .properties("title", p -> p.text(t -> t
                                        .analyzer("ik_max_word_analyzer")
                                        .fields("raw", f -> f.keyword(k -> k))))
                                .properties("content", p -> p.text(t -> t
                                        .analyzer("ik_max_word_analyzer")))
                                .properties("summary", p -> p.text(t -> t
                                        .analyzer("ik_smart_analyzer")))
                                .properties("file_type", p -> p.keyword(k -> k))
                                .properties("file_name", p -> p.keyword(k -> k))
                                .properties("upload_time", p -> p.date(d -> d
                                        .format("yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'T'HH:mm:ss||yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS||epoch_millis")))
                                .properties("milvus_id", p -> p.keyword(k -> k))
                                .properties("metadata_str", p -> p.text(t -> t.index(false))))
                ));
                log.info("ES 索引已自动创建: {} (IK 分词器已配置)", INDEX_NAME);
            }
            indexEnsured = true;
        } catch (Exception e) {
            // IK 插件可能未安装，降级使用默认 standard 分词器
            log.warn("ES 索引自动创建失败（可能 IK 插件未安装），将使用动态映射: {}", e.getMessage());
            indexEnsured = true; // 不再重试，避免阻塞
        }
    }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
