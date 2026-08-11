package org.example.all_my_trip_project.domain.accommodation.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 한국관광공사 국문 관광정보 서비스 설정.
 *
 * <p>서비스키는 저장소에 넣지 않고 {@code TOUR_API_SERVICE_KEY} 환경변수로 주입한다.
 * 키가 없으면 provider가 비활성화되고 Mock provider가 목록을 만든다.
 */
@Component
@ConfigurationProperties(prefix = "tour-api")
public class TourApiProperties {

    private String baseUrl = "https://apis.data.go.kr/B551011/KorService2";
    private String serviceKey = "";
    private String mobileApp = "AllMyTrips";
    private int maxRows = 100;

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

    public String getMobileApp() {
        return mobileApp;
    }

    public void setMobileApp(String mobileApp) {
        this.mobileApp = mobileApp;
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
