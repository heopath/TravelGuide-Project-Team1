package org.example.all_my_trip_project.global.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 형식에 맞지 않는 요청은 400으로 답해야 한다.
 *
 * <p>이 셋은 컨트롤러에 닿기 전에 걸리기 때문에, 따로 잡지 않으면 맨 아래
 * Exception 처리기로 떨어져 500이 된다. 보낸 쪽이 잘못 보낸 것을 서버 잘못으로
 * 알리게 되고, 부르는 쪽은 고칠 곳을 찾지 못한다.
 */
class MalformedRequestResponseTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SampleController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void 필수_파라미터가_빠지면_400이다() throws Exception {
        mockMvc.perform(get("/sample/range"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PARAMETER"));
    }

    @Test
    void 날짜_형식이_아니면_400이다() throws Exception {
        mockMvc.perform(get("/sample/range").param("from", "abc").param("to", "xyz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
    }

    @Test
    void 경로_변수의_형식이_아니면_400이다() throws Exception {
        mockMvc.perform(get("/sample/items/notanumber"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
    }

    @Test
    void 본문이_JSON이_아니면_400이다() throws Exception {
        mockMvc.perform(post("/sample/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"));
    }

    @Test
    void 제대로_보내면_그대로_통과한다() throws Exception {
        mockMvc.perform(get("/sample/range").param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(status().isOk());
    }

    @RestController
    static class SampleController {

        @GetMapping("/sample/range")
        String range(
                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
            return from + "~" + to;
        }

        @GetMapping("/sample/items/{itemId}")
        String item(@PathVariable Long itemId) {
            return String.valueOf(itemId);
        }

        @PostMapping("/sample/items")
        String create(@RequestBody SamplePayload payload) {
            return payload.name();
        }
    }

    record SamplePayload(String name) {}
}
