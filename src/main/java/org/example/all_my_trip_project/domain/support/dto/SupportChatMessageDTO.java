package org.example.all_my_trip_project.domain.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportChatMessageDTO {
    private Long supportChatMessageId;
    private Long supportChatRoomId;
    /** USER · BOT · ADMIN — 봇이 쓴 메시지는 보낸이가 없다. */
    private String senderType;
    private Long senderUserId;
    private String senderNickname;
    private String content;
    /** 봇 답변 뒤에 표시할 내부 화면 이동 액션. URL은 프론트의 허용 목록에서 결정한다. */
    private String actionKey;
    private String actionKey2;
    private String actionKey3;
    private OffsetDateTime createdAt;
}
