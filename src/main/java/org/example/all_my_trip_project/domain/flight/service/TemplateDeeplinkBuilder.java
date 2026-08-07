package org.example.all_my_trip_project.domain.flight.service;

import org.example.all_my_trip_project.domain.flight.dto.FlightOffer;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchQuery;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

@Component
public class TemplateDeeplinkBuilder implements DeeplinkBuilder {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final FlightDeeplinkProperties properties;

    public TemplateDeeplinkBuilder(FlightDeeplinkProperties properties) {
        this.properties = properties;
    }

    @Override
    public String build(FlightOffer offer, FlightSearchQuery query) {
        String template = properties.getTemplates()
                .getOrDefault(offer.carrierCode(), properties.getFallbackTemplate());
        if (template == null || template.isBlank()) {
            return "";
        }

        String url = template
                .replace("{origin}", enc(offer.origin()))
                .replace("{destination}", enc(offer.destination()))
                .replace("{date}", offer.departureAt().toLocalDate().format(DATE))
                .replace("{adults}", String.valueOf(query.adults()))
                .replace("{carrierCode}", enc(offer.carrierCode()))
                .replace("{flightNumber}", enc(offer.flightNumber()));

        return appendAffiliateId(url);
    }

    /** 제휴 승인 전에는 affiliateId가 비어 있고, 그때는 파라미터를 아예 붙이지 않는다. */
    private String appendAffiliateId(String url) {
        String affiliateId = properties.getAffiliateId();
        if (affiliateId == null || affiliateId.isBlank()) {
            return url;
        }
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + properties.getAffiliateParam() + "=" + enc(affiliateId);
    }

    private String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
