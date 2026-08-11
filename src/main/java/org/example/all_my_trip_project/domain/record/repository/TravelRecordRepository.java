package org.example.all_my_trip_project.domain.record.repository;

import org.example.all_my_trip_project.domain.record.entity.TravelRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TravelRecordRepository extends JpaRepository<TravelRecordEntity, Long> {

    Optional<TravelRecordEntity> findByTravelRecordIdAndDeletedAtIsNull(Long travelRecordId);

    boolean existsByTripIdAndDeletedAtIsNull(Long tripId);

    List<TravelRecordEntity> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);
}
