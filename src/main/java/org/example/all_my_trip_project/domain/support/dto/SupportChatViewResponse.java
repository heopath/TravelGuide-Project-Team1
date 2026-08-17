package org.example.all_my_trip_project.domain.support.dto;

import java.util.List;

/**
 * 상담방과 그 안의 메시지를 한 덩어리로 돌려준다.
 *
 * <p>방과 메시지를 따로 부르게 두면 화면이 두 번 호출하고, 그 사이에 상태가 갈릴 수 있다.
 * 폴링으로 갱신하는 화면이라 호출 수를 줄이는 편이 낫다.
 */
public record SupportChatViewResponse(
        SupportChatRoomDTO room,
        List<SupportChatMessageDTO> messages
) {}
