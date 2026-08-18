package org.example.all_my_trip_project.domain.support.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.support.dao.SupportChatDAO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatMessageDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatRoomDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatViewResponse;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 상담 채팅. 손님 쪽과 관리자 쪽을 한 서비스에서 다룬다.
 *
 * <p>둘로 나누면 방을 찾고 상태를 판단하는 규칙이 두 벌이 되고, 한쪽만 고쳐 어긋나기 쉽다.
 * 대신 <b>누가 부르는지</b>를 메서드 이름과 인자로 분명히 한다.
 *
 * <p><b>챗봇이 들어올 자리를 비워 두었다.</b> 보낸이 종류에 {@code BOT}이 있고 방 상태에도
 * {@code BOT}이 있다. 봇이 붙으면 방을 {@code BOT}으로 시작시키고 봇이 쓴 메시지를 넣으면
 * 되며, {@code 내가 응대하기}는 그 방을 사람에게 넘기는 동작으로 그대로 성립한다.
 */
@Service
@Profile("!ui")
@RequiredArgsConstructor
public class SupportChatService {

    /** 대화창이 한 번에 읽는 메시지 수. 상담 하나가 이보다 길어지는 일은 드물다. */
    private static final int MAX_MESSAGES = 200;
    private static final int MAX_ROOMS = 100;

    private final SupportChatDAO supportChatDAO;

    /* ── 손님 ── */

    /**
     * 상담을 시작하거나 이어간다.
     *
     * <p>열린 방이 있으면 그 방을 준다. 손님당 열린 방은 하나로 제한돼 있어, 새로 열면
     * 관리자가 같은 사람과 여러 창구에서 이야기하게 된다.
     */
    @Transactional
    public SupportChatViewResponse openMyRoom(Long userId) {
        requireUser(userId);
        SupportChatRoomDTO room = supportChatDAO.findOpenRoomByUser(userId).orElse(null);
        if (room == null) {
            SupportChatRoomDTO created = SupportChatRoomDTO.builder().userId(userId).build();
            try {
                supportChatDAO.insertRoom(created);
            } catch (DuplicateKeyException exception) {
                /*
                 * 두 창에서 동시에 상담을 열면 부분 유니크 인덱스가 뒤에 온 쪽을 막는다.
                 * 실패가 아니라 "이미 열려 있다"이므로 그 방을 준다.
                 */
                return view(requireOpenRoom(userId));
            }
            room = requireRoom(created.getSupportChatRoomId());
        }
        return view(room);
    }

    @Transactional(readOnly = true)
    public SupportChatViewResponse myRoom(Long userId) {
        requireUser(userId);
        return view(requireOpenRoom(userId));
    }

    @Transactional
    public SupportChatViewResponse sendAsUser(Long userId, String content) {
        requireUser(userId);
        SupportChatRoomDTO room = requireOpenRoom(userId);
        append(room.getSupportChatRoomId(), "USER", userId, content);
        return view(requireRoom(room.getSupportChatRoomId()));
    }

    /* ── 관리자 ── */

    @Transactional(readOnly = true)
    public List<SupportChatRoomDTO> rooms(Long adminId, String status, String keyword, int limit) {
        if (limit < 1 || limit > MAX_ROOMS) {
            throw new BusinessException(ErrorCode.INVALID_ADMIN_REQUEST);
        }
        String normalized = upper(status);
        if (normalized != null
                && !List.of("BOT", "WAITING", "ASSIGNED", "CLOSED").contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_ADMIN_REQUEST);
        }
        List<SupportChatRoomDTO> rooms = supportChatDAO.findRooms(normalized, text(keyword), limit);
        rooms.forEach(room -> markMine(room, adminId));
        return rooms;
    }

    @Transactional(readOnly = true)
    public SupportChatViewResponse room(Long adminId, Long roomId) {
        return view(markMine(requireRoom(roomId), adminId));
    }

    /**
     * 지금 보고 있는 관리자가 맡은 방인지 표시한다.
     *
     * <p>화면이 자기 사용자 번호를 들고 비교하게 두지 않는다. 그러려면 로그인한 사람의 번호를
     * 어딘가에 심어야 하고, 그 값이 낡으면 남의 방을 내 방으로 보게 된다.
     */
    private SupportChatRoomDTO markMine(SupportChatRoomDTO room, Long adminId) {
        room.setAssignedToMe(adminId != null && adminId.equals(room.getAssignedAdminId()));
        return room;
    }

    /**
     * 내가 응대한다. 봇이 응대하던 방도 여기서 사람에게 넘어온다.
     *
     * <p>방을 잠그고 바꾼다. 두 관리자가 동시에 누르면 뒤에 온 쪽이 앞의 결과를 보고 거부된다.
     * 잠그지 않으면 둘 다 "아직 아무도 안 맡았다"를 보고 통과해, 손님은 한 명과 이야기하는데
     * 관리자 둘이 각자 맡았다고 믿게 된다.
     */
    @Transactional
    public SupportChatViewResponse takeover(Long adminId, Long roomId) {
        requireUser(adminId);
        SupportChatRoomDTO locked = supportChatDAO.lockRoom(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_CHAT_ROOM_NOT_FOUND));

        if ("ASSIGNED".equals(locked.getStatus())) {
            /* 내가 이미 맡은 방을 다시 누른 것은 오류가 아니다. */
            if (adminId.equals(locked.getAssignedAdminId())) return view(markMine(requireRoom(roomId), adminId));
            throw new BusinessException(ErrorCode.SUPPORT_CHAT_ALREADY_ASSIGNED);
        }
        if ("CLOSED".equals(locked.getStatus())) {
            throw new BusinessException(ErrorCode.SUPPORT_CHAT_ROOM_CLOSED);
        }
        if (supportChatDAO.assignRoom(roomId, adminId) != 1) {
            throw new BusinessException(ErrorCode.SUPPORT_CHAT_ALREADY_ASSIGNED);
        }
        return view(markMine(requireRoom(roomId), adminId));
    }

    /**
     * 관리자가 답한다.
     *
     * <p>맡지 않은 방에는 쓸 수 없다. 아무나 끼어들면 손님은 여러 사람이 번갈아 답하는 것을
     * 보게 되고, 누가 맡았는지도 흐려진다. 먼저 {@code 내가 응대하기}를 눌러야 한다.
     */
    @Transactional
    public SupportChatViewResponse sendAsAdmin(Long adminId, Long roomId, String content) {
        requireUser(adminId);
        SupportChatRoomDTO room = requireRoom(roomId);
        if ("CLOSED".equals(room.getStatus())) {
            throw new BusinessException(ErrorCode.SUPPORT_CHAT_ROOM_CLOSED);
        }
        if (!adminId.equals(room.getAssignedAdminId())) {
            throw new BusinessException(ErrorCode.SUPPORT_CHAT_NOT_ASSIGNED);
        }
        append(roomId, "ADMIN", adminId, content);
        return view(markMine(requireRoom(roomId), adminId));
    }

    @Transactional
    public SupportChatViewResponse close(Long adminId, Long roomId) {
        requireUser(adminId);
        SupportChatRoomDTO room = requireRoom(roomId);
        if ("CLOSED".equals(room.getStatus())) return view(markMine(room, adminId));
        supportChatDAO.closeRoom(roomId);
        return view(markMine(requireRoom(roomId), adminId));
    }

    /* ── 공통 ── */

    private void append(Long roomId, String senderType, Long senderUserId, String content) {
        String normalized = text(content);
        if (normalized == null) throw new BusinessException(ErrorCode.INVALID_SUPPORT_CHAT_REQUEST);
        supportChatDAO.insertMessage(SupportChatMessageDTO.builder()
                .supportChatRoomId(roomId)
                .senderType(senderType)
                .senderUserId(senderUserId)
                .content(normalized)
                .build());
        /* 목록을 최근 대화순으로 세우려면 방에도 표시해야 한다. */
        supportChatDAO.touchRoom(roomId);
    }

    private SupportChatViewResponse view(SupportChatRoomDTO room) {
        return new SupportChatViewResponse(room,
                supportChatDAO.findMessages(room.getSupportChatRoomId(), MAX_MESSAGES));
    }

    private SupportChatRoomDTO requireOpenRoom(Long userId) {
        return supportChatDAO.findOpenRoomByUser(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_CHAT_ROOM_NOT_FOUND));
    }

    private SupportChatRoomDTO requireRoom(Long roomId) {
        if (roomId == null || roomId < 1) {
            throw new BusinessException(ErrorCode.SUPPORT_CHAT_ROOM_NOT_FOUND);
        }
        return supportChatDAO.findRoom(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_CHAT_ROOM_NOT_FOUND));
    }

    private void requireUser(Long userId) {
        if (userId == null || userId < 1) throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String upper(String value) {
        String normalized = text(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
