package org.example.all_my_trip_project.domain.flight.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TAGO 국내항공운항정보 설정.
 *
 * <p>서비스키는 저장소에 커밋하지 않고 {@code TAGO_SERVICE_KEY} 환경변수로 주입한다.
 * 비어 있으면 provider가 스스로 비활성화되고 Mock이 폴백을 맡는다.
 */
@Component
@ConfigurationProperties(prefix = "tago")
public class TagoProperties {

    private String baseUrl = "https://apis.data.go.kr/1613000/DmstcFlightNvgInfo";

    /**
     * 포털이 주는 <b>인코딩된</b> 인증키를 그대로 넣는다.
     *
     * <p>디코딩 키를 넣으면 안 된다. 키에 들어 있는 {@code +}가 쿼리스트링에서 공백으로 읽혀
     * 인증에 실패한다. 반대로 인코딩 키를 다시 인코딩하면 {@code %2F}가 {@code %252F}가 되어
     * 400이 난다. 그래서 URL은 이미 인코딩된 문자열로 취급해 만든다.
     */
    private String serviceKey = "";

    /** 한 번에 받아올 최대 편수. 김포→제주가 하루 118편이라 넉넉히 잡는다. */
    private int maxRows = 200;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getServiceKey() {
        return serviceKey;
    }

    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    public boolean isConfigured() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
