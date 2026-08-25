package org.example.all_my_trip_project.domain.record.service;

import org.example.all_my_trip_project.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TravelRecordS3StorageServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void refusesToReadS3ObjectOutsideConfiguredBucketAndPrefix() {
        @SuppressWarnings("unchecked")
        ObjectProvider<S3Client> provider = mock(ObjectProvider.class);
        TravelRecordS3Properties properties = new TravelRecordS3Properties();
        properties.setEnabled(true);
        properties.setBucket("travel-record-bucket");
        properties.setPrefix("travel-records");
        TravelRecordS3StorageService service = new TravelRecordS3StorageService(
                provider, properties, new TravelRecordLocalStorageProperties());

        assertThatThrownBy(() -> service.load("s3://another-bucket/private/photo.jpg"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.load("s3://travel-record-bucket/not-record-photo.jpg"))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(provider);
    }

    @Test
    void storesAndLoadsUploadedImageLocallyWhenS3IsDisabled() {
        @SuppressWarnings("unchecked")
        ObjectProvider<S3Client> provider = mock(ObjectProvider.class);
        TravelRecordS3Properties s3 = new TravelRecordS3Properties();
        TravelRecordLocalStorageProperties local = new TravelRecordLocalStorageProperties();
        local.setEnabled(true);
        local.setDirectory(tempDirectory.toString());
        TravelRecordS3StorageService service = new TravelRecordS3StorageService(provider, s3, local);
        MockMultipartFile file = new MockMultipartFile(
                "file", "memory.png", "image/png", new byte[]{1, 2, 3, 4});

        String reference = service.upload(7L, 11L, file);
        StoredTravelRecordImage stored = service.load(reference);

        assertThat(reference).startsWith("local://travel-records/7/11/").endsWith(".png");
        assertThat(stored.bytes()).containsExactly(1, 2, 3, 4);
        assertThat(stored.contentType()).isEqualTo("image/png");
        verifyNoInteractions(provider);
    }

    @Test
    void refusesLocalPathOutsideConfiguredDirectory() {
        @SuppressWarnings("unchecked")
        ObjectProvider<S3Client> provider = mock(ObjectProvider.class);
        TravelRecordLocalStorageProperties local = new TravelRecordLocalStorageProperties();
        local.setEnabled(true);
        local.setDirectory(tempDirectory.toString());
        TravelRecordS3StorageService service = new TravelRecordS3StorageService(
                provider, new TravelRecordS3Properties(), local);

        assertThatThrownBy(() -> service.load("local://../../outside.png"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(provider);
    }
}
