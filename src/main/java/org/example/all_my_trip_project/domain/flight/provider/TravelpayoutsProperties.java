package org.example.all_my_trip_project.domain.flight.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Travelpayouts(Aviasales) Flight Data API 설정.
 *
 * <p>토큰과 marker는 저장소에 커밋하지 않고 환경변수로 주입한다.
 * 비어 있으면 provider가 스스로 비활성화되고 전부 공시운임으로 간다.
 */
@Component
@ConfigurationProperties(prefix = "travelpayouts")
public class TravelpayoutsProperties {

    private String baseUrl = "https://api.travelpayouts.com/aviasales/v3";

    /** Aviasales 프로그램 Tools > API의 API token. */
    private String token = "";

    /** 같은 화면의 My Partner ID. 딥링크 커미션 추적에 쓴다. */
    private String marker = "";

    private String deeplinkBaseUrl = "https://www.aviasales.com";

    /**
     * 가격을 덮어쓰기 위해 필요한 최소 커버리지 비율.
     *
     * <p>Travelpayouts는 캐시 기반이라 특정 날짜에 노선당 1~2편만 준다.
     * 실측으로 김포→제주는 TAGO 118편 중 1편만 매칭됐다(약 1%).
     *
     * <p>그 1편만 실판매가로 바꾸면 나머지 117편과 기준이 달라진다.
     * 공시운임이 실판매가의 약 2배라, 그 한 편이 <b>실제로 가장 싸서가 아니라
     * 캐시에 우연히 들어있어서</b> 최저가 배지와 추천 1위를 가져간다.
     * 같은 기준으로 비교되지 않는 값을 나란히 놓고 최저가라고 단정하면 사용자가 손해를 본다.
     *
     * <p>그래서 목록의 절반 이상을 덮을 수 있을 때만 병합한다.
     * 미달이면 가격은 전부 공시운임으로 두고 딥링크만 가져다 쓴다.
     * 나중에 커버리지가 좋아지면 이 값만 조정하면 된다.
     */
    private double priceMergeThreshold = 0.5;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getMarker() {
        return marker;
    }

    public void setMarker(String marker) {
        this.marker = marker;
    }

    public String getDeeplinkBaseUrl() {
        return deeplinkBaseUrl;
    }

    public void setDeeplinkBaseUrl(String deeplinkBaseUrl) {
        this.deeplinkBaseUrl = deeplinkBaseUrl;
    }

    public double getPriceMergeThreshold() {
        return priceMergeThreshold;
    }

    public void setPriceMergeThreshold(double priceMergeThreshold) {
        this.priceMergeThreshold = priceMergeThreshold;
    }

    public boolean isConfigured() {
        return token != null && !token.isBlank();
    }
}
