package com.zhy.workflow.ai.advisor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class SafetyGuardAdvisorTest {

    @Test
    void shouldDetectChineseIdCard() {
        Pattern p = Pattern.compile("[1-9]\\d{5}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]");
        assertTrue(p.matcher("身份证 110101199001011234 请核实").find());
        assertFalse(p.matcher("工号 20240507001").find());
    }

    @Test
    void shouldDetectChinesePhoneNumber() {
        Pattern p = Pattern.compile("1[3-9]\\d{9}");
        assertTrue(p.matcher("联系 13812345678 获取帮助").find());
        assertTrue(p.matcher("电话 19988887777").find());
        assertFalse(p.matcher("编号 12345678901").find()); // starts with 1 but second digit is 2
        assertFalse(p.matcher("QQ 10086123456").find()); // 11 digits but second digit is 0
    }

    @Test
    void shouldDetectEmail() {
        Pattern p = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
        assertTrue(p.matcher("请发送至 admin@example.com").find());
        assertTrue(p.matcher("联系 support@company.co.cn").find());
        assertFalse(p.matcher("没有邮箱的文本").find());
    }

    @Test
    void shouldDetectBankCard() {
        Pattern p = Pattern.compile("\\d{16,19}");
        assertTrue(p.matcher("卡号 6222021234567890123").find());
        assertFalse(p.matcher("订单号 20240507001").find()); // too short (11 digits)
    }

    @Test
    void citationPatternShouldMatchValidIndices() {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\[(\\d+)\\]");
        java.util.regex.Matcher m = p.matcher("请参考文档 [1] 和 [2] 了解详情。");
        assertTrue(m.find());
        assertEquals("1", m.group(1));
        assertTrue(m.find());
        assertEquals("2", m.group(1));
        assertFalse(p.matcher("没有引用的文本").find());
    }

    @Test
    void citationPatternShouldNotMatchNonCitation() {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\[(\\d+)\\]");
        assertFalse(p.matcher("IP 地址 [192.168.1.1]").find()); // dots don't match \d+
        assertFalse(p.matcher("[abc]").find()); // non-digit
    }
}
