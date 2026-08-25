package org.example.all_my_trip_project.domain.support.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.support.dto.SupportChatMessageDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatMessageBlockDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatRoomDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SupportChatMapper {

    /** 손님의 열린 방. 손님당 하나로 제한돼 있어 최대 한 건이다. */
    Optional<SupportChatRoomDTO> findOpenRoomByUser(@Param("userId") Long userId);

    int insertRoom(SupportChatRoomDTO room);

    Optional<SupportChatRoomDTO> findRoom(@Param("roomId") Long roomId);

    /**
     * 상태를 바꾸기 전에 방을 잠근다.
     *
     * <p>두 관리자가 같은 방의 {@code 내가 응대하기}를 동시에 누르면, 잠그지 않을 경우 둘 다
     * "아직 아무도 안 맡았다"를 보고 통과해 나중 쓰기가 앞사람을 덮는다. 손님은 한 명과
     * 이야기하는데 관리자 둘이 각자 맡았다고 믿게 된다.
     */
    Optional<SupportChatRoomDTO> lockRoom(@Param("roomId") Long roomId);

    List<SupportChatRoomDTO> findRooms(@Param("status") String status,
                                       @Param("keyword") String keyword,
                                       @Param("limit") int limit);

    /** {@code WAITING}이나 {@code BOT}일 때만 배정한다. 이미 배정된 방은 0건이 된다. */
    int assignRoom(@Param("roomId") Long roomId, @Param("adminId") Long adminId);

    /**
     * 봇이 사람에게 넘긴다. {@code BOT}일 때만 바뀐다.
     *
     * <p>0건이면 그 사이 관리자가 이미 {@code takeover}로 가져갔다는 뜻이다 — 봇 응답을 저장하기
     * 직전 방 상태를 재확인하는 경쟁 조건 검사(설계 문서 §5)에 이 반환값을 그대로 쓴다.
     */
    int markWaiting(@Param("roomId") Long roomId);

    int returnToBot(@Param("roomId") Long roomId);

    int closeRoom(@Param("roomId") Long roomId);

    int insertMessage(SupportChatMessageDTO message);

    int insertMessageBlock(SupportChatMessageBlockDTO block);

    List<SupportChatMessageBlockDTO> findMessageBlocks(@Param("messageIds") List<Long> messageIds);

    /**
     * 방금 넣은 메시지를 닉네임·저장 시각까지 채워 다시 읽는다. {@code insertMessage}는 PK만
     * 채워 주므로, WebSocket으로 그대로 내보낼 완전한 형태가 필요할 때 쓴다.
     */
    Optional<SupportChatMessageDTO> findMessage(@Param("messageId") Long messageId);

    /** 방 목록을 최근 대화순으로 세우려면 방마다 들고 있어야 한다. */
    int touchRoom(@Param("roomId") Long roomId);

    List<SupportChatMessageDTO> findMessages(@Param("roomId") Long roomId,
                                             @Param("limit") int limit);
}
