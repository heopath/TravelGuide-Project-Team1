package org.example.all_my_trip_project.presentation.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class BookingPageController {

    /**
     * 예약 허브를 없애고 항공 화면으로 바로 보낸다.
     *
     * <p>허브가 하던 항공·숙소·티켓 이동은 항공 화면의 탭이 이미 전부 하고 있었다.
     * 같은 내비게이션이 두 겹이라 클릭만 한 번 더 들어갔고,
     * 허브의 유일한 고유 콘텐츠였던 "AI 추천 예약"은 근거 없는 하드코딩 값이었다.
     *
     * <p>라우트를 지우지 않고 리다이렉트로 남긴 이유는 이미 공유된 주소를 깨지 않기 위해서다.
     * 화면 목록(ALL_MY_TRIPS_SCREENS)에서 뺄지는 "탭을 별도 화면으로 셀 것인가"를
     * 정한 뒤에 함께 결정한다. 지금은 이 라우트와 /booking/hotels 둘 다 항공 화면의
     * 탭으로 흡수되므로, 목록에 남겨두면 같은 화면이 세 번 등장한다.
     */
    @GetMapping("/booking")
    public String booking() {
        return "redirect:/booking/flights";
    }

    @GetMapping("/booking/tickets/{ticketSlug}")
    public String ticket(@PathVariable String ticketSlug) {
        return "booking/ticket";
    }

    @GetMapping("/booking/hotels")
    public String hotels() {
        // 숙박 검색은 별도 목업 페이지가 아니라 예약 화면의 숙박 탭에서 제공한다.
        // 기존 주소는 외부 링크와 화면 디렉터리가 깨지지 않도록 리다이렉트로 남긴다.
        return "redirect:/booking/flights?tab=hotel";
    }

    @GetMapping("/booking/flights")
    public String flights() {
        return "booking/flights";
    }

    @GetMapping("/booking/queue")
    public String bookingQueue() {
        return "booking/queue";
    }
}
