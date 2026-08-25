package org.example.all_my_trip_project.domain.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

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
    /** 화면 표현 전용 블록. 방 상태나 검색 조건처럼 운영 판단에 필요한 값은 넣지 않는다. */
    @Builder.Default
    private List<SupportChatMessageBlockDTO> blocks = List.of();
    private OffsetDateTime createdAt;
}
