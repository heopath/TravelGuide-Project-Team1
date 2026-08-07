package org.example.all_my_trip_project.domain.flight.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 캐리어별 예약 페이지 URL 패턴.
 *
 * <p>{@code deeplink-templates.yml}로 외부화한 이유는 항공사가 예약 페이지 주소를
 * 예고 없이 바꾸기 때문이다. 코드를 고치지 않고 갱신할 수 있어야 한다.
 */
@Component
@ConfigurationProperties(prefix = "flight.deeplink")
public class FlightDeeplinkProperties {

    /** 캐리어 코드(KE, 7C ...) → URL 템플릿. */
    private Map<String, String> templates = new LinkedHashMap<>();

    /** 템플릿이 없는 캐리어에 쓰는 폴백. */
    private String fallbackTemplate = "";

    /** 어필리에이트 승인 후 주입한다. 비어 있으면 트래킹 파라미터를 붙이지 않는다. */
    private String affiliateId = "";

    /** 트래킹 파라미터 이름. 제휴사마다 aid / associateId 등으로 다르다. */
    private String affiliateParam = "aid";

    public Map<String, String> getTemplates() {
        return templates;
    }

    public void setTemplates(Map<String, String> templates) {
        this.templates = templates;
    }

    public String getFallbackTemplate() {
        return fallbackTemplate;
    }

    public void setFallbackTemplate(String fallbackTemplate) {
        this.fallbackTemplate = fallbackTemplate;
    }

    public String getAffiliateId() {
        return affiliateId;
    }

    public void setAffiliateId(String affiliateId) {
        this.affiliateId = affiliateId;
    }

    public String getAffiliateParam() {
        return affiliateParam;
    }

    public void setAffiliateParam(String affiliateParam) {
        this.affiliateParam = affiliateParam;
    }
}
