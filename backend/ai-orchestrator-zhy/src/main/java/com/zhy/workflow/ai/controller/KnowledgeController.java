package com.zhy.workflow.ai.controller;

import com.zhy.workflow.ai.service.DocumentLifecycleService;
import com.zhy.workflow.ai.service.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final KnowledgeService knowledgeService;
    private final DocumentLifecycleService documentLifecycleService;

    public KnowledgeController(KnowledgeService knowledgeService,
                                DocumentLifecycleService documentLifecycleService) {
        this.knowledgeService = knowledgeService;
        this.documentLifecycleService = documentLifecycleService;
    }

    /**
     * 获取索引状态（Milvus + ES 健康、文档数量、一致性概览）。
     */
    @GetMapping("/index-status")
    public ResponseEntity<Map<String, Object>> getIndexStatus() {
        try {
            Map<String, Object> data = knowledgeService.getIndexStatus();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取索引状态失败: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 获取文档列表（分页 + 筛选）。
     */
    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> getDocuments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String fileType,
            @RequestParam(defaultValue = "") String indexStatus) {
        try {
            Map<String, Object> data = knowledgeService.getDocuments(page, pageSize, keyword, fileType, indexStatus);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取文档列表失败: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 触发一致性校验（比较 ES 和 Milvus 的 doc_id 集合）。
     */
    @PostMapping("/consistency-check")
    public ResponseEntity<Map<String, Object>> checkConsistency() {
        try {
            Map<String, Object> data = knowledgeService.checkConsistency();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("一致性校验失败: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 触发重索引任务。
     * Body: { "target": "milvus" | "elasticsearch" | "all" }
     */
    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindex(@RequestBody Map<String, String> request) {
        try {
            String target = request.getOrDefault("target", "all");
            if (!target.equals("milvus") && !target.equals("elasticsearch") && !target.equals("all")) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("error", "target 参数无效，可选值: milvus, elasticsearch, all");
                return ResponseEntity.badRequest().body(err);
            }

            Map<String, Object> data = knowledgeService.reindex(target);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("重索引任务失败: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 查询重索引任务进度。
     */
    @GetMapping("/reindex/status")
    public ResponseEntity<Map<String, Object>> getReindexStatus(@RequestParam String taskId) {
        try {
            if (taskId == null || taskId.isBlank()) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("error", "taskId 不能为空");
                return ResponseEntity.badRequest().body(err);
            }
            Map<String, Object> data = knowledgeService.getReindexStatus(taskId);
            if (Boolean.FALSE.equals(data.get("success"))) {
                return ResponseEntity.ok(data);
            }
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询重索引进度失败: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 获取分页的错误日志。
     */
    @GetMapping("/error-logs")
    public ResponseEntity<Map<String, Object>> getErrorLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            Map<String, Object> data = knowledgeService.getErrorLogs(page, pageSize);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取错误日志失败: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 删除文档：级联清理 ES + Milvus + MinIO + MySQL。
     */
    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String docId) {
        try {
            Map<String, Object> data = documentLifecycleService.deleteByDocId(docId);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("删除文档失败: docId={}, {}", docId, e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }

    /**
     * 获取文档详情。
     */
    @GetMapping("/documents/{docId}")
    public ResponseEntity<Map<String, Object>> getDocument(@PathVariable String docId) {
        try {
            var record = documentLifecycleService.getByDocId(docId);
            if (record.isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("error", "文档不存在");
                return ResponseEntity.status(404).body(err);
            }
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", record.get());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取文档详情失败: docId={}, {}", docId, e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }
}
