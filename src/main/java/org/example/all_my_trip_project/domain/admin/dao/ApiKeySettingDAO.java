package org.example.all_my_trip_project.domain.admin.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.mapper.ApiKeySettingMapper;
import org.example.all_my_trip_project.global.apikey.ApiKeySetting;
import org.example.all_my_trip_project.global.apikey.ApiKeyStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class ApiKeySettingDAO implements ApiKeyStore {

    private final ApiKeySettingMapper apiKeySettingMapper;

    @Override
    public Optional<ApiKeySetting> find(String apiKeyName) {
        return Optional.ofNullable(apiKeySettingMapper.findByName(apiKeyName));
    }

    @Override
    public List<ApiKeySetting> findAll() {
        return apiKeySettingMapper.findAll();
    }

    @Override
    public void save(String apiKeyName, String encryptedValue, Long adminUserId) {
        apiKeySettingMapper.upsert(apiKeyName, encryptedValue, adminUserId);
    }

    @Override
    public int delete(String apiKeyName) {
        return apiKeySettingMapper.delete(apiKeyName);
    }
}
