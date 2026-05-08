package com.zhy.workflow.ai.service;

import com.zhy.workflow.ai.entity.Conversation;
import com.zhy.workflow.ai.entity.Message;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        // Redis miss → MySQL fallback
        when(valueOperations.get("chat:cache:test-session")).thenReturn(null);

        // MySQL: 无已有会话，saveRound 将创建新会话
        when(conversationRepository.findBySessionId("test-session")).thenReturn(Optional.empty());
        Conversation savedConv = new Conversation();
        savedConv.setSessionId("test-session");
        savedConv.setTotalRounds(0);
        savedConv.setCreatedAt(LocalDateTime.now());
        when(conversationRepository.save(any(Conversation.class))).thenReturn(savedConv);

        // MySQL: 消息保存
        when(messageRepository.save(any(Message.class))).thenReturn(new Message());

        // MySQL: getContext 回源查询
        Message userMsg = new Message();
        userMsg.setRole("user");
        userMsg.setContent("什么是JWT？");
        Message assistantMsg = new Message();
        assistantMsg.setRole("assistant");
        assistantMsg.setContent("JWT 是 JSON Web Token...");
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc("test-session"))
                .thenReturn(List.of(userMsg, assistantMsg));

        // 捕获写入 Redis 的 JSON
        AtomicReference<String> savedJson = new AtomicReference<>();
        doAnswer(inv -> {
            savedJson.set(inv.getArgument(1, String.class));
            return null;
        }).when(valueOperations).set(eq("chat:cache:test-session"), anyString(), any(Duration.class));

        memoryService.saveRound("test-session", "什么是JWT？", "JWT 是 JSON Web Token...");

        // 验证 Redis 写入
        assertNotNull(savedJson.get(), "saveRound 应将历史写入 Redis 缓存");

        // getContext：首次从 MySQL 加载时写回 Redis
        when(valueOperations.get("chat:cache:test-session")).thenReturn(null, savedJson.get());

        String context = memoryService.getContext("test-session", 5);
        assertTrue(context.contains("JWT"), "记忆上下文应包含对话内容");
    }

    @Test
    void shouldListSessions() {
        Conversation conv1 = new Conversation();
        conv1.setSessionId("s1");
        conv1.setTotalRounds(1);
        conv1.setCreatedAt(LocalDateTime.now());

        Conversation conv2 = new Conversation();
        conv2.setSessionId("s2");
        conv2.setTotalRounds(2);
        conv2.setCreatedAt(LocalDateTime.now());

        when(conversationRepository.findAll()).thenReturn(List.of(conv1, conv2));

        Message msg1 = new Message();
        msg1.setRole("user");
        msg1.setContent("问题1");
        Message msg2 = new Message();
        msg2.setRole("assistant");
        msg2.setContent("回答1");

        Message msg3 = new Message();
        msg3.setRole("user");
        msg3.setContent("问题2");

        when(messageRepository.findBySessionIdOrderByCreatedAtAsc("s1"))
                .thenReturn(List.of(msg1, msg2));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc("s2"))
                .thenReturn(List.of(msg3));

        var sessions = memoryService.listSessions();

        assertEquals(2, sessions.size());
        assertTrue(sessions.stream().anyMatch(s -> s.sessionId.equals("s1") && s.rounds == 1));
        assertTrue(sessions.stream().anyMatch(s -> s.sessionId.equals("s2") && s.preview.equals("问题2")));
    }

    @Test
    void shouldGetSessionDetail() {
        Message msg1 = new Message();
        msg1.setRole("user");
        msg1.setContent("测试问题");
        Message msg2 = new Message();
        msg2.setRole("assistant");
        msg2.setContent("测试回答");

        when(messageRepository.findBySessionIdOrderByCreatedAtAsc("detail-session"))
                .thenReturn(List.of(msg1, msg2));

        var detail = memoryService.getSessionDetail("detail-session");

        assertEquals("detail-session", detail.sessionId);
        assertEquals(2, detail.history.size());
        assertEquals("测试问题", detail.history.get(0).content);
    }

    @Test
    void shouldClearAllSessions() {
        when(stringRedisTemplate.keys("chat:cache:*")).thenReturn(Set.of("chat:cache:s1", "chat:cache:s2"));
        when(stringRedisTemplate.keys("chat:summary:*")).thenReturn(Set.of("chat:summary:s1"));
        lenient().when(stringRedisTemplate.delete(any(Set.class))).thenReturn(1L);
        when(conversationRepository.count()).thenReturn(3L);

        int count = memoryService.clearAll();

        assertTrue(count >= 3, "clearAll 应返回删除数量");
        verify(messageRepository).deleteAll();
        verify(conversationRepository).deleteAll();
    }

    @Test
    void shouldReturnEmptyListWhenNoSessions() {
        when(conversationRepository.findAll()).thenReturn(List.of());

        var sessions = memoryService.listSessions();

        assertTrue(sessions.isEmpty());
    }

    @Test
    void shouldReturnEmptyDetailForNonexistentSession() {
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc("nonexistent"))
                .thenReturn(List.of());

        var detail = memoryService.getSessionDetail("nonexistent");

        assertTrue(detail.history.isEmpty());
    }

    @Test
    void shouldDetectNeedsCompression() {
        Conversation conv = new Conversation();
        conv.setSessionId("compress-session");
        conv.setTotalRounds(11); // 超过 maxRounds=10
        conv.setCreatedAt(LocalDateTime.now());

        when(conversationRepository.findBySessionId("compress-session"))
                .thenReturn(Optional.of(conv));

        assertTrue(memoryService.needsCompression("compress-session"));
    }
}
