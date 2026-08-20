(function installCsrfAwareFetch() {
  const nativeFetch = window.fetch.bind(window);
  let csrfTokenPromise;

  function isUnsafeSameOriginRequest(input, options) {
    const method = (options?.method || (input instanceof Request ? input.method : "GET")).toUpperCase();
    if (!["POST", "PUT", "PATCH", "DELETE"].includes(method)) return false;
    const url = new URL(input instanceof Request ? input.url : input, window.location.href);
    return url.origin === window.location.origin;
  }

  async function csrfToken() {
    if (!csrfTokenPromise) {
      csrfTokenPromise = nativeFetch("/api/v1/csrf", {
        credentials: "same-origin",
        headers: { Accept: "application/json" },
        allMyTripsLoading: false,
      }).then(function (response) {
        if (!response.ok) throw new Error("CSRF 토큰을 발급받지 못했습니다.");
        return response.json();
      }).then(function (payload) { return payload.token; })
        .catch(function (error) {
          // 토큰 발급 자체가 실패한 거부 프로미스를 캐시하면 새로고침 전까지
          // 이후 모든 쓰기 요청이 같은 거부 프로미스를 받아 계속 막힌다.
          // 캐시를 비워 다음 요청이 다시 시도할 수 있게 한다.
          csrfTokenPromise = undefined;
          throw error;
        });
    }
    return csrfTokenPromise;
  }

  async function sendWithToken(input, options) {
    const token = await csrfToken();
    const requestOptions = { ...(options || {}) };
    requestOptions.credentials = requestOptions.credentials || "same-origin";
    // input이 Request면 그 안에 담긴 헤더(Content-Type 등)가 기본값이 되어야 한다.
    // options.headers가 없다고 해서 비워버리면 Request에 실려 있던 헤더가 사라진다.
    requestOptions.headers = new Headers(input instanceof Request ? input.headers : undefined);
    if (options && options.headers) {
      new Headers(options.headers).forEach(function (value, key) {
        requestOptions.headers.set(key, value);
      });
    }
    requestOptions.headers.set("X-CSRF-TOKEN", token);
    return nativeFetch(input, requestOptions);
  }

  window.fetch = async function csrfAwareFetch(input, options) {
    if (!isUnsafeSameOriginRequest(input, options)) return nativeFetch(input, options);

    // input이 Request면 첫 전송에서 body가 소모된다. 재시도가 필요할 경우를 대비해
    // 아직 아무것도 읽지 않은 지금 시점에 clone을 떠서 재시도 전용으로 남겨둔다.
    const retryInput = input instanceof Request ? input.clone() : input;

    const response = await sendWithToken(input, options);
    if (response.status !== 403) return response;

    // 403은 CSRF 토큰 실패(ACCESS_DENIED)와 권한 부족(FORBIDDEN 등)을 모두 포함한다.
    // 둘을 구분하려면 응답 본문의 code를 봐야 하는데, Response.body는 한 번 읽으면
    // 소모되므로 clone()으로 읽어 재시도하지 않을 때 호출자에게 넘길 원본은 그대로 둔다.
    let code;
    try {
      code = (await response.clone().json())?.code;
    } catch (error) {
      code = undefined;
    }
    if (code !== "ACCESS_DENIED") return response;

    // 토큰이 어긋난 것으로 보고 캐시를 버리고 새로 받은 토큰으로 딱 한 번만 재시도한다.
    // 재시도 후에도 403이면 그대로 호출자에게 넘겨 무한 재시도를 막는다(서버가 계속
    // 거부하는 경우 대비). ACCESS_DENIED가 앞으로도 CSRF 전용이라는 보장은 없어서
    // (권한 규칙이 늘면 그쪽도 ACCESS_DENIED일 수 있음) 이 1회 제한이 안전망 역할도 한다.
    //
    // 주의: options.body가 문자열(JSON.stringify 등)이면 재전송해도 안전하지만,
    // FormData나 스트림이면 두 번째 요청에서 바디가 비어버릴 수 있다. 지금은 모든
    // 쓰기 요청이 JSON 문자열 바디라 문제없지만, 파일 업로드처럼 스트림 바디를 쓰게
    // 되면 재시도 전에 바디를 미리 복제해두는 처리가 필요하다.
    csrfTokenPromise = undefined;
    return sendWithToken(retryInput, options);
  };
})();

window.AllMyTripsApi = {
  async get(url) {
    const response = await fetch(url, { headers: { Accept: "application/json" } });
    if (!response.ok) throw new Error("API 요청에 실패했습니다.");
    return response.json();
  },
  async post(url, body) {
    const response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(body),
    });
    if (!response.ok) throw new Error("API 요청에 실패했습니다.");
    return response.json();
  },
};


const ALL_MY_TRIPS_TEMPLATE_ROUTES = {
  "/home": "home/home",
  "/auth/login": "auth/login",
  "/auth/signup": "auth/signup",
  "/trips/new/plan": "trips/plan",
  "/trips/new/basic": "trips/basic",
  "/trips/new/style": "trips/style",
  "/guide": "guide/guide",
  "/guide/places/haeundae": "guide/place-detail",
  "/trips/schedule": "trips/schedule",
  "/trips/busan/map": "trips/map",
  "/ai-trip-plan": "guide/ai-trip-plan",
  "/ai-guide": "guide/ai-guide",
  "/trips/busan/optimize": "trips/optimize",
  "/guide/themes": "guide/themes",
  "/booking": "booking/flights",
  "/booking/tickets/1": "booking/ticket",
  "/booking/hotels": "booking/hotels",
  "/booking/flights": "booking/flights",
  "/booking/queue": "booking/queue",
  "/pay/qr": "payment/qr-approve",
  "/pay/toss": "payment/toss-return",
  "/pay/kakao": "payment/kakao-return",
  "/mypage": "mypage/mypage",
  "/trips/1/record": "trips/record",
  "/admin": "admin/admin",
  "/admin/places": "admin/places",
  "/admin/scan": "admin/scan"
};

function navigateTo(route) {
  let destination = route || "/home";
  if (window.location.protocol === "file:") {
    /* 탭·패널은 ?tab= / ?panel= 로 구분한다. 템플릿 맵은 경로만 들고 있으므로 쿼리를 뗀다.
       정적 파일로 열 때는 탭이 미리 선택되지 않는다. 파일에는 서버가 없어 어쩔 수 없다. */
    const template = ALL_MY_TRIPS_TEMPLATE_ROUTES[(route || "/home").split("?")[0]];
    if (template) {
      destination = new URL("../" + template + ".html", window.location.href).href;
    }
  }
  if (window.AllMyTripsLoading) {
    window.AllMyTripsLoading.show();
    window.setTimeout(function () {
      window.location.href = destination;
    }, 1400);
    return;
  }
  window.location.href = destination;
}

document.addEventListener("click", function (event) {
  const routeButton = event.target.closest("[data-route]:not(body)");
  if (routeButton) {
    navigateTo(routeButton.dataset.route);
    return;
  }

  const toggle = event.target.closest("[data-toggle]");
  if (toggle) {
    toggle.classList.toggle("selected");
    return;
  }

  const group = event.target.closest("[data-toggle-group]");
  if (group) {
    group.parentElement.querySelectorAll("button").forEach(function (button) {
      button.classList.remove("selected");
    });
    group.classList.add("selected");
  }
});

document.addEventListener("submit", function (event) {
  if (event.target.matches("[data-demo-form]")) {
    event.preventDefault();
    window.AllMyTripsModal.showToast("입력 내용을 저장했습니다.");
    if (event.target.dataset.next) navigateTo(event.target.dataset.next);
  }
  if (event.target.matches("[data-chat-form]")) {
    event.preventDefault();
    const input = event.target.querySelector("input");
    if (input && input.value.trim()) {
      // 친 글자를 HTML 문자열로 이어 붙이면 `<img onerror=...>` 같은 입력이 그대로 실행된다.
      // 요소를 만들어 textContent로 넣으면 무엇을 치든 글자로만 남는다. (CodeQL js/xss-through-dom)
      const message = document.createElement("div");
      message.className = "user-message";
      message.textContent = input.value;
      event.target.insertAdjacentElement("beforebegin", message);
      input.value = "";
    }
  }
});


/*
 * 전체 화면 목록. 각 항목은 [경로, 이름, 그룹] 세 칸이다.
 *
 * 경로는 실제로 열리는 주소여야 한다. 예전에는 여행 관련 화면이 전부 `/trips/busan/...`
 * 이었는데, map·optimize는 컨트롤러가 slug(String)를 받아 열리지만
 * schedule·record는 tripId(Long)를 받아 400이 났다. 목록에서 눌러 확인할 수 없는
 * 화면이 두 개 있었던 셈이다.
 */
const ALL_MY_TRIPS_SCREENS = [
  [
    "/home",
    "메인 화면",
    "home"
  ],
  [
    "/auth/login",
    "로그인",
    "auth"
  ],
  [
    "/auth/signup",
    "회원가입",
    "auth"
  ],
  [
    "/trips/new/plan",
    "여행 계획 방식",
    "trips"
  ],
  [
    "/trips/new/basic",
    "여행 기본 정보",
    "trips"
  ],
  [
    "/trips/new/style",
    "여행 스타일",
    "trips"
  ],
  [
    "/guide",
    "추천 장소",
    "guide"
  ],
  [
    "/guide/places/haeundae",
    "장소 상세",
    "guide"
  ],
  [
    /* tripId 없이 여는 진입점. schedule.js가 사용자의 여행 목록에서 첫 번째를 잡는다. */
    "/trips/schedule",
    "여행 일정 편집",
    "trips"
  ],
  [
    "/trips/busan/map",
    "지도 기반 경로",
    "trips"
  ],
  [
    "/ai-trip-plan",
    "AI 여행계획",
    "trips"
  ],
  [
    "/ai-guide",
    "AI 여행 가이드",
    "guide"
  ],
  [
    "/trips/busan/optimize",
    "AI 일정 최적화",
    "trips"
  ],
  [
    "/guide/themes",
    "테마 여행",
    "guide"
  ],
  /*
   * 예약은 주소 하나에 탭 넷이다. 사용자가 보는 것이 탭마다 완전히 다르므로 각각 한 화면으로 센다.
   * flights.js가 ?tab= 을 읽어 해당 탭을 열어준다(flight/hotel/ticket/mine).
   *
   * /booking 과 /booking/hotels 는 목록에서 뺐다. 둘 다 여기로 오는 리다이렉트라
   * 남겨두면 같은 화면이 세 번 등장한다. 이미 공유된 주소가 있어 라우트 자체는 남긴다.
   */
  [
    "/booking/flights?tab=flight",
    "예약 · 항공편 검색",
    "booking"
  ],
  [
    "/booking/flights?tab=hotel",
    "예약 · 숙소 검색",
    "booking"
  ],
  [
    "/booking/flights?tab=ticket",
    "예약 · 티켓·액티비티",
    "booking"
  ],
  [
    "/booking/flights?tab=mine",
    "예약 · 내 예약",
    "booking"
  ],
  /*
   * 티켓 상세는 상품 번호로 연다. 목록에서 고른 상품을 그리므로, 목록에 든 번호가
   * 아니면 상품을 찾을 수 없다는 안내가 뜬다. 여기서는 1번을 대표로 둔다. (#281)
   */
  [
    "/booking/tickets/1",
    "티켓 상세·예약",
    "booking"
  ],
  [
    "/booking/queue",
    "예약 대기열",
    "booking"
  ],
  /*
   * QR 결제 승인 화면. 보통은 결제 QR을 찍어서 들어오므로 주소에 토큰이 붙지만,
   * 목록에서 그냥 열면 "결제 QR 정보가 없습니다" 안내가 뜬다. 화면이 있다는 사실과
   * 생김새를 보는 자리라 토큰 없는 주소로 둔다. (#281)
   */
  [
    "/pay/qr",
    "QR 결제 승인",
    "booking"
  ],
  /*
   * 결제사에서 돌아오는 화면 둘. 보통은 토스·카카오페이가 결제 결과를 주소에 실어
   * 보내지만, 목록에서 그냥 열면 "결제가 취소되었거나 완료되지 않았어요" 안내가 뜬다.
   * QR 승인 화면과 같은 이유로 결과 없는 주소로 둔다. (#281)
   */
  [
    "/pay/toss",
    "토스 결제 결과",
    "booking"
  ],
  [
    "/pay/kakao",
    "카카오페이 결제 결과",
    "booking"
  ],
  /*
   * 마이페이지도 주소 하나에 사이드바로 고르는 패널이다. 예약·관리자와 같은 이유로 각각 센다.
   * mypage.js가 ?view= 를 읽는다(trips/favorites/reviews/support/settings, 없으면 대시보드).
   *
   * `예약 내역`과 `알림`은 사이드바에서 disabled라 열 수 없다. 목록에도 넣지 않는다.
   */
  [
    "/mypage",
    "마이 페이지",
    "mypage"
  ],
  [
    "/mypage?view=trips",
    "마이페이지 · 내 여행",
    "mypage"
  ],
  [
    "/mypage?view=tickets",
    "마이페이지 · 예매한 티켓",
    "mypage"
  ],
  [
    "/mypage?view=favorites",
    "마이페이지 · 찜한 여행지",
    "mypage"
  ],
  [
    "/mypage?view=reviews",
    "마이페이지 · 리뷰 & 후기",
    "mypage"
  ],
  [
    "/mypage?view=support",
    "마이페이지 · 고객센터 문의",
    "mypage"
  ],
  [
    "/mypage?view=settings",
    "마이페이지 · 계정 설정",
    "mypage"
  ],
  [
    /*
     * 기록 화면은 schedule과 달리 tripId 없이 여는 경로가 없고, record.js도
     * 여행 목록에서 대신 골라주지 않는다(없으면 "잘못된 여행 정보입니다"를 띄운다).
     * 그래서 실제 번호로 둔다. 여행이 하나도 없는 환경에서는 안내 문구가 보인다.
     */
    "/trips/1/record",
    "여행 기록",
    "trips"
  ],
  /*
   * 관리자도 주소 하나에 사이드바로 고르는 패널 일곱이다. 예약과 같은 이유로 각각 센다.
   * admin.js가 ?panel= 을 읽는다. 없으면 신고 관리가 열린다.
   */
  [
    "/admin?panel=reports",
    "관리자 · 신고 관리",
    "admin"
  ],
  [
    "/admin/places",
    "관리자 · 추천 장소 관리",
    "admin"
  ],
  [
    "/admin/scan",
    "관리자 · 현장 검표 (폰)",
    "admin"
  ],
  [
    "/admin?panel=metrics",
    "관리자 · 운영 지표",
    "admin"
  ],
  [
    "/admin?panel=products",
    "관리자 · 예약 상품·재고",
    "admin"
  ],
  [
    "/admin?panel=reservations",
    "관리자 · 예약 모니터링",
    "admin"
  ],
  [
    "/admin?panel=performance",
    "관리자 · 성능 모니터링",
    "admin"
  ],
  [
    "/admin?panel=chat",
    "관리자 · 상담 채팅",
    "admin"
  ],
  [
    "/admin?panel=support",
    "관리자 · 1:1 문의 관리",
    "admin"
  ],
  [
    "/admin?panel=audit",
    "관리자 · 조작 이력",
    "admin"
  ]
];

const MODALS = {
  destination: ["M01", "목적지 검색", "도시, 국가 또는 공항을 검색하세요.", "파리, 바르셀로나, 도쿄 중 원하는 목적지를 선택할 수 있습니다."],
  date: ["M02", "날짜 선택", "최대 30일까지 선택할 수 있어요.", "2026.08.12 → 2026.08.19 · 7박 8일"],
  guests: ["M03", "인원 선택", "여행 인원과 객실 수를 선택하세요.", "성인 2명 · 아동 0명 · 객실 1개"],
  terms: ["M04", "약관 상세", "서비스 이용 전 필수 내용을 확인해 주세요.", "서비스 이용 및 개인정보 처리 약관입니다."],
  "new-trip": ["M05", "새 여행 만들기", "여행 이름과 기간을 정해 주세요.", "2026 여름 부산 여행"],
  "add-place": ["M06", "일정에 장소 추가", "원하는 날짜와 시간에 장소를 추가하세요.", "해운대 블루라인파크 · DAY 1"],
  conflict: ["M07", "일정 충돌 경고", "시간이 겹치는 일정이 있어 확인이 필요합니다.", "감천문화마을을 16:30으로 이동하는 것을 추천합니다."],
  share: ["M08", "여행 공유", "동행자에게 링크를 보내세요.", "공유 링크: allmytrips.app/t/busan-2026"],
  "apply-ai": ["M09", "AI 추천 적용", "최적화된 순서로 현재 일정을 변경합니다.", "이동 45분 절약 · 영업시간 충돌 1건 해결"],
  payment: ["M10", "예약 옵션 및 결제", "결제 전 상품과 금액을 확인하세요.", "최종 결제 금액 ₩2,540,000"],
  refund: ["M11", "예약 취소 및 환불", "취소 조건과 예상 환불액을 확인하세요.", "예상 환불액 ₩2,420,000"],
  queue: ["M12", "대기열 만료", "예약 순번이 만료되었습니다.", "재입장하면 새로운 순번이 발급됩니다."],
  upload: ["M13", "여행 사진 업로드", "사진과 공개 범위를 설정하세요.", "JPG, PNG · 최대 10MB"],
  "login-required": ["M14", "로그인 필요", "저장한 여행을 계속하려면 로그인이 필요합니다.", "로그인 후 현재 화면으로 돌아올 수 있습니다."],
  "delete-account": ["M15", "회원 탈퇴", "탈퇴 전 남은 예약과 데이터 처리 내용을 확인하세요.", "진행 중인 예약 1건이 있습니다."],
  "admin-product": ["M16", "상품 및 재고 수정", "변경 내용은 예약 화면과 대기열에 즉시 반영됩니다.", "해운대 스카이캡슐 · 현재 재고 18"],
};

function roots() {
  return {
    modal: document.querySelector("#modal-root"),
    directory: document.querySelector("#directory-root"),
    toast: document.querySelector("#toast-root"),
  };
}

const MODAL_DIAGNOSTIC_KEY = "all-my-trips-modal-diagnostic";
let activeModalName = null;
let modalCloseInProgress = false;

function saveModalDiagnostic(reason) {
  try {
    sessionStorage.setItem(MODAL_DIAGNOSTIC_KEY, JSON.stringify({
      reason: reason,
      modal: activeModalName,
      time: Date.now(),
    }));
  } catch (error) {
    console.warn("[modal] 진단 기록을 저장하지 못했습니다.", error);
  }
}

function reportModalDiagnostic(reason) {
  const messages = {
    "page-unload": "페이지가 새로고침되거나 이동되어 모달이 닫혔습니다.",
    "unexpected-dom-clear": "다른 스크립트가 모달 DOM을 삭제했습니다.",
  };
  const message = messages[reason];
  if (message) {
    showToast("진단: " + message);
    console.warn("[modal] " + message);
  }
}

function openModal(name) {
  const root = roots().modal;
  if (!root) return;
  activeModalName = name;
  modalCloseInProgress = false;
  const modal = MODALS[name] || MODALS.destination;
  const danger = ["conflict", "refund", "queue", "delete-account"].includes(name);
  root.innerHTML = `
    <div class="modal-backdrop">
      <section class="modal-card ${danger ? "danger-modal" : ""}" role="dialog" aria-modal="true">
        <div class="modal-code">${modal[0]}</div>
        <button class="modal-close" type="button" data-close aria-label="닫기">×</button>
        <h2>${modal[1]}</h2>
        <p>${modal[2]}</p>
        <div class="notice ${danger ? "danger" : ""}"><span>${modal[3]}</span></div>
        <div class="modal-actions">
          <button class="outline-button" type="button" data-close>취소</button>
          <button class="${danger ? "danger-button" : "primary-button"}" type="button" data-complete="${modal[1]} 처리를 완료했습니다.">확인</button>
        </div>
      </section>
    </div>
  `;
}

function closeModal(reason) {
  const root = roots().modal;
  modalCloseInProgress = true;
  if (root) root.innerHTML = "";
  activeModalName = null;
  console.info("[modal] 닫힘 원인:", reason || "programmatic");
  window.setTimeout(function () { modalCloseInProgress = false; }, 0);
}

function showToast(message) {
  const root = roots().toast;
  if (!root) return;
  root.innerHTML = '<div class="toast">✓ ' + message + "</div>";
  window.setTimeout(function () { root.innerHTML = ""; }, 2400);
}

function openDirectory() {
  const root = roots().directory;
  if (!root) return;

  const isAuthenticated =
      document.documentElement.dataset.authenticated === "true";

  const userRole =
      document.documentElement.dataset.userRole || "";

  const visibleScreens = ALL_MY_TRIPS_SCREENS.filter(function (screen) {
    const group = screen[2];

    if (group === "auth") {
      return !isAuthenticated;
    }

    if (group === "mypage") {
      return isAuthenticated;
    }

    if (group === "admin") {
      return isAuthenticated && userRole === "ADMIN";
    }

    return true;
  });

  const groups = {};

  visibleScreens.forEach(function (screen) {
    groups[screen[2]] = groups[screen[2]] || [];
    groups[screen[2]].push(screen);
  });

  const current = document.body.dataset.route;

  root.innerHTML = `
    <div class="drawer-backdrop">
      <aside class="screen-directory">
        <div class="drawer-head">
          <div>
            <span>ALL SCREENS</span>
            <h2>전체 화면 ${visibleScreens.length}</h2>
          </div>
          <button data-directory-close>×</button>
        </div>
        ${Object.keys(groups).map(function (group) {
    return "<section><h3>" + group + "</h3>" + groups[group].map(function (screen) {
      return '<button class="' + (current === screen[0] ? "active" : "") + '" data-route="' + screen[0] + '"><span>•</span><b>' + screen[1] + "</b><em>›</em></button>";
    }).join("") + "</section>";
  }).join("")}
      </aside>
    </div>
  `;
}

function closeDirectory() {
  const root = roots().directory;
  if (root) root.innerHTML = "";
}

document.addEventListener("click", function (event) {
  const modalButton = event.target.closest("[data-modal]");
  if (modalButton) {
    event.preventDefault();
    event.stopImmediatePropagation();
    openModal(modalButton.dataset.modal);
    return;
  }
  if (event.target.closest("[data-directory]")) {
    openDirectory();
    return;
  }
  if (event.target.closest("[data-directory-close]")) {
    closeDirectory();
    return;
  }
  if (event.target.closest("[data-close]")) {
    closeModal("close-button");
    return;
  }
  const complete = event.target.closest("[data-complete]");
  if (complete) {
    closeModal("complete-button");
    showToast(complete.dataset.complete);
    return;
  }
  const toast = event.target.closest("[data-toast]");
  if (toast) showToast(toast.dataset.toast);
}, true);

window.addEventListener("keydown", function (event) {
  if (event.key === "Escape") {
    closeModal("escape-key");
    closeDirectory();
  }
});

window.addEventListener("beforeunload", function () {
  const root = roots().modal;
  if (root && root.children.length > 0) saveModalDiagnostic("page-unload");
});

document.addEventListener("DOMContentLoaded", function () {
  const root = roots().modal;
  if (root) {
    new MutationObserver(function () {
      if (activeModalName && root.children.length === 0 && !modalCloseInProgress) {
        saveModalDiagnostic("unexpected-dom-clear");
        activeModalName = null;
        reportModalDiagnostic("unexpected-dom-clear");
      }
    }).observe(root, { childList: true });
  }

  try {
    const saved = JSON.parse(sessionStorage.getItem(MODAL_DIAGNOSTIC_KEY) || "null");
    sessionStorage.removeItem(MODAL_DIAGNOSTIC_KEY);
    if (saved && Date.now() - saved.time < 10000) {
      window.setTimeout(function () { reportModalDiagnostic(saved.reason); }, 100);
    }
  } catch (error) {
    console.warn("[modal] 진단 기록을 읽지 못했습니다.", error);
  }
});

window.AllMyTripsModal = { openModal, closeModal, showToast, openDirectory, closeDirectory };


