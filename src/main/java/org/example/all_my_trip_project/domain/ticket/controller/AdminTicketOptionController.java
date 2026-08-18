package org.example.all_my_trip_project.domain.ticket.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketOptionDTO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketOptionRequest;
import org.example.all_my_trip_project.domain.ticket.service.AdminTicketProductService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 옵션 수정.
 *
 * <p>등록은 상품 아래({@code POST /ticket-products/{id}/options})에 두고 수정은 여기에 둔다.
 * 시간대와 같은 규칙이다 — 만들 때는 어느 상품인지가 필요하고, 고칠 때는 대상 id 하나면 된다.
 */
@RestController
@Profile("!ui")
@RequestMapping("/api/v1/admin/ticket-options")
@RequiredArgsConstructor
public class AdminTicketOptionController {

    private final AdminTicketProductService adminTicketProductService;

    @PutMapping("/{optionId}")
    public ApiResponse<AdminTicketOptionDTO> update(
            @PathVariable Long optionId,
            @Valid @RequestBody AdminTicketOptionRequest request) {
        return ApiResponse.success("옵션을 수정했습니다.",
                adminTicketProductService.updateOption(optionId, request));
    }
}
