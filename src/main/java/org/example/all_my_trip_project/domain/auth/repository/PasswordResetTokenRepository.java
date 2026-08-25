package org.example.all_my_trip_project.domain.auth.repository;

import org.example.all_my_trip_project.domain.auth.entity.PasswordResetTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetTokenEntity, Long> {

    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    /**
     * 새 링크를 보낼 때 그 사람의 아직 안 쓴 토큰을 한꺼번에 무른다.
     *
     * <p>여러 장이 동시에 살아 있으면, 손님이 새 링크를 받은 뒤에도 옛 링크가 그대로
     * 통한다. 메일함에 남은 예전 링크가 계속 열쇠 노릇을 하는 셈이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetTokenEntity t
               SET t.usedAt = :now
             WHERE t.userId = :userId
               AND t.usedAt IS NULL
            """)
    int expireAllFor(@Param("userId") Long userId, @Param("now") OffsetDateTime now);
}
