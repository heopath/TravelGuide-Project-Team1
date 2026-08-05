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
    "여행지 추천",
    "trips"
  ],
  [
    "/guide",
    "여행 가이드",
    "guide"
  ],
  [
    "/guide/places/haeundae",
    "장소 상세",
    "guide"
  ],
  [
    "/trips/busan/schedule",
    "여행 일정 편집",
    "trips"
  ],
  [
    "/trips/busan/map",
    "지도 기반 경로",
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
  [
    "/booking",
    "예약 허브",
    "booking"
  ],
  [
    "/booking/tickets/blueline",
    "티켓 상세·예약",
    "booking"
  ],
  [
    "/booking/hotels",
    "숙소 검색",
    "booking"
  ],
  [
    "/booking/flights",
    "항공편 검색",
    "booking"
  ],
  [
    "/booking/queue",
    "예약 대기열",
    "booking"
  ],
  [
    "/mypage",
    "마이 페이지",
    "mypage"
  ],
  [
    "/trips/busan/record",
    "여행 기록",
    "trips"
  ],
  [
    "/admin",
    "관리자 대시보드",
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
  const groups = {};
  ALL_MY_TRIPS_SCREENS.forEach(function (screen) {
    groups[screen[2]] = groups[screen[2]] || [];
    groups[screen[2]].push(screen);
  });
  const current = document.body.dataset.route;
  root.innerHTML = `
    <div class="drawer-backdrop">
      <aside class="screen-directory">
        <div class="drawer-head"><div><span>ALL SCREENS</span><h2>전체 화면 21</h2></div><button data-directory-close>×</button></div>
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
