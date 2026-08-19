package org.example.all_my_trip_project.presentation.advice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.admin.service.ServiceVersionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class FooterVersionViewAdvice {

    private final ObjectProvider<ServiceVersionService> serviceVersionService;

    /** 모든 서버 렌더링 화면에서 같은 푸터 버전을 사용한다. */
    @ModelAttribute("footerVersion")
    public String footerVersion() {
        try {
            ServiceVersionService service = serviceVersionService.getIfAvailable();
            return service == null
                    ? ServiceVersionService.DEFAULT_DISPLAY_VERSION
                    : service.displayVersion();
        } catch (RuntimeException exception) {
            // 설정 조회 장애 때문에 전체 페이지 렌더링까지 실패하면 안 된다.
            log.warn("푸터 표시 버전을 조회하지 못해 기본값을 사용합니다.", exception);
            return ServiceVersionService.DEFAULT_DISPLAY_VERSION;
        }
    }
}
