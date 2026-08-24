package org.example.all_my_trip_project.global.security.turnstile;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Component
public class TurnstileVerifier {

    private static final String SITEVERIFY_URL =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final TurnstileProperties properties;
    private final RestClient restClient;

    @Autowired
    public TurnstileVerifier(TurnstileProperties properties) {
        this(properties, createRestClient(properties));
    }

    TurnstileVerifier(TurnstileProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public void verify(String token, String expectedAction, String remoteIp) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(properties.getSecretKey())) {
            log.error("Turnstile is enabled but its secret key is missing");
            throw new BusinessException(ErrorCode.TURNSTILE_UNAVAILABLE);
        }
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.TURNSTILE_VERIFICATION_FAILED);
        }

        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.getSecretKey());
        form.add("response", token);
        if (StringUtils.hasText(remoteIp)) {
            form.add("remoteip", remoteIp);
        }

        try {
            SiteverifyResponse response = restClient.post()
                    .uri(SITEVERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(SiteverifyResponse.class);

            if (!isValid(response, expectedAction)) {
                log.warn("Turnstile verification rejected: errorCodes={}",
                        response == null ? List.of("empty-response") : response.errorCodes());
                throw new BusinessException(ErrorCode.TURNSTILE_VERIFICATION_FAILED);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("Turnstile Siteverify request failed: type={}",
                    exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.TURNSTILE_UNAVAILABLE);
        }
    }

    private boolean isValid(SiteverifyResponse response, String expectedAction) {
        if (response == null || !response.success()) {
            return false;
        }
        if (!expectedAction.equals(response.action())) {
            return false;
        }
        return !StringUtils.hasText(properties.getExpectedHostname())
                || properties.getExpectedHostname().equalsIgnoreCase(response.hostname());
    }

    private static RestClient createRestClient(TurnstileProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeout());
        requestFactory.setReadTimeout(properties.getTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    record SiteverifyResponse(
            boolean success,
            String hostname,
            String action,
            @JsonProperty("error-codes") List<String> errorCodes
    ) {
        SiteverifyResponse {
            errorCodes = errorCodes == null ? List.of() : List.copyOf(errorCodes);
        }
    }
}
