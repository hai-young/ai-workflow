package com.zhy.workflow.ai.advisor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 查询重写与拆解 Advisor。
 * 职责：指代消解 → 同义扩展 → 多跳拆解。
 * 使用轻量 prompt 调用 LLM 完成改写。
 */
@Component
public class QueryRewriteAdvisor {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriteAdvisor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ChatClient chatClient;

    public QueryRewriteAdvisor(ChatClient.Builder chatClientBuilder) {
        // 使用独立的 ChatClient 实例，可独立配置低温度
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 执行查询重写。
     * @param originalQuestion 原始问题
     * @param chatHistory 对话历史文本（可选，用于指代消解）
     * @return 重写结果
     */
    public RewriteResult rewrite(String originalQuestion, String chatHistory) {
        RewriteResult result = new RewriteResult();
        result.originalQuestion = originalQuestion;

        try {
            String rewritePrompt = buildRewritePrompt(originalQuestion, chatHistory);
            String llmResponse = chatClient.prompt()
                    .user(rewritePrompt)
                    .call()
                    .content();

            // 解析 LLM 返回的 JSON
            Map<String, Object> parsed = parseResponse(llmResponse);
            result.rewrittenQuestion = (String) parsed.getOrDefault("rewritten_question", originalQuestion);
            result.hasCoreference = (boolean) parsed.getOrDefault("has_coreference", false);
            result.subQueries = (List<String>) parsed.getOrDefault("sub_queries", Collections.emptyList());

            log.debug("查询重写: \"{}\" → \"{}\"",
                    originalQuestion.substring(0, Math.min(50, originalQuestion.length())),
                    result.rewrittenQuestion.substring(0, Math.min(50, result.rewrittenQuestion.length())));
        } catch (Exception e) {
            log.warn("查询重写失败，使用原始问题: {}", e.getMessage());
            result.rewrittenQuestion = originalQuestion;
            result.rewriteFailed = true;
        }

        return result;
    }

    private String buildRewritePrompt(String question, String chatHistory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                你是一个查询改写助手。根据对话历史和用户当前问题，完成以下任务：
                1. 指代消解：将"它"、"这个"等指代词替换为明确的实体名
                2. 多跳拆解：如果问题需要多步推理，拆解为 2-4 个子问题
                3. 输出改写后的问题

                请严格按以下 JSON 格式输出，不要包含其他文字：
                {
                  "rewritten_question": "改写后的问题",
                  "has_coreference": true/false,
                  "sub_queries": ["子问题1", "子问题2"]
                }

                """);

        if (chatHistory != null && !chatHistory.isEmpty()) {
            prompt.append("对话历史：\n").append(chatHistory).append("\n");
        }
        prompt.append("用户当前问题：").append(question);
        prompt.append("\n请输出JSON：");

        return prompt.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResponse(String response) {
        try {
            // 清理可能包裹的 ```json ... ``` 标记
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            }
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("JSON 解析失败，降级使用原始问题: {}", e.getMessage());
            return Map.of("rewritten_question", response, "has_coreference", false, "sub_queries", List.of());
        }
    }

    // ── 内部类 ──

    public static class RewriteResult {
        public String originalQuestion = "";
        public String rewrittenQuestion = "";
        public boolean hasCoreference;
        public List<String> subQueries = Collections.emptyList();
        public boolean rewriteFailed;
    }
}
