package com.zhy.workflow.ai.advisor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhy.workflow.ai.dto.Citation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 安全护栏与引用校验 Advisor。
 * 轻量模式：规则校验引用格式和范围。
 * 严格模式：LLM 校验事实忠实度。
 */
@Component
public class SafetyGuardAdvisor {

    private static final Logger log = LoggerFactory.getLogger(SafetyGuardAdvisor.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)\\]");

    private final ChatClient chatClient;
    private boolean strictMode = true;

    // ── PII 检测正则 ──
    private static final Pattern CN_ID_CARD = Pattern.compile("\\b[1-9]\\d{5}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]\\b");
    private static final Pattern CN_PHONE = Pattern.compile("\\b1[3-9]\\d{9}\\b");
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern BANK_CARD = Pattern.compile("\\b\\d{16,19}\\b");

    public SafetyGuardAdvisor(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    /**
     * 校验生成回答的安全性。
     * @param answer LLM 生成的回答
     * @param citations 引用列表
     * @param context 注入 LLM 的完整 system prompt（含参考资料）
     * @return 校验结果
     */
    public GuardResult guard(String answer, List<Citation> citations, String context) {
        GuardResult result = new GuardResult();

        // 1. PII 检测
        result.piiCheck = detectPii(answer);
        if (result.piiCheck.hasPii) {
            result.passed = true;
            result.status = "warn";
            result.reason = "回答含敏感个人信息，已做脱敏处理";
            result.maskedAnswer = result.piiCheck.masked;
            log.warn("PII 检测命中: {}", result.piiCheck.types);
            // 对脱敏后的回答继续做规则校验
        }

        // 2. 规则校验：引用格式（对脱敏后内容）
        String toCheck = result.piiCheck.hasPii ? result.piiCheck.masked : answer;
        result.ruleCheck = ruleCheck(toCheck, citations);

        // 3. 严格模式：LLM 事实校验
        if (strictMode && citations != null && !citations.isEmpty()) {
            result.llmCheck = llmFactCheck(toCheck, context);
        }

        if (!result.piiCheck.hasPii) {
            result.passed = result.ruleCheck.passed && result.llmCheck.passed;
            result.status = result.passed ? "pass" : "block";
        }
        result.reason = result.reason.isEmpty() ? buildReason(result) : result.reason;

        if (!result.passed || result.piiCheck.hasPii) {
            log.warn("安全护栏{}: {}", result.piiCheck.hasPii ? "(PII)" : "", result.reason);
        }

        return result;
    }

    /**
     * PII 检测 &amp; 掩码。
     */
    private PiiCheckResult detectPii(String text) {
        PiiCheckResult result = new PiiCheckResult();
        if (text == null || text.isBlank()) return result;

        StringBuilder masked = new StringBuilder(text);
        java.util.List<String> types = new java.util.ArrayList<>();

        java.util.regex.Matcher m;

        m = CN_ID_CARD.matcher(masked);
        while (m.find()) {
            types.add("身份证号");
            String original = m.group();
            String replaced = original.substring(0, 3) + "**********" + original.substring(original.length() - 3);
            masked.replace(m.start(), m.end(), replaced);
        }

        m = CN_PHONE.matcher(masked);
        while (m.find()) {
            types.add("手机号");
            String original = m.group();
            String replaced = original.substring(0, 3) + "****" + original.substring(7);
            masked.replace(m.start(), m.end(), replaced);
        }

        m = EMAIL.matcher(masked);
        while (m.find()) {
            types.add("邮箱");
            String original = m.group();
            int atIdx = original.indexOf('@');
            String replaced = original.charAt(0) + "***" + original.substring(atIdx);
            masked.replace(m.start(), m.end(), replaced);
        }

        m = BANK_CARD.matcher(masked);
        while (m.find()) {
            types.add("银行卡号");
            String original = m.group();
            String replaced = original.substring(0, 4) + " **** **** " + original.substring(original.length() - 4);
            masked.replace(m.start(), m.end(), replaced);
        }

        if (!types.isEmpty()) {
            result.hasPii = true;
            result.types = types;
            result.masked = masked.toString();
        }
        return result;
    }

    /**
     * 规则级校验：引用编号是否在有效范围内，是否有无引用的事实陈述。
     */
    private RuleCheckResult ruleCheck(String answer, List<Citation> citations) {
        RuleCheckResult rc = new RuleCheckResult();

        if (answer == null || answer.isBlank()) {
            rc.passed = false;
            rc.issues.add("回答为空");
            return rc;
        }

        // 无引用但回答包含事实性陈述时仅警告，不拦截
        if (citations == null || citations.isEmpty()) {
            rc.passed = true;
            rc.warnings.add("无引用来源（可能因检索结果为空）");
            return rc;
        }

        Set<Integer> validIndices = citations.stream()
                .map(Citation::getIndex)
                .collect(Collectors.toSet());

        Matcher matcher = CITATION_PATTERN.matcher(answer);
        boolean hasCitation = false;
        while (matcher.find()) {
            hasCitation = true;
            int idx = Integer.parseInt(matcher.group(1));
            if (!validIndices.contains(idx)) {
                rc.issues.add("引用编号 [" + idx + "] 不在有效范围内（有效范围: " + validIndices + "）");
            }
        }

        if (!hasCitation && answer.length() > 50) {
            rc.warnings.add("回答中未使用引用标注，可能包含未验证的信息");
        }

        // 检查回答长度是否异常短（潜在幻觉或生成失败）
        if (answer.length() < 5) {
            rc.passed = false;
            rc.issues.add("回答过短，可能生成失败");
        }

        rc.passed = rc.issues.isEmpty();
        return rc;
    }

    /**
     * LLM 事实校验：检查回答中的事实性陈述是否都有参考资料支撑。
     */
    private LlmCheckResult llmFactCheck(String answer, String context) {
        LlmCheckResult lr = new LlmCheckResult();
        try {
            String prompt = String.format("""
                    你是一个事实校验员。请判断以下 AI 回答中的事实性陈述是否都能在参考资料中找到依据。

                    %s

                    AI 回答：
                    %s

                    请严格按 JSON 格式输出：
                    {
                      "faithful": true/false,
                      "hallucinations": ["若无事实依据的陈述，逐一列出；若无不实内容则为空数组"],
                      "score": 0.0-1.0
                    }
                    """, context, answer);

            String llmResponse = chatClient.prompt().user(prompt).call().content();
            Map<String, Object> parsed = parseJson(llmResponse);

            lr.faithful = (boolean) parsed.getOrDefault("faithful", true);
            lr.score = ((Number) parsed.getOrDefault("score", 1.0)).doubleValue();
            @SuppressWarnings("unchecked")
            List<String> hallucinations = (List<String>) parsed.getOrDefault("hallucinations", List.of());
            lr.hallucinations = hallucinations;
            lr.passed = lr.faithful && lr.score >= 0.6;

        } catch (Exception e) {
            log.warn("LLM 事实校验失败，默认放行: {}", e.getMessage());
            lr.passed = true;
            lr.degraded = true;
        }
        return lr;
    }

    private String buildReason(GuardResult result) {
        if (result.passed) return "";
        StringBuilder sb = new StringBuilder();
        if (!result.ruleCheck.issues.isEmpty()) {
            sb.append("规则校验: ").append(String.join("; ", result.ruleCheck.issues));
        }
        if (!result.llmCheck.passed) {
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append("事实校验: 忠实度=").append(String.format("%.2f", result.llmCheck.score));
            if (!result.llmCheck.hallucinations.isEmpty()) {
                sb.append(", 疑似幻觉: ").append(result.llmCheck.hallucinations.get(0));
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String response) {
        try {
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            }
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("faithful", true, "score", 1.0, "hallucinations", List.of());
        }
    }

    // ── 内部类 ──

    public static class GuardResult {
        public boolean passed = true;
        public String status = "pass";
        public String reason = "";
        public String maskedAnswer;
        public PiiCheckResult piiCheck = new PiiCheckResult();
        public RuleCheckResult ruleCheck = new RuleCheckResult();
        public LlmCheckResult llmCheck = new LlmCheckResult();
    }

    public static class PiiCheckResult {
        public boolean hasPii;
        public String masked = "";
        public java.util.List<String> types = java.util.List.of();
    }

    public static class RuleCheckResult {
        public boolean passed = true;
        public final java.util.List<String> issues = new java.util.ArrayList<>();
        public final java.util.List<String> warnings = new java.util.ArrayList<>();
    }

    public static class LlmCheckResult {
        public boolean passed = true;
        public boolean faithful = true;
        public double score = 1.0;
        public boolean degraded;
        public List<String> hallucinations = List.of();
    }
}
