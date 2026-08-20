(function () {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const esc = (value) => String(value ?? "").replace(/[&<>"']/g,
    (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));
  const categoryLabels = {
    ATTRACTION: "관광지", RESTAURANT: "맛집", CAFE: "카페", ACCOMMODATION: "숙박",
    FESTIVAL: "축제", ACTIVITY: "액티비티", TRANSPORT: "교통"
  };
  // selectedId는 편집 중인 장소, selectedIds는 일괄 처리로 고른 장소다.
  // backfillCursor는 이미지 채우기가 어디까지 훑었는지 기억한다.
  const state = {
    items: [], total: 0, selectedId: null, selectedIds: new Set(), loading: false, backfillCursor: 0
  };

  /* 한 번 누를 때 도는 묶음 수. 서버가 묶음마다 외부 API를 부르므로 무한정 돌지 않는다. */
  const MAX_BACKFILL_BATCHES = 20;

  function safeImageUrl(value) {
    if (!value) return "";
    try {
      const url = new URL(value, location.origin);
      return ["http:", "https:"].includes(url.protocol) ? url.href : "";
    } catch (error) { return ""; }
  }

  async function request(method, url, body) {
    const options = { method, credentials: "same-origin", headers: { Accept: "application/json" } };
    if (body !== undefined) {
      options.headers["Content-Type"] = "application/json";
      options.body = JSON.stringify(body);
    }
    const response = await fetch(url, options);
    if (response.status === 401) {
      location.href = "/auth/login?redirect=" + encodeURIComponent(location.pathname + location.search);
      throw new Error("로그인이 필요합니다.");
    }
    const payload = await response.json().catch(() => null);
    if (!response.ok || payload?.success === false) {
      const error = new Error(payload?.message || "요청을 처리하지 못했습니다.");
      error.status = response.status;
      throw error;
    }
    return payload?.data;
  }

  function filters() {
    const params = new URLSearchParams({ page: "0", size: "100" });
    const keyword = $("placeKeyword").value.trim();
    const category = $("placeCategoryFilter").value;
    const recommended = $("placeRecommendedFilter").value;
    if (keyword) params.set("keyword", keyword);
    if (category) params.set("category", category);
    if (recommended) params.set("recommended", recommended);
    return params;
  }

  function card(place) {
    const image = safeImageUrl(place.primaryImageUrl);
    const checked = state.selectedIds.has(String(place.placeId)) ? " checked" : "";
    return `<article class="admin-place-row" data-place-row="${place.placeId}">
      <label class="admin-place-select">
        <input type="checkbox" data-place-select="${place.placeId}"${checked} aria-label="${esc(place.name)} 선택" />
      </label>
      <div class="admin-place-thumb">${image
        ? `<img src="${esc(image)}" alt="${esc(place.name)} 대표 이미지" loading="lazy" />`
        : '<span aria-hidden="true">⌖</span>'}</div>
      <div class="admin-place-copy">
        <div><span class="admin-place-badge${place.recommended ? "" : " hidden"}">${place.recommended ? "추천중" : "추천 아님"}</span>
          <span class="admin-place-badge">${esc(categoryLabels[place.category] || place.category)}</span></div>
        <h3>${esc(place.name)}</h3>
        <p>${esc([place.region, place.city, place.address].filter(Boolean).join(" · ") || "주소 정보 없음")}</p>
      </div>
      <div class="admin-place-row-actions">
        <button type="button" data-place-edit="${place.placeId}">수정</button>
        <button type="button" data-place-recommended="${place.placeId}">${place.recommended ? "추천 내리기" : "추천 등록"}</button>
      </div>
    </article>`;
  }

  function renderBulkBar() {
    const count = state.selectedIds.size;
    $("placeBulkBar").hidden = state.items.length === 0;
    $("placeSelectedCount").textContent = count + "곳 선택";
    $("placeBulkRecommend").disabled = count === 0;
    $("placeBulkUnrecommend").disabled = count === 0;
    // 이 페이지의 항목이 모두 선택됐을 때만 전체 선택을 켠다.
    $("placeSelectAll").checked = state.items.length > 0
      && state.items.every((item) => state.selectedIds.has(String(item.placeId)));
  }

  function render() {
    $("placeList").innerHTML = state.items.map(card).join("");
    renderBulkBar();
    $("placeCount").textContent = `총 ${state.total.toLocaleString("ko-KR")}곳`;
    $("placeEmpty").hidden = state.loading || state.items.length > 0;
    if (state.loading) {
      $("placeEmpty").hidden = false;
      $("placeEmpty").textContent = "장소를 불러오는 중입니다.";
    } else if (!state.items.length) {
      $("placeEmpty").textContent = "조건에 맞는 추천 장소가 없습니다.";
    }
  }

  function notice(message) {
    $("placeAuthNotice").textContent = message;
    $("placeAuthNotice").hidden = false;
  }

  async function load() {
    state.loading = true;
    // 목록이 바뀌면 선택도 지운다. 화면에 없는 장소가 선택된 채 남으면 무엇을 처리하는지 알 수 없다.
    state.selectedIds.clear();
    render();
    try {
      const page = await request("GET", `/api/v1/admin/places?${filters()}`);
      state.items = page?.items || [];
      state.total = Number(page?.total || 0);
      $("placeAuthNotice").hidden = true;
    } catch (error) {
      state.items = [];
      state.total = 0;
      notice(error.status === 403
        ? "관리자 권한이 없습니다. 권한 변경 후 로그아웃하고 다시 로그인해 주세요."
        : "추천 장소를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      state.loading = false;
      render();
    }
  }

  function value(id, fallback) {
    const text = $(id).value.trim();
    return text || (fallback ?? null);
  }

  function numberValue(id) {
    const raw = $(id).value.trim();
    return raw === "" ? null : Number(raw);
  }

  function payload() {
    return {
      category: $("placeCategory").value,
      name: value("placeName", ""),
      countryCode: value("placeCountryCode", "KR"),
      region: value("placeRegion"), city: value("placeCity"), address: value("placeAddress"),
      latitude: numberValue("placeLatitude"), longitude: numberValue("placeLongitude"),
      description: value("placeDescription"), phone: value("placePhone"),
      websiteUrl: value("placeWebsiteUrl"), primaryImageUrl: value("placeImageUrl"),
      recommended: $("placeRecommended").checked
    };
  }

  function setFormStatus(message, error) {
    $("placeFormStatus").textContent = message;
    $("placeFormStatus").className = `admin-place-form-status${error ? " error" : ""}`;
    $("placeFormStatus").hidden = false;
  }

  function resetEditor() {
    state.selectedId = null;
    $("placeEditorForm").reset();
    $("placeId").value = "";
    $("placeCountryCode").value = "KR";
    $("placeRecommended").checked = true;
    $("placeEditorTitle").textContent = "새 장소 등록";
    $("placeSaveButton").textContent = "장소 등록";
    $("placeFormStatus").hidden = true;
    setGeocodeStatus(null);
  }

  function edit(placeId) {
    const place = state.items.find((item) => String(item.placeId) === String(placeId));
    if (!place) return;
    state.selectedId = place.placeId;
    $("placeId").value = place.placeId;
    $("placeName").value = place.name || "";
    $("placeCategory").value = place.category || "ATTRACTION";
    $("placeCountryCode").value = place.countryCode || "KR";
    $("placeRegion").value = place.region || "";
    $("placeCity").value = place.city || "";
    $("placeAddress").value = place.address || "";
    $("placeLatitude").value = place.latitude ?? "";
    $("placeLongitude").value = place.longitude ?? "";
    $("placeDescription").value = place.description || "";
    $("placePhone").value = place.phone || "";
    $("placeWebsiteUrl").value = place.websiteUrl || "";
    $("placeImageUrl").value = place.primaryImageUrl || "";
    $("placeRecommended").checked = place.recommended !== false;
    $("placeEditorTitle").textContent = "추천 장소 수정";
    $("placeSaveButton").textContent = "수정 저장";
    $("placeFormStatus").hidden = true;
    $("placeName").focus();
  }

  async function save(event) {
    event.preventDefault();
    const button = $("placeSaveButton");
    button.disabled = true;
    try {
      const id = state.selectedId;
      await request(id ? "PUT" : "POST", id ? `/api/v1/admin/places/${id}` : "/api/v1/admin/places", payload());
      await load();
      if (!id) resetEditor();
      setFormStatus(id ? "추천 장소를 수정했습니다." : "추천 장소를 등록했습니다.");
    } catch (error) {
      setFormStatus(error.message || "추천 장소를 저장하지 못했습니다.", true);
    } finally { button.disabled = false; }
  }

  /*
   * 관리자가 만지는 스위치는 추천 노출 하나뿐이다.
   * is_active(데이터 유효성)는 화면에 두지 않는다. 이 화면의 목적이 추천 큐레이션이라
   * 두 스위치가 거의 같은 뜻으로 읽혀 혼동을 주고, 비활성으로 내리면 카카오 장소
   * 중복 확인(search)에서도 빠져 그 장소를 아무도 일정에 담을 수 없게 된다.
   */
  /* 고른 장소를 한 번에 처리한다. 서버가 한 트랜잭션으로 묶어 일부만 반영되는 일이 없다. */
  async function bulkRecommend(recommended, button) {
    const placeIds = [...state.selectedIds].map(Number).filter(Number.isInteger);
    if (!placeIds.length) return;
    button.disabled = true;
    try {
      await request("PATCH", "/api/v1/admin/places/recommendation", { placeIds, recommended });
      state.selectedIds.clear();
      await load();
    } catch (error) {
      notice(error.message || "추천 상태를 바꾸지 못했습니다.");
    } finally { button.disabled = false; }
  }

  /*
   * 대표 이미지가 비어 있는 장소를 훑어 채운다.
   *
   * 장소마다 외부 API를 한 번씩 부르므로 서버가 한 묶음씩만 처리한다. 여기서 done이 될
   * 때까지 이어 부르되, 커서(nextAfter)로 넘긴다. 이미지를 못 찾은 장소는 다음에도 결과가
   * 비어 있어서, 커서 없이 "남은 개수"만 보고 돌면 영영 끝나지 않는다.
   */
  async function backfillImages(button) {
    const status = $("placeImageBackfillStatus");
    button.disabled = true;
    let scanned = 0;
    let filled = 0;
    try {
      for (let batch = 0; batch < MAX_BACKFILL_BATCHES; batch += 1) {
        const result = await request("POST",
          `/api/v1/admin/places/images/backfill?after=${state.backfillCursor}`);
        scanned += Number(result?.scanned || 0);
        filled += Number(result?.filled || 0);
        // 커서는 state에 남긴다. 다시 눌렀을 때 0부터 시작하면 사진이 없는 장소를
        // 매번 다시 조회하게 되고, 그 구간을 넘어가지 못한다.
        state.backfillCursor = Number(result?.nextAfter ?? state.backfillCursor);
        status.textContent = `${scanned}곳 확인, ${filled}곳에 이미지를 넣었습니다.`;
        if (result?.done) {
          state.backfillCursor = 0;
          status.textContent = filled
            ? `끝났습니다. ${scanned}곳을 확인해 ${filled}곳에 이미지를 넣었습니다.`
            : `끝났습니다. ${scanned}곳을 확인했지만 확실한 사진을 찾지 못했습니다.`;
          await load();
          return;
        }
      }
      // 여기까지 왔으면 아직 남았다. 한 번에 다 도는 것보다 다시 누르게 하는 편이 안전하다.
      status.textContent = `${scanned}곳 확인, ${filled}곳 완료. 남은 장소가 있어 다시 눌러 주세요.`;
      await load();
    } catch (error) {
      status.textContent = error.message || "이미지를 채우지 못했습니다.";
    } finally { button.disabled = false; }
  }

  async function toggleRecommended(placeId, button) {
    const place = state.items.find((item) => String(item.placeId) === String(placeId));
    if (!place) return;
    button.disabled = true;
    try {
      await request("PATCH", `/api/v1/admin/places/${placeId}/recommendation`,
        { recommended: !place.recommended });
      await load();
    } catch (error) {
      notice(error.message || "추천 상태를 바꾸지 못했습니다.");
    } finally { button.disabled = false; }
  }

  /**
   * 주소로 좌표를 찾아 위도·경도 칸을 채운다.
   *
   * <p>좌표를 손으로 알아내 옮겨 적게 두면 등록이 사실상 막힌다. 그렇다고 좌표를 필수로
   * 만들 수는 없다 — DB도 nullable이고, 좌표를 모르는 장소도 등록할 수 있어야 한다.
   * 그래서 필수로 만드는 대신 채우는 것을 쉽게 한다.
   *
   * <p>지도를 그리지 않으므로 services 라이브러리만 쓴다. 키가 없으면 SDK 자체가
   * 로드되지 않으므로 버튼을 숨긴다. 그때도 직접 입력하면 등록은 그대로 된다.
   */
  function geocodeAddress() {
    const address = value("placeAddress", "");
    if (!address) {
      setGeocodeStatus("주소를 먼저 입력해 주세요.", true);
      $("placeAddress").focus();
      return;
    }

    const button = $("placeGeocodeButton");
    button.disabled = true;
    setGeocodeStatus("좌표를 찾는 중이에요.");

    window.kakao.maps.load(() => {
      new window.kakao.maps.services.Geocoder().addressSearch(address, (result, status) => {
        button.disabled = false;

        if (status !== window.kakao.maps.services.Status.OK || !result.length) {
          /* 실패해도 등록을 막지 않는다. 좌표는 선택 항목이다. */
          setGeocodeStatus(
            "이 주소로는 좌표를 찾지 못했어요. 주소를 더 정확히 적거나 좌표를 직접 입력해 주세요.", true);
          return;
        }

        /* 카카오는 x가 경도, y가 위도다. 뒤집으면 엉뚱한 곳에 찍힌다. */
        $("placeLatitude").value = Number(result[0].y).toFixed(7);
        $("placeLongitude").value = Number(result[0].x).toFixed(7);
        setGeocodeStatus(`좌표를 채웠어요. ${result[0].address_name}`);
      });
    });
  }

  /* 첫 안내 문구는 마크업이 갖고 있다. 조회 결과를 덮어쓴 뒤 되돌릴 수 있게 붙잡아 둔다. */
  let geocodeHintDefault = "";

  function setGeocodeStatus(message, error) {
    const el = $("placeGeocodeStatus");
    if (!geocodeHintDefault) geocodeHintDefault = el.innerHTML;
    if (message === null) el.innerHTML = geocodeHintDefault;
    else el.textContent = message;
    el.classList.toggle("error", Boolean(error));
  }

  function bind() {
    const geocodeButton = $("placeGeocodeButton");
    /* SDK가 없으면(키 미설정) 눌러도 아무 일이 없는 버튼이 된다. 그럴 바엔 숨긴다. */
    if (window.kakao && window.kakao.maps) geocodeButton.addEventListener("click", geocodeAddress);
    else geocodeButton.hidden = true;

    $("placeSearchForm").addEventListener("submit", (event) => { event.preventDefault(); load(); });
    $("newPlaceButton").addEventListener("click", () => { resetEditor(); $("placeName").focus(); });
    $("placeResetButton").addEventListener("click", resetEditor);
    $("placeEditorForm").addEventListener("submit", save);
    $("placeList").addEventListener("click", (event) => {
      const editButton = event.target.closest("[data-place-edit]");
      if (editButton) return edit(editButton.dataset.placeEdit);
      const recommendedButton = event.target.closest("[data-place-recommended]");
      if (recommendedButton) toggleRecommended(recommendedButton.dataset.placeRecommended, recommendedButton);
    });

    // 체크박스는 click이 아니라 change로 받는다. 키보드로 켜고 끌 때도 동작해야 한다.
    $("placeList").addEventListener("change", (event) => {
      const box = event.target.closest("[data-place-select]");
      if (!box) return;
      const placeId = String(box.dataset.placeSelect);
      if (box.checked) state.selectedIds.add(placeId);
      else state.selectedIds.delete(placeId);
      renderBulkBar();
    });

    $("placeSelectAll").addEventListener("change", (event) => {
      state.items.forEach((item) => {
        if (event.target.checked) state.selectedIds.add(String(item.placeId));
        else state.selectedIds.delete(String(item.placeId));
      });
      render();
    });

    $("placeImageBackfill").addEventListener("click", (event) => backfillImages(event.target));
    $("placeBulkRecommend").addEventListener("click", (event) => bulkRecommend(true, event.target));
    $("placeBulkUnrecommend").addEventListener("click", (event) => bulkRecommend(false, event.target));
  }

  document.addEventListener("DOMContentLoaded", () => { bind(); resetEditor(); load(); });
  window.__adminPlaces = { state, load, edit, resetEditor, payload, geocodeAddress, backfillImages };
})();
