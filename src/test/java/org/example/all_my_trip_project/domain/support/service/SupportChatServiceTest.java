package org.example.all_my_trip_project.domain.support.service;

import org.example.all_my_trip_project.domain.support.dao.SupportChatDAO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatMessageDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatRoomDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatSocketEvent;
import org.example.all_my_trip_project.domain.support.dto.SupportChatViewResponse;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportChatServiceTest {

    private static final long USER_ID = 7L;
    private static final long ADMIN_ID = 90L;
    private static final long OTHER_ADMIN_ID = 91L;
    private static final long ROOM_ID = 5L;

    private SupportChatDAO dao;
    private SimpMessagingTemplate messagingTemplate;
    private ApplicationEventPublisher eventPublisher;
    private SupportChatService service;

    @BeforeEach
    void setUp() {
        dao = mock(SupportChatDAO.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new SupportChatService(dao, messagingTemplate, eventPublisher);
        when(dao.findMessages(any(), anyInt())).thenReturn(List.of());
    }

    private SupportChatRoomDTO room(String status, Long assignedAdminId) {
        return SupportChatRoomDTO.builder()
                .supportChatRoomId(ROOM_ID).userId(USER_ID)
                .status(status).assignedAdminId(assignedAdminId).build();
    }

    /* ── 손님 ── */

    @Test
    @DisplayName("열린 상담이 있으면 새로 열지 않고 그 상담을 준다")
    void reusesOpenRoom() {
        when(dao.findOpenRoomByUser(USER_ID)).thenReturn(Optional.of(room("WAITING", null)));

        SupportChatViewResponse result = service.openMyRoom(USER_ID);

        assertThat(result.room().getSupportChatRoomId()).isEqualTo(ROOM_ID);
        verify(dao, never()).insertRoom(any());
    }

    /*
     * 두 창에서 동시에 상담을 열면 부분 유니크 인덱스가 뒤에 온 쪽을 막는다.
     * 실패가 아니라 "이미 열려 있다"이므로 그 방을 줘야 한다.
     */
    @Test
    @DisplayName("동시에 상담을 열면 이미 열린 상담을 돌려준다")
    void returnsExistingRoomWhenOpenRaces() {
        when(dao.findOpenRoomByUser(USER_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(room("WAITING", null)));
        when(dao.insertRoom(any())).thenThrow(new DuplicateKeyException("uk_support_chat_rooms_open_user"));

        SupportChatViewResponse result = service.openMyRoom(USER_ID);

        assertThat(result.room().getSupportChatRoomId()).isEqualTo(ROOM_ID);
    }

    @Test
    @DisplayName("손님이 보낸 메시지는 USER로 남는다")
    void recordsUserMessage() {
        when(dao.findOpenRoomByUser(USER_ID)).thenReturn(Optional.of(room("WAITING", null)));
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(room("WAITING", null)));

        service.sendAsUser(USER_ID, "  문의드립니다  ");

        ArgumentCaptor<SupportChatMessageDTO> saved = ArgumentCaptor.forClass(SupportChatMessageDTO.class);
        verify(dao).insertMessage(saved.capture());
        assertThat(saved.getValue().getSenderType()).isEqualTo("USER");
        assertThat(saved.getValue().getSenderUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getContent()).isEqualTo("문의드립니다");
        /* 목록을 최근 대화순으로 세우려면 방에도 표시해야 한다. */
        verify(dao).touchRoom(ROOM_ID);
    }

    /* ── 봇 ── */

    @Test
    @DisplayName("방을 새로 열면 봇을 부르는 이벤트를 발행한다")
    void publishesBotTriggerOnNewRoom() {
        when(dao.findOpenRoomByUser(USER_ID)).thenReturn(Optional.empty());
        /* 진짜 MyBatis insert는 useGeneratedKeys로 넘긴 객체에 PK를 채워 준다 — mock에서 흉내낸다. */
        when(dao.insertRoom(any())).thenAnswer(invocation -> {
            SupportChatRoomDTO created = invocation.getArgument(0);
            created.setSupportChatRoomId(ROOM_ID);
            return 1;
        });
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(room("BOT", null)));

        service.openMyRoom(USER_ID);

        verify(eventPublisher).publishEvent(any(SupportChatBotTriggerEvent.class));
    }

    @Test
    @DisplayName("이미 열린 방을 그대로 주는 것은 봇을 부르지 않는다")
    void doesNotTriggerBotWhenReusingOpenRoom() {
        when(dao.findOpenRoomByUser(USER_ID)).thenReturn(Optional.of(room("BOT", null)));

        service.openMyRoom(USER_ID);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("봇 응대 중인 방에 평범한 메시지를 보내면 봇을 부르는 이벤트를 발행한다")
    void publishesBotTriggerOnUserMessage() {
        when(dao.findOpenRoomByUser(USER_ID)).thenReturn(Optional.of(room("BOT", null)));
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(room("BOT", null)));

        service.sendAsUser(USER_ID, "환불하고 싶어요");

        verify(eventPublisher).publishEvent(any(SupportChatBotTriggerEvent.class));
        verify(dao, never()).markWaiting(any());
    }

    /* 상담원 요청은 봇을 부를 이유가 없다 — 곧장 사람 대기로 넘긴다. */
    @Test
    @DisplayName("봇 응대 중에 상담원을 찾으면 곧장 대기로 넘기고 봇은 부르지 않는다")
    void handsOffImmediatelyOnExplicitHumanRequest() {
        when(dao.findOpenRoomByUser(USER_ID)).thenReturn(Optional.of(room("BOT", null)));
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(room("BOT", null)));
        when(dao.lockRoom(ROOM_ID)).thenReturn(Optional.of(room("BOT", null)));
        when(dao.markWaiting(ROOM_ID)).thenReturn(1);

        service.sendAsUser(USER_ID, "상담원 연결해 주세요");

        verify(eventPublisher, never()).publishEvent(any());
        verify(dao).markWaiting(ROOM_ID);
        ArgumentCaptor<SupportChatMessageDTO> saved = ArgumentCaptor.forClass(SupportChatMessageDTO.class);
        verify(dao, times(2)).insertMessage(saved.capture());
        assertThat(saved.getAllValues().get(1).getSenderType()).isEqualTo("BOT");
    }

    @Test
    @DisplayName("대기 중인 방에 메시지를 보내는 것은 봇을 부르지 않는다")
    void doesNotTriggerBotWhenRoomIsWaiting() {
        when(dao.findOpenRoomByUser(USER_ID)).thenReturn(Optional.of(room("WAITING", null)));
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(room("WAITING", null)));

        service.sendAsUser(USER_ID, "상담원 있나요");

        verify(eventPublisher, never()).publishEvent(any());
        verify(dao, never()).lockRoom(any());
    }

    @Test
    @DisplayName("봇 답을 저장할 때 방이 여전히 BOT이면 메시지로 남는다")
    void recordsBotReplyWhenStillBot() {
        when(dao.lockRoom(ROOM_ID)).thenReturn(Optional.of(room("BOT", null)));

        service.recordBotReply(ROOM_ID, "환불은 마이페이지에서 신청할 수 있어요.");

        ArgumentCaptor<SupportChatMessageDTO> saved = ArgumentCaptor.forClass(SupportChatMessageDTO.class);
        verify(dao).insertMessage(saved.capture());
        assertThat(saved.getValue().getSenderType()).isEqualTo("BOT");
        assertThat(saved.getValue().getSenderUserId()).isNull();
        verify(dao, never()).markWaiting(any());
    }

    /* 관리자가 그 사이 가져간 방에는 봇의 늦은 답을 끼워 넣지 않는다(설계 문서 §5). */
    @Test
    @DisplayName("봇 답이 오기 전에 관리자가 가져간 방에는 답을 버린다")
    void discardsBotReplyWhenRoomNoLongerBot() {
        when(dao.lockRoom(ROOM_ID)).thenReturn(Optional.of(room("ASSIGNED", ADMIN_ID)));

        service.recordBotReply(ROOM_ID, "환불은 마이페이지에서 신청할 수 있어요.");

        verify(dao, never()).insertMessage(any());
    }

    @Test
    @DisplayName("봇이 넘기면 메시지를 남기고 대기로 바꾼다")
    void recordsBotHandoffWhenStillBot() {
        when(dao.lockRoom(ROOM_ID)).thenReturn(Optional.of(room("BOT", null)));
        when(dao.markWaiting(ROOM_ID)).thenReturn(1);
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(room("WAITING", null)));

        service.recordBotHandoff(ROOM_ID, "상담원에게 연결해 드릴게요.");

        verify(dao).insertMessage(any());
        verify(dao).markWaiting(ROOM_ID);
        ArgumentCaptor<SupportChatSocketEvent> event = ArgumentCaptor.forClass(SupportChatSocketEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/support-chat/rooms/" + ROOM_ID), event.capture());
        assertThat(event.getValue().type()).isEqualTo("ROOM_STATUS");
    }

    @Test
    @DisplayName("봇이 넘기려 할 때 이미 관리자가 가져간 방은 그대로 둔다")
    void discardsBotHandoffWhenRoomNoLongerBot() {
        when(dao.lockRoom(ROOM_ID)).thenReturn(Optional.of(room("ASSIGNED", ADMIN_ID)));

        service.recordBotHandoff(ROOM_ID, "상담원에게 연결해 드릴게요.");

        verify(dao, never()).insertMessage(any());
        verify(dao, never()).markWaiting(any());
    }

    /* ── 내가 응대하기 ── */

    @Test
    @DisplayName("대기 중인 상담을 맡으면 배정된다")
    void takesOverWaitingRoom() {
        when(dao.lockRoom(ROOM_ID)).thenReturn(Optional.of(room("WAITING", null)));
        when(dao.assignRoom(ROOM_ID, ADMIN_ID)).thenReturn(1);
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(room("ASSIGNED", ADMIN_ID)));

        SupportChatViewResponse result = service.takeover(ADMIN_ID, ROOM_ID);

        assertThat(result.room().getStatus()).isEqualTo("ASSIGNED");
        assertThat(result.room().getAssignedToMe()).isTrue();
    }

    /* 봇이 응대하던 방을 사람이 가져가는 것이 `내가 응대하기`의 뜻이다. */
    @Test
    @DisplayName("봇이 응대하던 상담도 사람이 가져갈 수 있다")
    void takesOverBotRoom() {
        when(dao.lockRoom(ROOM_ID)).thenReturn(Optional.of(room("BOT", null)));
        when(dao.assignRoom(ROOM_ID, ADMIN_ID)).thenReturn(1);
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(room("ASSIGNED", ADMIN_ID)));

        assertThat(service.takeover(ADMIN_ID, ROOM_ID).room().getStatus()).isEqualTo("ASSIGNED");
    }

    /*
     * 잠그지 않으면 두 관리자가 같은 방을 각자 맡았다고 믿는다. 손님은 한 명과 이야기하는데
     * 관리자 둘이 답을 준비하는 셈이다.
     */
    @Test
    @DisplayName("다른 관리자가 맡은 상담은 가져갈 수 없다")
    void rejectsTakeoverOfAssignedRoom() {
        when(dao.lockRoom(ROOM_ID)).thenReturn(Optional.of(room("ASSIGNED", OTHER_ADMIN_ID)));

        assertThatThrownBy(() -> service.takeover(ADMIN_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SUPPORT_CHAT_ALREADY_ASSIGNED);

        verify(dao, never()).assignRoom(any(), any());
    }

    @Test
    @DisplayName("내가 이미 맡은 상담을 다시 눌러도 오류가 아니다")
    void takeoverIsIdempotentForSameAdmin() {
        when(dao.lockRoom(ROOM_ID)).thenReturn(Optional.of(room("ASSIGNED", ADMIN_ID)));
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(room("ASSIGNED", ADMIN_ID)));

        assertThat(service.takeover(ADMIN_ID, ROOM_ID).room().getAssignedToMe()).isTrue();
        verify(dao, never()).assignRoom(any(), any());
    }

    /* 잠갔는데도 0건이면 그 사이 다른 관리자가 먼저 가져간 것이다. */
    @Test
    @DisplayName("동시에 눌러 배정에 실패하면 거절한다")
    void rejectsWhenAssignLosesRace() {
        when(dao.lockRoom(ROOM_ID)).thenReturn(Optional.of(room("WAITING", null)));
        when(dao.assignRoom(ROOM_ID, ADMIN_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.takeover(ADMIN_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SUPPORT_CHAT_ALREADY_ASSIGNED);
    }

    @Test
    @DisplayName("종료된 상담은 가져갈 수 없다")
    void rejectsTakeoverOfClosedRoom() {
        when(dao.lockRoom(ROOM_ID)).thenReturn(Optional.of(room("CLOSED", null)));

        assertThatThrownBy(() -> service.takeover(ADMIN_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SUPPORT_CHAT_ROOM_CLOSED);
    }

    /* ── 관리자 답장 ── */

    /*
     * 아무나 끼어들면 손님은 여러 사람이 번갈아 답하는 것을 보게 되고, 누가 맡았는지도 흐려진다.
     */
    @Test
    @DisplayName("맡지 않은 상담에는 답할 수 없다")
    void rejectsReplyWhenNotAssigned() {
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(room("ASSIGNED", OTHER_ADMIN_ID)));

        assertThatThrownBy(() -> service.sendAsAdmin(ADMIN_ID, ROOM_ID, "답변"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SUPPORT_CHAT_NOT_ASSIGNED);

        verify(dao, never()).insertMessage(any());
    }

    @Test
    @DisplayName("아직 아무도 맡지 않은 상담에도 답할 수 없다")
    void rejectsReplyWhenWaiting() {
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(room("WAITING", null)));

        assertThatThrownBy(() -> service.sendAsAdmin(ADMIN_ID, ROOM_ID, "답변"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SUPPORT_CHAT_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("내가 맡은 상담에는 ADMIN으로 답이 남는다")
    void recordsAdminMessage() {
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(room("ASSIGNED", ADMIN_ID)));

        service.sendAsAdmin(ADMIN_ID, ROOM_ID, "확인해 드릴게요");

        ArgumentCaptor<SupportChatMessageDTO> saved = ArgumentCaptor.forClass(SupportChatMessageDTO.class);
        verify(dao).insertMessage(saved.capture());
        assertThat(saved.getValue().getSenderType()).isEqualTo("ADMIN");
        assertThat(saved.getValue().getSenderUserId()).isEqualTo(ADMIN_ID);
    }

    @Test
    @DisplayName("종료된 상담에는 답할 수 없다")
    void rejectsReplyToClosedRoom() {
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(room("CLOSED", null)));

        assertThatThrownBy(() -> service.sendAsAdmin(ADMIN_ID, ROOM_ID, "답변"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SUPPORT_CHAT_ROOM_CLOSED);
    }

    /* ── 목록 ── */

    @Test
    @DisplayName("알 수 없는 상태로는 상담을 거를 수 없다")
    void rejectsUnknownStatusFilter() {
        assertThatThrownBy(() -> service.rooms(ADMIN_ID, "WHATEVER", null, 30))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ADMIN_REQUEST);
    }

    @Test
    @DisplayName("상태 조건은 대소문자를 가리지 않는다")
    void normalizesStatusFilter() {
        when(dao.findRooms(any(), any(), anyInt())).thenReturn(List.of());

        service.rooms(ADMIN_ID, "waiting", "  민재 ", 30);

        verify(dao).findRooms(eq("WAITING"), eq("민재"), anyInt());
    }

    /*
     * 화면이 자기 사용자 번호를 들고 비교하게 두지 않는다. 그 값이 낡으면 남의 방을 내 방으로
     * 보게 된다.
     */
    @Test
    @DisplayName("내가 맡은 상담인지 서버가 표시해 내린다")
    void marksRoomsAssignedToMe() {
        when(dao.findRooms(any(), any(), anyInt())).thenReturn(List.of(
                room("ASSIGNED", ADMIN_ID), room("ASSIGNED", OTHER_ADMIN_ID), room("WAITING", null)));

        List<SupportChatRoomDTO> rooms = service.rooms(ADMIN_ID, null, null, 30);

        assertThat(rooms).extracting(SupportChatRoomDTO::getAssignedToMe)
                .containsExactly(true, false, false);
    }

    @Test
    @DisplayName("한 번에 가져올 수 있는 상담 수를 제한한다")
    void rejectsTooLargeLimit() {
        assertThatThrownBy(() -> service.rooms(ADMIN_ID, null, null, 1000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ADMIN_REQUEST);
    }

    @Test
    @DisplayName("빈 메시지는 보내지 않는다")
    void rejectsBlankMessage() {
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(room("ASSIGNED", ADMIN_ID)));

        assertThatThrownBy(() -> service.sendAsAdmin(ADMIN_ID, ROOM_ID, "   "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SUPPORT_CHAT_REQUEST);
    }
}
