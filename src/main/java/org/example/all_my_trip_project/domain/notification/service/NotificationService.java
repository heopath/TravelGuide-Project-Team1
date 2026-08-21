package org.example.all_my_trip_project.domain.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.notification.dao.NotificationDAO;
import org.example.all_my_trip_project.domain.notification.dto.NotificationDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 마이페이지 알림. (#191)
 *
 * <p>알림은 <b>곁다리</b>다. 알림을 못 만들었다고 결제나 답변이 실패해서는 안 된다.
 * 그래서 만들어 넣는 쪽({@link #notify})은 예외를 밖으로 내보내지 않고 로그만 남긴다.
 * 조회·읽음 처리는 손님이 직접 부르는 것이라 실패를 그대로 알린다.
 */
@Slf4j
@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    /** 한 번에 내려주는 최대 개수. 알림은 훑어보는 목록이라 무한정 내릴 이유가 없다. */
    private static final int MAX_SIZE = 50;

    private final NotificationDAO notificationDAO;

    public List<NotificationDTO> findMine(Long userId, int page, int size) {
        requireUser(userId);
        int limit = Math.min(Math.max(size, 1), MAX_SIZE);
        int offset = Math.max(page, 0) * limit;
        return notificationDAO.findMine(userId, offset, limit);
    }

    public long countMine(Long userId) {
        requireUser(userId);
        return notificationDAO.countMine(userId);
    }

    public long countUnread(Long userId) {
        requireUser(userId);
        return notificationDAO.countUnread(userId);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        requireUser(userId);
        /* 값 자체가 이상한 경우는 컨트롤러의 @Positive가 400으로 거른다. */
        if (notificationId == null) return;
        /*
         * 0건이어도 오류로 보지 않는다. 이미 읽었거나 남의 알림이라는 뜻인데, 둘을
         * 갈라 알리면 "그 번호의 알림이 있다"는 사실이 새어 나간다.
         */
        notificationDAO.markRead(userId, notificationId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        requireUser(userId);
        notificationDAO.markAllRead(userId);
    }

    /**
     * 알림을 만들어 넣는다. 실패해도 부르는 쪽을 멈추지 않는다.
     *
     * <p>결제가 끝났는데 알림 저장이 안 됐다고 결제를 되돌리면 손님이 잃는 것이 훨씬
     * 크다. 알림은 없어도 티켓은 마이페이지에 있다.
     *
     * <p>그래서 <b>자기 트랜잭션에서</b> 넣는다. 부르는 쪽 트랜잭션에 참여하면 저장이
     * 실패했을 때 그 트랜잭션이 롤백 전용으로 표시되어 결제까지 되돌아간다. 아래
     * try-catch로는 그것을 막지 못한다.
     *
     * <p>대신 부르는 쪽이 나중에 롤백되면 알림만 남는다. 결제가 안 됐는데 완료 알림이
     * 남는 셈인데, 알림 하나 때문에 끝난 결제를 무르는 것보다는 낫다고 봤다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(Long userId, String type, String title, String body, String link) {
        if (userId == null) return;
        try {
            notificationDAO.insert(NotificationDTO.builder()
                    .userId(userId)
                    .type(type)
                    .title(title)
                    .body(body)
                    .link(link)
                    .build());
        } catch (Exception exception) {
            log.warn("알림을 남기지 못했습니다: user={} type={} 이유={}",
                    userId, type, exception.getMessage());
        }
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
