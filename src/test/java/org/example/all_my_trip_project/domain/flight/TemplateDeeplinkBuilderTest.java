package org.example.all_my_trip_project.domain.flight;

import org.example.all_my_trip_project.domain.flight.dto.FlightOffer;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchQuery;
import org.example.all_my_trip_project.domain.flight.service.FlightDeeplinkProperties;
import org.example.all_my_trip_project.domain.flight.service.TemplateDeeplinkBuilder;
import org.example.all_my_trip_project.domain.flight.type.PriceSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 딥링크가 실제 {@code deeplink-templates.yml} 값으로 어디를 가리키는지 확인한다.
 *
 * <p>#132에서 KE·OZ·7C·TW·BX 예약 페이지를 직접 열어보니, 쿼리로 넘긴 노선·날짜를
 * 읽는 곳이 하나도 없었다. OZ·7C는 에러 페이지로 갔고 나머지는 빈 검색 폼이 떴다.
 * 그래서 캐리어별 템플릿을 걷어내고 전부 fallback으로 보낸다.
 *
 * <p>이 테스트가 잡으려는 것은 "검증하지 않은 항공사 URL이 다시 들어오는 것"이다.
 * 딥링크는 사용자를 외부로 내보내는 링크라 깨지면 그대로 이탈이 되는데,
 * 코드가 아니라 설정 파일이 조용히 바뀌는 형태로 망가진다.
 */
class TemplateDeeplinkBuilderTest {

    private static final String YML = "deeplink-templates.yml";

    private final TemplateDeeplinkBuilder builder = new TemplateDeeplinkBuilder(loadProperties());

    @Test
    @DisplayName("검증하지 않은 캐리어별 템플릿이 설정에 남아 있지 않다")
    void hasNoUnverifiedCarrierTemplates() {
        // 다시 넣으려면 그 항공사 예약 페이지를 실제로 열어 노선·날짜가 채워지는지 확인하고,
        // 이 테스트에 그 캐리어의 기대 URL을 함께 추가한다.
        assertThat(loadProperties().getTemplates()).isEmpty();
    }

    @Test
    @DisplayName("템플릿이 없는 캐리어는 노선·날짜가 채워진 편도 검색으로 나간다")
    void fallsBackToOneWaySearchWithRouteAndDate() {
        String url = builder.build(offer("KE", "1201"), query());

        assertThat(url)
                .startsWith("https://www.google.com/travel/flights?q=")
                .contains("One-way")
                .contains("GMP")
                .contains("CJU")
                .contains("2026-08-20");
    }

    @Test
    @DisplayName("캐리어가 달라도 같은 폴백을 쓴다")
    void usesSameFallbackForEveryCarrier() {
        FlightSearchQuery query = query();

        // ZE·RS는 원래도 템플릿이 없었고, KE·BX는 #132에서 걷어냈다. 이제 넷 다 같은 곳으로 간다.
        String ke = builder.build(offer("KE", "1201"), query);
        String bx = builder.build(offer("BX", "8101"), query);
        String ze = builder.build(offer("ZE", "201"), query);
        String rs = builder.build(offer("RS", "901"), query);

        assertThat(ke).isEqualTo(bx).isEqualTo(ze).isEqualTo(rs);
    }

    @Test
    @DisplayName("어필리에이트 ID가 없으면 트래킹 파라미터를 붙이지 않는다")
    void omitsAffiliateParamWhenIdIsBlank() {
        assertThat(builder.build(offer("KE", "1201"), query())).doesNotContain("aid=");
    }

    private FlightDeeplinkProperties loadProperties() {
        // 테스트용 값을 새로 쓰면 정작 배포되는 yml이 바뀌어도 통과한다. 실제 파일을 읽는다.
        StandardEnvironment environment = new StandardEnvironment();
        try {
            List<PropertySource<?>> sources =
                    new YamlPropertySourceLoader().load(YML, new ClassPathResource(YML));
            sources.forEach(environment.getPropertySources()::addFirst);
        } catch (Exception e) {
            throw new IllegalStateException(YML + "을 읽지 못했습니다.", e);
        }
        return Binder.get(environment)
                .bind("flight.deeplink", FlightDeeplinkProperties.class)
                .orElseThrow(() -> new IllegalStateException("flight.deeplink 바인딩에 실패했습니다."));
    }

    private FlightSearchQuery query() {
        return new FlightSearchQuery("GMP", "CJU", LocalDate.of(2026, 8, 20),
                1, true, "KRW", null, null);
    }

    private FlightOffer offer(String carrierCode, String flightNumber) {
        LocalDateTime departure = LocalDateTime.of(2026, 8, 20, 9, 0);
        return new FlightOffer(
                "test:" + carrierCode + flightNumber,
                "TEST",
                carrierCode,
                carrierCode + " 항공",
                flightNumber,
                "GMP",
                "CJU",
                departure,
                departure.plusMinutes(70),
                Duration.ofMinutes(70),
                new BigDecimal("59000"),
                new BigDecimal("59000"),
                "KRW",
                PriceSource.PUBLISHED,
                List.of(),
                null);
    }
}
