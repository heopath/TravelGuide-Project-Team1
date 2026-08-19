package org.example.all_my_trip_project.domain.admin.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.mapper.ServiceSettingMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class ServiceSettingDAO {

    private final ServiceSettingMapper serviceSettingMapper;

    public Optional<String> findValue(String settingKey) {
        return Optional.ofNullable(serviceSettingMapper.findValue(settingKey));
    }

    public int upsert(String settingKey, String settingValue, Long updatedBy) {
        return serviceSettingMapper.upsert(settingKey, settingValue, updatedBy);
    }
}
