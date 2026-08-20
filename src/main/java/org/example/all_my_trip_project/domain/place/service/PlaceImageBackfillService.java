package org.example.all_my_trip_project.domain.place.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.admin.service.AdminAuditService;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.dto.PlaceImageBackfillResult;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 이미 저장된 장소의 대표 이미지를 뒤늦게 채운다.
 *
 * <p>{@link PlaceService}는 장소가 <em>처음 저장될 때</em>만 이미지를 찾는다. 그래서
 * TourAPI 키가 없던 동안, 또는 키가 잘못 전달되던 동안 들어온 장소는 이미지가 비어 있고
 * 그 뒤로는 아무도 다시 찾아주지 않는다. 그 장소들을 훑는 것이 여기다.
 *
 * <p>{@link PlaceService}와 같은 규칙을 따른다. 이미지를 만들어내지 않고, 공공기관이
 * 그 좌표·이름에 대해 제공하는 사진만 쓴다. 확신이 없으면 넣지 않는다.
 */
@Service
@Profile("!ui")
@RequiredArgsConstructor
@Slf4j
public class PlaceImageBackfillService {

    /** 외부 API를 장소마다 한 번씩 부르므로 한 요청에서 처리할 양을 묶어 둔다. */
    private static final int DEFAULT_BATCH_SIZE = 25;
    private static final int MAX_BATCH_SIZE = 100;

    private final PlaceDAO placeDAO;
    private final TourApiPlaceImageProvider placeImageProvider;
    private final AdminAuditService adminAuditService;

    /**
     * 커서 뒤쪽 장소를 한 묶음만 처리한다. 화면이 done이 될 때까지 이어서 부른다.
     *
     * <p>일부러 트랜잭션으로 묶지 않는다. 외부 API 응답을 기다리는 동안 커넥션을 잡고 있으면
     * 안 되고, 중간에 한 장소가 실패해도 앞서 채운 이미지는 남는 편이 낫다.
     */
    @CacheEvict(cacheNames = "placeDetail", allEntries = true)
    public PlaceImageBackfillResult backfill(long afterPlaceId, Integer batchSize) {
        if (afterPlaceId < 0) {
            throw new BusinessException(ErrorCode.INVALID_PLACE_REQUEST);
        }
        int size = batchSize == null ? DEFAULT_BATCH_SIZE : batchSize;
        if (size < 1 || size > MAX_BATCH_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_PLACE_REQUEST);
        }

        List<PlaceDTO> candidates = placeDAO.findMissingImageCandidates(afterPlaceId, size);
        if (candidates.isEmpty()) {
            return new PlaceImageBackfillResult(0, 0, afterPlaceId, true, placeDAO.countMissingImages());
        }

        int filled = 0;
        for (PlaceDTO place : candidates) {
            if (fill(place)) filled++;
        }

        long nextAfter = candidates.getLast().getPlaceId();
        long remaining = placeDAO.countMissingImages();
        log.info("대표 이미지 채우기 scanned={} filled={} nextAfter={} remaining={}",
                candidates.size(), filled, nextAfter, remaining);
        adminAuditService.record("PLACE_IMAGE_BACKFILL", "PLACE", null, null,
                AdminAuditService.payload("scanned", candidates.size(), "filled", filled));

        // 마지막 묶음이 배치 크기보다 작으면 더 볼 장소가 없다.
        return new PlaceImageBackfillResult(
                candidates.size(), filled, nextAfter, candidates.size() < size, remaining);
    }

    private boolean fill(PlaceDTO place) {
        try {
            return placeImageProvider
                    .findImageUrl(place.getName(), place.getLatitude(), place.getLongitude())
                    .map(imageUrl -> placeDAO.insertPrimaryImage(
                            place.getPlaceId(), imageUrl, place.getName() + " 대표 이미지") == 1)
                    .orElse(false);
        } catch (Exception exception) {
            // 한 장소 때문에 나머지를 못 채우면 안 된다. 다음 장소로 넘어간다.
            log.warn("대표 이미지 채우기 실패 placeId={} type={}",
                    place.getPlaceId(), exception.getClass().getSimpleName());
            return false;
        }
    }
}
