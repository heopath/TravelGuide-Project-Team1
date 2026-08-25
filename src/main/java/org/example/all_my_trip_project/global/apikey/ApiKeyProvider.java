package org.example.all_my_trip_project.global.apikey;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 외부 API 키를 꺼내 주는 창구.
 *
 * <p><b>왜 이게 필요한가.</b> 예전에는 각 서비스가 {@code @Value}로 키를 생성자에서 한 번 받고
 * 끝이었다. 싱글톤이라 그 뒤로는 값이 바뀌지 않는다. 관리자가 화면에서 키를 교체해도 재시작
 * 전까지는 옛 키로 호출한다. 사용량이 차서 급히 바꾸려는 상황에 정작 안 바뀌면 기능이 없는
 * 것과 같다. 그래서 호출 시점마다 여기서 꺼내 쓴다.
 *
 * <p><b>우선순위는 DB → 환경변수다.</b> 관리자가 넣은 값이 있으면 그것을 쓰고, 없으면 기존
 * 설정값을 그대로 쓴다. 이 순서 덕분에 관리자 화면을 한 번도 쓰지 않은 환경은 지금까지와
 * 똑같이 동작한다. 기존 배포를 건드리지 않는 것이 이 기능의 전제다.
 *
 * <p><b>캐시.</b> 장소 검색처럼 자주 불리는 경로가 있어 매번 DB를 읽지 않는다. 관리자가 저장할
 * 때 {@link #evict}로 비운다. 인스턴스를 여러 대 띄우면 저장한 인스턴스만 즉시 반영되고 나머지는
 * 재시작 전까지 옛 값을 쓴다. 현재 이 서비스는 단일 인스턴스라 문제되지 않지만, 확장할 때는
 * 캐시를 Redis로 옮기거나 만료 시간을 둬야 한다.
 */
@Slf4j
@Component
public class ApiKeyProvider {

    /** {@link ConcurrentHashMap}은 null을 담지 못한다. "저장값 없음"을 빈 문자열로 표시한다. */
    private static final String NOT_STORED = "";

    private final Environment environment;
    private final ObjectProvider<ApiKeyStore> storeProvider;
    private final ApiKeyCipher apiKeyCipher;
    private final Map<ManagedApiKey, String> cache = new ConcurrentHashMap<>();

    public ApiKeyProvider(Environment environment,
                          ObjectProvider<ApiKeyStore> storeProvider,
                          ApiKeyCipher apiKeyCipher) {
        this.environment = environment;
        this.storeProvider = storeProvider;
        this.apiKeyCipher = apiKeyCipher;
    }

    /**
     * 지금 사용할 키를 돌려준다. 어디에도 없으면 빈 문자열이다.
     *
     * <p>예외를 던지지 않는 이유는, 키가 없다는 것이 곧 장애는 아니기 때문이다. 호출부는
     * 이미 "키가 비면 기능을 건너뛴다"로 동작하고 있다. 그 판단을 여기서 뺏지 않는다.
     */
    public String resolve(ManagedApiKey key) {
        String stored = cache.computeIfAbsent(key, this::loadStored);
        if (!stored.isEmpty()) return stored;
        return fromEnvironment(key);
    }

    /** 관리자가 키를 저장·삭제했을 때 호출한다. 다음 요청부터 새 값이 쓰인다. */
    public void evict(ManagedApiKey key) {
        cache.remove(key);
    }

    /** DB에 저장된 값이 있는지. 화면이 "환경변수 사용 중"과 "관리자 저장값 사용 중"을 구분한다. */
    public boolean hasStoredValue(ManagedApiKey key) {
        return !cache.computeIfAbsent(key, this::loadStored).isEmpty();
    }

    private String loadStored(ManagedApiKey key) {
        ApiKeyStore store = storeProvider.getIfAvailable();
        /* ui 프로필에는 DAO가 등록되지 않는다. 그때는 조용히 환경변수만 본다. */
        if (store == null || !apiKeyCipher.isConfigured()) return NOT_STORED;

        try {
            return store.find(key.name())
                    .map(ApiKeySetting::getEncryptedValue)
                    .map(apiKeyCipher::decrypt)
                    .orElse(NOT_STORED);
        } catch (Exception exception) {
            /*
             * 마스터 키를 바꿨거나 값이 손상되면 복호화가 실패한다. 이때 예외를 그대로 올리면
             * AI 추천이나 장소 검색 전체가 멈춘다. 환경변수 값으로 버티게 두고 로그로 알린다.
             */
            log.error("Stored API key for {} could not be read. Falling back to configuration.",
                    key.name(), exception);
            return NOT_STORED;
        }
    }

    /**
     * 저장값을 무시하고 기존 설정(환경변수) 값만 본다.
     *
     * <p>관리자 화면이 "저장값을 지우면 무슨 값으로 돌아가는지"를 보여줄 때 쓴다. 이때
     * {@link #resolve}를 쓰면 아직 비워지지 않은 캐시의 저장값이 나와, 지웠는데도 지워지지
     * 않은 것처럼 보인다.
     */
    public String fromConfiguration(ManagedApiKey key) {
        return fromEnvironment(key);
    }

    private String fromEnvironment(ManagedApiKey key) {
        try {
            String value = environment.getProperty(key.propertyKey());
            return value == null ? "" : value.trim();
        } catch (IllegalArgumentException exception) {
            /*
             * application-ai.properties의 openai.api-key는 기본값 없는 ${OPENAI_API_KEY}다.
             * 환경변수가 없으면 여기서 치환에 실패한다. 설정이 안 된 것과 같으므로 빈 값으로 본다.
             */
            return "";
        }
    }
}
