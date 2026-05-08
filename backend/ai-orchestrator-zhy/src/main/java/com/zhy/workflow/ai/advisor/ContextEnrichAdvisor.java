package com.zhy.workflow.ai.advisor;

import com.zhy.workflow.ai.dto.Citation;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 上下文组装 Advisor：将精排后的文档拼接为 LLM System Prompt，
 * 并为每个文档分配引用编号。
 */
@Component
public class ContextEnrichAdvisor {

    private int maxContextLength = 4000;

    /**
     * 构建带引用的 RAG Prompt，同时生成引用映射表。
     */
    public EnrichResult enrich(String question, List<Document> documents) {
        List<Citation> citations = new ArrayList<>();
        StringBuilder contextBuilder = new StringBuilder();
        int citationIndex = 1;

        for (Document doc : documents) {
            String text = doc.getText();
            if (text == null || text.isEmpty()) continue;

            // 截断过长的单个文档
            if (text.length() > 800) {
                text = text.substring(0, 800) + "...";
            }

            // 检查总上下文长度限制
            if (contextBuilder.length() + text.length() > maxContextLength) break;

            String docId = (String) doc.getMetadata().getOrDefault("doc_id", "unknown");
            String fileName = (String) doc.getMetadata().getOrDefault("file_name", "");
            Object rerankScore = doc.getMetadata().get("rerank_score");
            if (!(rerankScore instanceof Number)) {
                rerankScore = doc.getMetadata().get("rrf_score");
            }
            double score = rerankScore instanceof Number ? ((Number) rerankScore).doubleValue() : 0.0;

            // 构建引用片段
            contextBuilder.append("[").append(citationIndex).append("] ").append(text).append("\n\n");

            citations.add(new Citation(citationIndex, docId, fileName, text, score));
            citationIndex++;
        }

        // 构造 System Prompt
        String systemPrompt = String.format("""
                你是一个基于知识库的专业AI助手。请根据以下参考资料回答用户的问题。
                如果参考资料不足以回答问题，请明确说明无法回答，不要编造信息。

                要求：
                1. 每个事实性陈述后标注引用来源编号，如 [1][2]
                2. 回答简洁准确，避免冗余

                参考资料：
                %s
                """, contextBuilder.toString());

        return new EnrichResult(systemPrompt, question, citations);
    }

    public void setMaxContextLength(int maxContextLength) { this.maxContextLength = maxContextLength; }

    public static class EnrichResult {
        public final String systemPrompt;
        public final String userQuestion;
        public final List<Citation> citations;

        public EnrichResult(String systemPrompt, String userQuestion, List<Citation> citations) {
            this.systemPrompt = systemPrompt;
            this.userQuestion = userQuestion;
            this.citations = citations;
        }
    }
}
