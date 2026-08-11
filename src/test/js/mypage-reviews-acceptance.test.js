/* 마이페이지 장소 후기 목록 수용 기준 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/mypage/mypage.html");
const REVIEW_JS = path.join(ROOT, "src/main/resources/static/js/pages/mypage/mypage-reviews.js");

let passed = 0;
let failed = 0;
function test(name, condition) {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name); }
}

async function run() {
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/mypage?view=reviews",
    runScripts: "outside-only",
  });
  const w = dom.window;
  const d = w.document;
  let requestedUrl = "";
  w.request = async (url) => {
    requestedUrl = url;
    return {
      reviews: [{
        placeReviewId: 10,
        placeId: 301,
        placeName: "테라로사 사천해변점",
        placeCategory: "CAFE",
        placeAddress: "강원특별자치도 강릉시 사천면",
        placeImageUrl: "https://example.com/cafe.jpg",
        rating: 5,
        content: "바다를 보며 쉬기 좋았습니다.",
        verifiedVisit: true,
        updatedAt: "2026-08-11T10:00:00+09:00",
      }],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    };
  };
  w.renderPagination = (container) => { container.hidden = true; };

  const source = fs.readFileSync(REVIEW_JS, "utf8")
    .replace(/import\s*\{[\s\S]*?\}\s*from\s*"\.\/mypage-common\.js";/, "")
    .replace("export function initReviews()", "window.initReviews = function initReviews()");
  w.eval(source);
  await w.initReviews();

  test("리뷰 메뉴가 활성 버튼이다", !d.querySelector("[data-open-reviews]").disabled);
  test("내 후기 API를 호출한다", requestedUrl.includes("/api/v1/members/me/place-reviews"));
  test("작성한 후기 카드가 표시된다", d.querySelectorAll(".mypage-review-card").length === 1);
  test("장소명과 후기 내용을 표시한다", d.querySelector(".mypage-review-card").textContent.includes("테라로사")
    && d.querySelector(".mypage-review-card").textContent.includes("바다를 보며"));
  test("장소 상세로 이동할 수 있다", d.querySelector(".mypage-review-foot a").getAttribute("href") === "/guide/places/301");
  test("전체 후기 개수를 표시한다", d.querySelector("[data-review-total-count]").textContent === "1개");

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed) process.exitCode = 1;
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
