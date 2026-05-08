package com.zhy.workflow.ai.dto;

import java.util.ArrayList;
import java.util.List;

public class ThinkingProcess {
    private String intention;
    private String rewrittenQuery;
    private List<String> subQueries = new ArrayList<>();
    private int retrievedCount;
    private int rerankedCount;
    private String rerankStatus;
    private String safetyStatus;
    private List<String> fallbacks = new ArrayList<>();

    public String getIntention() { return intention; }
    public void setIntention(String intention) { this.intention = intention; }
    public String getRewrittenQuery() { return rewrittenQuery; }
    public void setRewrittenQuery(String rewrittenQuery) { this.rewrittenQuery = rewrittenQuery; }
    public List<String> getSubQueries() { return subQueries; }
    public void setSubQueries(List<String> subQueries) { this.subQueries = subQueries; }
    public int getRetrievedCount() { return retrievedCount; }
    public void setRetrievedCount(int retrievedCount) { this.retrievedCount = retrievedCount; }
    public int getRerankedCount() { return rerankedCount; }
    public void setRerankedCount(int rerankedCount) { this.rerankedCount = rerankedCount; }
    public String getRerankStatus() { return rerankStatus; }
    public void setRerankStatus(String rerankStatus) { this.rerankStatus = rerankStatus; }
    public String getSafetyStatus() { return safetyStatus; }
    public void setSafetyStatus(String safetyStatus) { this.safetyStatus = safetyStatus; }
    public List<String> getFallbacks() { return fallbacks; }
    public void setFallbacks(List<String> fallbacks) { this.fallbacks = fallbacks; }
    public void addFallback(String fallback) { this.fallbacks.add(fallback); }
}
