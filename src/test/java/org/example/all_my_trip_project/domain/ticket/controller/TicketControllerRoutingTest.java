package org.example.all_my_trip_project.domain.ticket.controller;

import org.example.all_my_trip_project.domain.ticket.dto.TicketProductDetailDTO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketProductPage;
import org.example.all_my_trip_project.domain.ticket.dto.TicketProductSummaryDTO;
import org.example.all_my_trip_project.domain.ticket.service.TicketService;
import org.example.all_my_trip_project.global.config.ApiSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주소가 실제로 붙어 있는지 본다.
 *
 * <p>서비스 단위 테스트만으로는 컨트롤러에 메서드가 없는 것을 잡지 못한다. 실제로 #255에서
 * 서비스·DTO·SQL·화면이 다 들어갔는데 컨트롤러 메서드만 빠진 채로 배포됐고, 쓰지 않는
 * import는 컴파일 오류가 아니라 빌드도 통과했다. 운영에서 404로 드러났다.
 *
 * <p>TicketController는 {@code @Profile("!ui")}이고 기본 프로필이 ui라 test 프로필을 켠다.
 */
@WebMvcTest(TicketController.class)
@Import(ApiSecurityConfig.class)
@ActiveProfiles("test")
class TicketControllerRoutingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @Test
    @DisplayName("GET /api/v1/tickets/products 가 붙어 있다")
    void productsIsMapped() throws Exception {
        when(ticketService.products(anyInt(), anyInt(), any()))
                .thenReturn(new TicketProductPage(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/tickets/products"))
                .andExpect(status().isOk());

        /* 기본값도 함께 못 박는다. 화면이 값을 안 보내도 동작해야 한다. */
        verify(ticketService).products(eq(0), eq(20), eq(null));
    }

    @Test
    @DisplayName("GET /api/v1/tickets/products 는 로그인 없이 볼 수 있다")
    void productsIsPublic() throws Exception {
        when(ticketService.products(anyInt(), anyInt(), any()))
                .thenReturn(new TicketProductPage(List.of(), 0, 20, 0, 0));

        /* 상품 목록은 사기 전에 보는 화면이다. 401이면 둘러볼 수도 없다. */
        mockMvc.perform(get("/api/v1/tickets/products?page=0&size=5&keyword=제주"))
                .andExpect(status().isOk());

        verify(ticketService).products(0, 5, "제주");
    }

    @Test
    @DisplayName("GET /api/v1/tickets/products/{id} 가 붙어 있다")
    void productDetailIsMapped() throws Exception {
        when(ticketService.product(20L)).thenReturn(new TicketProductDetailDTO(
                TicketProductSummaryDTO.builder().productId(20L).build(), List.of()));

        mockMvc.perform(get("/api/v1/tickets/products/20"))
                .andExpect(status().isOk());

        verify(ticketService).product(20L);
    }

    @Test
    @DisplayName("GET /api/v1/tickets 는 그대로 남아 있다")
    void legacySearchStillMapped() throws Exception {
        when(ticketService.search(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tickets?destination=&from=2026-09-10&to=2026-09-20"))
                .andExpect(status().isOk());
    }
}
