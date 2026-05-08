package com.zhy.workflow.ai.dto;

public class Citation {
    private int index;
    private String docId;
    private String fileName;
    private String content;
    private double relevanceScore;

    public Citation() {}

    public Citation(int index, String docId, String fileName, String content, double relevanceScore) {
        this.index = index;
        this.docId = docId;
        this.fileName = fileName;
        this.content = content;
        this.relevanceScore = relevanceScore;
    }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public double getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(double relevanceScore) { this.relevanceScore = relevanceScore; }
}
