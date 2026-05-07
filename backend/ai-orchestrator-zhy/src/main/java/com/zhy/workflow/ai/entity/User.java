package com.zhy.workflow.ai.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column
    private String phone;

    @Column(nullable = false)
    private String password;

    @Column
    private String email;

    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * User role for RBAC. Default: ROLE_USER
     */
    @Column(nullable = false, length = 20)
    private String role = "ROLE_USER";

    /**
     * Last successful login time.
     */
    @Column
    private LocalDateTime lastLoginTime;

    /**
     * Consecutive failed login attempts. Resets on success.
     */
    @Column(nullable = false)
    private int loginFailCount = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Whether the account is locked due to too many failed attempts.
     */
    @Transient
    public boolean isLocked() {
        return loginFailCount >= 5;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
