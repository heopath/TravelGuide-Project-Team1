package org.example.all_my_trip_project.global.security.turnstile;

import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TurnstileVerifierTest {

    private static final String SITEVERIFY_URL =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    @Test
    void disabledTurnstileDoesNotCallCloudflare() {
        TurnstileProperties properties = new TurnstileProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TurnstileVerifier verifier = new TurnstileVerifier(properties, builder.build());

        assertThatCode(() -> verifier.verify(null, "login", "127.0.0.1"))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void acceptsMatchingActionAndHostname() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(SITEVERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string("secret=test-secret&response=test-token&remoteip=203.0.113.10"))
                .andRespond(withSuccess(
                        "{\"success\":true,\"hostname\":\"www.allmytrip.click\",\"action\":\"login\"}",
                        MediaType.APPLICATION_JSON
                ));

        assertThatCode(() -> fixture.verifier().verify(
                "test-token", "login", "203.0.113.10"
        )).doesNotThrowAnyException();
        fixture.server().verify();
    }

    @Test
    void rejectsTokenIssuedForAnotherAction() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(SITEVERIFY_URL))
                .andRespond(withSuccess(
                        "{\"success\":true,\"hostname\":\"www.allmytrip.click\",\"action\":\"signup\"}",
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> fixture.verifier().verify(
                "test-token", "login", "203.0.113.10"
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.TURNSTILE_VERIFICATION_FAILED));
    }

    @Test
    void rejectsMissingTokenBeforeCallingCloudflare() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.verifier().verify("", "login", "203.0.113.10"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.TURNSTILE_VERIFICATION_FAILED));
        fixture.server().verify();
    }

    private Fixture fixture() {
        TurnstileProperties properties = new TurnstileProperties();
        properties.setEnabled(true);
        properties.setSecretKey("test-secret");
        properties.setExpectedHostname("www.allmytrip.click");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new TurnstileVerifier(properties, builder.build()), server);
    }

    private record Fixture(TurnstileVerifier verifier, MockRestServiceServer server) {
    }
}
