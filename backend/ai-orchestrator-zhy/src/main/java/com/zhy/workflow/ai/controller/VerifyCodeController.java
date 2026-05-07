package com.zhy.workflow.ai.controller;

import com.zhy.workflow.ai.dto.VerifyCodeRequest;
import com.zhy.workflow.ai.dto.VerifyCodeResponse;
import com.zhy.workflow.ai.service.VerifyCodeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for verification code operations.
 */
@RestController
@RequestMapping("/api/verify-code")
public class VerifyCodeController {

    @Autowired
    private VerifyCodeService verifyCodeService;

    /**
     * Send verification code to phone.
     */
    @PostMapping("/send")
    public ResponseEntity<Void> sendVerificationCode(@Valid @RequestBody VerifyCodeRequest request) {
        verifyCodeService.sendVerificationCode(request);
        return ResponseEntity.ok().build();
    }

    /**
     * Verify a verification code.
     */
    @PostMapping("/verify")
    public ResponseEntity<VerifyCodeResponse> verifyCode(
            @RequestParam String phone,
            @RequestParam String code,
            @RequestParam String type) {

        boolean isValid = verifyCodeService.verifyCode(phone, code, type);

        if (!isValid) {
            return ResponseEntity.badRequest().build();
        }

        long expireTime = verifyCodeService.getExpirationTime();
        int countdown = (int) (expireTime / 1000);

        return ResponseEntity.ok(new VerifyCodeResponse(expireTime, countdown));
    }
}
