package org.example.all_my_trip_project.domain.place.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.place.dao.PlaceViewHistoryDAO;
import org.example.all_my_trip_project.domain.place.dto.RecentPlaceResult;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 마이페이지 "최근 본 여행지"에 쓰는 조회 이력.
 *
 * <p>브라우저가 아니라 서버에 남긴다. 이 패널은 로그인해야 들어가는 마이페이지 안에 있어서,
 * 기기별 저장소에 두면 같은 계정인데 PC와 폰에서 다른 목록이 보인다.
 *
 * <p>기록은 조회(GET)에 얹지 않고 별도 요청으로 받는다. {@code PlaceService.getDetail()}은
 * 캐시를 타므로 그 안에 넣으면 캐시가 맞을 때 기록이 빠진다.
 */
@Service
@Profile("!ui")
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PlaceViewHistoryService {

    /** 화면에 보여주는 최대 개수. */
    public static final int DEFAULT_SIZE = 12;
    /** 사용자당 보관 상한. 없으면 계정마다 이력이 끝없이 쌓인다. */
    private static final int KEEP_PER_USER = 30;
    private static final int MAX_SIZE = KEEP_PER_USER;

    private final PlaceViewHistoryDAO placeViewHistoryDAO;

    /**
     * 장소를 열어봤다고 남긴다.
     *
     * <p>기록이 실패해도 상세 화면은 그대로 보여야 한다. 이력은 부가 정보이고,
     * 이것 때문에 장소를 못 보는 쪽이 훨씬 나쁘다. 실패는 로그로만 남긴다.
     *
     * <p>{@code NOT_SUPPORTED}로 트랜잭션 밖에서 돈다. 이유가 둘이다.
     *
     * <p>하나. 이 클래스는 {@code @Transactional(readOnly = true)}라, 아무것도 안 붙이면
     * 읽기 전용 트랜잭션을 물려받아 INSERT가 거부된다. 게다가 아래에서 예외를 삼키므로
     * 조용히 실패한다. 실제로 그렇게 한 번 나갔고 화면에는 아무것도 쌓이지 않았다.
     *
     * <p>둘. 쓰기 트랜잭션으로 바꿔도 안 된다. 트랜잭션 안에서 SQL이 실패하면 PostgreSQL이
     * 그 트랜잭션을 통째로 무효로 만들어, 예외를 삼켜도 커밋에서 다시 터진다. 관리자 추천
     * 일괄 처리가 감사 로그 INSERT 실패로 통째로 롤백된 것이 같은 이유였다.
     *
     * <p>두 문장이 원자적일 필요는 없다. 기록만 되고 정리가 안 되면 다음번에 정리된다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void record(Long userId, Long placeId) {
        if (userId == null || placeId == null || placeId < 1) {
            throw new BusinessException(ErrorCode.INVALID_PLACE_REQUEST);
        }
        try {
            placeViewHistoryDAO.record(userId, placeId);
            placeViewHistoryDAO.deleteBeyondLimit(userId, KEEP_PER_USER);
        } catch (Exception exception) {
            // 없는 장소를 열어보려 한 경우(FK 위반)도 여기로 온다. 조용히 넘어간다.
            log.warn("최근 본 여행지 기록 실패 userId={} placeId={} type={}",
                    userId, placeId, exception.getClass().getSimpleName());
        }
    }

    public List<RecentPlaceResult> findRecent(Long userId, Integer size) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        int limit = size == null ? DEFAULT_SIZE : size;
        if (limit < 1 || limit > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_PLACE_REQUEST);
        }
        return placeViewHistoryDAO.findRecent(userId, limit);
    }

    @Transactional
    public void clear(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        placeViewHistoryDAO.deleteByUserId(userId);
    }
}
