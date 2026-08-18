package org.example.all_my_trip_project.domain.user.repository;

import org.example.all_my_trip_project.domain.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /**
     * 세션 확인용 최소 조회. 인증된 요청마다 불리므로 엔티티를 통째로 읽지 않는다.
     */
    @Query("""
            SELECT u.status AS status, u.role AS role
            FROM UserEntity u
            WHERE u.userId = :userId
            """)
    Optional<UserAccountView> findAccountByUserId(@Param("userId") Long userId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndUserIdNotAndDeletedAtIsNull(
            String nickname,
            Long userId
    );

    Optional<UserEntity> findByEmailIgnoreCase(String email);
}