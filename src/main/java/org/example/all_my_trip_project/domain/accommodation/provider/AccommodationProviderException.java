package org.example.all_my_trip_project.domain.accommodation.provider;

/**
 * 숙소 목록 공급자가 정상적인 검색 결과를 만들지 못했을 때 던지는 내부 예외.
 *
 * <p>외부 API 예외 원문에는 서비스 키가 포함된 요청 URL이 들어갈 수 있으므로,
 * 사용자 응답이나 상위 로그에는 공급자 이름과 안전한 원인 종류만 전달한다.
 */
public class AccommodationProviderException extends RuntimeException {

    private final String providerName;

    public AccommodationProviderException(String providerName, String reason, Throwable cause) {
        super(providerName + " accommodation provider failed: " + reason, cause);
        this.providerName = providerName;
    }

    public String providerName() {
        return providerName;
    }
}
