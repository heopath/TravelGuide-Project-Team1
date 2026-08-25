package org.example.all_my_trip_project.domain.place.service;

/**
 * 카카오가 주는 한국 주소 문자열에서 places.region/city에 넣을 행정구역을 끊어낸다.
 *
 * <p>같은 컬럼을 채우는 경로가 카카오 검색({@link KakaoLocalPlaceClient})과 AI 일정
 * 저장 둘이라 규칙을 여기에 모은다. 경로마다 다르게 끊으면 같은 장소가 어디로
 * 들어왔느냐에 따라 다른 지역으로 저장되고, 지역 필터 검색 결과가 갈린다.
 */
public final class KoreanAddress {

    private KoreanAddress() {
    }

    /** 첫 토큰이 시·도다. "서울특별시 성동구 성수동1가 10" -> "서울특별시". */
    public static String region(String address) {
        String[] tokens = tokens(address);
        return tokens.length == 0 ? null : tokens[0];
    }

    /**
     * 둘째 토큰이 시·군·구다. "서울특별시 성동구 성수동1가 10" -> "성동구".
     *
     * <p>접미사를 확인하고 넘긴다. 세종특별자치시처럼 시·군·구가 없는 주소는 둘째
     * 토큰이 도로명("한누리대로")이라, 그대로 넣으면 city에 도로가 들어간다.
     */
    public static String city(String address) {
        String[] tokens = tokens(address);
        if (tokens.length < 2) {
            return null;
        }
        String candidate = tokens[1];
        boolean administrativeArea = candidate.endsWith("시")
                || candidate.endsWith("군")
                || candidate.endsWith("구");
        return administrativeArea ? candidate : null;
    }

    private static String[] tokens(String address) {
        if (address == null || address.isBlank()) {
            return new String[0];
        }
        return address.trim().split("\\s+");
    }
}
