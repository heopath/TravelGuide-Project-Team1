package org.example.all_my_trip_project.domain.accommodation.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** LiteAPI Sandbox 요금 조회 설정. 공개키가 아니라 서버용 Sandbox Key를 사용한다. */
@Component
@ConfigurationProperties(prefix = "liteapi.sandbox")
public class LiteApiSandboxProperties {

    private String baseUrl = "https://api.liteapi.travel/v3.0";
    private String apiKey = "";
    private String guestNationality = "KR";
    private int timeoutSeconds = 8;
    private int maxHotels = 100;
    private int maxRadiusMeters = 50_000;
    private int matchDistanceMeters = 800;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getGuestNationality() { return guestNationality; }
    public void setGuestNationality(String guestNationality) { this.guestNationality = guestNationality; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getMaxHotels() { return maxHotels; }
    public void setMaxHotels(int maxHotels) { this.maxHotels = maxHotels; }
    public int getMaxRadiusMeters() { return maxRadiusMeters; }
    public void setMaxRadiusMeters(int maxRadiusMeters) { this.maxRadiusMeters = maxRadiusMeters; }
    public int getMatchDistanceMeters() { return matchDistanceMeters; }
    public void setMatchDistanceMeters(int matchDistanceMeters) { this.matchDistanceMeters = matchDistanceMeters; }

    public boolean hasSandboxKey() {
        if (apiKey == null) return false;
        String key = apiKey.trim().toLowerCase();
        return key.startsWith("sand_") || key.startsWith("sandbox_");
    }
}
