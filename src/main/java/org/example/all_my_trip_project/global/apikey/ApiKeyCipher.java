package org.example.all_my_trip_project.global.apikey;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API 키를 DB에 넣기 전에 봉하고, 꺼낼 때 연다.
 *
 * <p>AES-256-GCM을 쓴다. GCM은 암호화와 무결성 검사를 함께 하므로, DB에서 값이 한 글자라도
 * 바뀌면 복호화 자체가 실패한다. 손상된 값을 키인 줄 알고 외부 API에 보내는 사고를 막는다.
 *
 * <p>저장 형식은 {@code Base64(IV 12바이트 + 암호문 + 인증태그)}다. IV를 앞에 붙여 두면
 * 별도 컬럼 없이 값 하나만 옮겨도 복호화가 된다. IV는 매번 새로 뽑는다. 같은 키를 두 번
 * 저장해도 DB에는 다른 문자열이 남아, 값이 같은지 눈으로 비교할 수 없게 된다.
 *
 * <p>마스터 키({@code API_KEY_ENCRYPTION_KEY})는 <b>DB에 두지 않는다.</b> DB에 함께 두면
 * 자물쇠 옆에 열쇠를 붙여 두는 것과 같아 암호화의 의미가 없다. 값은 Base64로 인코딩한
 * 32바이트이며 다음과 같이 만든다.
 *
 * <pre>{@code openssl rand -base64 32}</pre>
 *
 * <p>마스터 키가 없으면 이 빈은 "설정 안 됨" 상태로 뜬다. 기동을 막지는 않는다. 로컬이나 UI
 * 미리보기까지 못 뜨게 만들 이유가 없기 때문이다. 대신 저장 시도는 거절한다. 평문으로라도
 * 저장해 두는 편의는 두지 않는다. 한 번 평문으로 들어간 키는 아무도 다시 확인하지 않는다.
 */
@Slf4j
@Component
public class ApiKeyCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKeySpec masterKey;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyCipher(@Value("${security.api-key-encryption-key:}") String encodedMasterKey) {
        this.masterKey = parseMasterKey(encodedMasterKey);
    }

    /** 마스터 키가 준비됐는지. 화면이 "왜 저장이 안 되는지"를 설명할 수 있도록 노출한다. */
    public boolean isConfigured() {
        return masterKey != null;
    }

    public String encrypt(String plainText) {
        requireConfigured();
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] packed = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(cipherText, 0, packed, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (Exception exception) {
            /* 예외 메시지에 평문이 섞여 나갈 여지를 없앤다. 원인은 로그에만 남긴다. */
            log.error("API key encryption failed.", exception);
            throw new IllegalStateException("API key encryption failed");
        }
    }

    public String decrypt(String encoded) {
        requireConfigured();
        try {
            byte[] packed = Base64.getDecoder().decode(encoded);
            if (packed.length <= IV_LENGTH) throw new IllegalArgumentException("ciphertext too short");

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(packed, 0, iv, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(packed, IV_LENGTH, packed.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            log.error("API key decryption failed. The stored value may be corrupted "
                    + "or encrypted with a different master key.", exception);
            throw new IllegalStateException("API key decryption failed");
        }
    }

    private void requireConfigured() {
        if (masterKey == null) {
            throw new IllegalStateException("API_KEY_ENCRYPTION_KEY is not configured");
        }
    }

    private SecretKeySpec parseMasterKey(String encodedMasterKey) {
        if (encodedMasterKey == null || encodedMasterKey.isBlank()) {
            log.info("API_KEY_ENCRYPTION_KEY is not set. Admin API key management is read-only.");
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedMasterKey.trim());
            if (decoded.length != KEY_LENGTH_BYTES) {
                log.error("API_KEY_ENCRYPTION_KEY must decode to {} bytes but was {}.",
                        KEY_LENGTH_BYTES, decoded.length);
                return null;
            }
            return new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException exception) {
            log.error("API_KEY_ENCRYPTION_KEY is not valid Base64.");
            return null;
        }
    }
}
