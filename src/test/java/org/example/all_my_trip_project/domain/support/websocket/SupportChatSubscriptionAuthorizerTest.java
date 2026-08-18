package org.example.all_my_trip_project.domain.support.websocket;

import org.example.all_my_trip_project.domain.support.dao.SupportChatDAO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatRoomDTO;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportChatSubscriptionAuthorizerTest {

    private static final long ROOM_ID = 5L;
    private static final long OWNER_ID = 7L;
    private static final long OTHER_USER_ID = 8L;

    private SupportChatDAO dao;
    private SupportChatSubscriptionAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        dao = mock(SupportChatDAO.class);
        authorizer = new SupportChatSubscriptionAuthorizer(dao);
    }

    private SupportChatRoomDTO roomOwnedBy(long userId) {
        return SupportChatRoomDTO.builder().supportChatRoomId(ROOM_ID).userId(userId).build();
    }

    @Test
    @DisplayName("관리자는 아무 방이나 구독할 수 있다")
    void adminCanSubscribeToAnyRoom() {
        AuthenticatedUser admin = new AuthenticatedUser(90L, "admin@example.com", "ADMIN");

        assertThat(authorizer.canSubscribe(admin, ROOM_ID)).isTrue();
        verify(dao, never()).findRoom(any());
    }

    @Test
    @DisplayName("손님은 자기 방만 구독할 수 있다")
    void userCanSubscribeToOwnRoom() {
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(roomOwnedBy(OWNER_ID)));
        AuthenticatedUser owner = new AuthenticatedUser(OWNER_ID, "user@example.com", "USER");

        assertThat(authorizer.canSubscribe(owner, ROOM_ID)).isTrue();
    }

    /* 클라이언트가 보낸 roomId만 믿고 판단하지 않는다 — DB의 실제 주인과 대조한다. */
    @Test
    @DisplayName("손님은 남의 방을 구독할 수 없다")
    void userCannotSubscribeToOthersRoom() {
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.of(roomOwnedBy(OWNER_ID)));
        AuthenticatedUser stranger = new AuthenticatedUser(OTHER_USER_ID, "other@example.com", "USER");

        assertThat(authorizer.canSubscribe(stranger, ROOM_ID)).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 방은 아무도 구독할 수 없다")
    void rejectsUnknownRoom() {
        when(dao.findRoom(ROOM_ID)).thenReturn(Optional.empty());
        AuthenticatedUser user = new AuthenticatedUser(OWNER_ID, "user@example.com", "USER");

        assertThat(authorizer.canSubscribe(user, ROOM_ID)).isFalse();
    }

    @Test
    @DisplayName("로그인하지 않은 요청은 거부한다")
    void rejectsMissingPrincipal() {
        assertThat(authorizer.canSubscribe(null, ROOM_ID)).isFalse();
    }

    @Test
    @DisplayName("방 번호가 없으면 거부한다")
    void rejectsMissingRoomId() {
        AuthenticatedUser user = new AuthenticatedUser(OWNER_ID, "user@example.com", "USER");

        assertThat(authorizer.canSubscribe(user, null)).isFalse();
    }
}
