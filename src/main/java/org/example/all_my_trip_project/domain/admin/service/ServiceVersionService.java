package org.example.all_my_trip_project.domain.admin.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.dao.ServiceSettingDAO;
import org.example.all_my_trip_project.domain.admin.dto.ServiceVersionDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceVersionService {

    public static final String DEFAULT_VERSION = "0.0.7";
    public static final String DEFAULT_DISPLAY_VERSION = "v" + DEFAULT_VERSION;

    private static final String SETTING_KEY = "footer.version";
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");

    private final ServiceSettingDAO serviceSettingDAO;
    private final AdminAuditService adminAuditService;

    public ServiceVersionDTO get() {
        return new ServiceVersionDTO(displayVersion());
    }

    public String displayVersion() {
        String stored = serviceSettingDAO.findValue(SETTING_KEY).orElse(DEFAULT_VERSION);
        return "v" + normalizeStored(stored);
    }

    @Transactional
    public ServiceVersionDTO update(String version, Long adminUserId) {
        String normalized = normalizeInput(version);
        String before = serviceSettingDAO.findValue(SETTING_KEY).orElse(DEFAULT_VERSION);

        if (normalized.equals(normalizeStored(before))) {
            return new ServiceVersionDTO("v" + normalized);
        }

        serviceSettingDAO.upsert(SETTING_KEY, normalized, adminUserId);
        adminAuditService.record(
                "SERVICE_VERSION_CHANGE",
                "SERVICE_SETTING",
                SETTING_KEY,
                AdminAuditService.payload("version", "v" + normalizeStored(before)),
                AdminAuditService.payload("version", "v" + normalized));
        return new ServiceVersionDTO("v" + normalized);
    }

    private String normalizeInput(String version) {
        String trimmed = version == null ? "" : version.trim();
        if (!VERSION_PATTERN.matcher(trimmed).matches()) {
            throw new BusinessException(ErrorCode.INVALID_SERVICE_VERSION);
        }
        return trimmed.startsWith("v") ? trimmed.substring(1) : trimmed;
    }

    /** DB 값이 수동 변경으로 깨져도 푸터 렌더링을 막지 않고 현재 기본값으로 복구한다. */
    private String normalizeStored(String version) {
        String trimmed = version == null ? "" : version.trim();
        if (!VERSION_PATTERN.matcher(trimmed).matches()) return DEFAULT_VERSION;
        return trimmed.startsWith("v") ? trimmed.substring(1) : trimmed;
    }
}
