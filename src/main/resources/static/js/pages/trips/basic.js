/* 여행 기본 정보 목적지 검색 */
document.addEventListener("DOMContentLoaded", function () {
  const openButton = document.querySelector("[data-destination-open]");
  const destinationLabel = document.querySelector("[data-destination-label]");
  const destinationValue = document.querySelector("[data-destination-value]");
  const modalRoot = document.querySelector("#modal-root");
  const startDateInput = document.querySelector("[data-trip-start-date]");
  const endDateInput = document.querySelector("[data-trip-end-date]");
  const budgetInput = document.querySelector("[data-trip-budget]");
  const nextButton = document.querySelector("[data-basic-next]");
  const draftKey = "all-my-trips-trip-draft";

  function toDateValue(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return year + "-" + month + "-" + day;
  }

  function readDraft() {
    try {
      return JSON.parse(sessionStorage.getItem(draftKey) || "{}") || {};
    } catch (error) {
      sessionStorage.removeItem(draftKey);
      return {};
    }
  }

  function closeDestinationModal() {
    modalRoot.replaceChildren();
  }

  function uniqueDestinations(places) {
    const destinations = new Map();
    places.forEach(function (place) {
      const label = [place.region, place.city].filter(Boolean).join(" · ");
      if (!label || destinations.has(label)) return;
      destinations.set(label, {
        label: label,
        value: place.region || place.city,
        countryCode: place.countryCode,
      });
    });
    return Array.from(destinations.values());
  }

  function renderDestinationList(list, destinations, message) {
    list.replaceChildren();
    if (destinations.length === 0) {
      const empty = document.createElement("p");
      empty.className = "destination-empty";
      empty.textContent = message || "검색 결과가 없습니다.";
      list.appendChild(empty);
      return;
    }

    destinations.forEach(function (destination) {
      const button = document.createElement("button");
      const text = document.createElement("span");
      const country = document.createElement("small");
      button.type = "button";
      button.className = "destination-option";
      text.textContent = destination.label;
      country.textContent = destination.countryCode === "KR" ? "대한민국" : destination.countryCode;
      button.append(text, country);
      button.addEventListener("click", function () {
        destinationLabel.textContent = destination.label;
        destinationValue.value = destination.value;
        openButton.classList.add("has-value");
        sessionStorage.setItem("all-my-trips-destination", JSON.stringify(destination));
        closeDestinationModal();
        window.AllMyTripsModal.showToast(destination.label + "을 여행지로 선택했습니다.");
      });
      list.appendChild(button);
    });
  }

  async function searchDestinations(keyword, list) {
    renderDestinationList(list, [], "목적지를 찾는 중입니다.");
    const params = new URLSearchParams({ page: "0", size: "100" });
    if (keyword) params.set("keyword", keyword);

    try {
      const response = await fetch("/api/places?" + params.toString(), {
        headers: { Accept: "application/json" },
        allMyTripsLoading: false,
      });
      if (!response.ok) throw new Error("목적지 검색 요청에 실패했습니다.");
      const places = await response.json();
      renderDestinationList(
        list,
        uniqueDestinations(places),
        "일치하는 도시나 지역이 없습니다."
      );
    } catch (error) {
      renderDestinationList(list, [], "목적지를 불러오지 못했습니다. 다시 시도해주세요.");
    }
  }

  function openDestinationModal() {
    modalRoot.innerHTML = `
      <div class="modal-backdrop destination-backdrop">
        <section class="modal-card destination-modal" role="dialog" aria-modal="true"
                 aria-labelledby="destination-modal-title">
          <button class="modal-close" type="button" data-destination-close aria-label="닫기">×</button>
          <span class="destination-kicker">DESTINATION</span>
          <h2 id="destination-modal-title">어디로 떠나볼까요?</h2>
          <p>도시나 지역을 검색하고 여행 목적지를 선택하세요.</p>
          <form class="destination-search" data-destination-search data-no-global-loading>
            <input type="search" aria-label="목적지 검색"
                   placeholder="예: 서울, 부산, 제주" autocomplete="off" />
            <button type="submit">검색</button>
          </form>
          <div class="destination-list" data-destination-list aria-live="polite"></div>
        </section>
      </div>`;

    const searchForm = modalRoot.querySelector("[data-destination-search]");
    const searchInput = searchForm.querySelector("input");
    const list = modalRoot.querySelector("[data-destination-list]");
    modalRoot.querySelector("[data-destination-close]").addEventListener("click", closeDestinationModal);
    searchForm.addEventListener("submit", function (event) {
      event.preventDefault();
      searchDestinations(searchInput.value.trim(), list);
    });
    searchDestinations("", list);
    searchInput.focus();
  }

  openButton.addEventListener("click", openDestinationModal);

  const draft = readDraft();
  try {
    const saved = JSON.parse(sessionStorage.getItem("all-my-trips-destination") || "null");
    const destination = saved || (draft.destinationName ? {
      label: draft.destinationLabel || draft.destinationName,
      value: draft.destinationName,
    } : null);
    if (destination && destination.label && destination.value) {
      destinationLabel.textContent = destination.label;
      destinationValue.value = destination.value;
      openButton.classList.add("has-value");
    }
  } catch (error) {
    sessionStorage.removeItem("all-my-trips-destination");
  }

  const defaultStart = new Date();
  defaultStart.setDate(defaultStart.getDate() + 7);
  const defaultEnd = new Date(defaultStart);
  defaultEnd.setDate(defaultEnd.getDate() + (draft.themeNights || 3));
  startDateInput.value = draft.startDate || toDateValue(defaultStart);
  endDateInput.value = draft.endDate || toDateValue(defaultEnd);
  budgetInput.value = draft.budgetAmount || 300000;

  if (draft.companionType) {
    document.querySelectorAll("[data-companion-group] button").forEach(function (button) {
      button.classList.toggle("selected", button.dataset.value === draft.companionType &&
        (!draft.companionLabel || button.textContent.trim() === draft.companionLabel));
    });
  }

  nextButton.addEventListener("click", function () {
    const destinationName = destinationValue.value.trim();
    const startDate = startDateInput.value;
    const endDate = endDateInput.value;
    const companion = document.querySelector("[data-companion-group] .selected");

    if (!destinationName) {
      window.AllMyTripsModal.showToast("여행 목적지를 선택해주세요.");
      return;
    }
    if (!startDate || !endDate) {
      window.AllMyTripsModal.showToast("여행 기간을 선택해주세요.");
      return;
    }
    if (endDate < startDate) {
      window.AllMyTripsModal.showToast("도착일은 출발일보다 빠를 수 없습니다.");
      return;
    }

    const nextDraft = Object.assign({}, draft, {
      destinationName: destinationName,
      destinationLabel: destinationLabel.textContent.trim(),
      startDate: startDate,
      endDate: endDate,
      companionType: companion ? companion.dataset.value : "SOLO",
      companionLabel: companion ? companion.textContent.trim() : "혼자",
      companionCount: 1,
      budgetAmount: Number(budgetInput.value) || 0,
      currencyCode: "KRW",
    });
    sessionStorage.setItem(draftKey, JSON.stringify(nextDraft));
    window.location.href = "/trips/new/style";
  });

  document.body.dataset.pageReady = "true";
});
