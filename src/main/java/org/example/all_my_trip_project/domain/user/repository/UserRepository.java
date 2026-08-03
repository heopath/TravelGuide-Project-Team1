package org.example.all_my_trip_project.domain.user.repository;

import org.example.all_my_trip_project.domain.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    boolean existsByNicknameAndUserIdNotAndDeletedAtIsNull(
            String nickname,
            Long userId
    );

    Optional<UserEntity> findByEmailIgnoreCase(String email);
}
