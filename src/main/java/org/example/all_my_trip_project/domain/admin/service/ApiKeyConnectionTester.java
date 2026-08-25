package org.example.all_my_trip_project.domain.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.admin.dto.ApiKeyTestResultDTO;
import org.example.all_my_trip_project.global.apikey.ManagedApiKey;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 저장하기 전에 키가 실제로 먹히는지 확인한다.
 *
 * <p>이 확인이 이 기능의 핵심이다. 사용량이 차서 급히 키를 바꾸는 상황은 대개 서비스가 이미
 * 반쯤 멈춘 시점이다. 거기서 오타 난 키를 저장하면 멀쩡하던 옛 키까지 잃고 완전히 멈춘다.
 *
 * <p>각 발급처에서 <b>키만 맞으면 200이 오는 가장 가벼운 조회</b>를 골라 한 번 부른다. 목록
 * 조회라 과금이나 사용량에 의미 있는 영향을 주지 않는다.
 */
@Slf4j
@Component
@Profile("!ui")
public class ApiKeyConnectionTester {

    /** 관리자가 저장 버튼 앞에서 기다리는 시간이다. 길게 잡으면 화면이 멈춘 것처럼 보인다. */
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    public ApiKeyTestResultDTO test(ManagedApiKey key, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return new ApiKeyTestResultDTO(false, 0, "확인할 키가 없습니다.");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(key.testUrl()))
                .timeout(TIMEOUT)
                .header("Authorization", key.authorizationHeader(apiKey.trim()))
                .GET()
                .build();

        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return interpret(response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ApiKeyTestResultDTO(false, 0, "확인이 중단되었습니다. 다시 시도해 주세요.");
        } catch (Exception exception) {
            /*
             * 예외 메시지를 화면에 그대로 내보내지 않는다. 요청 URL이나 헤더가 섞여 나올 수 있다.
             * 관리자에게는 "서버에서 외부로 못 나갔다"는 사실만 알려도 충분하다.
             */
            log.warn("API key connection test failed for {}.", key.name(), exception);
            return new ApiKeyTestResultDTO(false, 0, "외부 서버에 연결하지 못했습니다. 네트워크 상태를 확인해 주세요.");
        }
    }

    /**
     * 상태 코드를 관리자가 읽을 수 있는 말로 바꾼다.
     *
     * <p>401과 429를 구분하는 것이 중요하다. 401은 "키가 틀렸다"이고 429는 "키는 맞는데 한도를
     * 넘었다"다. 후자를 실패로만 알려주면, 멀쩡한 새 키를 잘못된 키로 오해하고 버리게 된다.
     */
    private ApiKeyTestResultDTO interpret(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return new ApiKeyTestResultDTO(true, statusCode, "정상 응답을 받았습니다.");
        }
        return switch (statusCode) {
            case 401, 403 -> new ApiKeyTestResultDTO(false, statusCode, "키가 거부되었습니다. 값을 다시 확인해 주세요.");
            case 429 -> new ApiKeyTestResultDTO(false, statusCode,
                    "키는 인식되었지만 사용량 한도를 넘었습니다. 결제·한도 설정을 확인해 주세요.");
            default -> new ApiKeyTestResultDTO(false, statusCode,
                    "예상치 못한 응답(" + statusCode + ")을 받았습니다.");
        };
    }
}
