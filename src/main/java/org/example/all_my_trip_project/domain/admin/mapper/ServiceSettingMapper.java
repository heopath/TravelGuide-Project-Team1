package org.example.all_my_trip_project.domain.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ServiceSettingMapper {

    String findValue(@Param("settingKey") String settingKey);

    int upsert(@Param("settingKey") String settingKey,
               @Param("settingValue") String settingValue,
               @Param("updatedBy") Long updatedBy);
}
