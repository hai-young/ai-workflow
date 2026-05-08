package com.zhy.workflow.ai.dto;

import java.util.List;

public class AskResponse {
    private boolean success;
    private String answer;
    private ThinkingProcess thinking;
    private List<Citation> citations;
    private String conversationId;
    private String messageId;
    private String error;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public ThinkingProcess getThinking() { return thinking; }
    public void setThinking(ThinkingProcess thinking) { this.thinking = thinking; }
    public List<Citation> getCitations() { return citations; }
    public void setCitations(List<Citation> citations) { this.citations = citations; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public static AskResponse ok(String answer, ThinkingProcess thinking, List<Citation> citations, String conversationId, String messageId) {
        AskResponse r = new AskResponse();
        r.success = true;
        r.answer = answer;
        r.thinking = thinking;
        r.citations = citations;
        r.conversationId = conversationId;
        r.messageId = messageId;
        return r;
    }

    public static AskResponse fail(String error) {
        AskResponse r = new AskResponse();
        r.success = false;
        r.error = error;
        return r;
    }
}
