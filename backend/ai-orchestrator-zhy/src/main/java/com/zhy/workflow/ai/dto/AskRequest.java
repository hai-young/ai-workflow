package com.zhy.workflow.ai.dto;

import java.util.ArrayList;
import java.util.List;

public class AskRequest {
    private String question;
    private String sessionId;
    private boolean stream;

    public AskRequest() {}

    public AskRequest(String question, String sessionId, boolean stream) {
        this.question = question;
        this.sessionId = sessionId;
        this.stream = stream;
    }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public boolean isStream() { return stream; }
    public void setStream(boolean stream) { this.stream = stream; }
}
