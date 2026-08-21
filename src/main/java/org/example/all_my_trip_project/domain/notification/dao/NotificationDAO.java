package org.example.all_my_trip_project.domain.notification.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.notification.dto.NotificationDTO;
import org.example.all_my_trip_project.domain.notification.mapper.NotificationMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class NotificationDAO {
    private final NotificationMapper mapper;

    public int insert(NotificationDTO notification) { return mapper.insert(notification); }

    public List<NotificationDTO> findMine(Long userId, int offset, int limit) {
        return mapper.findMine(userId, offset, limit);
    }

    public long countMine(Long userId) { return mapper.countMine(userId); }

    public long countUnread(Long userId) { return mapper.countUnread(userId); }

    /** 남의 알림을 읽음 처리하지 못하게 사용자 번호를 조건에 함께 넣는다. */
    public int markRead(Long userId, Long notificationId) {
        return mapper.markRead(userId, notificationId);
    }

    public int markAllRead(Long userId) { return mapper.markAllRead(userId); }
}
