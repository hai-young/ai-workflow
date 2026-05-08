package com.zhy.workflow.ai.advisor;

import com.zhy.workflow.ai.service.ConversationMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 对话记忆 Advisor：在检索前注入短期对话记忆上下文。
 * 职责：加载记忆 → 判断是否需压缩 → 为后续 Advisor 提供增强上下文。
 */
@Component
public class MemoryAdvisor {

    private static final Logger log = LoggerFactory.getLogger(MemoryAdvisor.class);

    private final ConversationMemoryService memoryService;
    private final ChatClient chatClient;
    private int recentRounds = 5;

    public MemoryAdvisor(ConversationMemoryService memoryService, ChatClient.Builder chatClientBuilder) {
        this.memoryService = memoryService;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 加载记忆上下文。
     * @param sessionId 会话 ID
     * @return 记忆结果（含上下文文本 + 是否需压缩）
     */
    public MemoryResult loadMemory(String sessionId) {
        MemoryResult result = new MemoryResult();
        result.sessionId = sessionId;

        try {
            result.needsCompression = memoryService.needsCompression(sessionId);
            result.context = memoryService.getContext(sessionId, recentRounds);

            if (result.needsCompression) {
                log.debug("会话 {} 轮数超阈值，需压缩", sessionId);
            }
        } catch (Exception e) {
            log.warn("记忆加载失败: sessionId={}, {}", sessionId, e.getMessage());
        }

        return result;
    }

    /**
     * 保存本轮对话到记忆，并在需要时触发压缩。
     */
    public void saveRound(String sessionId, String question, String answer) {
        try {
            memoryService.saveRound(sessionId, question, answer);

            // 触发摘要压缩
            if (memoryService.needsCompression(sessionId)) {
                try {
                    String context = memoryService.getContext(sessionId, 10);
                    String summaryPrompt = String.format("""
                        请将以下对话历史压缩为 200 字以内的摘要，保留所有关键事实和技术细节。
                        只输出摘要文本，不要包含其他内容。

                        对话历史：
                        %s
                        """, context);
                    String summary = chatClient.prompt().user(summaryPrompt).call().content();
                    if (summary != null && !summary.isBlank()) {
                        memoryService.compress(sessionId, summary.trim());
                        log.info("对话压缩完成: sessionId={}, rounds>=10, summaryLen={}", sessionId, summary.length());
                    }
                } catch (Exception e) {
                    log.warn("对话压缩失败: sessionId={}, {}", sessionId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("记忆保存失败: sessionId={}", sessionId);
        }
    }

    public void setRecentRounds(int recentRounds) { this.recentRounds = recentRounds; }

    // ── 内部类 ──

    public static class MemoryResult {
        public String sessionId;
        public String context = "";
        public boolean needsCompression;
    }
}
