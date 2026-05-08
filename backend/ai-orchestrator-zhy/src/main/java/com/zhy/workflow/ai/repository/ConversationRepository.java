package com.zhy.workflow.ai.repository;

import com.zhy.workflow.ai.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);

    boolean existsBySessionId(String sessionId);
}
