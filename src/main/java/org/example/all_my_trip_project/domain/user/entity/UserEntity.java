package org.example.all_my_trip_project.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    private UserEntity(
            String email,
            String passwordHash,
            String nickname
    ) {
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.passwordHash = passwordHash;
        this.nickname = nickname.trim();
        this.role = "USER";
        this.status = "ACTIVE";
    }

    public static UserEntity create(
            String email,
            String passwordHash,
            String nickname
    ) {
        return new UserEntity(email, passwordHash, nickname);
    }

    public void recordLogin() {
        this.lastLoginAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PrePersist
    private void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}