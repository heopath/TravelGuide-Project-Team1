package org.example.all_my_trip_project.domain.record.service;

import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TravelRecordImageStorageTest {

    @TempDir
    Path directory;

    @Test
    void storesAndLoadsPngUsingDetectedBytesInsteadOfClientContentType() {
        TravelRecordImageStorage storage = storage();
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3};
        MockMultipartFile file = new MockMultipartFile("file", "photo.txt", "text/plain", png);

        TravelRecordImageStorage.StoredImage stored = storage.store(7L, file);
        TravelRecordImageContent loaded = storage.load(7L, stored.fileName());

        assertThat(stored.imageUrl()).startsWith("/api/v1/travel-records/7/images/files/").endsWith(".png");
        assertThat(loaded.contentType()).isEqualTo("image/png");
        assertThat(loaded.bytes()).isEqualTo(png);
    }

    @Test
    void rejectsAFileWhoseBytesAreNotASupportedImage() {
        TravelRecordImageStorage storage = storage();
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "not-an-image".getBytes());

        assertThatThrownBy(() -> storage.store(7L, file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_RECORD_IMAGE_FILE);
    }

    @Test
    void rejectsPathTraversalWhenLoading() {
        TravelRecordImageStorage storage = storage();

        assertThatThrownBy(() -> storage.load(7L, "../secret.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECORD_IMAGE_NOT_FOUND);
    }

    private TravelRecordImageStorage storage() {
        TravelRecordImageStorage storage = new TravelRecordImageStorage(directory.toString());
        storage.initialize();
        return storage;
    }
}
