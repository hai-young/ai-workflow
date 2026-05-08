package com.zhy.workflow.ai.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserSettingsController {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsController.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String KEY_PREFIX = "user:settings:";

    private final StringRedisTemplate redis;

    public UserSettingsController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 更新用户设置。
     */
    @PatchMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> body) {
        try {
            String userId = getCurrentUserId();
            String key = KEY_PREFIX + userId;

            // 读取已有设置，合并更新
            Map<String, Object> existing = loadSettings(userId);
            existing.putAll(body);
            existing.putIfAbsent("memoryTtlDays", 30);
            existing.putIfAbsent("maxRounds", 10);
            existing.putIfAbsent("streamSpeed", "normal");

            redis.opsForValue().set(key, mapper.writeValueAsString(existing), Duration.ofDays(365));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "设置已更新");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("更新用户设置失败: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "保存设置失败");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 获取用户设置。
     */
    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        try {
            String userId = getCurrentUserId();
            Map<String, Object> settings = loadSettings(userId);

            // 填充默认值
            settings.putIfAbsent("memoryTtlDays", 30);
            settings.putIfAbsent("maxRounds", 10);
            settings.putIfAbsent("streamSpeed", "normal");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", settings);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("获取用户设置失败: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "获取设置失败");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    // ── 内部方法 ──

    private Map<String, Object> loadSettings(String userId) {
        try {
            String json = redis.opsForValue().get(KEY_PREFIX + userId);
            if (json == null || json.isEmpty()) {
                return new HashMap<>();
            }
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    /**
     * 获取当前用户 ID。当前简化实现：尝试从 SecurityContext 或请求头提取，
     * 如果都不存在则返回默认值。
     */
    private String getCurrentUserId() {
        // 简化实现：后续可从 JWT token 或 SecurityContext 中提取
        // 目前使用默认用户 ID
        return "default";
    }
}
