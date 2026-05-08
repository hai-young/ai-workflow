package com.zhy.workflow.ai.service;

import com.zhy.workflow.ai.repository.ConversationRepository;
import com.zhy.workflow.ai.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationMemoryServiceTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;

    private ConversationMemoryService memoryService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        memoryService = new ConversationMemoryService(stringRedisTemplate, conversationRepository, messageRepository, 10, 86400);
    }

    @Test
    void shouldSaveAndLoadMemory() {
        // 捕获存入 Redis 的 JSON
        AtomicReference<String> savedJson = new AtomicReference<>();
        when(valueOperations.get("chat:memory:test-session")).thenReturn(null);
        doAnswer(inv -> {
            savedJson.set(inv.getArgument(1, String.class));
            return null;
        }).when(valueOperations).set(eq("chat:memory:test-session"), anyString(), any(Duration.class));

        memoryService.saveRound("test-session", "什么是JWT？", "JWT 是 JSON Web Token...");

        // save 后 getContext 会再次 loadHistory → 返回捕获的 JSON
        when(valueOperations.get("chat:memory:test-session")).thenReturn(savedJson.get());

        String context = memoryService.getContext("test-session", 5);
        assertTrue(context.contains("JWT"), "记忆上下文应包含对话内容");
    }

    @Test
    void shouldListSessions() {
        String session1Json = "[{\"role\":\"user\",\"content\":\"问题1\"},{\"role\":\"assistant\",\"content\":\"回答1\"}]";
        String session2Json = "[{\"role\":\"user\",\"content\":\"问题2\"}]";

        when(stringRedisTemplate.keys("chat:memory:*"))
                .thenReturn(Set.of("chat:memory:s1", "chat:memory:s2"));
        when(valueOperations.get("chat:memory:s1")).thenReturn(session1Json);
        when(valueOperations.get("chat:memory:s2")).thenReturn(session2Json);

        var sessions = memoryService.listSessions();

        assertEquals(2, sessions.size());
        assertTrue(sessions.stream().anyMatch(s -> s.sessionId.equals("s1") && s.rounds == 1));
        assertTrue(sessions.stream().anyMatch(s -> s.sessionId.equals("s2") && s.preview.equals("问题2")));
    }

    @Test
    void shouldGetSessionDetail() {
        String json = "[{\"role\":\"user\",\"content\":\"测试问题\"},{\"role\":\"assistant\",\"content\":\"测试回答\"}]";
        when(valueOperations.get("chat:memory:detail-session")).thenReturn(json);

        var detail = memoryService.getSessionDetail("detail-session");

        assertEquals("detail-session", detail.sessionId);
        assertEquals(2, detail.history.size());
        assertEquals("测试问题", detail.history.get(0).content);
    }

    @Test
    void shouldClearAllSessions() {
        when(stringRedisTemplate.keys("chat:memory:*"))
                .thenReturn(Set.of("chat:memory:s1", "chat:memory:s2", "chat:memory:s3"));
        lenient().when(stringRedisTemplate.delete(any(Set.class))).thenReturn(3L);

        int count = memoryService.clearAll();

        assertEquals(3, count);
    }

    @Test
    void shouldReturnEmptyListWhenNoSessions() {
        when(stringRedisTemplate.keys("chat:memory:*")).thenReturn(Set.of());

        var sessions = memoryService.listSessions();

        assertTrue(sessions.isEmpty());
    }

    @Test
    void shouldReturnEmptyDetailForNonexistentSession() {
        when(valueOperations.get("chat:memory:nonexistent")).thenReturn(null);

        var detail = memoryService.getSessionDetail("nonexistent");

        assertTrue(detail.history.isEmpty());
    }

    @Test
    void shouldDetectNeedsCompression() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 22; i++) {
            if (i > 0) json.append(",");
            json.append("{\"role\":\"").append(i % 2 == 0 ? "user" : "assistant")
                    .append("\",\"content\":\"msg").append(i).append("\"}");
        }
        json.append("]");
        when(valueOperations.get("chat:memory:compress-session")).thenReturn(json.toString());

        assertTrue(memoryService.needsCompression("compress-session"));
    }
}
