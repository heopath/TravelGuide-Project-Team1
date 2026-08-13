/* 관리자 예약 상품·재고 관리
 *
 * 재고는 시간대(slot)마다 따로 있고 서버가 상품 단위로 합쳐서 내려준다.
 * 이 화면은 합계만 보여주고 수량을 직접 고치지 않는다. 예약이 걸린 시간대의
 * 수량을 목록에서 일괄로 건드리면 reserved > total 이 되는 순간이 생긴다.
 */
(function () {
  "use strict";

  const PAGE_SIZE = 20;
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
  if (!list || !empty) return;

  let status = "";
  let keyword = "";
  let currentPage = 0;

  async function request(url, options) {
    const response = await fetch(url, Object.assign({
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      allMyTripsLoading: false,
    }, options || {}));
    const payload = await response.json().catch(function () { return null; });
    if (!response.ok || payload?.success === false) {
      if (response.status === 401) window.location.href = "/auth/login?redirect=/admin";
      if (response.status === 403) throw new Error("관리자만 접근할 수 있습니다.");
      throw new Error(payload?.message || "요청을 처리하지 못했습니다.");
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
    select.addEventListener("change", function () {
      changeStatus(product, select);
    });
    return select;
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

    const stockCell = document.createElement("span");
    stockCell.dataset.productStock = "";
    stockCell.textContent = stockLabel(product);

    const statusCell = document.createElement("span");
    statusCell.appendChild(statusSelect(product));

    const noteCell = document.createElement("span");
    noteCell.dataset.productNote = "";
    /* 옵션이 없으면 판매 중으로 올려도 예약 화면에 안 뜬다. 그 이유를 여기서 밝힌다. */
    if (!product.optionCount) noteCell.textContent = "옵션 없음";

    item.append(nameCell, stockCell, statusCell, noteCell);
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

  /* 관리자 화면은 패널이 전환될 때마다 다시 부르지 않는다. 처음 한 번만 채운다. */
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () { load(0); });
  } else {
    load(0);
  }

  window.__adminProducts = { load: load, current: function () { return currentPage; } };
})();
