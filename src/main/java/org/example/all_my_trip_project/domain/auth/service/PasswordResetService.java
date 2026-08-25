package org.example.all_my_trip_project.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.auth.entity.PasswordResetTokenEntity;
import org.example.all_my_trip_project.domain.auth.repository.PasswordResetTokenRepository;
import org.example.all_my_trip_project.domain.user.entity.UserEntity;
import org.example.all_my_trip_project.domain.user.repository.UserRepository;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 비밀번호 재설정.
 *
 * <p>지키는 것 셋:
 *
 * <ol>
 *   <li>계정이 있든 없든 같은 답을 준다. 답이 갈리면 이메일만 넣어 보며 가입 여부를 알아낼 수 있다.
 *   <li>토큰 원문은 저장하지 않는다. 해시만 두고 확인할 때 다시 해시해 맞춘다.
 *   <li>한 번 쓰면 끝이고, 새 링크를 보내면 이전 링크는 그 자리에서 무른다.
 * </ol>
 */
@Slf4j
@Service
// ui 프로필은 JPA를 통째로 빼서 저장소 빈이 없다. AuthService와 같은 조건으로 맞춘다.
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetService {

    /** 링크가 살아 있는 시간. 메일함을 확인할 만큼은 되고, 오래 굴러다니지는 않을 만큼. */
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    /** 32바이트면 무작위로 맞힐 수 없다. base64url이라 주소에 그대로 실린다. */
    private static final int TOKEN_BYTES = 32;

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * 재설정 링크를 보낸다.
     *
     * <p>없는 계정이어도 아무 일 없었다는 듯 끝낸다. 호출한 쪽은 결과를 알 수 없고,
     * 화면에는 늘 같은 안내가 나간다.
     */
    @Transactional
    public void requestReset(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        Optional<UserEntity> found = userRepository.findByEmailIgnoreCase(normalized);
        if (found.isEmpty()) {
            log.info("비밀번호 재설정 요청을 받았지만 해당 계정이 없습니다. 화면에는 같은 안내를 보냅니다.");
            return;
        }

        UserEntity user = found.get();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        /* 새 링크를 주기 전에 예전 링크를 먼저 무른다. 두 장이 동시에 살아 있으면 안 된다. */
        tokenRepository.expireAllFor(user.getUserId(), now);

        String token = newToken();
        tokenRepository.save(PasswordResetTokenEntity.issue(
                user.getUserId(), hash(token), now.plus(TOKEN_TTL)));

        mailer.send(user.getEmail(), resetUrl(token));
    }

    /** 재설정 화면을 열기 전에 링크가 아직 쓸 수 있는지 본다. */
    public void verifyToken(String token) {
        readUsableToken(token);
    }

    @Transactional
    public void confirmReset(String token, String newPassword) {
        validatePassword(newPassword);

        PasswordResetTokenEntity entity = readUsableToken(token);
        UserEntity user = userRepository.findById(entity.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));

        user.changePassword(passwordEncoder.encode(newPassword));
        entity.markUsed(OffsetDateTime.now(ZoneOffset.UTC));

        log.info("비밀번호를 재설정했습니다. userId={}", user.getUserId());
    }

    private PasswordResetTokenEntity readUsableToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }
        PasswordResetTokenEntity entity = tokenRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
        if (!entity.isUsable(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }
        return entity;
    }

    private void validatePassword(String password) {
        if (password == null || password.trim().length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_SHORT);
        }
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", exception);
        }
    }

    private String resetUrl(String token) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/auth/reset-password?token=" + token;
    }
}
