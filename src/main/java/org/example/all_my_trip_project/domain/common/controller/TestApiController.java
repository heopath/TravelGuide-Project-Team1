package org.example.all_my_trip_project.domain.common.controller;

import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test")
public class TestApiController {

    @GetMapping
    public ApiResponse<String> test() {
        return ApiResponse.success("API 연결 성공");
    }

    @GetMapping("/error")
    public ApiResponse<Void> testError() {
        throw new IllegalArgumentException("테스트 오류입니다.");
    }
}