package org.example.all_my_trip_project.domain.weather.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.all_my_trip_project.domain.weather.dto.WeatherResponse;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class WeatherService {
    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${weather.service-key:}")
    private String serviceKey;

    public WeatherResponse getWeather(double latitude, double longitude, String date, String time) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new BusinessException(ErrorCode.WEATHER_NOT_CONFIGURED);
        }
        LocalDate visitDate = LocalDate.parse(date);
        long daysFromToday = ChronoUnit.DAYS.between(LocalDate.now(), visitDate);
        if (daysFromToday < 0 || daysFromToday > 3) {
            throw new BusinessException(ErrorCode.WEATHER_DATE_OUT_OF_RANGE);
        }

        Grid grid = convertToGrid(latitude, longitude);
        LocalDateTime base = LocalDateTime.now().minusMinutes(40);
        String baseDate = base.getHour() < 2
                ? base.toLocalDate().minusDays(1).format(API_DATE)
                : base.toLocalDate().format(API_DATE);
        String baseTime = baseTime(base.getHour());
        String url = UriComponentsBuilder
                .fromUriString("https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst")
                .queryParam("serviceKey", serviceKey)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 1000)
                .queryParam("dataType", "JSON")
                .queryParam("base_date", baseDate)
                .queryParam("base_time", baseTime)
                .queryParam("nx", grid.x())
                .queryParam("ny", grid.y())
                .build(true)
                .toUriString();

        String json = RestClient.create().get().uri(url).retrieve().body(String.class);
        List<JsonNode> items = forecastItems(json);
        String targetDate = visitDate.format(API_DATE);
        String targetTime = targetTime(time);
        String sky = value(items, targetDate, targetTime, "SKY");
        String precipitationType = value(items, targetDate, targetTime, "PTY");
        String temperature = value(items, targetDate, targetTime, "TMP");
        String rainPercent = value(items, targetDate, targetTime, "POP");
        String weatherType = weatherType(sky, precipitationType);
        int rain = parseInt(rainPercent);
        return new WeatherResponse(date, weatherType, icon(weatherType),
                temperature == null ? "-" : temperature,
                rainPercent == null ? "-" : rainPercent,
                rain >= 60 ? "실내 일정 추천" : rain >= 40 ? "실내·야외 혼합" : "야외 일정 적합",
                message(weatherType, rain));
    }

    private List<JsonNode> forecastItems(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode nodes = root.path("response").path("body").path("items").path("item");
            if (!nodes.isArray()) return List.of();
            List<JsonNode> result = new ArrayList<>();
            nodes.forEach(result::add);
            return result;
        } catch (Exception error) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "기상청 응답을 처리하지 못했습니다.", error);
        }
    }

    private String value(List<JsonNode> items, String date, String time, String category) {
        return items.stream()
                .filter(item -> category.equals(item.path("category").asText()))
                .filter(item -> date.equals(item.path("fcstDate").asText()))
                .min(Comparator.comparingInt(item -> Math.abs(
                        parseInt(item.path("fcstTime").asText()) - parseInt(time))))
                .map(item -> item.path("fcstValue").asText())
                .orElse(null);
    }

    private String targetTime(String time) {
        if (time == null || time.isBlank() || time.length() < 2) return "1200";
        return time.substring(0, 2) + "00";
    }

    private String baseTime(int hour) {
        if (hour < 2) return "2300";
        if (hour < 5) return "0200";
        if (hour < 8) return "0500";
        if (hour < 11) return "0800";
        if (hour < 14) return "1100";
        if (hour < 17) return "1400";
        if (hour < 20) return "1700";
        if (hour < 23) return "2000";
        return "2300";
    }

    private String weatherType(String sky, String pty) {
        if (pty != null && !"0".equals(pty)) return switch (pty) {
            case "1" -> "비"; case "2" -> "비/눈"; case "3" -> "눈"; case "4" -> "소나기"; default -> "강수";
        };
        return switch (sky == null ? "" : sky) {
            case "1" -> "맑음"; case "3" -> "구름 많음"; case "4" -> "흐림"; default -> "정보 없음";
        };
    }

    private String icon(String type) {
        return switch (type) {
            case "맑음" -> "☀️"; case "구름 많음" -> "⛅"; case "흐림" -> "☁️";
            case "비", "소나기", "강수" -> "🌧️"; case "눈", "비/눈" -> "❄️"; default -> "🌤️";
        };
    }

    private String message(String type, int rain) {
        if (rain >= 60) return "강수 가능성이 높아 우산과 실내 일정을 함께 준비하세요.";
        if ("맑음".equals(type)) return "야외 관광과 산책 코스를 즐기기 좋은 날씨입니다.";
        if ("흐림".equals(type)) return "야외 일정과 실내 일정을 적절히 섞어보세요.";
        return "이동 거리와 방문 시간을 함께 확인해보세요.";
    }

    private int parseInt(String value) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return 0; }
    }

    private Grid convertToGrid(double lat, double lng) {
        double re = 6371.00877 / 5.0, degrad = Math.PI / 180.0;
        double slat1 = 30.0 * degrad, slat2 = 60.0 * degrad;
        double olon = 126.0 * degrad, olat = 38.0 * degrad;
        double sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) /
                Math.log(Math.tan(Math.PI * .25 + slat2 * .5) / Math.tan(Math.PI * .25 + slat1 * .5));
        double sf = Math.pow(Math.tan(Math.PI * .25 + slat1 * .5), sn) * Math.cos(slat1) / sn;
        double ro = re * sf / Math.pow(Math.tan(Math.PI * .25 + olat * .5), sn);
        double ra = re * sf / Math.pow(Math.tan(Math.PI * .25 + lat * degrad * .5), sn);
        double theta = lng * degrad - olon;
        if (theta > Math.PI) theta -= 2.0 * Math.PI;
        if (theta < -Math.PI) theta += 2.0 * Math.PI;
        theta *= sn;
        return new Grid((int) Math.floor(ra * Math.sin(theta) + 43 + .5),
                (int) Math.floor(ro - ra * Math.cos(theta) + 136 + .5));
    }

    private record Grid(int x, int y) {}
}
