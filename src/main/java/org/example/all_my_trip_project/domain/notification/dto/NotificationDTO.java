package org.example.all_my_trip_project.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 알림 한 건. (#191)
 *
 * <p>문구를 만들어질 때 그대로 담는다. 예약이나 문의를 조인해 그때그때 문장을 만들지
 * 않는다 — 원본이 지워지거나 바뀌어도 손님이 받은 알림은 그대로여야 하고, 목록을 그릴
 * 때마다 여러 표를 조인하면 알림이 늘수록 느려진다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long notificationId;
    private Long userId;

    /** 화면이 이 값으로 아이콘을 고른다. DB의 CHECK 제약과 같은 값이어야 한다. */
    private String type;

    private String title;
    private String body;

    /** 눌렀을 때 갈 곳. 우리 사이트 안의 경로만 담는다. */
    private String link;

    /** 읽은 시각. 비어 있으면 안 읽은 것이다. */
    private OffsetDateTime readAt;

    private OffsetDateTime createdAt;
}
