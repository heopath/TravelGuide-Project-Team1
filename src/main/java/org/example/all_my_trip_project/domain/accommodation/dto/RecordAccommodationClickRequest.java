package org.example.all_my_trip_project.domain.accommodation.dto;

/**
 * 딥링크 클릭 시점에 보내는 요청.
 *
 * <p><b>항공의 {@code OutboundClickRequest}보다 훨씬 가볍다.</b> 항공은 나가는 순간이
 * 선택을 저장하는 유일한 시점이라 운임 스냅샷을 전부 실어 보냈지만, 숙박은 카드에서
 * "이 숙소 선택"을 누를 때 이미 저장돼 있다. 그래서 여기서는 어디로 나갔는지만 남긴다.
 *
 * @param deeplinkUrl 실제로 연 주소. 나중에 어디로 내보냈는지 대조할 때 쓴다
 */
public record RecordAccommodationClickRequest(
        String deeplinkUrl
) {}
