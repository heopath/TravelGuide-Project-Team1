package org.example.all_my_trip_project.domain.accommodation.service;

/**
 * 숙소를 예약할 수 있는 외부 페이지 주소 생성.
 *
 * <p><b>항공과 사정이 다르다.</b> 항공은 캐리어별 공식 예약 페이지 주소가 있어
 * 템플릿으로 조합했지만, 숙소는 TourAPI가 홈페이지를 주지 않는다. 검증되지 않은 주소를
 * 추측해서 내보내는 것은 #132에서 이미 겪은 실패라, 검색 결과로 보내고 사용자가
 * 실제 예약 채널을 고르게 한다.
 *
 * <p>제휴가 승인되면 이 인터페이스의 구현만 갈아끼운다. provider와 화면은 손대지 않는다.
 */
public interface AccommodationDeeplinkBuilder {

    /**
     * @param name      숙소명
     * @param areaLabel 지역. 같은 이름의 숙소가 여러 지역에 있어 함께 넣는다
     * @return 외부 페이지 주소. 만들 수 없으면 빈 문자열
     */
    String build(String name, String areaLabel);
}
