package org.example.all_my_trip_project.domain.notification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.notification.dto.NotificationDTO;

import java.util.List;

@Mapper
public interface NotificationMapper {
    int insert(NotificationDTO notification);

    List<NotificationDTO> findMine(@Param("userId") Long userId,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    long countMine(@Param("userId") Long userId);

    long countUnread(@Param("userId") Long userId);

    int markRead(@Param("userId") Long userId,
                 @Param("notificationId") Long notificationId);

    int markAllRead(@Param("userId") Long userId);
}
