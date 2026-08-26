package org.example.all_my_trip_project.domain.support.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportChatMessageBlockDTO {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private Long supportChatMessageBlockId;
    private Long supportChatMessageId;
    private String blockType;
    private Short displayOrder;
    private Short schemaVersion;
    private Map<String, Object> payload;

    @JsonIgnore
    public String getPayloadJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("상담 메시지 블록을 JSON으로 변환하지 못했습니다.", exception);
        }
    }

    public void setPayloadJson(String payloadJson) {
        try {
            payload = payloadJson == null ? Map.of() : OBJECT_MAPPER.readValue(payloadJson, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("저장된 상담 메시지 블록 JSON이 올바르지 않습니다.", exception);
        }
    }
}
