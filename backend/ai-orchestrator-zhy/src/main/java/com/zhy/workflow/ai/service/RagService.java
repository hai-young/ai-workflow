package com.zhy.workflow.ai.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    public RagService(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 上传文档并存入向量库
     */
    public void uploadDocument(MultipartFile file) throws IOException {
        TikaDocumentReader reader = new TikaDocumentReader(file.getResource());
        List<Document> documents = reader.get();

        // 将文档切分为块（如果内容较长）
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(documents);

        vectorStore.add(chunks);
        System.out.println("成功添加 " + chunks.size() + " 个文档块到向量库");
    }

    /**
     * 根据问题构建 RAG 增强的 Prompt
     */
    public String buildRagPrompt(String question) {
        List<Document> relevantDocs = vectorStore.similaritySearch(question);
        if (relevantDocs.isEmpty()) {
            return question;
        }

        // 使用 getText() 替代 getContent()
        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        return """
                请根据以下参考资料回答用户的问题。如果参考资料不足以回答问题，请说明无法回答。
                
                参考资料：
                %s
                
                用户问题：%s
                
                请给出简洁准确的回答：
                """.formatted(context, question);
    }
}