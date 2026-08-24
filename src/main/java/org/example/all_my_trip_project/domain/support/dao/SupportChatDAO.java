package org.example.all_my_trip_project.domain.support.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.support.dto.SupportChatMessageDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatRoomDTO;
import org.example.all_my_trip_project.domain.support.mapper.SupportChatMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class SupportChatDAO {

    private final SupportChatMapper mapper;

    public Optional<SupportChatRoomDTO> findOpenRoomByUser(Long userId) {
        return mapper.findOpenRoomByUser(userId);
    }

    public int insertRoom(SupportChatRoomDTO room) { return mapper.insertRoom(room); }

    public Optional<SupportChatRoomDTO> findRoom(Long roomId) { return mapper.findRoom(roomId); }

    public Optional<SupportChatRoomDTO> lockRoom(Long roomId) { return mapper.lockRoom(roomId); }

    public List<SupportChatRoomDTO> findRooms(String status, String keyword, int limit) {
        return mapper.findRooms(status, keyword, limit);
    }

    public int assignRoom(Long roomId, Long adminId) { return mapper.assignRoom(roomId, adminId); }

    public int markWaiting(Long roomId) { return mapper.markWaiting(roomId); }

    public int returnToBot(Long roomId) { return mapper.returnToBot(roomId); }

    public int closeRoom(Long roomId) { return mapper.closeRoom(roomId); }

    public int insertMessage(SupportChatMessageDTO message) { return mapper.insertMessage(message); }

    public Optional<SupportChatMessageDTO> findMessage(Long messageId) { return mapper.findMessage(messageId); }

    public int touchRoom(Long roomId) { return mapper.touchRoom(roomId); }

    public List<SupportChatMessageDTO> findMessages(Long roomId, int limit) {
        return mapper.findMessages(roomId, limit);
    }
}
