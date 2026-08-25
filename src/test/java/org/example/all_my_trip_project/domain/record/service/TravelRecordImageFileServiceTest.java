package org.example.all_my_trip_project.domain.record.service;

import org.example.all_my_trip_project.domain.record.dto.ReplaceRecordImagesRequest;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordEntity;
import org.example.all_my_trip_project.domain.record.type.RecordVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TravelRecordImageFileServiceTest {

    @Mock private TravelRecordReader reader;
    @Mock private TravelRecordValidator validator;
    @Mock private TravelRecordImageReplacer imageReplacer;
    @Mock private TravelRecordResponseMapper responseMapper;
    @Mock private TravelRecordImageStorage storage;

    private TravelRecordImageFileService service;

    @BeforeEach
    void setUp() {
        service = new TravelRecordImageFileService(reader, validator, imageReplacer, responseMapper, storage);
    }

    @Test
    void firstUploadedImageAutomaticallyBecomesTheCover() {
        TravelRecordEntity record = TravelRecordEntity.create(
                269L, 42L, "부산 여행", "본문", (short) 5, RecordVisibility.PRIVATE);
        MockMultipartFile file = new MockMultipartFile("file", "busan.jpg", "image/jpeg", new byte[]{1});
        String url = "/api/v1/travel-records/7/images/files/11111111-1111-1111-1111-111111111111.jpg";
        when(reader.findOwned(42L, 7L)).thenReturn(record);
        when(reader.findImages(7L)).thenReturn(List.of());
        when(storage.store(7L, file)).thenReturn(new TravelRecordImageStorage.StoredImage(url, "file.jpg"));

        service.upload(42L, 7L, file, "  해운대  ", false);

        ArgumentCaptor<ReplaceRecordImagesRequest> captor = ArgumentCaptor.forClass(ReplaceRecordImagesRequest.class);
        verify(imageReplacer).replace(org.mockito.ArgumentMatchers.eq(7L), captor.capture());
        assertThat(captor.getValue().images()).singleElement().satisfies(image -> {
            assertThat(image.imageUrl()).isEqualTo(url);
            assertThat(image.altText()).isEqualTo("해운대");
            assertThat(image.cover()).isTrue();
        });
        verify(validator).validateImages(any());
    }
}
