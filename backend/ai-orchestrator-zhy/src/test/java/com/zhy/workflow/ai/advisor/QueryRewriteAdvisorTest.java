package com.zhy.workflow.ai.advisor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryRewriteAdvisorTest {

    @Mock private ChatClient.Builder chatClientBuilder;
    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callResponseSpec;

    private QueryRewriteAdvisor advisor;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        advisor = new QueryRewriteAdvisor(chatClientBuilder);
    }

    @Test
    void shouldRewriteWithCoreferenceResolution() {
        String llmResponse = """
                {
                  "rewritten_question": "JWT令牌过期后会发生什么？",
                  "has_coreference": true,
                  "sub_queries": []
                }""";

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(llmResponse);

        String chatHistory = "用户: JWT令牌的有效期怎么设置？\nAI: 可在 application.yml 中配置 jwt.expiration...\n";
        QueryRewriteAdvisor.RewriteResult result = advisor.rewrite("它过期后会怎样？", chatHistory);

        assertEquals("JWT令牌过期后会发生什么？", result.rewrittenQuestion);
        assertTrue(result.hasCoreference);
        assertFalse(result.rewriteFailed);
    }

    @Test
    void shouldDecomposeMultiHopQuestion() {
        String llmResponse = """
                {
                  "rewritten_question": "Milvus和Elasticsearch在RAG场景下各自的优缺点是什么？",
                  "has_coreference": false,
                  "sub_queries": [
                    "Milvus在RAG场景下的优缺点",
                    "Elasticsearch在RAG场景下的优缺点"
                  ]
                }""";

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(llmResponse);

        QueryRewriteAdvisor.RewriteResult result = advisor.rewrite(
                "Milvus 和 Elasticsearch 在 RAG 场景下各自的优缺点是什么？", "");

        assertFalse(result.rewriteFailed);
        assertEquals(2, result.subQueries.size());
        assertTrue(result.subQueries.get(0).contains("Milvus"));
        assertTrue(result.subQueries.get(1).contains("Elasticsearch"));
    }

    @Test
    void shouldFallbackToOriginalOnLLMFailure() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenThrow(new RuntimeException("LLM timeout"));

        QueryRewriteAdvisor.RewriteResult result = advisor.rewrite("简单问题", "");

        assertEquals("简单问题", result.rewrittenQuestion);
        assertTrue(result.rewriteFailed);
    }

    @Test
    void shouldFallbackToOriginalOnInvalidJson() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("not valid json");

        QueryRewriteAdvisor.RewriteResult result = advisor.rewrite("测试问题", "");

        assertFalse(result.rewriteFailed);
        // JSON 解析失败时降级使用原始问题
        assertNotNull(result.rewrittenQuestion);
    }
}
