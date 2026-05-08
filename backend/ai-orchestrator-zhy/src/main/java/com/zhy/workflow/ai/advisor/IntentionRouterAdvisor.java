package com.zhy.workflow.ai.advisor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * 意图路由 Advisor：区分闲聊、知识问答、操作指令。
 * 非问答请求不触发检索链路，直接由 LLM 回复。
 */
@Component
public class IntentionRouterAdvisor {

    private static final Logger log = LoggerFactory.getLogger(IntentionRouterAdvisor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ChatClient chatClient;

    public IntentionRouterAdvisor(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 分析用户意图。
     * @param question 用户问题
     * @return 路由结果
     */
    public RouteResult route(String question) {
        RouteResult result = new RouteResult();
        result.originalQuestion = question;

        try {
            String prompt = buildIntentPrompt(question);
            String llmResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            Map<String, Object> parsed = parseResponse(llmResponse);
            String intent = ((String) parsed.getOrDefault("intent", "qa")).toLowerCase(Locale.ROOT);
            result.intent = intent;
            result.confidence = ((Number) parsed.getOrDefault("confidence", 0.5)).doubleValue();

            // 非问答类型生成直接回复
            switch (result.intent) {
                case "chat" -> {
                    result.skipRetrieval = true;
                    result.directResponse = (String) parsed.getOrDefault("response", "");
                }
                case "command" -> {
                    result.skipRetrieval = true;
                    result.directResponse = (String) parsed.getOrDefault("response",
                            "抱歉，我目前无法执行该操作。请尝试通过系统界面操作。");
                }
                default -> result.skipRetrieval = false; // qa
            }

            log.debug("意图路由: \"{}\" → {} (confidence={})",
                    question.substring(0, Math.min(40, question.length())),
                    result.intent, result.confidence);

        } catch (Exception e) {
            log.warn("意图路由失败，默认按知识问答处理: {}", e.getMessage());
            result.intent = "qa";
            result.skipRetrieval = false;
            result.degraded = true;
        }

        return result;
    }

    private String buildIntentPrompt(String question) {
        return String.format("""
                你是一个意图分类助手。分析用户输入，判断意图类型：

                1. chat — 闲聊、打招呼、感谢、告别等无信息需求的对话
                2. qa — 知识问答，需要从知识库检索信息才能回答的问题
                3. command — 操作指令，要求执行某个操作（如"删除文档"、"重建索引"）

                请严格按以下 JSON 格式输出，不要包含其他文字：
                {
                  "intent": "chat|qa|command",
                  "confidence": 0.0-1.0,
                  "response": "如果是chat或command类型，给出简短友好的回复"
                }

                用户输入：%s
                请输出JSON：""", question);
    }

    private Map<String, Object> parseResponse(String response) {
        try {
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            }
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("意图路由 JSON 解析失败: {}", e.getMessage());
            return Map.of("intent", "qa", "confidence", 0.3);
        }
    }

    // ── 内部类 ──

    public static class RouteResult {
        public String originalQuestion = "";
        public String intent = "qa";
        public double confidence;
        public boolean skipRetrieval;
        public String directResponse;
        public boolean degraded;
    }
}
