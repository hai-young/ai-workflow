package com.zhy.workflow.ai.repository;

import com.zhy.workflow.ai.entity.DocumentRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentRecord, Long> {

    Optional<DocumentRecord> findByDocId(String docId);

    void deleteByDocId(String docId);

    Page<DocumentRecord> findAll(Pageable pageable);

    Page<DocumentRecord> findByFileNameContainingOrFileType(String keyword, String fileType, Pageable pageable);

    long countByEsStatusAndMilvusStatus(String esStatus, String milvusStatus);
}
