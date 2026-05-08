package com.zhy.workflow.ai.controller;

import com.zhy.workflow.ai.dto.AskRequest;
import com.zhy.workflow.ai.dto.AskResponse;
import com.zhy.workflow.ai.service.ConversationMemoryService;
import com.zhy.workflow.ai.service.RagService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;
    private final ConversationMemoryService memoryService;

    public RagController(RagService ragService, ConversationMemoryService memoryService) {
        this.ragService = ragService;
        this.memoryService = memoryService;
    }

    /**
     * 文档上传（内部已支持 Milvus + ES 双写）。
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            ragService.uploadDocument(file);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "文档已上传并索引成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 智能 RAG 问答（新版，支持 SSE 流式和非流式）。
     * 当 request.stream=true 时返回 SSE 事件流，否则返回 JSON。
     */
    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody AskRequest request) {
        String question = request.getQuestion();
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(AskResponse.fail("参数无效：问题内容为空"));
        }
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        if (request.isStream()) {
            SseEmitter emitter = ragService.askStream(question, sessionId);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(emitter);
        }

        AskResponse response = ragService.ask(question, sessionId);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.internalServerError().body(response);
    }

    /**
     * 向后兼容旧版 API（Map 请求体）。
     */
    @PostMapping(value = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE,
            headers = "X-Api-Version=1")
    public ResponseEntity<Map<String, Object>> askLegacy(@RequestBody Map<String, String> request) {
        String question = request.getOrDefault("question", "").trim();
        if (question.isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", "参数无效：问题内容为空");
            return ResponseEntity.badRequest().body(err);
        }

        // 兼容旧版：仅返回 answer 字段
        AskResponse response = ragService.ask(question, UUID.randomUUID().toString());
        Map<String, Object> legacyResponse = new HashMap<>();
        if (response.isSuccess()) {
            legacyResponse.put("success", true);
            legacyResponse.put("answer", response.getAnswer());
        } else {
            legacyResponse.put("success", false);
            legacyResponse.put("error", response.getError());
        }
        return ResponseEntity.ok(legacyResponse);
    }

    /**
     * SSE 流式问答端点（独立路径，简化接入）。
     */
    @PostMapping("/ask/stream")
    public ResponseEntity<SseEmitter> askStream(@RequestBody AskRequest request) {
        String question = request.getQuestion();
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        SseEmitter emitter = ragService.askStream(question, sessionId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

    // ── 对话管理 ──

    /**
     * 获取所有对话列表。
     */
    @GetMapping("/conversations")
    public ResponseEntity<Map<String, Object>> listConversations() {
        var sessions = memoryService.listSessions();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", sessions.size());
        result.put("sessions", sessions);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取指定对话详情（完整历史）。
     */
    @GetMapping("/conversations/{sessionId}")
    public ResponseEntity<Map<String, Object>> getConversation(@PathVariable String sessionId) {
        var detail = memoryService.getSessionDetail(sessionId);
        if (detail.history.isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("error", "会话不存在或已过期");
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("sessionId", detail.sessionId);
        result.put("history", detail.history);
        return ResponseEntity.ok(result);
    }

    /**
     * 删除指定对话。
     */
    @DeleteMapping("/conversations/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteConversation(@PathVariable String sessionId) {
        memoryService.clear(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "会话已删除");
        return ResponseEntity.ok(result);
    }

    /**
     * 清空所有对话。
     */
    @DeleteMapping("/conversations")
    public ResponseEntity<Map<String, Object>> clearAllConversations() {
        int count = memoryService.clearAll();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "已清空 " + count + " 个会话");
        return ResponseEntity.ok(result);
    }

    /**
     * 获取默认的推荐问题。
     */
    @GetMapping("/suggestions")
    public ResponseEntity<Map<String, Object>> getSuggestions() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("suggestions", java.util.List.of(
                "如何配置JWT令牌的有效期？",
                "Spring AI 和 LangChain 的区别？",
                "Milvus 如何优化检索性能？"
        ));
        return ResponseEntity.ok(result);
    }
}
