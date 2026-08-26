package org.example.all_my_trip_project.domain.record.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** S3는 원본만 보관한다. 브라우저에는 객체 URL 대신 기록 공개 범위를 검사하는 API URL만 내보낸다. */
@Service
@RequiredArgsConstructor
class TravelRecordS3StorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final ObjectProvider<S3Client> s3ClientProvider;
    private final TravelRecordS3Properties properties;
    private final TravelRecordLocalStorageProperties localProperties;

    String upload(Long userId, Long travelRecordId, MultipartFile file) {
        validate(file);
        String key = normalizedPrefix() + "/" + userId + "/" + travelRecordId + "/"
                + LocalDate.now() + "/" + UUID.randomUUID() + extension(file.getContentType());
        if (properties.isEnabled()) {
            return uploadToS3(key, file);
        }
        if (localProperties.isEnabled()) {
            return uploadLocally(key, file);
        }
        throw new BusinessException(ErrorCode.INVALID_RECORD_REQUEST);
    }

    private String uploadToS3(String key, MultipartFile file) {
        try {
            client().putObject(PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromBytes(file.getBytes()));
            return "s3://" + properties.getBucket() + "/" + key;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_REQUEST);
        }
    }

    StoredTravelRecordImage load(String storageReference) {
        if (storageReference != null && storageReference.startsWith("local://")) {
            return loadLocal(storageReference);
        }
        S3Location location = parse(storageReference);
        var response = client().getObject(GetObjectRequest.builder().bucket(location.bucket()).key(location.key()).build(),
                ResponseTransformer.toBytes());
        String contentType = response.response().contentType();
        return new StoredTravelRecordImage(response.asByteArray(), contentType == null ? "application/octet-stream" : contentType);
    }

    private String uploadLocally(String key, MultipartFile file) {
        Path root = localRoot();
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_REQUEST);
        }
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return "local://" + key.replace('\\', '/');
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_REQUEST);
        }
    }

    private StoredTravelRecordImage loadLocal(String storageReference) {
        if (!localProperties.isEnabled()) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        String key = storageReference.substring("local://".length());
        Path root = localRoot();
        Path target = root.resolve(key).normalize();
        if (key.isBlank() || !target.startsWith(root) || !Files.isRegularFile(target)) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        try {
            String detected = Files.probeContentType(target);
            return new StoredTravelRecordImage(
                    Files.readAllBytes(target),
                    detected == null ? contentTypeFromName(target.getFileName().toString()) : detected
            );
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
    }

    private Path localRoot() {
        String directory = localProperties.getDirectory();
        if (directory == null || directory.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_REQUEST);
        }
        return Path.of(directory).toAbsolutePath().normalize();
    }

    private String contentTypeFromName(String filename) {
        String normalized = filename.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".png")) return "image/png";
        if (normalized.endsWith(".webp")) return "image/webp";
        if (normalized.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > properties.getMaxFileSizeBytes()
                || !ALLOWED_TYPES.contains(String.valueOf(file.getContentType()).toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_REQUEST);
        }
    }

    private S3Client client() {
        S3Client client = s3ClientProvider.getIfAvailable();
        if (!properties.isEnabled() || properties.getBucket() == null || properties.getBucket().isBlank() || client == null) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_REQUEST);
        }
        return client;
    }

    private String normalizedPrefix() {
        return properties.getPrefix().replaceAll("^/+|/+$", "");
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }

    private S3Location parse(String reference) {
        if (reference == null || !reference.startsWith("s3://")) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        String value = reference.substring("s3://".length());
        int separator = value.indexOf('/');
        if (separator < 1) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        String bucket = value.substring(0, separator);
        String key = value.substring(separator + 1);
        if (!bucket.equals(properties.getBucket()) || !key.startsWith(normalizedPrefix() + "/")) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return new S3Location(bucket, key);
    }

    private record S3Location(String bucket, String key) { }
}
