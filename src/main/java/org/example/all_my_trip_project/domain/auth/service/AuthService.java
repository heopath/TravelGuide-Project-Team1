package org.example.all_my_trip_project.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.auth.dto.LoginRequest;
import org.example.all_my_trip_project.domain.auth.dto.SignupRequest;
import org.example.all_my_trip_project.domain.user.dto.MemberResponse;
import org.example.all_my_trip_project.domain.user.entity.UserEntity;
import org.example.all_my_trip_project.domain.user.repository.UserRepository;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String nickname = request.nickname().trim();

        validateEmailDuplicate(email);
        validateNicknameDuplicate(nickname);

        String passwordHash =
                passwordEncoder.encode(request.password());

        UserEntity user = UserEntity.create(
                email,
                passwordHash,
                nickname
        );

        UserEntity savedUser = userRepository.save(user);

        return toMemberResponse(savedUser);
    }

    @Transactional
    public MemberResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        UserEntity user = userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_CREDENTIALS
                ));

        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )) {
            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS
            );
        }

        validateAccountStatus(user);

        user.recordLogin();

        return toMemberResponse(user);
    }

    private String normalizeEmail(String email) {
        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private void validateEmailDuplicate(String email) {
        if (userRepository
                .existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new BusinessException(
                    ErrorCode.EMAIL_DUPLICATED
            );
        }
    }

    private void validateNicknameDuplicate(String nickname) {
        if (userRepository
                .existsByNicknameAndDeletedAtIsNull(nickname)) {
            throw new BusinessException(
                    ErrorCode.NICKNAME_DUPLICATED
            );
        }
    }

    private void validateAccountStatus(UserEntity user) {
        if ("SUSPENDED".equals(user.getStatus())) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_SUSPENDED
            );
        }

        if ("WITHDRAWN".equals(user.getStatus())) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_WITHDRAWN
            );
        }
    }

    private MemberResponse toMemberResponse(UserEntity user) {
        return new MemberResponse(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getStatus()
        );
    }
}