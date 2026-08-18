package org.example.all_my_trip_project.domain.support.dto;

/**
 * {@code /topic/support-chat/rooms/{roomId}}로 내려가는 WebSocket 이벤트.
 *
 * <p>기존 REST DTO({@link SupportChatMessageDTO}/{@link SupportChatRoomDTO})를 그대로 담는
 * 얇은 envelope다 — 새 스키마를 따로 만들지 않는다(설계 문서 §3).
 */
public record SupportChatSocketEvent(
        String type,
        SupportChatMessageDTO message,
        SupportChatRoomDTO room
) {
    public static SupportChatSocketEvent message(SupportChatMessageDTO message) {
        return new SupportChatSocketEvent("MESSAGE", message, null);
    }

    public static SupportChatSocketEvent roomStatus(SupportChatRoomDTO room) {
        return new SupportChatSocketEvent("ROOM_STATUS", null, room);
    }
}
