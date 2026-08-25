package org.example.all_my_trip_project.domain.record.config;

import org.example.all_my_trip_project.domain.record.service.TravelRecordS3Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(prefix = "travel-record.s3", name = "enabled", havingValue = "true")
public class TravelRecordS3Config {

    @Bean
    S3Client travelRecordS3Client(TravelRecordS3Properties properties) {
        return S3Client.builder().region(Region.of(properties.getRegion())).build();
    }
}
