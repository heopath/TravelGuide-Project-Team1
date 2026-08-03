package org.example.all_my_trip_project.domain.user.repository;

import org.example.all_my_trip_project.domain.user.entity.UserPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface UserPreferenceRepository
        extends JpaRepository<UserPreferenceEntity, Long> {

    List<UserPreferenceEntity> findAllByUserId(Long userId);

    @Query(value = """
            SELECT
                up.travel_style_id AS "travelStyleId",
                ts.code AS code,
                ts.name AS name,
                up.preference_score AS "preferenceScore",
                up.source AS source
            FROM user_preferences up
            JOIN travel_styles ts
              ON ts.travel_style_id = up.travel_style_id
            WHERE up.user_id = :userId
            ORDER BY ts.sort_order, ts.travel_style_id
            """, nativeQuery = true)
    List<UserPreferenceView> findViewsByUserId(
            @Param("userId") Long userId
    );

    @Query(value = """
            SELECT travel_style_id
            FROM travel_styles
            WHERE is_active = TRUE
              AND travel_style_id IN (:travelStyleIds)
            """, nativeQuery = true)
    List<Short> findActiveTravelStyleIds(
            @Param("travelStyleIds") Collection<Short> travelStyleIds
    );
}
