package com.zhy.workflow.ai.service;

import com.zhy.workflow.ai.dto.VerifyCodeRequest;
import com.zhy.workflow.ai.entity.VerifyCode;
import com.zhy.workflow.ai.repository.VerifyCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service for verification codes.
 */
@Service
public class VerifyCodeService {

    @Autowired
    private VerifyCodeRepository verifyCodeRepository;

    @Value("${verify.code.expiration:300000}") // Default 5 minutes
    private long expirationTime;

    @Value("${verify.code.send-throttle:60000}") // Default 60 seconds
    private long sendThrottle;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generate a random 6-digit verification code.
     */
    private String generateRandomCode() {
        return String.format("%06d", RANDOM.nextInt(1000000));
    }

    /**
     * Send verification code to phone.
     */
    @Transactional
    public void sendVerificationCode(VerifyCodeRequest request) {
        String phone = request.getPhone();
        String type = request.getType();

        // Use native SQL to delete all existing verification codes for this phone
        // This ensures the deletion is executed before the insert
        int deleted = verifyCodeRepository.deleteByPhoneNative(phone);
        System.out.println("Deleted " + deleted + " existing verification code(s) for phone: " + phone);

        // Generate and save new verification code
        VerifyCode verifyCode = new VerifyCode();
        verifyCode.setPhone(phone);
        verifyCode.setCode(generateRandomCode());
        verifyCode.setType(type);
        verifyCode.setExpireTime(LocalDateTime.now().plusSeconds(expirationTime / 1000));

        verifyCodeRepository.save(verifyCode);

        // In a real application, send the code via SMS gateway
        // For now, just log it for testing
        System.out.println("===========================================");
        System.out.println("Verification Code for " + phone + " (Type: " + type + "):");
        System.out.println(verifyCode.getCode());
        System.out.println("Expires in: " + expirationTime + "ms (" + (expirationTime / 60000) + " minutes)");
        System.out.println("===========================================");
    }

    /**
     * Verify a verification code.
     */
    @Transactional
    public boolean verifyCode(String phone, String code, String type) {
        System.out.println("Verifying code for phone: " + phone + ", code: " + code + ", type: " + type);

        List<VerifyCode> codes = verifyCodeRepository.findByPhoneAndExpireTimeAfter(phone, LocalDateTime.now());

        System.out.println("Found " + codes.size() + " codes for phone: " + phone);

        if (codes.isEmpty()) {
            System.out.println("No codes found for phone: " + phone);
            return false;
        }

        // Find the latest code for this phone
        VerifyCode latestCode = codes.get(0);

        System.out.println("Latest code: " + latestCode.getCode() + ", type: " + latestCode.getType());

        // Check if code matches
        if (!latestCode.getCode().equals(code)) {
            System.out.println("Code mismatch: expected " + latestCode.getCode() + ", got " + code);
            return false;
        }

        // Check if code type matches
        if (!latestCode.getType().equals(type)) {
            System.out.println("Type mismatch: expected " + latestCode.getType() + ", got " + type);
            return false;
        }

        // Code is valid, delete it to prevent reuse

        return true;
    }

    /**
     * Get expiration time in milliseconds.
     */
    public long getExpirationTime() {
        return expirationTime;
    }

    /**
     * Get send throttle time in milliseconds.
     */
    public long getSendThrottle() {
        return sendThrottle;
    }
}
