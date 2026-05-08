package com.zhy.workflow.ai.retrieval;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RrfFusionTest {

    private final RrfFusion fusion = new RrfFusion(60);

    @Test
    void shouldFuseTwoListsAndReturnTopK() {
        Document docA = new Document("Content A");
        docA.getMetadata().put("id", "a");
        Document docB = new Document("Content B");
        docB.getMetadata().put("id", "b");
        Document docC = new Document("Content C");
        docC.getMetadata().put("id", "c");

        List<Document> list1 = List.of(docA, docB);     // A rank1, B rank2
        List<Document> list2 = List.of(docC, docA);     // C rank1, A rank2

        List<Document> result = fusion.fuse(3, list1, list2);

        assertEquals(3, result.size());
        // A appears in both lists → highest RRF score
        assertEquals("a", result.get(0).getMetadata().get("id"));
        assertNotNull(result.get(0).getMetadata().get("rrf_score"));
    }

    @Test
    void shouldReturnEmptyWhenAllListsEmpty() {
        List<Document> result = fusion.fuse(5, List.of(), List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldNotExceedTopK() {
        Document docA = new Document("A");
        Document docB = new Document("B");
        Document docC = new Document("C");

        List<Document> result = fusion.fuse(2,
                List.of(docA, docB, docC),
                List.of(docA, docB));

        assertEquals(2, result.size());
    }

    @Test
    void shouldDeduplicateDocuments() {
        Document doc = new Document("Same Content");
        doc.getMetadata().put("id", "same");

        List<Document> result = fusion.fuse(5,
                List.of(doc, doc, new Document("Other")));

        // 去重：same 文档只出现一次
        long sameCount = result.stream()
                .filter(d -> "same".equals(d.getMetadata().get("id")))
                .count();
        assertEquals(1, sameCount);
    }

    @Test
    void shouldHandleSingleList() {
        Document doc1 = new Document("First");
        Document doc2 = new Document("Second");

        List<Document> result = fusion.fuse(2, List.of(doc1, doc2));

        assertEquals(2, result.size());
    }
}
