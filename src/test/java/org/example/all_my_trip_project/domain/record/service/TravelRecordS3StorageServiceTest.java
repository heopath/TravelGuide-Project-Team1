package org.example.all_my_trip_project.domain.record.service;

import org.example.all_my_trip_project.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TravelRecordS3StorageServiceTest {

    @Test
    void refusesToReadS3ObjectOutsideConfiguredBucketAndPrefix() {
        @SuppressWarnings("unchecked")
        ObjectProvider<S3Client> provider = mock(ObjectProvider.class);
        TravelRecordS3Properties properties = new TravelRecordS3Properties();
        properties.setEnabled(true);
        properties.setBucket("travel-record-bucket");
        properties.setPrefix("travel-records");
        TravelRecordS3StorageService service = new TravelRecordS3StorageService(provider, properties);

        assertThatThrownBy(() -> service.load("s3://another-bucket/private/photo.jpg"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.load("s3://travel-record-bucket/not-record-photo.jpg"))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(provider);
    }
}
