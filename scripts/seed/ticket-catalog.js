/*
 * 체험·축제 예약 상품을 관리자 API로 한 번에 올린다.
 *
 * 화면에서 손으로 넣으면 장소 7개 · 상품 7개 · 옵션 15개 · 회차 수백 개를 일일이
 * 눌러야 한다. 여기서는 관리자가 쓰는 것과 같은 API를 그대로 부른다. SQL로 밀어
 * 넣지 않는 이유가 이것이다 — 화면 뒤 검증과 상태 규칙을 똑같이 거친다.
 *
 * 만드는 사슬:
 *
 *   장소  ->  예약 상품  ->  옵션(등급·가격)  ->  회차(이용일·시각)  ->  재고
 *                                                        |
 *                                        그 뒤는 예매 -> 결제 -> 발급 -> 검표
 *
 * 실행:
 *
 *   ADMIN_EMAIL=관리자@example.com ADMIN_PASSWORD=비밀번호 \
 *     node scripts/seed/ticket-catalog.js
 *
 *   BASE_URL=http://localhost:8099 ...   다른 포트에 붙일 때
 *
 * 다시 돌려도 안전하다. 같은 이름의 장소가 이미 있으면 새로 만들지 않고 그 장소에
 * 상품이 붙어 있는지만 본다. 회차는 서버가 겹치는 날을 건너뛰고 몇 개를 건너뛰었는지
 * 알려준다.
 */

const BASE_URL = (process.env.BASE_URL || "http://localhost:8080").replace(/\/$/, "");
const ADMIN_EMAIL = process.env.ADMIN_EMAIL;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;

/* 판매 기간과 이용 기간. 이 사이에 여행이 걸쳐 있어야 예매가 막히지 않는다. */
const SALE_START = "2026-08-01T00:00:00+09:00";
const SALE_END = "2026-09-20T23:59:00+09:00";
const USAGE_START = "2026-08-24";
const USAGE_END = "2026-09-20";

/* =========================================================
   올릴 것

   장소마다 상품 하나, 상품마다 옵션 여럿, 옵션마다 회차를 만든다.
   times가 비어 있으면 종일권이라 시각 없이 하루에 하나만 생긴다.
   ========================================================= */
const CATALOG = [
  {
    place: {
      category: "ACTIVITY",
      name: "해운대 블루라인파크 스카이캡슐",
      region: "부산광역시", city: "해운대구",
      address: "부산 해운대구 달맞이길62번길 13",
      latitude: 35.1587, longitude: 129.1723,
      description: "미포에서 청사포까지 옛 동해남부선 폐선 구간을 따라 달리는 해안 관광열차입니다. 캡슐은 4인까지 함께 탈 수 있고, 창밖으로 해운대 해안선이 이어집니다.",
      websiteUrl: "https://www.bluelinepark.com",
    },
    product: { name: "해운대 블루라인파크 스카이캡슐 이용권", description: "미포 정거장에서 출발하는 편도 이용권입니다. 탑승 20분 전까지 정거장에 도착해 주세요." },
    options: [
      { name: "스카이캡슐 2인승 (편도)", unitPrice: 35000, maxQuantityPerUser: 4, sortOrder: 1, times: [["09:30", "10:10"], ["13:00", "13:40"], ["16:30", "17:10"]], quantity: 40 },
      { name: "스카이캡슐 4인승 (편도)", unitPrice: 55000, maxQuantityPerUser: 3, sortOrder: 2, times: [["10:30", "11:10"], ["14:00", "14:40"]], quantity: 24 },
    ],
  },
  {
    place: {
      category: "ACTIVITY",
      name: "서울스카이 전망대",
      region: "서울특별시", city: "송파구",
      address: "서울 송파구 올림픽로 300 롯데월드타워",
      latitude: 37.5125, longitude: 127.1025,
      description: "롯데월드타워 117층부터 123층까지 이어지는 전망대입니다. 유리 바닥 전망대와 야외 테라스에서 한강과 도심을 내려다볼 수 있습니다.",
      websiteUrl: "https://seoulsky.lotteworld.com",
    },
    product: { name: "서울스카이 전망대 입장권", description: "지정한 시간대에 입장하는 전망대 입장권입니다. 매표소를 거치지 않고 바로 입장할 수 있습니다." },
    options: [
      { name: "일반 입장권", unitPrice: 29000, maxQuantityPerUser: 6, sortOrder: 1, times: [["10:00", "12:00"], ["14:00", "16:00"], ["18:00", "20:00"]], quantity: 120 },
      { name: "패스트 입장권 (대기 없이 입장)", unitPrice: 45000, maxQuantityPerUser: 4, sortOrder: 2, times: [["11:00", "13:00"], ["17:00", "19:00"]], quantity: 40 },
    ],
  },
  {
    place: {
      category: "ACTIVITY",
      name: "부산 엑스더스카이",
      region: "부산광역시", city: "해운대구",
      address: "부산 해운대구 달맞이길 30 엘시티 랜드마크타워",
      latitude: 35.1594, longitude: 129.1683,
      description: "엘시티 랜드마크타워 98층부터 100층에 있는 전망대입니다. 광안대교와 해운대 해수욕장이 한눈에 들어옵니다.",
      websiteUrl: "https://www.xtheskybusan.com",
    },
    product: { name: "부산 엑스더스카이 전망대 입장권", description: "100층 전망대 입장권입니다. 일몰 시간대는 일찍 마감될 수 있습니다." },
    options: [
      { name: "일반 입장권", unitPrice: 27000, maxQuantityPerUser: 6, sortOrder: 1, times: [["11:00", "13:00"], ["15:00", "17:00"], ["18:30", "20:30"]], quantity: 90 },
    ],
  },
  {
    place: {
      category: "ACTIVITY",
      name: "강촌레일파크 레일바이크",
      region: "강원특별자치도", city: "춘천시",
      address: "강원 춘천시 남산면 강촌구곡길 17",
      latitude: 37.8148, longitude: 127.6191,
      description: "옛 경춘선 강촌역 구간을 따라 북한강을 끼고 달리는 레일바이크입니다. 김유정역 코스와 강촌 코스로 나뉩니다.",
      websiteUrl: "https://www.railpark.co.kr",
    },
    product: { name: "강촌레일파크 레일바이크 이용권", description: "출발 시각 30분 전까지 매표소에서 좌석을 배정받습니다. 우천 시에도 운행합니다." },
    options: [
      { name: "레일바이크 2인승", unitPrice: 40000, maxQuantityPerUser: 3, sortOrder: 1, times: [["09:00", "10:30"], ["13:30", "15:00"]], quantity: 60 },
      { name: "레일바이크 4인승", unitPrice: 56000, maxQuantityPerUser: 2, sortOrder: 2, times: [["11:00", "12:30"], ["15:30", "17:00"]], quantity: 30 },
    ],
  },
  {
    place: {
      category: "FESTIVAL",
      name: "안동국제탈춤페스티벌",
      region: "경상북도", city: "안동시",
      address: "경북 안동시 육사로 239 탈춤공원",
      latitude: 36.5636, longitude: 128.7292,
      description: "탈춤공원과 하회마을 일대에서 열리는 탈춤 축제입니다. 국내외 탈춤 공연과 탈 만들기 체험이 함께 진행됩니다.",
      websiteUrl: "https://www.maskdance.com",
    },
    product: { name: "안동국제탈춤페스티벌 입장권", description: "축제장 입장권입니다. 공연 관람과 체험 프로그램 참여가 포함됩니다." },
    options: [
      { name: "종일 입장권 (성인)", unitPrice: 12000, maxQuantityPerUser: 6, sortOrder: 1, times: [], quantity: 300 },
      { name: "종일 입장권 (청소년)", unitPrice: 8000, maxQuantityPerUser: 6, sortOrder: 2, times: [], quantity: 150 },
    ],
  },
  {
    place: {
      category: "FESTIVAL",
      name: "진주남강유등축제",
      region: "경상남도", city: "진주시",
      address: "경남 진주시 남강로 626 진주성",
      latitude: 35.1895, longitude: 128.0797,
      description: "진주성과 남강 일대에 등을 띄우는 축제입니다. 해가 지면 강 위의 유등과 성벽의 조명이 함께 켜집니다.",
      websiteUrl: "https://www.jinju.go.kr",
    },
    product: { name: "진주남강유등축제 관람권", description: "축제장 관람권입니다. 유등은 일몰 이후에 점등됩니다." },
    options: [
      { name: "관람권 (성인)", unitPrice: 10000, maxQuantityPerUser: 6, sortOrder: 1, times: [["17:00", "22:00"]], quantity: 400 },
      { name: "관람권 (어린이·청소년)", unitPrice: 5000, maxQuantityPerUser: 6, sortOrder: 2, times: [["17:00", "22:00"]], quantity: 200 },
    ],
  },
  {
    place: {
      category: "FESTIVAL",
      name: "서울세계불꽃축제",
      region: "서울특별시", city: "영등포구",
      address: "서울 영등포구 여의동로 330 여의도한강공원",
      latitude: 37.5285, longitude: 126.9326,
      description: "여의도한강공원에서 열리는 불꽃 축제입니다. 한강 둔치와 인근 고수부지에서 관람할 수 있습니다.",
      websiteUrl: "https://hangang.seoul.go.kr",
    },
    product: { name: "서울세계불꽃축제 지정 관람석", description: "혼잡을 피해 앉아서 볼 수 있는 지정 관람석입니다. 자리는 입장 순서대로 배정됩니다." },
    options: [
      { name: "지정 관람석 (1인)", unitPrice: 30000, maxQuantityPerUser: 4, sortOrder: 1, times: [["17:30", "21:00"]], quantity: 500 },
    ],
  },
];

/* =========================================================
   HTTP — 쿠키와 CSRF를 직접 챙긴다
   ========================================================= */

const cookies = new Map();

function storeCookies(response) {
  const raw = response.headers.getSetCookie ? response.headers.getSetCookie() : [];
  for (const line of raw) {
    const [pair] = line.split(";");
    const index = pair.indexOf("=");
    if (index > 0) cookies.set(pair.slice(0, index).trim(), pair.slice(index + 1).trim());
  }
}

function cookieHeader() {
  return [...cookies.entries()].map(([k, v]) => k + "=" + v).join("; ");
}

async function call(method, path, body) {
  /*
   * 토큰을 먼저 받는다. 그 응답이 CSRF 쿠키도 함께 심으므로, 헤더를 만들기 전에
   * 받아 둬야 쿠키와 토큰이 짝이 맞는다. 순서가 바뀌면 로그인부터 403이 난다.
   */
  const token = method === "GET" ? null : await csrfToken();

  const headers = { Accept: "application/json", Cookie: cookieHeader() };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers["X-CSRF-TOKEN"] = token;

  const response = await fetch(BASE_URL + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  storeCookies(response);

  const text = await response.text();
  let payload = null;
  try { payload = text ? JSON.parse(text) : null; } catch { /* HTML 오류 페이지 */ }

  if (!response.ok || (payload && payload.success === false)) {
    const reason = payload?.message || text.slice(0, 200) || response.status;
    throw new Error(method + " " + path + " -> " + response.status + " " + reason);
  }
  return payload?.data;
}

async function csrfToken() {
  const response = await fetch(BASE_URL + "/api/v1/csrf", {
    headers: { Accept: "application/json", Cookie: cookieHeader() },
  });
  storeCookies(response);
  return (await response.json()).token;
}

/* =========================================================
   올리기
   ========================================================= */

/** 같은 이름이 이미 있으면 그것을 쓴다. 다시 돌려도 장소가 겹쳐 쌓이지 않게. */
async function ensurePlace(place) {
  const found = await call("GET", "/api/v1/admin/places?page=0&size=20&keyword="
      + encodeURIComponent(place.name));
  const already = (found?.items || []).find((candidate) => candidate.name === place.name);
  if (already) return { placeId: already.placeId, created: false };

  const created = await call("POST", "/api/v1/admin/places", {
    countryCode: "KR",
    active: true,
    recommended: true,
    ...place,
  });
  return { placeId: created.placeId, created: true };
}

/** 이 장소에 이미 상품이 있으면 건너뛴다. 회차까지 다시 만들면 수량이 꼬인다. */
async function existingProduct(placeId, name) {
  const page = await call("GET", "/api/v1/admin/ticket-products?page=0&size=100&keyword="
      + encodeURIComponent(name));
  return (page?.items || []).find(
      (candidate) => candidate.placeId === placeId && candidate.name === name);
}

async function seedOne(entry) {
  const { placeId, created } = await ensurePlace(entry.place);
  const label = entry.place.name;

  const already = await existingProduct(placeId, entry.product.name);
  if (already) {
    console.log(`  = ${label} — 상품이 이미 있어 건너뜀 (ticketProductId=${already.ticketProductId})`);
    return { places: created ? 1 : 0, products: 0, options: 0, slots: 0 };
  }

  const product = await call("POST", "/api/v1/admin/ticket-products", {
    placeId,
    name: entry.product.name,
    description: entry.product.description,
    saleStartAt: SALE_START,
    saleEndAt: SALE_END,
    usageStartDate: USAGE_START,
    usageEndDate: USAGE_END,
    saleType: "NORMAL",
  });

  let optionCount = 0;
  let slotCount = 0;
  let skippedCount = 0;

  for (const option of entry.options) {
    const saved = await call("POST", `/api/v1/admin/ticket-products/${product.ticketProductId}/options`, {
      name: option.name,
      description: null,
      unitPrice: option.unitPrice,
      maxQuantityPerUser: option.maxQuantityPerUser,
      sortOrder: option.sortOrder,
      isActive: true,
    });
    optionCount += 1;

    /* 종일권(times 비어 있음)은 시각 없이 하루에 하나만 만든다. */
    const rounds = option.times.length ? option.times : [[null, null]];
    for (const [startTime, endTime] of rounds) {
      const result = await call("POST", `/api/v1/admin/ticket-products/${product.ticketProductId}/slots`, {
        ticketProductOptionId: saved.ticketProductOptionId,
        usageStartDate: USAGE_START,
        usageEndDate: USAGE_END,
        weekdays: null,
        startTime,
        endTime,
        totalQuantity: option.quantity,
      });
      slotCount += result.created;
      skippedCount += result.skipped;
    }
  }

  await call("PATCH", `/api/v1/admin/ticket-products/${product.ticketProductId}/status`, {
    status: "ON_SALE",
  });

  const skippedNote = skippedCount ? ` (겹쳐서 건너뜀 ${skippedCount})` : "";
  console.log(`  + ${label} — 옵션 ${optionCount} · 회차 ${slotCount}${skippedNote} · 판매중`);
  return { places: created ? 1 : 0, products: 1, options: optionCount, slots: slotCount };
}

async function main() {
  if (!ADMIN_EMAIL || !ADMIN_PASSWORD) {
    console.error("ADMIN_EMAIL과 ADMIN_PASSWORD를 넘겨야 한다.");
    console.error('  ADMIN_EMAIL=관리자@example.com ADMIN_PASSWORD=비밀번호 node scripts/seed/ticket-catalog.js');
    process.exit(1);
  }

  console.log(`대상: ${BASE_URL}`);
  await call("POST", "/api/v1/auth/login", { email: ADMIN_EMAIL, password: ADMIN_PASSWORD });
  console.log(`로그인: ${ADMIN_EMAIL}`);
  console.log(`이용 기간: ${USAGE_START} ~ ${USAGE_END}`);
  console.log("");

  const total = { places: 0, products: 0, options: 0, slots: 0 };
  for (const entry of CATALOG) {
    const one = await seedOne(entry);
    for (const key of Object.keys(total)) total[key] += one[key];
  }

  console.log("");
  console.log(`끝. 장소 ${total.places} · 상품 ${total.products} · 옵션 ${total.options} · 회차 ${total.slots}`);
}

main().catch((error) => {
  console.error("");
  console.error("실패:", error.message);
  process.exit(1);
});
