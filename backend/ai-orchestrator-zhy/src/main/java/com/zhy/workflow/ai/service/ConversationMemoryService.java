package com.zhy.workflow.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhy.workflow.ai.entity.Conversation;
import com.zhy.workflow.ai.entity.Message;
import com.zhy.workflow.ai.repository.ConversationRepository;
import com.zhy.workflow.ai.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * 对话记忆服务：MySQL 持久化 + Redis 热点缓存。
 * Key: chat:cache:{sessionId}
 */
@Service
public class ConversationMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String CACHE_KEY_PREFIX = "chat:cache:";
    private static final String SUMMARY_KEY_PREFIX = "chat:summary:";
    private static final int CACHE_ROUNDS = 5;

    private final StringRedisTemplate redis;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final int maxRounds;
    private final Duration ttl;

    public ConversationMemoryService(
            StringRedisTemplate redis,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            @Value("${rag.memory.max-rounds:10}") int maxRounds,
            @Value("${rag.memory.ttl-seconds:86400}") int ttlSeconds) {
        this.redis = redis;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.maxRounds = maxRounds;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * 保存一轮对话：同时写入 MySQL + 更新 Redis 缓存。
     */
    public void saveRound(String sessionId, String userQuestion, String assistantAnswer) {
        try {
            // MySQL: 确保会话记录存在
            Conversation conv = conversationRepository.findBySessionId(sessionId)
                    .orElseGet(() -> {
                        Conversation c = new Conversation();
                        c.setSessionId(sessionId);
                        c.setTitle(userQuestion.length() > 200
                                ? userQuestion.substring(0, 197) + "..."
                                : userQuestion);
                        c.setTotalRounds(0);
                        return conversationRepository.save(c);
                    });

            // MySQL: 写入用户消息
            Message userMsg = new Message();
            userMsg.setSessionId(sessionId);
            userMsg.setRole("user");
            userMsg.setContent(userQuestion);
            messageRepository.save(userMsg);

            // MySQL: 写入助手消息
            Message assistantMsg = new Message();
            assistantMsg.setSessionId(sessionId);
            assistantMsg.setRole("assistant");
            assistantMsg.setContent(truncate(assistantAnswer, 500));
            messageRepository.save(assistantMsg);

            // MySQL: 更新轮数
            conv.setTotalRounds(conv.getTotalRounds() + 1);
            conversationRepository.save(conv);

            // Redis: 更新热点缓存
            List<MemoryEntry> cached = loadCachedHistory(sessionId);
            cached.add(new MemoryEntry("user", userQuestion));
            cached.add(new MemoryEntry("assistant", truncate(assistantAnswer, 500)));
            // 只保留最近 CACHE_ROUNDS 轮
            if (cached.size() > CACHE_ROUNDS * 2) {
                cached = cached.subList(cached.size() - CACHE_ROUNDS * 2, cached.size());
            }
            redis.opsForValue().set(CACHE_KEY_PREFIX + sessionId,
                    mapper.writeValueAsString(cached), Duration.ofHours(1));

        } catch (Exception e) {
            log.warn("记忆保存失败: sessionId={}, {}", sessionId, e.getMessage());
        }
    }

    /**
     * 获取最近 N 轮对话的格式化上下文。
     * 优先从 Redis 读取，miss 回源 MySQL 并写回 Redis。
     */
    public String getContext(String sessionId, int recentRounds) {
        List<MemoryEntry> history = loadCachedHistory(sessionId);

        if (history.isEmpty()) {
            // Redis miss → 回源 MySQL
            List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
            for (Message msg : messages) {
                history.add(new MemoryEntry(msg.getRole(), msg.getContent()));
            }
            // 写回 Redis 缓存
            if (!history.isEmpty()) {
                if (history.size() > CACHE_ROUNDS * 2) {
                    history = history.subList(history.size() - CACHE_ROUNDS * 2, history.size());
                }
                try {
                    redis.opsForValue().set(CACHE_KEY_PREFIX + sessionId,
                            mapper.writeValueAsString(history), Duration.ofHours(1));
                } catch (JsonProcessingException e) {
                    // ignore
                }
            }
        }

        if (history.isEmpty()) return "";

        int entries = Math.min(history.size(), recentRounds * 2);
        List<MemoryEntry> recent = history.subList(history.size() - entries, history.size());

        StringBuilder sb = new StringBuilder();
        for (MemoryEntry e : recent) {
            sb.append(e.role.equals("user") ? "用户: " : "AI: ").append(e.content).append("\n");
        }
        return sb.toString();
    }

    public boolean needsCompression(String sessionId) {
        Conversation conv = conversationRepository.findBySessionId(sessionId).orElse(null);
        return conv != null && conv.getTotalRounds() >= maxRounds;
    }

    public void compress(String sessionId, String summary) {
        try {
            // MySQL: 更新摘要
            conversationRepository.findBySessionId(sessionId).ifPresent(conv -> {
                conv.setSummary(summary);
                conversationRepository.save(conv);
            });

            // Redis: 保存压缩摘要
            redis.opsForValue().set(SUMMARY_KEY_PREFIX + sessionId, summary, ttl);

            // Redis: 清空历史缓存（压缩后重新开始）
            redis.delete(CACHE_KEY_PREFIX + sessionId);

        } catch (Exception e) {
            log.warn("记忆压缩失败: sessionId={}, {}", sessionId, e.getMessage());
        }
    }

    /**
     * 清除指定会话：MySQL + Redis。
     */
    @org.springframework.transaction.annotation.Transactional
    public void clear(String sessionId) {
        messageRepository.deleteBySessionId(sessionId);
        conversationRepository.deleteBySessionId(sessionId);
        redis.delete(CACHE_KEY_PREFIX + sessionId);
        redis.delete(SUMMARY_KEY_PREFIX + sessionId);
    }

    /**
     * 列表所有会话：从 MySQL 查询。
     */
    public List<SessionInfo> listSessions() {
        List<SessionInfo> sessions = new ArrayList<>();
        try {
            List<Conversation> all = conversationRepository.findAll();
            for (Conversation conv : all) {
                String preview = "";
                List<Message> messages = messageRepository
                        .findBySessionIdOrderByCreatedAtAsc(conv.getSessionId());
                if (!messages.isEmpty()) {
                    Message first = messages.stream()
                            .filter(m -> "user".equals(m.getRole()))
                            .findFirst().orElse(messages.get(0));
                    preview = truncate(first.getContent(), 80);
                }
                String lastRole = "";
                if (!messages.isEmpty()) {
                    lastRole = messages.get(messages.size() - 1).getRole();
                }
                sessions.add(new SessionInfo(conv.getSessionId(), conv.getTotalRounds(),
                        preview, lastRole, conv.getCreatedAt() != null
                                ? conv.getCreatedAt().toString() : ""));
            }
        } catch (Exception e) {
            log.warn("会话列表查询失败: {}", e.getMessage());
        }
        sessions.sort((a, b) -> Integer.compare(b.rounds, a.rounds));
        return sessions;
    }

    /**
     * 获取指定会话完整历史（从 MySQL）。
     */
    public SessionDetail getSessionDetail(String sessionId) {
        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<MemoryEntry> history = new ArrayList<>();
        for (Message msg : messages) {
            history.add(new MemoryEntry(msg.getRole(), msg.getContent()));
        }
        return new SessionDetail(sessionId, history);
    }

    public int clearAll() {
        int count = 0;
        try {
            var keys = redis.keys(CACHE_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                count = keys.size();
                redis.delete(keys);
            }
            keys = redis.keys(SUMMARY_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
            messageRepository.deleteAll();
            conversationRepository.deleteAll();
            count += (int) conversationRepository.count();
        } catch (Exception e) {
            log.warn("清空所有会话失败: {}", e.getMessage());
        }
        return count;
    }

    // ── 内部方法 ──

    private List<MemoryEntry> loadCachedHistory(String sessionId) {
        try {
            String json = redis.opsForValue().get(CACHE_KEY_PREFIX + sessionId);
            if (json == null || json.isEmpty()) return new ArrayList<>();
            return mapper.readValue(json, new TypeReference<List<MemoryEntry>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    // ── 内部类 ──

    public static class MemoryEntry {
        public String role;
        public String content;

        public MemoryEntry() {}

        public MemoryEntry(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public static class SessionInfo {
        public String sessionId;
        public int rounds;
        public String preview;
        public String lastRole;
        public String createdAt;

        public SessionInfo() {}

        public SessionInfo(String sessionId, int rounds, String preview,
                           String lastRole, String createdAt) {
            this.sessionId = sessionId;
            this.rounds = rounds;
            this.preview = preview;
            this.lastRole = lastRole;
            this.createdAt = createdAt;
        }
    }

    public static class SessionDetail {
        public String sessionId;
        public List<MemoryEntry> history;

        public SessionDetail() {}

        public SessionDetail(String sessionId, List<MemoryEntry> history) {
            this.sessionId = sessionId;
            this.history = history;
        }
    }
}
