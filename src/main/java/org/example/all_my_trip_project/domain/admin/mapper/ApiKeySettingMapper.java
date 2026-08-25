package org.example.all_my_trip_project.domain.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.global.apikey.ApiKeySetting;

import java.util.List;

@Mapper
public interface ApiKeySettingMapper {

    ApiKeySetting findByName(@Param("apiKeyName") String apiKeyName);

    List<ApiKeySetting> findAll();

    int upsert(@Param("apiKeyName") String apiKeyName,
               @Param("encryptedValue") String encryptedValue,
               @Param("updatedBy") Long updatedBy);

    int delete(@Param("apiKeyName") String apiKeyName);
}
