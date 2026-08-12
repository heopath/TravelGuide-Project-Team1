package org.example.all_my_trip_project.domain.record.repository;

import org.example.all_my_trip_project.domain.record.entity.TravelRecordImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelRecordImageRepository extends JpaRepository<TravelRecordImageEntity, Long> {

    List<TravelRecordImageEntity> findByTravelRecordIdOrderBySortOrderAsc(Long travelRecordId);

    void deleteByTravelRecordId(Long travelRecordId);
}
