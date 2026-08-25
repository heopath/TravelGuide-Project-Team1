package org.example.all_my_trip_project.domain.support.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.support.dto.SupportChatMessageDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatMessageBlockDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatRoomDTO;
import org.example.all_my_trip_project.domain.support.mapper.SupportChatMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;

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

    public int insertMessageBlock(SupportChatMessageBlockDTO block) { return mapper.insertMessageBlock(block); }

    public Optional<SupportChatMessageDTO> findMessage(Long messageId) {
        Optional<SupportChatMessageDTO> message = mapper.findMessage(messageId);
        message.ifPresent(value -> hydrateBlocks(List.of(value)));
        return message;
    }

    public int touchRoom(Long roomId) { return mapper.touchRoom(roomId); }

    public List<SupportChatMessageDTO> findMessages(Long roomId, int limit) {
        List<SupportChatMessageDTO> messages = mapper.findMessages(roomId, limit);
        hydrateBlocks(messages);
        return messages;
    }

    private void hydrateBlocks(List<SupportChatMessageDTO> messages) {
        if (messages.isEmpty()) return;
        Map<Long, List<SupportChatMessageBlockDTO>> blocksByMessage = mapper.findMessageBlocks(
                        messages.stream().map(SupportChatMessageDTO::getSupportChatMessageId).toList())
                .stream().collect(Collectors.groupingBy(SupportChatMessageBlockDTO::getSupportChatMessageId));
        messages.forEach(message -> message.setBlocks(
                blocksByMessage.getOrDefault(message.getSupportChatMessageId(), List.of())));
    }
}
