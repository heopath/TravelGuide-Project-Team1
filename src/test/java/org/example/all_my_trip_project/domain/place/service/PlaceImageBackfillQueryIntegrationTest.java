package org.example.all_my_trip_project.domain.place.service;

import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이미지 채우기 조회를 실제 DB에 대고 확인한다.
 *
 * <p>후보를 고르는 조건은 단위 테스트로 덮을 수 없다. 목을 세우면 내가 짠 SQL이 아니라
 * 내가 상상한 SQL을 검증하게 된다. 좌표 없는 장소를 빼는지, 이미 이미지가 있는 장소를
 * 빼는지, 커서 뒤만 보는지는 여기서만 드러난다.
 *
 * <p>로컬 컨테이너에서만 돈다. 테스트가 끝나면 넣은 행을 지운다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.docker.compose.enabled=false"
})
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "PLACE_IMAGE_BACKFILL_DB_TEST", matches = "true")
class PlaceImageBackfillQueryIntegrationTest {

    private static final String MARKER = "백필테스트-";

    @Autowired
    private PlaceDAO placeDAO;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("""
                delete from place_images
                where place_id in (select place_id from places where name like ?)
                """, MARKER + "%");
        jdbcTemplate.update("delete from places where name like ?", MARKER + "%");
    }

    @Test
    @DisplayName("좌표가 있고 이미지가 없는 활성 장소만, 커서 뒤에서 고른다")
    void picksOnlyPlacesThatCanBeFilled() {
        long withoutImage = insertPlace("좌표있고이미지없음", true, true);
        long withImage = insertPlace("이미지있음", true, true);
        jdbcTemplate.update("""
                insert into place_images (place_id, image_url, alt_text, sort_order, is_primary)
                values (?, 'https://example.com/a.jpg', '기존', 0, true)
                """, withImage);
        long withoutCoordinates = insertPlace("좌표없음", false, true);
        long inactive = insertPlace("미사용", true, false);

        List<Long> picked = placeDAO.findMissingImageCandidates(0L, 100).stream()
                .map(PlaceDTO::getPlaceId).toList();

        assertThat(picked).contains(withoutImage)
                .doesNotContain(withImage, withoutCoordinates, inactive);
        // 커서 뒤만 본다. 이 값을 넘기면 방금 그 장소는 더 이상 나오지 않는다.
        assertThat(placeDAO.findMissingImageCandidates(withoutImage, 100).stream()
                .map(PlaceDTO::getPlaceId).toList())
                .doesNotContain(withoutImage);
    }

    @Test
    @DisplayName("남은 개수는 후보 조건과 같은 기준으로 센다")
    void countsWithTheSameCondition() {
        long before = placeDAO.countMissingImages();
        insertPlace("좌표있고이미지없음", true, true);
        insertPlace("좌표없어서세지않음", false, true);

        assertThat(placeDAO.countMissingImages()).isEqualTo(before + 1);
    }

    private long insertPlace(String suffix, boolean withCoordinates, boolean active) {
        PlaceDTO place = PlaceDTO.builder()
                .category("ATTRACTION")
                .name(MARKER + suffix)
                .countryCode("KR")
                .latitude(withCoordinates ? new BigDecimal("37.5796000") : null)
                .longitude(withCoordinates ? new BigDecimal("126.9770000") : null)
                .active(active)
                .recommended(false)
                .build();
        placeDAO.insert(place);
        return place.getPlaceId();
    }
}
