package org.example.all_my_trip_project.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ServiceVersionUpdateRequest(
        @NotBlank(message = "표시 버전을 입력해 주세요.")
        @Pattern(
                regexp = "^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$",
                message = "버전은 0.0.5 또는 v0.0.5 형식으로 입력해 주세요."
        )
        String version
) {
}
