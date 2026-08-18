package org.example.all_my_trip_project.domain.support.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.support.dao.SupportChatDAO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatMessageDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatRoomDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatSocketEvent;
import org.example.all_my_trip_project.domain.support.dto.SupportChatViewResponse;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Locale;

/**
 * 상담 채팅. 손님 쪽과 관리자 쪽을 한 서비스에서 다룬다.
 *
 * <p>둘로 나누면 방을 찾고 상태를 판단하는 규칙이 두 벌이 되고, 한쪽만 고쳐 어긋나기 쉽다.
 * 대신 <b>누가 부르는지</b>를 메서드 이름과 인자로 분명히 한다.
 *
 * <p><b>챗봇이 붙었다.</b> 방을 {@code BOT} 상태로 시작시키고 {@link SupportChatBotClient}로
 * 첫 인사·이후 답변을 만든다. 봇이 상담원 연결이 필요하다고 판단하면(또는 호출 자체가 실패하면)
 * {@code WAITING}으로 넘기며, 그 뒤로는 기존 {@code 내가 응대하기} 흐름을 그대로 탄다.
 *
 * <p>Gemini 호출은 이 클래스 밖, {@link SupportChatBotOrchestrator}(별도 빈, {@code @Async})에서
 * 트랜잭션 커밋 뒤에 한다 — 외부 API 응답을 기다리며 DB 트랜잭션을 오래 붙잡지 않기 위해서다
 * (설계 문서 §5). 이 클래스는 그 결과를 저장하는 {@link #recordBotReply}/{@link #recordBotHandoff}만
 * 제공하며, 두 메서드 모두 저장 직전 방을 잠그고 상태를 다시 확인해 그 사이 관리자가
 * {@code takeover}로 가져간 방에 뒤늦게 봇 답변이 끼어들지 않게 한다.
 */
@Service
@Profile("!ui")
@RequiredArgsConstructor
public class SupportChatService {

    /** 대화창이 한 번에 읽는 메시지 수. 상담 하나가 이보다 길어지는 일은 드물다. */
    private static final int MAX_MESSAGES = 200;
    private static final int MAX_ROOMS = 100;

    private static final String ROOM_TOPIC_PREFIX = "/topic/support-chat/rooms/";

    /** 봇 호출 없이 곧장 사람을 붙여도 되는, 손님이 명시적으로 상담원을 찾는 표현들. */
    private static final List<String> HUMAN_HANDOFF_KEYWORDS =
            List.of("상담원", "상담사", "사람이랑", "사람과 얘기", "사람 연결", "직원 연결", "실제 사람");

    private static final String HUMAN_REQUEST_REPLY = "상담원에게 연결해 드릴게요. 잠시만 기다려 주세요.";

    private final SupportChatDAO supportChatDAO;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /* ── 손님 ── */

    /**
     * 상담을 시작하거나 이어간다.
     *
     * <p>열린 방이 있으면 그 방을 준다. 손님당 열린 방은 하나로 제한돼 있어, 새로 열면
     * 관리자가 같은 사람과 여러 창구에서 이야기하게 된다.
     *
     * <p>새로 연 방은 봇의 첫 인사를 기다려야 한다. 인사는 이 메서드가 커밋된 뒤
     * {@link SupportChatBotOrchestrator}가 비동기로 채우므로, 이 응답에는 아직 담기지 않는다.
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
            eventPublisher.publishEvent(new SupportChatBotTriggerEvent(room.getSupportChatRoomId()));
        }
        return view(room);
    }

    @Transactional(readOnly = true)
    public SupportChatViewResponse myRoom(Long userId) {
        requireUser(userId);
        return view(requireOpenRoom(userId));
    }

    /**
     * 손님이 보낸다.
     *
     * <p>방이 아직 봇 응대 중이면, 손님이 상담원을 대놓고 찾는 경우에만 여기서 곧장
     * {@code WAITING}으로 넘긴다(봇을 부를 이유가 없다). 그 밖에는 봇을 부르는 이벤트를
     * 발행하고, 실제 호출과 판단은 {@link SupportChatBotOrchestrator}에 맡긴다.
     */
    @Transactional
    public SupportChatViewResponse sendAsUser(Long userId, String content) {
        requireUser(userId);
        SupportChatRoomDTO room = requireOpenRoom(userId);
        append(room.getSupportChatRoomId(), "USER", userId, content);

        if ("BOT".equals(room.getStatus())) {
            if (requestsHuman(content)) {
                recordBotHandoff(room.getSupportChatRoomId(), HUMAN_REQUEST_REPLY);
            } else {
                eventPublisher.publishEvent(new SupportChatBotTriggerEvent(room.getSupportChatRoomId()));
            }
        }
        return view(requireRoom(room.getSupportChatRoomId()));
    }

    private boolean requestsHuman(String content) {
        String normalized = content == null ? "" : content;
        return HUMAN_HANDOFF_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    /* ── 봇(비동기 오케스트레이터 전용) ── */

    /** Gemini를 부르기 전에 값싸게 거르는 사전 확인. 최종 판단은 저장 시점에 다시 한다. */
    @Transactional(readOnly = true)
    public boolean isStillBot(Long roomId) {
        return supportChatDAO.findRoom(roomId).map(r -> "BOT".equals(r.getStatus())).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<SupportChatMessageDTO> recentMessages(Long roomId) {
        return supportChatDAO.findMessages(roomId, MAX_MESSAGES);
    }

    /**
     * 봇의 답을 저장한다.
     *
     * <p>저장 직전 방을 잠그고 여전히 {@code BOT}인지 다시 본다. Gemini를 부르는 동안 관리자가
     * {@code takeover}로 이미 가져갔다면(더 이상 {@code BOT}이 아니라면) 이 답은 버린다 — 이미
     * 사람이 응대를 시작한 방에 봇 답변이 뒤늦게 끼어드는 것을 막는다(설계 문서 §5).
     */
    @Transactional
    public void recordBotReply(Long roomId, String content) {
        SupportChatRoomDTO locked = supportChatDAO.lockRoom(roomId).orElse(null);
        if (locked == null || !"BOT".equals(locked.getStatus())) return;
        append(roomId, "BOT", null, content);
    }

    /** 봇이 스스로 상담원에게 넘긴다(사용자 요청 / Gemini 실패 / 정책 범위 밖 / 반복 미해결). */
    @Transactional
    public void recordBotHandoff(Long roomId, String content) {
        SupportChatRoomDTO locked = supportChatDAO.lockRoom(roomId).orElse(null);
        if (locked == null || !"BOT".equals(locked.getStatus())) return;
        append(roomId, "BOT", null, content);
        if (supportChatDAO.markWaiting(roomId) == 1) {
            broadcastRoomStatus(roomId);
        }
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
        broadcastRoomStatus(roomId);
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
        broadcastRoomStatus(roomId);
        return view(markMine(requireRoom(roomId), adminId));
    }

    /* ── 공통 ── */

    private void append(Long roomId, String senderType, Long senderUserId, String content) {
        String normalized = text(content);
        if (normalized == null) throw new BusinessException(ErrorCode.INVALID_SUPPORT_CHAT_REQUEST);
        SupportChatMessageDTO toInsert = SupportChatMessageDTO.builder()
                .supportChatRoomId(roomId)
                .senderType(senderType)
                .senderUserId(senderUserId)
                .content(normalized)
                .build();
        supportChatDAO.insertMessage(toInsert);
        /* 목록을 최근 대화순으로 세우려면 방에도 표시해야 한다. */
        supportChatDAO.touchRoom(roomId);
        broadcastMessage(roomId, toInsert.getSupportChatMessageId());
    }

    /**
     * 방금 넣은 메시지를 구독자에게 내려보낸다.
     *
     * <p>{@code insertMessage}는 PK만 채워 주므로, 닉네임·저장 시각까지 채운 완전한 형태로
     * 다시 읽어 보낸다. REST 응답과 같은 {@link SupportChatMessageDTO}를 그대로 쓴다(설계
     * 문서 §3) — WebSocket 전용 스키마를 새로 만들지 않는다.
     *
     * <p>트랜잭션이 커밋된 뒤에만 내보낸다. 커밋 전에 보내면, 이후 어떤 이유로든 롤백됐을 때
     * 구독자가 존재하지 않는 메시지를 이미 화면에 그린 상태가 된다.
     */
    private void broadcastMessage(Long roomId, Long messageId) {
        afterCommit(() -> supportChatDAO.findMessage(messageId).ifPresent(message ->
                messagingTemplate.convertAndSend(
                        ROOM_TOPIC_PREFIX + roomId, SupportChatSocketEvent.message(message))));
    }

    private void broadcastRoomStatus(Long roomId) {
        afterCommit(() -> supportChatDAO.findRoom(roomId).ifPresent(room ->
                messagingTemplate.convertAndSend(
                        ROOM_TOPIC_PREFIX + roomId, SupportChatSocketEvent.roomStatus(room))));
    }

    /** 활성 트랜잭션이 있으면 커밋 후로 미루고, 없으면(단위 테스트 등) 곧바로 실행한다. */
    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
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
