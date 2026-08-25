package org.example.all_my_trip_project.global.apikey;

import java.util.Arrays;
import java.util.Optional;

/**
 * 관리자 화면에서 교체할 수 있는 외부 API 키 목록.
 *
 * <p>키를 하나 추가하려면 여기에 한 줄만 넣으면 된다. 화면 목록, 저장 API, 연결 테스트가 모두
 * 이 열거형을 그대로 읽는다.
 *
 * <p><b>결제 키(토스·카카오페이)는 일부러 넣지 않았다.</b> 실제 돈이 오가는 값이라, 화면에서
 * 잘못 바꾸면 승인 실패가 곧 매출 손실이 된다. 이 구조가 운영에서 검증된 뒤에 옮긴다.
 *
 * @param propertyKey DB에 저장된 값이 없을 때 사용할 기존 설정 키. 환경변수 주입 경로를 그대로 둔다
 * @param testUrl     연결 테스트용 엔드포인트. 키만 맞으면 200이 오는 가장 가벼운 조회를 고른다
 * @param authFormat  Authorization 헤더 값 형식. {@code %s} 자리에 키가 들어간다
 */
public enum ManagedApiKey {

    OPENAI(
            "openai.api-key",
            "OpenAI",
            "AI 여행 추천, 챗봇, 문서 임베딩에 사용합니다.",
            "https://api.openai.com/v1/models",
            "Bearer %s"
    ),

    /**
     * {@code kakao.rest-api-key}와 {@code kakao.local.rest-api-key}는 둘 다 환경변수
     * {@code KAKAO_REST_API_KEY} 하나를 본다. 그래서 관리 대상도 하나로 둔다.
     */
    KAKAO_REST(
            "kakao.rest-api-key",
            "카카오 REST",
            "장소 검색과 길찾기에 사용합니다.",
            "https://dapi.kakao.com/v2/local/search/keyword.json?query=%EC%84%9C%EC%9A%B8&size=1",
            "KakaoAK %s"
    );

    private final String propertyKey;
    private final String label;
    private final String description;
    private final String testUrl;
    private final String authFormat;

    ManagedApiKey(String propertyKey, String label, String description, String testUrl, String authFormat) {
        this.propertyKey = propertyKey;
        this.label = label;
        this.description = description;
        this.testUrl = testUrl;
        this.authFormat = authFormat;
    }

    public String propertyKey() {
        return propertyKey;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public String testUrl() {
        return testUrl;
    }

    public String authorizationHeader(String apiKey) {
        return authFormat.formatted(apiKey);
    }

    /** 화면이 보내온 이름이 목록에 없을 수 있다. 예외 대신 비어 있는 값을 돌려주고 호출부가 판단한다. */
    public static Optional<ManagedApiKey> from(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String normalized = name.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(key -> key.name().equals(normalized))
                .findFirst();
    }
}
