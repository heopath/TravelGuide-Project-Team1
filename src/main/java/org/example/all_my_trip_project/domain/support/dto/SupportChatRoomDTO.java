package org.example.all_my_trip_project.domain.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 상담방 한 개.
 *
 * <p>{@code BOT} 상태는 챗봇이 들어올 자리다. 봇이 없는 동안은 그 상태의 방이 생기지 않을
 * 뿐, 관리자 화면은 처음부터 그 칸을 다룬다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportChatRoomDTO {
    private Long supportChatRoomId;
    private Long userId;
    private String userNickname;
    private String userEmail;
    private Long assignedAdminId;
    private String assignedAdminNickname;
    /** BOT · WAITING · ASSIGNED · CLOSED */
    private String status;
    private OffsetDateTime lastMessageAt;
    private OffsetDateTime assignedAt;
    private OffsetDateTime closedAt;
    private OffsetDateTime createdAt;

    /** 목록에서 어떤 대화인지 알아볼 수 있게 마지막 한 줄을 함께 내린다. */
    private String lastMessagePreview;
    private Integer messageCount;

    /**
     * 지금 보고 있는 관리자가 맡은 방인지. 서버가 계산해 내린다.
     *
     * <p>화면이 자기 사용자 번호를 알고 비교하게 두지 않는다. 그러려면 로그인한 사람의 번호를
     * 어딘가에 심어야 하는데, 그 값이 낡으면 남의 방을 내 방으로 보게 된다.
     */
    private Boolean assignedToMe;
}
