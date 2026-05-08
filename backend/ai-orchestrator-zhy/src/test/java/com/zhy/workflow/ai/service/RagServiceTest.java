package com.zhy.workflow.ai.service;

import com.zhy.workflow.ai.advisor.*;
import com.zhy.workflow.ai.client.RerankerClient;
import com.zhy.workflow.ai.dto.AskResponse;
import com.zhy.workflow.ai.repository.ConversationRepository;
import com.zhy.workflow.ai.repository.MessageRepository;
import com.zhy.workflow.ai.retrieval.ElasticsearchRetriever;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock private VectorStore vectorStore;
    @Mock private ChatClient.Builder chatClientBuilder;
    @Mock private ChatClient chatClient;
    @Mock private ElasticsearchRetriever esRetriever;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callResponseSpec;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private IntentionRouterAdvisor intentionRouterAdvisor;
    @Mock private SafetyGuardAdvisor safetyGuardAdvisor;
    @Mock private MinioClient minioClient;
    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private DocumentLifecycleService documentLifecycleService;

    private RagService ragService;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // lenient: ConversationMemoryService 需要 MySQL 仓库 mock
        lenient().when(conversationRepository.findBySessionId(anyString())).thenReturn(java.util.Optional.empty());
        lenient().when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 构造 Phase 1 Advisors
        HybridRetrievalAdvisor hybridAdvisor = new HybridRetrievalAdvisor(vectorStore, esRetriever);
        RerankerClient rerankerClient = new RerankerClient("http://localhost:6006/v1/rerank", 2000) {
            @Override
            public List<Document> rerank(String query, List<Document> documents, int topK) {
                return documents.subList(0, Math.min(topK, documents.size()));
            }
        };
        RerankAdvisor rerankAdvisor = new RerankAdvisor(rerankerClient);
        ContextEnrichAdvisor contextAdvisor = new ContextEnrichAdvisor();

        // 构造 Phase 2 Advisors
        QueryRewriteAdvisor queryRewriteAdvisor = new QueryRewriteAdvisor(chatClientBuilder);
        ConversationMemoryService memoryService = new ConversationMemoryService(stringRedisTemplate, conversationRepository, messageRepository, 10, 86400);
        MemoryAdvisor memoryAdvisor = new MemoryAdvisor(memoryService, chatClientBuilder);

        // 默认 Mock 行为：意图路由 → 知识问答，安全护栏 → 通过
        // lenient: 部分测试（检索失败/闲聊意图）不会触发所有 Advisor
        IntentionRouterAdvisor.RouteResult defaultRoute = new IntentionRouterAdvisor.RouteResult();
        defaultRoute.intent = "qa";
        defaultRoute.skipRetrieval = false;
        lenient().when(intentionRouterAdvisor.route(anyString())).thenReturn(defaultRoute);

        SafetyGuardAdvisor.GuardResult defaultGuard = new SafetyGuardAdvisor.GuardResult();
        defaultGuard.passed = true;
        defaultGuard.status = "pass";
        lenient().when(safetyGuardAdvisor.guard(anyString(), anyList(), anyString())).thenReturn(defaultGuard);

        ragService = new RagService(vectorStore, chatClientBuilder,
                hybridAdvisor, rerankAdvisor, contextAdvisor, esRetriever,
                queryRewriteAdvisor, memoryAdvisor,
                intentionRouterAdvisor, safetyGuardAdvisor,
                minioClient, documentLifecycleService);
    }

    @Test
    void shouldCompleteFullAdvisorChainWithMemory() {
        // Milvus 检索模拟
        Document doc1 = new Document("JWT 令牌过期时服务端返回 401。");
        doc1.getMetadata().put("doc_id", "doc-jwt");
        doc1.getMetadata().put("file_name", "jwt-guide.pdf");
        Document doc2 = new Document("客户端检测到 401 后调用 refresh 接口获取新令牌。");
        doc2.getMetadata().put("doc_id", "doc-auth");
        doc2.getMetadata().put("file_name", "auth-flow.md");

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc1, doc2));
        when(esRetriever.search(anyString(), anyInt())).thenReturn(List.of());
        when(esRetriever.isAvailable()).thenReturn(true);

        // Redis 记忆：无历史
        when(valueOperations.get(anyString())).thenReturn(null);

        // ChatClient: 第一次调用 = 查询重写，第二次调用 = 回答生成
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content())
                .thenReturn("""
                {
                  "rewritten_question": "JWT令牌过期后会发生什么？",
                  "has_coreference": true,
                  "sub_queries": []
                }""")   // 第1次：查询重写
                .thenReturn("JWT 令牌过期后，服务端返回 401 状态码 [1]，客户端应调用 refresh 接口获取新令牌 [2]。"); // 第2次：回答

        // Redis 写操作（记忆保存）— void 方法使用 doNothing
        doNothing().when(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));

        // 执行
        AskResponse response = ragService.ask("它过期后会怎样？", "test-session");

        // 验证完整链路
        assertTrue(response.isSuccess());
        assertNotNull(response.getAnswer());
        assertTrue(response.getAnswer().contains("[1]"));
        assertNotNull(response.getCitations());
        assertEquals(2, response.getCitations().size());
        assertNotNull(response.getConversationId());
        assertEquals("test-session", response.getConversationId());

        // 验证思考过程包含查询重写结果
        assertNotNull(response.getThinking());
        assertEquals("JWT令牌过期后会发生什么？", response.getThinking().getRewrittenQuery());
    }

    @Test
    void shouldFallbackWhenRewriteFailsButContinueChain() {
        // Milvus 检索模拟
        Document doc1 = new Document("参考内容。");
        doc1.getMetadata().put("doc_id", "doc-1");

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc1));
        when(esRetriever.search(anyString(), anyInt())).thenReturn(List.of());
        when(esRetriever.isAvailable()).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn(null);

        // 查询重写 LLM 失败
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content())
                .thenThrow(new RuntimeException("LLM rewrite timeout"))  // 重写失败
                .thenReturn("基于参考资料的简短回答。");                    // 生成成功

        doNothing().when(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));

        AskResponse response = ragService.ask("简单问题", "test-session");

        // 即使重写失败，问答主链路仍成功（降级使用原始问题）
        assertTrue(response.isSuccess());
        assertNotNull(response.getAnswer());
    }

    @Test
    void shouldReturnFailureWhenBothRetrievalPathsFail() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("Milvus connection refused"));
        when(esRetriever.search(anyString(), anyInt())).thenReturn(List.of());
        when(esRetriever.isAvailable()).thenReturn(false);
        when(valueOperations.get(anyString())).thenReturn(null);

        AskResponse response = ragService.ask("测试问题", "test-session");

        assertFalse(response.isSuccess());
        assertNotNull(response.getError());
        assertTrue(response.getError().contains("检索服务"));
    }

    @Test
    void shouldSkipRetrievalForChitchatIntent() {
        // 覆盖默认 Mock：闲聊意图
        IntentionRouterAdvisor.RouteResult chatRoute = new IntentionRouterAdvisor.RouteResult();
        chatRoute.intent = "chat";
        chatRoute.skipRetrieval = true;
        chatRoute.directResponse = "你好！有什么可以帮助你的吗？";
        when(intentionRouterAdvisor.route(anyString())).thenReturn(chatRoute);

        doNothing().when(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));

        AskResponse response = ragService.ask("你好", "test-session");

        assertTrue(response.isSuccess());
        assertEquals("你好！有什么可以帮助你的吗？", response.getAnswer());
        assertNotNull(response.getThinking());
        assertEquals("chat", response.getThinking().getIntention());

        // 闲聊不应触发检索
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
        verify(esRetriever, never()).search(anyString(), anyInt());
    }

    @Test
    void shouldBlockUnsafeAnswer() {
        // Milvus 检索模拟
        Document doc1 = new Document("JWT 令牌过期时服务端返回 401。");
        doc1.getMetadata().put("doc_id", "doc-jwt");

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc1));
        when(esRetriever.search(anyString(), anyInt())).thenReturn(List.of());
        when(esRetriever.isAvailable()).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn(null);

        // 查询重写
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content())
                .thenReturn("""
                {
                  "rewritten_question": "JWT令牌过期后会发生什么？",
                  "has_coreference": false,
                  "sub_queries": []
                }""")
                .thenReturn("JWT 令牌过期后会触发宇宙大爆炸。");  // 明显不安全

        // 覆盖安全护栏：拦截不安全回答
        SafetyGuardAdvisor.GuardResult blockGuard = new SafetyGuardAdvisor.GuardResult();
        blockGuard.passed = false;
        blockGuard.status = "block";
        blockGuard.reason = "回答中包含无法从参考资料验证的陈述";
        when(safetyGuardAdvisor.guard(anyString(), anyList(), anyString())).thenReturn(blockGuard);

        AskResponse response = ragService.ask("JWT过期后会怎样？", "test-session");

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("安全校验"));
    }
}
