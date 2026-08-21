package org.example.all_my_trip_project.domain.place.service;

import org.example.all_my_trip_project.domain.place.dao.PlaceViewHistoryDAO;
import org.example.all_my_trip_project.domain.place.dto.RecentPlaceResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 최근 본 여행지 조회를 실제 DB에 대고 확인한다.
 *
 * <p>목으로는 내가 짠 SQL이 아니라 내가 상상한 SQL을 검증하게 된다. 컬럼 별칭이 DTO
 * 필드로 제대로 붙는지, 미사용 장소를 빼는지, 상한이 정확히 걸리는지는 여기서만 드러난다.
 *
 * <p>로컬 DB에서만 돈다. 테스트가 넣은 행은 끝나면 지운다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.docker.compose.enabled=false"
})
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "PLACE_VIEW_HISTORY_DB_TEST", matches = "true")
class PlaceViewHistoryQueryIntegrationTest {

    @Autowired
    private PlaceViewHistoryDAO placeViewHistoryDAO;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;

    @AfterEach
    void tearDown() {
        if (userId != null) {
            jdbcTemplate.update("delete from place_view_history where user_id = ?", userId);
            jdbcTemplate.update("delete from users where user_id = ?", userId);
        }
    }

    @Test
    @DisplayName("본 장소를 최근 순으로 돌려주고 컬럼이 DTO에 붙는다")
    void returnsRecentPlacesWithMappedColumns() {
        userId = createUser();
        List<Long> placeIds = activePlaceIds(3);

        placeIds.forEach(placeId -> placeViewHistoryDAO.record(userId, placeId));

        List<RecentPlaceResult> recent = placeViewHistoryDAO.findRecent(userId, 10);

        assertThat(recent).hasSize(3);
        assertThat(recent.getFirst().getPlaceName())
                .as("place_name 별칭이 placeName으로 붙어야 한다")
                .isNotBlank();
        assertThat(recent.getFirst().getViewedAt()).isNotNull();
        assertThat(recent.getFirst().getCategory()).isNotBlank();
        // 마지막으로 기록한 장소가 맨 앞이어야 한다.
        assertThat(recent.getFirst().getPlaceId()).isEqualTo(placeIds.getLast());
    }

    @Test
    @DisplayName("같은 장소를 다시 봐도 행이 늘지 않고 맨 앞으로 온다")
    void movesRepeatedPlaceToFront() {
        userId = createUser();
        List<Long> placeIds = activePlaceIds(3);
        placeIds.forEach(placeId -> placeViewHistoryDAO.record(userId, placeId));

        placeViewHistoryDAO.record(userId, placeIds.getFirst());

        List<RecentPlaceResult> recent = placeViewHistoryDAO.findRecent(userId, 10);
        assertThat(recent).hasSize(3);
        assertThat(recent.getFirst().getPlaceId()).isEqualTo(placeIds.getFirst());
    }

    @Test
    @DisplayName("미사용 장소는 목록에서 뺀다")
    void hidesInactivePlaces() {
        userId = createUser();
        Long placeId = activePlaceIds(1).getFirst();
        placeViewHistoryDAO.record(userId, placeId);

        jdbcTemplate.update("update places set is_active = false where place_id = ?", placeId);
        try {
            assertThat(placeViewHistoryDAO.findRecent(userId, 10)).isEmpty();
        } finally {
            jdbcTemplate.update("update places set is_active = true where place_id = ?", placeId);
        }
    }

    @Test
    @DisplayName("보관 상한을 넘으면 오래된 것부터 지운다")
    void trimsBeyondLimit() {
        userId = createUser();
        activePlaceIds(5).forEach(placeId -> placeViewHistoryDAO.record(userId, placeId));

        placeViewHistoryDAO.deleteBeyondLimit(userId, 3);

        assertThat(placeViewHistoryDAO.findRecent(userId, 10)).hasSize(3);
    }

    private Long createUser() {
        return jdbcTemplate.queryForObject("""
                insert into users (email, password_hash, nickname, role, status)
                values (?, 'x', '조회이력테스트', 'USER', 'ACTIVE')
                returning user_id
                """, Long.class, "view-history-" + System.nanoTime() + "@example.invalid");
    }

    private List<Long> activePlaceIds(int count) {
        return jdbcTemplate.queryForList(
                "select place_id from places where is_active = true order by place_id limit ?",
                Long.class, count);
    }
}
