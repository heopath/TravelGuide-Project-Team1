/* 장소 상세 후기 화면 수용 기준 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/guide/place-detail.html");
const REVIEW_JS = path.join(ROOT, "src/main/resources/static/js/pages/guide/place-reviews.js");

let passed = 0;
let failed = 0;
const T = (name, condition) => {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name); }
};

function until(predicate, timeoutMs = 3000) {
  return new Promise((resolve, reject) => {
    const started = Date.now();
    const tick = () => {
      if (predicate()) return resolve();
      if (Date.now() - started > timeoutMs) return reject(new Error("timeout"));
      setTimeout(tick, 10);
    };
    tick();
  });
}

async function run() {
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/guide/places/301",
    runScripts: "outside-only"
  });
  const w = dom.window;
  const d = w.document;
  const toasts = [];
  w.AllMyTripsModal = { showToast: (message) => toasts.push(message) };
  w.fetch = async () => ({
    ok: true,
    status: 200,
    json: async () => ({
      success: true,
      data: {
        authenticated: true,
        summary: {
          averageRating: 4.5,
          reviewCount: 2,
          ratingDistribution: { 5: 1, 4: 1, 3: 0, 2: 0, 1: 0 }
        },
        reviews: [{
          placeReviewId: 10,
          placeId: 301,
          userId: 7,
          nickname: "강릉여행자",
          rating: 5,
          content: "바다와 숲을 함께 볼 수 있어 좋았습니다.",
          verifiedVisit: true,
          ownedByRequester: true,
          createdAt: "2026-08-10T10:00:00+09:00"
        }],
        myReview: {
          placeReviewId: 10,
          rating: 5,
          content: "바다와 숲을 함께 볼 수 있어 좋았습니다."
        },
        hasNext: false
      }
    })
  });

  w.eval(fs.readFileSync(REVIEW_JS, "utf8"));
  d.dispatchEvent(new w.Event("DOMContentLoaded"));
  d.dispatchEvent(new w.CustomEvent("placeDetailLoaded", {
    detail: { place: { placeId: 301, name: "테라로사 사천해변점" } }
  }));
  await until(() => d.querySelectorAll(".review-item").length === 1);

  T("평균 평점을 표시한다", d.querySelector("[data-review-average]").textContent === "4.5");
  T("후기 개수를 표시한다", d.querySelector("[data-review-count]").textContent === "2");
  T("방문 인증을 표시한다", Boolean(d.querySelector(".verified-badge")));
  T("본인 후기 수정·삭제 버튼을 표시한다", d.querySelectorAll(".review-owner-actions button").length === 2);
  T("작성한 사용자는 수정 버튼으로 표시한다", d.querySelector("[data-review-open]").textContent === "내 후기 수정하기");
  T("후기 폼 제출은 전역 로딩창을 사용하지 않는다", d.querySelector("[data-review-form]").hasAttribute("data-no-global-loading"));

  d.querySelector("[data-review-open]").click();
  T("후기 작성은 별도 페이지가 아닌 모달로 연다", !d.querySelector("[data-review-modal]").hidden);
  T("수정 모달에 기존 내용을 채운다", d.querySelector("[data-review-content]").value.includes("바다와 숲"));

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed) process.exitCode = 1;
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
