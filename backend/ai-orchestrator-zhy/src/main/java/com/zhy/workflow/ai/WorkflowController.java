package com.zhy.workflow.ai;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    private final WorkflowOrchestrator orchestrator;

    public WorkflowController(WorkflowOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run(@RequestBody(required = false) Map<String, String> request) {
        // 从请求体中获取 input 字段，如果没有则使用默认提示
        String userInput = request != null && request.containsKey("input")
                ? request.get("input")
                : "用中文写一句励志短句";
        try {
            String result = orchestrator.executeWorkflow(userInput);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("result", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 流式输出接口
     * 示例请求: POST /api/workflow/stream
     * Content-Type: application/json
     * Body: {"input": "什么是人工智能？"}
     *
     * 响应类型: text/event-stream
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody(required = false) Map<String, String> request) {
        String userInput = request != null && request.containsKey("input")
                ? request.get("input")
                : "用中文写一句励志短句";
        return orchestrator.streamAIResponse(userInput);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}