package org.example.all_my_trip_project.domain.accommodation.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 숙소 딥링크 목적지.
 *
 * <p>외부화한 이유는 목적지가 아직 확정이 아니기 때문이다(#147). 제휴가 열리면
 * 배포 없이 {@code accommodation.deeplink.search-template}만 바꿔 검증할 수 있어야 한다.
 */
@Component
@ConfigurationProperties(prefix = "accommodation.deeplink")
public class AccommodationDeeplinkProperties {

    /** {@code {query}} 자리에 검색어가 URL 인코딩되어 들어간다. 비우면 이동 버튼이 나오지 않는다. */
    private String searchTemplate = "https://www.google.com/search?q={query}";

    public String getSearchTemplate() {
        return searchTemplate;
    }

    public void setSearchTemplate(String searchTemplate) {
        this.searchTemplate = searchTemplate;
    }
}
