package com.zhy.workflow.ai.dto;

/**
 * Verification code response DTO.
 */
public class VerifyCodeResponse {

    private long expireIn;
    private int countdown;

    public VerifyCodeResponse() {
    }

    public VerifyCodeResponse(long expireIn, int countdown) {
        this.expireIn = expireIn;
        this.countdown = countdown;
    }

    public long getExpireIn() {
        return expireIn;
    }

    public void setExpireIn(long expireIn) {
        this.expireIn = expireIn;
    }

    public int getCountdown() {
        return countdown;
    }

    public void setCountdown(int countdown) {
        this.countdown = countdown;
    }
}
