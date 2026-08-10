package org.example.all_my_trip_project.domain.social.repository;

import org.example.all_my_trip_project.domain.social.entity.TravelRecordReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelRecordReportRepository extends JpaRepository<TravelRecordReportEntity, Long> {

    boolean existsByTravelRecordIdAndReporterUserIdAndStatusIn(
            Long travelRecordId,
            Long reporterUserId,
            List<String> statuses
    );

    List<TravelRecordReportEntity> findByStatusOrderByCreatedAtAsc(String status);

    List<TravelRecordReportEntity> findAllByOrderByCreatedAtAsc();
}
