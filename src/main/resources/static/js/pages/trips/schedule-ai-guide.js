document.addEventListener("DOMContentLoaded", function () {
  const modal = document.querySelector("[data-schedule-ai-modal]");
  const dialog = modal?.querySelector(".schedule-ai-dialog");
  const openButtons = Array.from(document.querySelectorAll("[data-schedule-ai-open]"));
  const form = document.querySelector("[data-schedule-ai-form]");
  const input = document.querySelector("#schedule-ai-question");
  const messages = document.querySelector("[data-schedule-ai-messages]");
  const errorBox = document.querySelector("[data-schedule-ai-error]");
  const errorMessage = document.querySelector("[data-schedule-ai-error-message]");
  const retryButton = document.querySelector("[data-schedule-ai-retry]");
  const resetButton = document.querySelector("[data-schedule-ai-reset]");
  if (!modal || !dialog || !openButtons.length || !form || !input || !messages) return;

  let csrfToken;
  let lastQuestion = "";
  let submitting = false;
  let lastOpenButton = openButtons[0];
  const dayControls = [];
  const nudge = document.querySelector("[data-schedule-ai-nudge]");
  const nudgeStorageKey = "allMyTrips.scheduleAiNudgeSeen";

  function create(tag, text, className) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text) element.textContent = text;
    return element;
  }

  function currentTripId() {
    const tripId = Number(document.body.dataset.tripId);
    return Number.isInteger(tripId) && tripId > 0 ? tripId : null;
  }

  function appendMessage(className, build) {
    const message = create("div", "", "schedule-ai-message " + className);
    build(message);
    messages.appendChild(message);
    message.scrollIntoView({ behavior: "smooth", block: "nearest" });
    return message;
  }

  function appendUserMessage(question) {
    appendMessage("schedule-ai-user", function (message) { message.textContent = question; });
  }

  function appendLoading() {
    return appendMessage("schedule-ai-assistant schedule-ai-loading", function (message) {
      message.append(create("p", "여행 일정과 장소를 살펴보고 있어요..."));
    });
  }

  function safeExternalUrl(value) {
    try {
      const url = new URL(value);
      return ["https:", "http:"].includes(url.protocol) ? url.href : null;
    } catch (error) {
      return null;
    }
  }

  function categoryLabel(value) {
    const labels = {
      ATTRACTION: "관광·명소", RESTAURANT: "맛집", CAFE: "카페", ACCOMMODATION: "숙소",
      FESTIVAL: "축제", ACTIVITY: "체험", TRANSPORT: "교통"
    };
    return labels[value] || "실제 장소";
  }

  function isVerifiedPlace(item) {
    return Number.isInteger(Number(item?.placeId)) && Number(item.placeId) > 0;
  }

  function setAddButtonState(button, added) {
    button.disabled = added;
    button.textContent = added ? "추가됨" : "일정에 추가";
  }

  function setDayStatus(control, text, isError) {
    if (!control.status) return;
    control.status.textContent = text || "";
    control.status.classList.toggle("is-error", Boolean(isError));
  }

  function resultMessage(result) {
    const parts = [];
    if (result.added) parts.push(result.added + "개 추가됨");
    if (result.alreadyAdded) parts.push(result.alreadyAdded + "개 이미 추가됨");
    if (result.failed?.length) parts.push(result.failed.length + "개 추가 실패");
    return parts.join(" · ");
  }

  async function refreshDayControl(control) {
    if (!window.AllMyTripsSchedule?.getAiRecommendationStates) return;
    try {
      const addedPlaceIds = await window.AllMyTripsSchedule.getAiRecommendationStates(
        control.items, control.dayNumber
      );
      control.itemButtons.forEach(function (entry) {
        setAddButtonState(entry.button, addedPlaceIds.has(Number(entry.item.placeId)));
      });
      const hasUnaddedItem = control.items.some(function (item) {
        return !addedPlaceIds.has(Number(item.placeId));
      });
      control.bulkButton.disabled = !hasUnaddedItem;
      control.bulkButton.textContent = hasUnaddedItem
        ? "DAY " + control.dayNumber + " 일정 모두 추가"
        : "모두 추가됨";
    } catch (error) {
      setDayStatus(control, "현재 저장 상태를 확인하지 못했습니다.", true);
    }
  }

  function refreshAllDayControls() {
    dayControls.forEach(function (control) { refreshDayControl(control); });
  }

  function renderResponse(payload) {
    const response = payload.data;
    appendMessage("schedule-ai-assistant", function (message) {
      message.append(create("p", response.answer));
      (response.days || []).forEach(function (day) {
        const dayCard = create("section", "", "schedule-ai-day");
        const heading = create("div", "", "schedule-ai-day-heading");
        heading.append(create("h3", day.title));
        const list = create("ul");
        const verifiedItems = (day.items || []).filter(isVerifiedPlace);
        const control = verifiedItems.length ? {
          dayNumber: day.day,
          items: verifiedItems,
          itemButtons: [],
          bulkButton: create("button", "DAY " + day.day + " 일정 모두 추가", "schedule-ai-add-all"),
          status: create("small", "", "schedule-ai-bulk-status")
        } : null;
        if (control) {
          control.bulkButton.type = "button";
          control.bulkButton.addEventListener("click", async function () {
            if (!window.AllMyTripsSchedule?.addAiRecommendations) {
              showError("일정 화면을 준비하지 못했습니다. 새로고침 후 다시 시도해주세요.");
              return;
            }
            control.bulkButton.disabled = true;
            control.bulkButton.textContent = "추가 중";
            try {
              const result = await window.AllMyTripsSchedule.addAiRecommendations(control.items, control.dayNumber);
              setDayStatus(control, resultMessage(result), Boolean(result.failed?.length));
              await refreshDayControl(control);
            } catch (error) {
              setDayStatus(control, error.message || "일정 추가에 실패했습니다.", true);
              await refreshDayControl(control);
            }
          });
          heading.append(control.bulkButton);
        }
        dayCard.append(heading);
        (day.items || []).forEach(function (item) {
          const row = create("li");
          row.append(create("time", item.time));
          const copy = create("div");
          copy.append(create("strong", item.name));
          copy.append(create("span", item.reason));
          if (isVerifiedPlace(item)) {
            const meta = create("div", "", "schedule-ai-place-meta");
            meta.append(create("em", categoryLabel(item.placeCategory)));
            if (item.placeAddress) meta.append(create("small", item.placeAddress));
            copy.append(meta);
          }
          row.append(copy);
          if (isVerifiedPlace(item)) {
            const actions = create("div", "", "schedule-ai-place-actions");
            const placeUrl = safeExternalUrl(item.placeUrl);
            if (placeUrl) {
              const mapLink = create("a", "지도 보기 ↗", "schedule-ai-map-link");
              mapLink.href = placeUrl;
              mapLink.target = "_blank";
              mapLink.rel = "noopener noreferrer";
              actions.append(mapLink);
            }
            const addButton = create("button", "일정에 추가", "schedule-ai-add-item");
            addButton.type = "button";
            addButton.addEventListener("click", async function () {
              if (!window.AllMyTripsSchedule?.addAiRecommendations) {
                showError("일정 화면을 준비하지 못했습니다. 새로고침 후 다시 시도해주세요.");
                return;
              }
              addButton.disabled = true;
              addButton.textContent = "추가 중";
              try {
                const result = await window.AllMyTripsSchedule.addAiRecommendations([item], day.day);
                if (control) setDayStatus(control, resultMessage(result), Boolean(result.failed?.length));
                await refreshDayControl(control);
              } catch (error) {
                addButton.disabled = false;
                addButton.textContent = "일정에 추가";
                showError(error.message || "일정을 추가하지 못했습니다.");
              }
            });
            if (control) control.itemButtons.push({ item, button: addButton });
            actions.append(addButton);
            row.append(actions);
          }
          list.append(row);
        });
        dayCard.append(list);
        if (control) {
          dayCard.append(control.status);
          dayControls.push(control);
          refreshDayControl(control);
        }
        message.append(dayCard);
      });
      const links = create("div", "", "schedule-ai-links");
      (response.externalLinks || []).forEach(function (link) {
        const href = safeExternalUrl(link.url);
        if (!href) return;
        const anchor = create("a", link.label + " ↗");
        anchor.href = href;
        anchor.target = "_blank";
        anchor.rel = "noopener noreferrer";
        links.append(anchor);
      });
      if (links.childElementCount) message.append(links);
    });
  }

  async function requestGuide(question) {
    const tripId = currentTripId();
    if (!tripId) throw new Error("현재 여행을 불러온 뒤 AI 가이드를 이용해주세요.");
    if (!csrfToken) {
      const csrfResponse = await fetch("/api/v1/csrf", {
        headers: { Accept: "application/json" },
        credentials: "same-origin",
        allMyTripsLoading: false
      });
      const csrfPayload = await csrfResponse.json().catch(function () { return null; });
      if (!csrfResponse.ok || !csrfPayload?.headerName || !csrfPayload?.token) {
        throw new Error("보안 토큰을 준비하지 못했습니다.");
      }
      csrfToken = csrfPayload;
    }
    const headers = { "Content-Type": "application/json", Accept: "application/json" };
    headers[csrfToken.headerName] = csrfToken.token;
    const response = await fetch("/api/v1/ai-guides/generate", {
      method: "POST",
      credentials: "same-origin",
      allMyTripsLoading: false,
      headers,
      body: JSON.stringify({ tripId, question })
    });
    const payload = await response.json().catch(function () { return null; });
    if (!response.ok || !payload?.success) {
      throw new Error(payload?.message || "AI 추천을 생성하지 못했습니다.");
    }
    return payload;
  }

  async function resetConversation() {
    const tripId = currentTripId();
    if (!tripId) throw new Error("현재 여행을 불러온 뒤 새 대화를 시작해 주세요.");

    if (!csrfToken) {
      const csrfResponse = await fetch("/api/v1/csrf", {
        headers: { Accept: "application/json" },
        credentials: "same-origin",
        allMyTripsLoading: false
      });
      const csrfPayload = await csrfResponse.json().catch(function () { return null; });
      if (!csrfResponse.ok || !csrfPayload?.headerName || !csrfPayload?.token) {
        throw new Error("보안 토큰을 준비하지 못했습니다.");
      }
      csrfToken = csrfPayload;
    }

    const headers = { Accept: "application/json" };
    headers[csrfToken.headerName] = csrfToken.token;
    const response = await fetch("/api/v1/ai-guides/conversation?tripId=" + encodeURIComponent(tripId), {
      method: "DELETE",
      credentials: "same-origin",
      allMyTripsLoading: false,
      headers
    });
    const payload = await response.json().catch(function () { return null; });
    if (!response.ok || !payload?.success) {
      throw new Error(payload?.message || "대화 초기화에 실패했습니다.");
    }
  }

  function showError(message) {
    errorMessage.textContent = message;
    errorBox.hidden = false;
  }

  async function submit(question) {
    if (!question || submitting) return;
    lastQuestion = question;
    submitting = true;
    errorBox.hidden = true;
    input.disabled = true;
    appendUserMessage(question);
    const loading = appendLoading();
    try {
      renderResponse(await requestGuide(question));
    } catch (error) {
      showError(error.message || "잠시 후 다시 시도해주세요.");
    } finally {
      loading.remove();
      submitting = false;
      input.disabled = false;
      input.focus();
    }
  }

  function openModal() {
    try {
      localStorage.setItem(nudgeStorageKey, "true");
    } catch (error) {
      // Ignore storage access errors and keep the guide usable.
    }
    if (nudge) nudge.hidden = true;
    modal.hidden = false;
    modal.setAttribute("aria-hidden", "false");
    window.setTimeout(function () { input.focus(); }, 0);
  }

  function closeModal() {
    modal.hidden = true;
    modal.setAttribute("aria-hidden", "true");
    lastOpenButton.focus();
  }

  openButtons.forEach(function (button) {
    button.addEventListener("click", function () {
      lastOpenButton = button;
      openModal();
    });
  });
  modal.querySelectorAll("[data-schedule-ai-close]").forEach(function (button) {
    button.addEventListener("click", closeModal);
  });
  document.addEventListener("click", function (event) {
    const clickedOpenButton = openButtons.some(function (button) {
      return button.contains(event.target);
    });
    if (!modal.hidden && !dialog.contains(event.target) && !clickedOpenButton) {
      closeModal();
    }
  });
  form.addEventListener("submit", function (event) {
    event.preventDefault();
    const question = input.value.trim();
    if (!question) return;
    input.value = "";
    submit(question);
  });
  document.querySelectorAll("[data-schedule-ai-question]").forEach(function (button) {
    button.addEventListener("click", function () {
      input.value = button.dataset.scheduleAiQuestion;
      input.focus();
    });
  });
  retryButton.addEventListener("click", function () { submit(lastQuestion); });
  resetButton?.addEventListener("click", async function () {
    if (submitting) return;
    resetButton.disabled = true;
    try {
      await resetConversation();
      messages.replaceChildren();
      dayControls.length = 0;
      errorBox.hidden = true;
      lastQuestion = "";
      appendMessage("schedule-ai-assistant", function (message) {
        message.append(create("p", "새 대화를 시작했어요. 현재 여행 일정에 대해 편하게 물어보세요."));
      });
    } catch (error) {
      showError(error.message || "대화 초기화에 실패했습니다.");
    } finally {
      resetButton.disabled = false;
      input.focus();
    }
  });
  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && !modal.hidden) closeModal();
  });
  window.addEventListener("allmytrips:schedule-changed", refreshAllDayControls);

  if (nudge) {
    try {
      if (localStorage.getItem(nudgeStorageKey) !== "true") {
        window.setTimeout(function () { nudge.hidden = false; }, 600);
      }
    } catch (error) {
      window.setTimeout(function () { nudge.hidden = false; }, 600);
    }
  }
});
