package com.zhy.workflow.ai.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import com.zhy.workflow.ai.entity.DocumentRecord;
import com.zhy.workflow.ai.repository.DocumentRepository;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.dml.DeleteParam;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DocumentLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(DocumentLifecycleService.class);
    private static final String ES_INDEX = "knowledge_base";
    private static final String MILVUS_COLLECTION = "workflow_vector_store";

    private final DocumentRepository documentRepository;
    private final ElasticsearchClient esClient;
    private final MilvusServiceClient milvusClient;
    private final MinioClient minioClient;

    @org.springframework.beans.factory.annotation.Value("${minio.bucket-documents:rag-documents}")
    private String minioBucket;

    public DocumentLifecycleService(DocumentRepository documentRepository,
                                     ElasticsearchClient esClient,
                                     MilvusServiceClient milvusClient,
                                     MinioClient minioClient) {
        this.documentRepository = documentRepository;
        this.esClient = esClient;
        this.milvusClient = milvusClient;
        this.minioClient = minioClient;
    }

    /**
     * 文档上传完成后注册到 MySQL，并更新各索引状态为 indexed。
     */
    public void register(String docId, String fileName, String fileType, long fileSize,
                         String fileHash, int chunkCount, String minioPath) {
        DocumentRecord record = new DocumentRecord();
        record.setDocId(docId);
        record.setFileName(fileName);
        record.setFileType(fileType);
        record.setFileSize(fileSize);
        record.setFileHash(fileHash);
        record.setChunkCount(chunkCount);
        record.setMinioPath(minioPath);
        record.setEsStatus("indexed");
        record.setMilvusStatus("indexed");
        documentRepository.save(record);
        log.info("文档注册完成: docId={}, chunks={}", docId, chunkCount);
    }

    /**
     * 级联删除文档：ES → Milvus → MinIO → MySQL。
     * 每步失败记录日志但不中断后续步骤。
     */
    public Map<String, Object> deleteByDocId(String docId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("docId", docId);
        int deletedChunks = 0;

        // 1. ES 按条件删除
        try {
            DeleteByQueryResponse esResp = esClient.deleteByQuery(d -> d
                    .index(ES_INDEX)
                    .query(q -> q.term(t -> t.field("doc_id").value(docId))));
            Long deleted = esResp.deleted();
            deletedChunks = deleted != null ? deleted.intValue() : 0;
            log.info("ES 文档已删除: docId={}, deleted={}", docId, deletedChunks);
        } catch (Exception e) {
            log.error("ES 删除失败: docId={}, {}", docId, e.getMessage());
            result.put("esError", e.getMessage());
        }

        // 2. Milvus 按 JSON metadata 字段表达式删除
        try {
            milvusClient.delete(DeleteParam.newBuilder()
                    .withCollectionName(MILVUS_COLLECTION)
                    .withExpr("metadata[\"doc_id\"] == \"" + docId + "\"")
                    .build());
            log.info("Milvus 文档已删除: docId={}", docId);
        } catch (Exception e) {
            log.error("Milvus 删除失败: docId={}, {}", docId, e.getMessage());
            result.put("milvusError", e.getMessage());
        }

        // 3. MinIO 删除原始文件
        try {
            DocumentRecord record = documentRepository.findByDocId(docId).orElse(null);
            if (record != null && record.getMinioPath() != null) {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(record.getMinioPath())
                        .build());
                log.info("MinIO 文件已删除: docId={}, path={}", docId, record.getMinioPath());
            }
        } catch (Exception e) {
            log.error("MinIO 删除失败: docId={}, {}", docId, e.getMessage());
            result.put("minioError", e.getMessage());
        }

        // 4. MySQL 删除记录
        try {
            documentRepository.deleteByDocId(docId);
        } catch (Exception e) {
            log.error("MySQL 文档记录删除失败: docId={}, {}", docId, e.getMessage());
            result.put("mysqlError", e.getMessage());
        }

        result.put("deletedChunks", deletedChunks);
        return result;
    }

    /**
     * 获取文档详情（含 MySQL 元信息）。
     */
    public Optional<DocumentRecord> getByDocId(String docId) {
        return documentRepository.findByDocId(docId);
    }

    /**
     * 分页获取文档列表（MySQL 为主数据源）。
     */
    public Page<DocumentRecord> listDocuments(int page, int pageSize) {
        return documentRepository.findAll(
                PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending()));
    }

    /**
     * 更新文档索引状态。
     */
    public void updateStatus(String docId, String esStatus, String milvusStatus) {
        documentRepository.findByDocId(docId).ifPresent(record -> {
            if (esStatus != null) record.setEsStatus(esStatus);
            if (milvusStatus != null) record.setMilvusStatus(milvusStatus);
            documentRepository.save(record);
        });
    }
}
