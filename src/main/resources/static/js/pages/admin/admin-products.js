/* 관리자 예약 상품·재고 관리
 *
 * 재고는 시간대(slot)마다 따로 있고 서버가 상품 단위로 합쳐서 내려준다.
 * 목록은 합계만 보여주고, 수량 조정은 시간대를 펼쳐서 한 칸씩 한다. 목록에서 상품 단위로
 * 일괄 조정하면 예약이 걸린 시간대에서 reserved > total 이 되는 순간이 생긴다.
 */
(function () {
  "use strict";

  const PAGE_SIZE = 20;
  /* 시간대 등록 폼의 기본 수량. 마크업에 두면 실제 재고로 오해할 수 있어 여기 둔다. */
  const DEFAULT_SLOT_QUANTITY = 100;
  const statusLabels = {
    DRAFT: "작성 중",
    ON_SALE: "판매 중",
    SOLD_OUT: "품절",
    ENDED: "종료",
    CANCELLED: "취소",
  };

  const list = document.getElementById("productList");
  const empty = document.getElementById("productEmpty");
  const filter = document.querySelector("[data-product-filter]");
  const search = document.querySelector("[data-product-search]");
  const pagination = document.querySelector("[data-product-pagination]");
  const form = document.querySelector("[data-product-form]");
  const formTitle = document.querySelector("[data-form-title]");
  const formMessage = document.querySelector("[data-form-message]");
  const newButton = document.querySelector("[data-product-new]");
  const cancelButton = document.querySelector("[data-product-cancel]");
  const slotPanel = document.querySelector("[data-slot-panel]");
  const slotTitle = document.querySelector("[data-slot-title]");
  const slotList = document.querySelector("[data-slot-list]");
  const slotEmpty = document.querySelector("[data-slot-empty]");
  const slotClose = document.querySelector("[data-slot-close]");
  const optionList = document.querySelector("[data-option-list]");
  const optionEmpty = document.querySelector("[data-option-empty]");
  const optionForm = document.querySelector("[data-option-form]");
  const optionMessage = document.querySelector("[data-option-message]");
  const optionSubmit = document.querySelector("[data-option-submit]");
  const optionReset = document.querySelector("[data-option-reset]");
  const slotForm = document.querySelector("[data-slot-form]");
  const slotFormMessage = document.querySelector("[data-slot-form-message]");
  const slotSubmit = document.querySelector("[data-slot-submit]");
  const slotWeekdays = document.querySelector("[data-slot-weekdays]");
  if (!list || !empty) return;

  let status = "";
  let keyword = "";
  let currentPage = 0;
  let editingId = null;
  let placesLoaded = false;
  /* 시간대 패널이 열려 있는 상품. 옵션·시간대 등록이 모두 이 상품 아래로 들어간다. */
  let currentProduct = null;
  let loadedOptions = [];
  let editingOptionId = null;

  const field = (name) => form?.querySelector(`[data-field="${name}"]`);

  async function request(url, options) {
    const response = await fetch(url, Object.assign({
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      allMyTripsLoading: false,
    }, options || {}));
    const payload = await response.json().catch(function () { return null; });
    if (!response.ok || payload?.success === false) {
      if (response.status === 401) window.location.href = "/auth/login?redirect=/admin";
      if (response.status === 403) throw new Error("관리자만 접근할 수 있습니다.");
      const error = new Error(payload?.message || "요청을 처리하지 못했습니다.");
      error.code = payload?.code;
      throw error;
    }
    return payload?.data ?? payload;
  }

  function number(value) {
    return typeof value === "number" ? value.toLocaleString("ko-KR") : "—";
  }

  function placeLabel(product) {
    return [product.placeName, product.city || product.region].filter(Boolean).join(" · ");
  }

  /* 재고가 0인 것과 시간대를 아직 안 만든 것은 다른 상태다. 합계만 쓰면 구분이 안 된다. */
  function stockLabel(product) {
    if (!product.slotCount) return "시간대 없음";
    return `${number(product.remainingQuantity)} / ${number(product.totalQuantity)}`;
  }

  function statusSelect(product) {
    const select = document.createElement("select");
    select.setAttribute("aria-label", `${product.name} 판매 상태`);
    select.dataset.productStatus = String(product.ticketProductId);
    Object.keys(statusLabels).forEach(function (value) {
      const option = document.createElement("option");
      option.value = value;
      option.textContent = statusLabels[value];
      if (value === product.status) option.selected = true;
      select.appendChild(option);
    });
    select.addEventListener("change", function () { changeStatus(product, select); });
    return select;
  }

  function actionButton(label, dataset, handler) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "admin-chip";
    button.textContent = label;
    Object.assign(button.dataset, dataset);
    button.addEventListener("click", handler);
    return button;
  }

  function row(product) {
    const item = document.createElement("div");
    item.className = "admin-product-row";
    item.dataset.productRow = String(product.ticketProductId);

    const nameCell = document.createElement("span");
    const name = document.createElement("strong");
    name.textContent = product.name;
    const place = document.createElement("small");
    place.textContent = placeLabel(product);
    nameCell.append(name, place);
    /* 옵션이 없으면 판매 중으로 올려도 예약 화면에 안 뜬다. 그 이유를 여기서 밝힌다. */
    if (!product.optionCount) {
      const note = document.createElement("small");
      note.dataset.productNote = "";
      note.textContent = "옵션 없음";
      nameCell.appendChild(note);
    }

    const stockCell = document.createElement("span");
    stockCell.dataset.productStock = "";
    stockCell.textContent = stockLabel(product);

    const statusCell = document.createElement("span");
    statusCell.appendChild(statusSelect(product));

    const actionCell = document.createElement("span");
    actionCell.className = "admin-row-actions";
    actionCell.append(
      actionButton("수정", { productEdit: String(product.ticketProductId) },
        function () { openForm(product); }),
      actionButton("시간대", { productSlots: String(product.ticketProductId) },
        function () { openSlots(product); })
    );

    item.append(nameCell, stockCell, statusCell, actionCell);
    return item;
  }

  function renderPages(page, totalPages) {
    if (!pagination) return;
    pagination.replaceChildren();
    for (let index = 0; index < totalPages; index += 1) {
      if (totalPages > 7 && Math.abs(index - page) > 3 && index !== 0 && index !== totalPages - 1) continue;
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = String(index + 1);
      button.className = index === page ? "is-current" : "";
      button.addEventListener("click", function () { load(index); });
      pagination.appendChild(button);
    }
  }

  async function changeStatus(product, select) {
    const next = select.value;
    const previous = product.status;
    select.disabled = true;
    try {
      const updated = await request(
        `/api/v1/admin/ticket-products/${product.ticketProductId}/status`,
        { method: "PATCH", body: JSON.stringify({ status: next }) }
      );
      product.status = updated.status;
      const container = list.querySelector(`[data-product-row="${product.ticketProductId}"]`);
      const stock = container?.querySelector("[data-product-stock]");
      if (stock) stock.textContent = stockLabel(updated);
      empty.hidden = true;
    } catch (error) {
      /* 서버가 거부하면 화면만 바뀐 채로 두지 않는다. 되돌려야 지금 상태가 진짜가 된다. */
      select.value = previous;
      empty.hidden = false;
      empty.textContent = error.message || "판매 상태를 변경하지 못했습니다.";
    } finally {
      select.disabled = false;
    }
  }

  /* ── 등록·수정 폼 ── */

  /* 장소 목록은 폼을 처음 열 때 한 번만 받는다. 목록을 열 때마다 부르면 낭비다. */
  async function loadPlaces() {
    if (placesLoaded) return;
    const select = field("placeId");
    if (!select) return;
    const data = await request("/api/v1/admin/places?page=0&size=100");
    select.replaceChildren();
    (data?.items || []).forEach(function (place) {
      const option = document.createElement("option");
      option.value = String(place.placeId);
      option.textContent = [place.name, place.city || place.region].filter(Boolean).join(" · ");
      select.appendChild(option);
    });
    placesLoaded = true;
  }

  /* datetime-local은 초·타임존이 없다. 서버가 주는 값에서 로컬 시각 부분만 잘라 채운다. */
  function toLocalInput(value) {
    if (!value) return "";
    const date = new Date(value);
    const offset = date.getTimezoneOffset() * 60000;
    return new Date(date.getTime() - offset).toISOString().slice(0, 16);
  }

  /* 반대로 보낼 때는 오프셋을 붙여야 한다. 서버가 OffsetDateTime으로 받는다. */
  function toOffsetDateTime(value) {
    return value ? new Date(value).toISOString() : null;
  }

  async function openForm(product) {
    if (!form) return;
    editingId = product ? product.ticketProductId : null;
    formMessage.textContent = "";
    try {
      await loadPlaces();
    } catch (error) {
      formMessage.textContent = error.message || "장소 목록을 불러오지 못했어요.";
    }
    formTitle.textContent = product ? "상품 수정" : "새 상품 등록";
    field("placeId").value = product ? String(product.placeId ?? "") : "";
    field("name").value = product ? product.name ?? "" : "";
    field("description").value = "";
    field("saleStartAt").value = product ? toLocalInput(product.saleStartAt) : "";
    field("saleEndAt").value = product ? toLocalInput(product.saleEndAt) : "";
    field("usageStartDate").value = product ? product.usageStartDate ?? "" : "";
    field("usageEndDate").value = product ? product.usageEndDate ?? "" : "";
    form.hidden = false;
    if (slotPanel) slotPanel.hidden = true;
    field("name").focus();
  }

  function closeForm() {
    if (!form) return;
    form.hidden = true;
    editingId = null;
    formMessage.textContent = "";
  }

  async function submitForm(event) {
    event.preventDefault();
    const submit = form.querySelector("[data-product-submit]");
    const body = {
      placeId: Number(field("placeId").value) || null,
      name: field("name").value.trim(),
      description: field("description").value.trim() || null,
      saleStartAt: toOffsetDateTime(field("saleStartAt").value),
      saleEndAt: toOffsetDateTime(field("saleEndAt").value),
      usageStartDate: field("usageStartDate").value || null,
      usageEndDate: field("usageEndDate").value || null,
    };
    submit.disabled = true;
    formMessage.textContent = "";
    try {
      if (editingId) {
        await request(`/api/v1/admin/ticket-products/${editingId}`,
          { method: "PUT", body: JSON.stringify(body) });
      } else {
        await request("/api/v1/admin/ticket-products",
          { method: "POST", body: JSON.stringify(body) });
      }
      closeForm();
      await load(editingId ? currentPage : 0);
    } catch (error) {
      formMessage.textContent = error.message || "저장하지 못했어요.";
    } finally {
      submit.disabled = false;
    }
  }

  /* ── 시간대 재고 ── */

  function slotLabel(slot) {
    const time = slot.startTime ? ` ${String(slot.startTime).slice(0, 5)}` : "";
    return `${slot.usageDate}${time}`;
  }

  function slotRow(slot) {
    const item = document.createElement("div");
    item.className = "admin-slot-row";
    item.dataset.slotRow = String(slot.ticketTimeSlotId);

    const infoCell = document.createElement("span");
    const option = document.createElement("strong");
    option.textContent = slot.optionName;
    const when = document.createElement("small");
    when.textContent = slotLabel(slot);
    infoCell.append(option, when);
    /* 옵션이 꺼져 있거나 시간대가 닫혀 있으면 재고가 남아도 예약 화면에 안 뜬다. */
    if (slot.optionActive === false || slot.status !== "OPEN") {
      const closed = document.createElement("small");
      closed.dataset.slotClosed = "";
      closed.textContent = slot.optionActive === false ? "옵션 비활성" : "시간대 닫힘";
      infoCell.appendChild(closed);
    }

    const reservedCell = document.createElement("span");
    reservedCell.dataset.slotReserved = "";
    reservedCell.textContent = number(slot.reservedQuantity);

    const inputCell = document.createElement("span");
    const input = document.createElement("input");
    input.type = "number";
    input.min = String(slot.reservedQuantity ?? 0);
    input.value = String(slot.totalQuantity ?? 0);
    input.dataset.slotTotal = String(slot.ticketTimeSlotId);
    input.setAttribute("aria-label", `${slot.optionName} 전체 수량`);
    inputCell.appendChild(input);

    const actionCell = document.createElement("span");
    actionCell.appendChild(actionButton("저장", {}, function () {
      saveInventory(slot, input, item);
    }));
    /* 삭제는 없다. reservation_items가 시간대를 참조해 지우면 팔린 예약을 되짚을 수 없다. */
    actionCell.appendChild(actionButton(
      slot.status === "OPEN" ? "닫기" : "열기", {}, function () {
        toggleSlotStatus(slot, item);
      }));

    item.append(infoCell, reservedCell, inputCell, actionCell);
    return item;
  }

  /* dataset은 읽기 전용이라 Object.assign으로 통째로 넣을 수 없다. 속성으로 붙인다. */
  function slotMessageElement(container) {
    let message = container.querySelector("[data-slot-message]");
    if (!message) {
      message = document.createElement("small");
      message.dataset.slotMessage = "";
      container.querySelector("span").appendChild(message);
    }
    return message;
  }

  async function saveInventory(slot, input, container) {
    const total = Number(input.value);
    const message = slotMessageElement(container);
    input.disabled = true;
    try {
      const updated = await request(
        `/api/v1/admin/ticket-slots/${slot.ticketTimeSlotId}/inventory`,
        { method: "PATCH", body: JSON.stringify({ totalQuantity: total }) }
      );
      slot.totalQuantity = updated.totalQuantity;
      slot.reservedQuantity = updated.reservedQuantity;
      input.value = String(updated.totalQuantity);
      message.textContent = "저장했어요.";
      message.dataset.slotError = "";
    } catch (error) {
      /* 거부되면 입력값을 되돌린다. 화면에 남은 숫자가 저장된 값처럼 보이면 안 된다. */
      input.value = String(slot.totalQuantity ?? 0);
      message.textContent = error.message || "재고를 변경하지 못했어요.";
      message.dataset.slotError = "true";
    } finally {
      input.disabled = false;
    }
  }

  /* ── 옵션 ── */

  function optionRow(option) {
    const item = document.createElement("div");
    item.className = "admin-slot-row";

    const infoCell = document.createElement("span");
    const name = document.createElement("strong");
    name.textContent = option.name;
    const price = document.createElement("small");
    price.textContent = `${number(Number(option.unitPrice))}원 · 1인 ${option.maxQuantityPerUser}장`;
    infoCell.append(name, price);
    /* 옵션이 꺼져 있으면 그 아래 시간대가 통째로 예약 화면에서 사라진다. 이유를 밝혀둔다. */
    if (option.isActive === false) {
      const off = document.createElement("small");
      off.dataset.slotClosed = "";
      off.textContent = "비활성";
      infoCell.appendChild(off);
    }

    const slotCountCell = document.createElement("span");
    slotCountCell.textContent = option.slotCount ? `시간대 ${option.slotCount}개` : "시간대 없음";

    const orderCell = document.createElement("span");
    orderCell.textContent = `순서 ${option.sortOrder}`;

    const actionCell = document.createElement("span");
    actionCell.appendChild(actionButton("수정", {}, function () { fillOptionForm(option); }));
    actionCell.appendChild(actionButton(
      option.isActive === false ? "켜기" : "끄기", {}, function () {
        saveOption(Object.assign({}, optionPayload(option), { isActive: option.isActive === false }),
          option.ticketProductOptionId);
      }));

    item.append(infoCell, slotCountCell, orderCell, actionCell);
    return item;
  }

  function optionPayload(option) {
    return {
      name: option.name,
      description: option.description ?? null,
      unitPrice: Number(option.unitPrice),
      maxQuantityPerUser: option.maxQuantityPerUser,
      sortOrder: option.sortOrder,
      isActive: option.isActive !== false,
    };
  }

  function optionField(name) {
    return optionForm ? optionForm.querySelector(`[data-option-field="${name}"]`) : null;
  }

  function fillOptionForm(option) {
    editingOptionId = option ? option.ticketProductOptionId : null;
    optionField("name").value = option ? option.name : "";
    optionField("unitPrice").value = option ? String(Number(option.unitPrice)) : "";
    optionField("maxQuantityPerUser").value = option ? String(option.maxQuantityPerUser) : "4";
    optionField("sortOrder").value = option
      ? String(option.sortOrder)
      : String(loadedOptions.length + 1);
    if (optionSubmit) optionSubmit.textContent = option ? "옵션 저장" : "옵션 추가";
    if (optionReset) optionReset.hidden = !option;
    if (optionMessage) optionMessage.textContent = "";
  }

  async function saveOption(body, optionId) {
    if (!currentProduct) return;
    if (optionSubmit) optionSubmit.disabled = true;
    try {
      if (optionId) {
        await request(`/api/v1/admin/ticket-options/${optionId}`,
          { method: "PUT", body: JSON.stringify(body) });
      } else {
        await request(`/api/v1/admin/ticket-products/${currentProduct.ticketProductId}/options`,
          { method: "POST", body: JSON.stringify(body) });
      }
      fillOptionForm(null);
      await reloadSlotPanel();
    } catch (error) {
      if (optionMessage) optionMessage.textContent = error.message || "옵션을 저장하지 못했어요.";
    } finally {
      if (optionSubmit) optionSubmit.disabled = false;
    }
  }

  function submitOption(event) {
    event.preventDefault();
    const price = Number(optionField("unitPrice").value);
    saveOption({
      name: optionField("name").value.trim(),
      description: null,
      unitPrice: Number.isFinite(price) ? price : 0,
      maxQuantityPerUser: Number(optionField("maxQuantityPerUser").value),
      sortOrder: Number(optionField("sortOrder").value),
      /* 수정 중이면 현재 노출 상태를 유지한다. 폼에 항목이 없어 끄기/켜기는 목록 버튼이 맡는다. */
      isActive: editingOptionId
        ? (loadedOptions.find(function (o) { return o.ticketProductOptionId === editingOptionId; })
            ?.isActive !== false)
        : true,
    }, editingOptionId);
  }

  /* ── 시간대 등록 ── */

  const WEEKDAYS = [
    ["MONDAY", "월"], ["TUESDAY", "화"], ["WEDNESDAY", "수"], ["THURSDAY", "목"],
    ["FRIDAY", "금"], ["SATURDAY", "토"], ["SUNDAY", "일"],
  ];

  function buildWeekdays() {
    if (!slotWeekdays || slotWeekdays.querySelector("input")) return;
    WEEKDAYS.forEach(function (pair) {
      const label = document.createElement("label");
      const input = document.createElement("input");
      input.type = "checkbox";
      input.value = pair[0];
      const text = document.createElement("span");
      text.textContent = pair[1];
      label.append(input, text);
      slotWeekdays.appendChild(label);
    });
  }

  function slotField(name) {
    return slotForm ? slotForm.querySelector(`[data-slot-field="${name}"]`) : null;
  }

  function fillOptionSelect() {
    const select = slotField("ticketProductOptionId");
    if (!select) return;
    const previous = select.value;
    select.replaceChildren();
    loadedOptions.forEach(function (option) {
      const item = document.createElement("option");
      item.value = String(option.ticketProductOptionId);
      item.textContent = option.isActive === false
        ? `${option.name} (비활성)`
        : option.name;
      select.appendChild(item);
    });
    if (previous) select.value = previous;
  }

  async function submitSlots(event) {
    event.preventDefault();
    if (!currentProduct) return;
    const optionId = Number(slotField("ticketProductOptionId").value);
    if (!optionId) {
      slotFormMessage.textContent = "옵션을 먼저 등록해 주세요.";
      return;
    }
    const weekdays = slotWeekdays
      ? Array.from(slotWeekdays.querySelectorAll("input:checked")).map(function (i) { return i.value; })
      : [];
    const body = {
      ticketProductOptionId: optionId,
      usageStartDate: slotField("usageStartDate").value,
      usageEndDate: slotField("usageEndDate").value || null,
      weekdays: weekdays,
      startTime: slotField("startTime").value || null,
      endTime: slotField("endTime").value || null,
      totalQuantity: Number(slotField("totalQuantity").value),
    };
    if (slotSubmit) slotSubmit.disabled = true;
    try {
      const result = await request(
        `/api/v1/admin/ticket-products/${currentProduct.ticketProductId}/slots`,
        { method: "POST", body: JSON.stringify(body) });
      /* 건너뛴 날을 반드시 알린다. 요청한 만큼 다 열렸다고 믿으면 빠진 날을 못 찾는다. */
      slotFormMessage.textContent = result.skipped
        ? `${result.created}개를 등록했어요. ${result.skipped}개는 이미 있어 건너뛰었어요.`
        : `${result.created}개를 등록했어요.`;
      renderSlots(result.slots);
      await refreshOptions();
    } catch (error) {
      slotFormMessage.textContent = error.message || "시간대를 등록하지 못했어요.";
    } finally {
      if (slotSubmit) slotSubmit.disabled = false;
    }
  }

  async function toggleSlotStatus(slot, container) {
    const next = slot.status === "OPEN" ? "CLOSED" : "OPEN";
    const message = slotMessageElement(container);
    try {
      const updated = await request(
        `/api/v1/admin/ticket-slots/${slot.ticketTimeSlotId}/status`,
        { method: "PATCH", body: JSON.stringify({ status: next }) });
      slot.status = updated.status;
      await reloadSlotPanel();
    } catch (error) {
      message.textContent = error.message || "상태를 바꾸지 못했어요.";
      message.dataset.slotError = "true";
    }
  }

  /* ── 패널 ── */

  function renderSlots(slots) {
    slotList.replaceChildren();
    if (!slots || !slots.length) {
      slotEmpty.hidden = false;
      slotEmpty.textContent = loadedOptions.length
        ? "등록된 시간대가 없어요. 위에서 시간대를 추가하면 예약을 받을 수 있어요."
        : "옵션을 먼저 등록해 주세요. 시간대는 옵션에 달려요.";
      return;
    }
    slotEmpty.hidden = true;
    slots.forEach(function (slot) { slotList.appendChild(slotRow(slot)); });
  }

  async function refreshOptions() {
    if (!currentProduct) return;
    const received = await request(
      `/api/v1/admin/ticket-products/${currentProduct.ticketProductId}/options`);
    /* 배열이 아니면 빈 목록으로 본다. forEach를 바로 부르면 여기서 터져 시간대까지 못 그린다. */
    loadedOptions = Array.isArray(received) ? received : [];
    optionList.replaceChildren();
    if (!loadedOptions.length) {
      optionEmpty.hidden = false;
      optionEmpty.textContent = "등록된 옵션이 없어요. 옵션에 가격과 1인 구매 한도가 붙어요.";
    } else {
      optionEmpty.hidden = true;
      loadedOptions.forEach(function (option) { optionList.appendChild(optionRow(option)); });
    }
    fillOptionSelect();
  }

  /*
   * 옵션과 시간대는 따로 받는다. 옵션 조회가 실패해도 시간대는 보여야 한다 — 재고 조정이
   * 옵션과 무관한 동작인데, 옵션 때문에 막히면 고칠 수 있는 것까지 못 고치게 된다.
   */
  async function reloadSlotPanel() {
    try {
      await refreshOptions();
    } catch (error) {
      loadedOptions = [];
      optionEmpty.hidden = false;
      optionEmpty.textContent = error.message || "옵션을 불러오지 못했어요.";
    }
    const slots = await request(
      `/api/v1/admin/ticket-products/${currentProduct.ticketProductId}/slots`);
    renderSlots(Array.isArray(slots) ? slots : []);
  }

  async function openSlots(product) {
    if (!slotPanel) return;
    closeForm();
    currentProduct = product;
    editingOptionId = null;
    slotPanel.hidden = false;
    slotTitle.textContent = `${product.name} · 옵션과 시간대`;
    slotList.replaceChildren();
    slotEmpty.hidden = false;
    slotEmpty.textContent = "시간대를 불러오는 중이에요.";
    buildWeekdays();
    fillOptionForm(null);
    if (slotFormMessage) slotFormMessage.textContent = "";
    /* 상품의 이용 기간을 기본값으로 넣는다. 그 밖의 날짜를 여는 일은 드물다. */
    if (slotField("usageStartDate")) slotField("usageStartDate").value = product.usageStartDate || "";
    if (slotField("usageEndDate")) slotField("usageEndDate").value = product.usageEndDate || "";
    /* 수량 기본값은 마크업이 아니라 여기서 넣는다. 서버에서 온 값과 섞이면 안 된다. */
    if (slotField("totalQuantity")) slotField("totalQuantity").value = DEFAULT_SLOT_QUANTITY;
    try {
      await reloadSlotPanel();
    } catch (error) {
      slotEmpty.textContent = error.message || "시간대를 불러오지 못했어요.";
    }
  }

  /* ── 목록 ── */

  async function load(page) {
    currentPage = page || 0;
    list.replaceChildren();
    empty.hidden = false;
    empty.textContent = "상품 목록을 불러오는 중이에요.";
    const query = new URLSearchParams({ page: String(currentPage), size: String(PAGE_SIZE) });
    if (status) query.set("status", status);
    if (keyword) query.set("keyword", keyword);
    try {
      const data = await request(`/api/v1/admin/ticket-products?${query}`);
      const items = data?.items || [];
      if (!items.length) {
        empty.textContent = status || keyword
          ? "조건에 맞는 상품이 없어요."
          : "등록된 예약 상품이 없어요.";
        renderPages(0, 0);
        return;
      }
      empty.hidden = true;
      items.forEach(function (product) { list.appendChild(row(product)); });
      renderPages(data.page, data.totalPages);
    } catch (error) {
      empty.textContent = error.message || "상품 목록을 불러오지 못했어요.";
      renderPages(0, 0);
    }
  }

  if (filter) {
    filter.addEventListener("click", function (event) {
      const button = event.target.closest("[data-product-status]");
      if (!button) return;
      status = button.dataset.productStatus || "";
      filter.querySelectorAll("[data-product-status]").forEach(function (chip) {
        chip.classList.toggle("on", chip === button);
      });
      load(0);
    });
  }

  if (search) {
    search.addEventListener("keydown", function (event) {
      if (event.key !== "Enter") return;
      event.preventDefault();
      keyword = search.value.trim();
      load(0);
    });
  }

  if (newButton) newButton.addEventListener("click", function () { openForm(null); });
  if (cancelButton) cancelButton.addEventListener("click", closeForm);
  if (form) form.addEventListener("submit", submitForm);
  if (slotClose) slotClose.addEventListener("click", function () {
    slotPanel.hidden = true;
    currentProduct = null;
  });
  if (optionForm) optionForm.addEventListener("submit", submitOption);
  if (optionReset) optionReset.addEventListener("click", function () { fillOptionForm(null); });
  if (slotForm) slotForm.addEventListener("submit", submitSlots);

  /* 관리자 화면은 패널이 전환될 때마다 다시 부르지 않는다. 처음 한 번만 채운다. */
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () { load(0); });
  } else {
    load(0);
  }

  window.__adminProducts = { load: load, current: function () { return currentPage; } };
})();
