package com.zhy.workflow.ai.repository;

import com.zhy.workflow.ai.entity.VerifyCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for verification codes.
 */
@Repository
public interface VerifyCodeRepository extends JpaRepository<VerifyCode, Long> {

    /**
     * Find verification codes for a phone number.
     */
    List<VerifyCode> findByPhone(String phone);

    /**
     * Find the latest verification code for a phone number.
     */
    Optional<VerifyCode> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    /**
     * Find active verification codes for a phone number.
     */
    List<VerifyCode> findByPhoneAndExpireTimeAfter(String phone, LocalDateTime now);

    /**
     * Delete all verification codes for a phone number using native SQL.
     * This bypasses JPA entity management to ensure atomic deletion.
     */
    @Modifying
    @Query(value = "DELETE FROM verify_codes WHERE phone = :phone", nativeQuery = true)
    int deleteByPhoneNative(@Param("phone") String phone);
}
