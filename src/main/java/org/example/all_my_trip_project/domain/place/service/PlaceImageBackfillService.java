package org.example.all_my_trip_project.domain.place.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.admin.service.AdminAuditService;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.dto.PlaceImageFillResult;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 이미 저장된 장소의 대표 이미지를 뒤늦게 채운다.
 *
 * <p>{@link PlaceService}는 장소가 <em>처음 저장될 때</em>만 이미지를 찾는다. 그래서
 * TourAPI 키가 없던 동안, 또는 키가 잘못 전달되던 동안 들어온 장소는 이미지가 비어 있고
 * 그 뒤로는 아무도 다시 찾아주지 않는다. 그 장소들을 관리자가 골라 채우는 것이 여기다.
 *
 * <p>목록 전체를 훑지 않고 <b>고른 것만</b> 처리한다. 한 번에 수백 곳을 돌리면 몇 분을
 * 기다려야 하고, 그동안 무엇이 되고 무엇이 안 됐는지 알 수 없어 실패해도 알아채기 어렵다.
 * 골라서 누르면 결과가 바로 목록에 보인다.
 *
 * <p>{@link PlaceService}와 같은 규칙을 따른다. 이미지를 만들어내지 않고, 공공기관이
 * 그 좌표·이름에 대해 제공하는 사진만 쓴다. 확신이 없으면 넣지 않는다.
 */
@Service
@Profile("!ui")
@RequiredArgsConstructor
@Slf4j
public class PlaceImageBackfillService {

    /** 장소마다 외부 API를 한 번씩 부른다. 화면 한 페이지가 상한이다. */
    private static final int MAX_SELECTION = 100;

    private final PlaceDAO placeDAO;
    private final TourApiPlaceImageProvider placeImageProvider;
    private final AdminAuditService adminAuditService;

    /**
     * 고른 장소들의 대표 이미지를 찾아 넣는다.
     *
     * <p>일부러 트랜잭션으로 묶지 않는다. 외부 API 응답을 기다리는 동안 커넥션을 잡고 있으면
     * 안 되고, 중간에 한 장소가 실패해도 앞서 채운 이미지는 남는 편이 낫다.
     */
    @CacheEvict(cacheNames = "placeDetail", allEntries = true)
    public PlaceImageFillResult fill(List<Long> placeIds) {
        if (placeIds == null || placeIds.isEmpty() || placeIds.size() > MAX_SELECTION) {
            throw new BusinessException(ErrorCode.INVALID_PLACE_REQUEST);
        }
        List<Long> targets = placeIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (targets.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PLACE_REQUEST);
        }

        int filled = 0;
        int skipped = 0;
        for (Long placeId : targets) {
            switch (fillOne(placeId)) {
                case FILLED -> filled++;
                case SKIPPED -> skipped++;
                case NOT_FOUND -> { /* 아래에서 나머지로 센다 */ }
            }
        }
        int notFound = targets.size() - filled - skipped;

        log.info("대표 이미지 채우기 요청={} 채움={} 건너뜀={} 못찾음={}",
                targets.size(), filled, skipped, notFound);
        adminAuditService.record("PLACE_IMAGE_FILL", "PLACE", null, null,
                AdminAuditService.payload("requested", targets.size(), "filled", filled));

        return new PlaceImageFillResult(targets.size(), filled, skipped, notFound);
    }

    private enum Outcome { FILLED, SKIPPED, NOT_FOUND }

    private Outcome fillOne(Long placeId) {
        try {
            Optional<PlaceDTO> found = placeDAO.findById(placeId);
            if (found.isEmpty()) return Outcome.SKIPPED;

            PlaceDTO place = found.get();
            // 이미 사진이 있으면 덮어쓰지 않는다. 관리자가 직접 넣은 것일 수 있다.
            boolean hasImage = place.getPrimaryImageUrl() != null && !place.getPrimaryImageUrl().isBlank();
            if (hasImage || place.getLatitude() == null || place.getLongitude() == null) {
                return Outcome.SKIPPED;
            }

            return placeImageProvider
                    .findImageUrl(place.getName(), place.getLatitude(), place.getLongitude())
                    .filter(imageUrl -> placeDAO.insertPrimaryImage(
                            placeId, imageUrl, place.getName() + " 대표 이미지") == 1)
                    .map(imageUrl -> Outcome.FILLED)
                    .orElse(Outcome.NOT_FOUND);
        } catch (Exception exception) {
            // 한 장소 때문에 나머지를 못 채우면 안 된다. 다음 장소로 넘어간다.
            log.warn("대표 이미지 채우기 실패 placeId={} type={}",
                    placeId, exception.getClass().getSimpleName());
            return Outcome.NOT_FOUND;
        }
    }
}
