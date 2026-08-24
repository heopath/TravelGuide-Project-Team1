package org.example.all_my_trip_project.global.security.turnstile;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "cloudflare.turnstile")
public class TurnstileProperties {

    private boolean enabled;
    private String siteKey = "";
    private String secretKey = "";
    private String expectedHostname = "";
    private Duration timeout = Duration.ofSeconds(3);

    @AssertTrue(message = "Turnstile을 활성화하려면 site-key, secret-key, expected-hostname이 모두 필요합니다.")
    public boolean isConfigurationComplete() {
        return !enabled
                || StringUtils.hasText(siteKey)
                && StringUtils.hasText(secretKey)
                && StringUtils.hasText(expectedHostname);
    }
}
