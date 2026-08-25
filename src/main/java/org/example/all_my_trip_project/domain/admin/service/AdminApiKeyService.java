package org.example.all_my_trip_project.domain.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.admin.dto.ApiKeyDTO;
import org.example.all_my_trip_project.domain.admin.dto.ApiKeyTestResultDTO;
import org.example.all_my_trip_project.global.apikey.ApiKeyCipher;
import org.example.all_my_trip_project.global.apikey.ApiKeyProvider;
import org.example.all_my_trip_project.global.apikey.ApiKeySetting;
import org.example.all_my_trip_project.global.apikey.ApiKeyStore;
import org.example.all_my_trip_project.global.apikey.ManagedApiKey;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 관리자 API 키 관리.
 *
 * <p>지켜야 할 규칙이 하나 있다. <b>키 평문은 이 클래스 밖으로 나가지 않는다.</b> 응답에도,
 * 감사 로그에도, 예외 메시지에도 마스킹한 값만 남긴다. 감사 로그는 나중에 화면에서 그대로
 * 조회되므로, 여기에 평문을 넣으면 "관리자만 본다"는 전제가 무너진다.
 */
@Slf4j
@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminApiKeyService {

    /** 앞뒤를 이만큼씩 남긴다. 어떤 키인지 알아볼 정도만 남기고 나머지는 가린다. */
    private static final int PREFIX_LENGTH = 3;
    private static final int SUFFIX_LENGTH = 4;

    private final ApiKeyStore apiKeyStore;
    private final ApiKeyProvider apiKeyProvider;
    private final ApiKeyCipher apiKeyCipher;
    private final ApiKeyConnectionTester apiKeyConnectionTester;
    private final AdminAuditService adminAuditService;

    /** 암호화 설정이 없으면 화면이 저장 버튼을 잠그고 이유를 안내한다. */
    public boolean isEncryptionReady() {
        return apiKeyCipher.isConfigured();
    }

    public List<ApiKeyDTO> list() {
        Map<String, ApiKeySetting> stored = new HashMap<>();
        for (ApiKeySetting setting : apiKeyStore.findAll()) {
            stored.put(setting.getApiKeyName(), setting);
        }
        return Arrays.stream(ManagedApiKey.values())
                .map(key -> describe(key, stored.get(key.name())))
                .toList();
    }

    /**
     * 연결 테스트. 입력값이 비어 있으면 지금 쓰이는 키로 확인한다.
     *
     * <p>저장 전 확인과 "지금 키가 아직 살아 있나" 확인을 같은 버튼으로 처리하기 위한 것이다.
     */
    public ApiKeyTestResultDTO test(String name, String candidate) {
        ManagedApiKey key = require(name);
        String target = (candidate == null || candidate.isBlank())
                ? apiKeyProvider.resolve(key)
                : candidate.trim();
        return apiKeyConnectionTester.test(key, target);
    }

    @Transactional
    public ApiKeyDTO update(String name, String apiKey, Long adminUserId) {
        ManagedApiKey key = require(name);
        if (!apiKeyCipher.isConfigured()) {
            throw new BusinessException(ErrorCode.API_KEY_ENCRYPTION_UNAVAILABLE);
        }

        String trimmed = apiKey == null ? "" : apiKey.trim();
        if (trimmed.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_API_KEY);
        }

        /* 화면의 저장 버튼 잠금은 우회할 수 있다. PUT API를 직접 호출해도 검증되지 않은 키로
           정상 키를 덮어쓰지 못하도록 서버가 저장 직전에 다시 연결을 확인한다. */
        ApiKeyTestResultDTO verification = apiKeyConnectionTester.test(key, trimmed);
        if (!verification.valid()) {
            throw new BusinessException(ErrorCode.API_KEY_CONNECTION_TEST_FAILED);
        }

        String before = maskOf(currentValue(key));
        apiKeyStore.save(key.name(), apiKeyCipher.encrypt(trimmed), adminUserId);
        evictAfterCommit(key);

        adminAuditService.record(
                "API_KEY_CHANGE",
                "API_KEY",
                key.name(),
                AdminAuditService.payload("maskedValue", before),
                AdminAuditService.payload("maskedValue", mask(trimmed)));

        return describe(key, apiKeyStore.find(key.name()).orElse(null));
    }

    /** 저장값을 지워 환경변수 값으로 되돌린다. 잘못 넣은 키에서 빠져나오는 경로다. */
    @Transactional
    public ApiKeyDTO reset(String name, Long adminUserId) {
        ManagedApiKey key = require(name);
        String before = maskOf(currentValue(key));

        if (apiKeyStore.delete(key.name()) == 0) {
            throw new BusinessException(ErrorCode.API_KEY_NOT_STORED);
        }
        evictAfterCommit(key);

        adminAuditService.record(
                "API_KEY_RESET",
                "API_KEY",
                key.name(),
                AdminAuditService.payload("maskedValue", before),
                AdminAuditService.payload("source", ApiKeyDTO.SOURCE_ENV));

        return describe(key, null);
    }

    /**
     * 캐시는 커밋된 뒤에 비운다.
     *
     * <p>커밋 전에 비우면 그 사이에 들어온 다른 요청이 아직 옛 값인 DB를 읽어 캐시를 다시
     * 채운다. 그러면 저장은 됐는데 계속 옛 키로 호출하는, 화면과 동작이 어긋난 상태가 된다.
     *
     * <p>트랜잭션 밖에서 불릴 수도 있어(테스트 등) 동기화가 없으면 즉시 비운다.
     */
    private void evictAfterCommit(ManagedApiKey key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            apiKeyProvider.evict(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                apiKeyProvider.evict(key);
            }
        });
    }

    private ApiKeyDTO describe(ManagedApiKey key, ApiKeySetting setting) {
        Optional<String> storedValue = decrypt(setting);
        if (storedValue.isPresent()) {
            return new ApiKeyDTO(key.name(), key.label(), key.description(),
                    mask(storedValue.get()), ApiKeyDTO.SOURCE_STORED,
                    setting.getUpdatedAt(), setting.getUpdatedBy());
        }

        /*
         * 저장값이 없을 때만 환경변수를 본다. 저장값이 깨져 복호화에 실패한 경우도 여기로
         * 오는데, 실제 동작(ApiKeyProvider)도 같은 규칙으로 환경변수를 쓰므로 화면과 동작이
         * 어긋나지 않는다.
         */
        String fallback = apiKeyProvider.fromConfiguration(key);
        if (fallback.isBlank()) {
            return new ApiKeyDTO(key.name(), key.label(), key.description(),
                    "", ApiKeyDTO.SOURCE_NONE, null, null);
        }
        return new ApiKeyDTO(key.name(), key.label(), key.description(),
                mask(fallback), ApiKeyDTO.SOURCE_ENV, null, null);
    }

    private Optional<String> decrypt(ApiKeySetting setting) {
        if (setting == null || setting.getEncryptedValue() == null) return Optional.empty();
        if (!apiKeyCipher.isConfigured()) return Optional.empty();
        try {
            return Optional.of(apiKeyCipher.decrypt(setting.getEncryptedValue()));
        } catch (Exception exception) {
            log.error("Stored API key for {} could not be decrypted.", setting.getApiKeyName(), exception);
            return Optional.empty();
        }
    }

    private String currentValue(ManagedApiKey key) {
        return apiKeyProvider.resolve(key);
    }

    private String maskOf(String value) {
        return value == null || value.isBlank() ? "(없음)" : mask(value);
    }

    /**
     * 앞 3글자와 뒤 4글자만 남긴다.
     *
     * <p>짧은 값은 앞뒤를 남기면 원본이 거의 드러난다. 그럴 땐 길이만 알려주고 전부 가린다.
     */
    private String mask(String value) {
        String trimmed = value.trim();
        if (trimmed.length() < PREFIX_LENGTH + SUFFIX_LENGTH + 4) {
            return "•".repeat(Math.min(trimmed.length(), 12));
        }
        return trimmed.substring(0, PREFIX_LENGTH)
                + "•".repeat(8)
                + trimmed.substring(trimmed.length() - SUFFIX_LENGTH);
    }

    private ManagedApiKey require(String name) {
        return ManagedApiKey.from(name)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNKNOWN_API_KEY));
    }
}
