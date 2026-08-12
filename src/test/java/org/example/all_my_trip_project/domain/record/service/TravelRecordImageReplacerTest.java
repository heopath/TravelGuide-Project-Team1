package org.example.all_my_trip_project.domain.record.service;

import org.example.all_my_trip_project.domain.record.dto.ReplaceRecordImagesRequest;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordImageEntity;
import org.example.all_my_trip_project.domain.record.repository.TravelRecordImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TravelRecordImageReplacerTest {

    @Mock
    private TravelRecordImageRepository travelRecordImageRepository;

    private TravelRecordImageReplacer replacer;

    @BeforeEach
    void setUp() {
        replacer = new TravelRecordImageReplacer(travelRecordImageRepository);
    }

    @Test
    void deletesExistingImagesBeforeInsertingTheNewSet() {
        InOrder order = inOrder(travelRecordImageRepository);

        replacer.replace(1L, new ReplaceRecordImagesRequest(List.of(
                new ReplaceRecordImagesRequest.ImageItem("https://example.com/a.jpg", "a", true))));

        order.verify(travelRecordImageRepository).deleteByTravelRecordId(1L);
        order.verify(travelRecordImageRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void assignsSequentialSortOrderStartingAtOneInRequestOrder() {
        replacer.replace(1L, new ReplaceRecordImagesRequest(List.of(
                new ReplaceRecordImagesRequest.ImageItem("  https://example.com/a.jpg  ", "  대표  ", true),
                new ReplaceRecordImagesRequest.ImageItem("https://example.com/b.jpg", null, false))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TravelRecordImageEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(travelRecordImageRepository).saveAll(captor.capture());

        List<TravelRecordImageEntity> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getImageUrl()).isEqualTo("https://example.com/a.jpg");
        assertThat(saved.get(0).getAltText()).isEqualTo("대표");
        assertThat(saved.get(0).getSortOrder()).isEqualTo(1);
        assertThat(saved.get(0).getCover()).isTrue();
        assertThat(saved.get(1).getSortOrder()).isEqualTo(2);
        assertThat(saved.get(1).getAltText()).isNull();
        assertThat(saved.get(1).getCover()).isFalse();
    }
}
