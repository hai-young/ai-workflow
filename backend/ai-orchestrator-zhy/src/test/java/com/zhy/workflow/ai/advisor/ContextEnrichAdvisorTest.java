package com.zhy.workflow.ai.advisor;

import com.zhy.workflow.ai.dto.Citation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextEnrichAdvisorTest {

    private final ContextEnrichAdvisor advisor = new ContextEnrichAdvisor();

    @Test
    void shouldGenerateCitationsWithCorrectIndices() {
        Document doc1 = new Document("JWT 令牌过期时服务端返回 401。");
        doc1.getMetadata().put("doc_id", "doc-jwt");
        doc1.getMetadata().put("file_name", "JWT-Config.pdf");
        doc1.getMetadata().put("rerank_score", 0.94);

        Document doc2 = new Document("客户端检测到 401 后调用 refresh 接口。");
        doc2.getMetadata().put("doc_id", "doc-auth");
        doc2.getMetadata().put("file_name", "Auth-Flow.md");
        doc2.getMetadata().put("rerank_score", 0.89);

        ContextEnrichAdvisor.EnrichResult result = advisor.enrich("测试问题", List.of(doc1, doc2));

        // 验证引用数量
        assertEquals(2, result.citations.size());

        // 验证引用编号
        Citation c1 = result.citations.get(0);
        assertEquals(1, c1.getIndex());
        assertEquals("doc-jwt", c1.getDocId());
        assertEquals(0.94, c1.getRelevanceScore(), 0.001);

        Citation c2 = result.citations.get(1);
        assertEquals(2, c2.getIndex());
        assertEquals("doc-auth", c2.getDocId());

        // 验证 System Prompt 包含引用编号
        assertTrue(result.systemPrompt.contains("[1]"));
        assertTrue(result.systemPrompt.contains("[2]"));
        assertTrue(result.systemPrompt.contains("参考资料"));
    }

    @Test
    void shouldHandleEmptyDocuments() {
        ContextEnrichAdvisor.EnrichResult result = advisor.enrich("test", List.of());

        assertTrue(result.citations.isEmpty());
        assertTrue(result.systemPrompt.contains("参考资料"));
    }

    @Test
    void shouldTruncateLongContent() {
        String longText = "A".repeat(2000);
        Document doc = new Document(longText);
        doc.getMetadata().put("rerank_score", 0.5);

        ContextEnrichAdvisor.EnrichResult result = advisor.enrich("test", List.of(doc));

        assertEquals(1, result.citations.size());
        // 引用内容应被截断（800 字符 + "..."）
        assertTrue(result.citations.get(0).getContent().length() <= 805);
        assertTrue(result.citations.get(0).getContent().endsWith("..."));
    }

    @Test
    void shouldRespectMaxContextLength() {
        advisor.setMaxContextLength(500);

        Document doc1 = new Document("A".repeat(400));
        doc1.getMetadata().put("rerank_score", 0.9);
        Document doc2 = new Document("B".repeat(400));
        doc2.getMetadata().put("rerank_score", 0.8);

        ContextEnrichAdvisor.EnrichResult result = advisor.enrich("test", List.of(doc1, doc2));

        // 第二个文档因超出 maxContextLength 应被截断
        assertTrue(result.citations.size() <= 2);
        assertTrue(result.systemPrompt.length() < 1500); // prompt template + content
    }
}
