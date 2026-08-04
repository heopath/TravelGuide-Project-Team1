(function () {
  const DRAFT_KEY = "tripDraft";
  const META_KEY = "savedTripDraft";
  const PERSISTED_ID_KEY = "allMyTripsDraftId";
  let syncTimer = null;
  let restorePromise = null;

  function readJson(storage, key) {
    try {
      return JSON.parse(storage.getItem(key) || "null");
    } catch (error) {
      console.warn("저장된 여행 초안을 읽지 못했습니다.", error);
      return null;
    }
  }

  function currentDraftId() {
    const metadata = readJson(sessionStorage, META_KEY);
    return (metadata && metadata.draftId) || localStorage.getItem(PERSISTED_ID_KEY) || "";
  }

  function remember(responseData) {
    sessionStorage.setItem(META_KEY, JSON.stringify(responseData));
    localStorage.setItem(PERSISTED_ID_KEY, responseData.draftId);
    if (responseData.draft) {
      sessionStorage.setItem(DRAFT_KEY, JSON.stringify(responseData.draft));
    }
  }

  async function request(url, method, draft) {
    const options = {
      method: method,
      headers: { Accept: "application/json" },
    };
    if (draft) {
      options.headers["Content-Type"] = "application/json";
      options.body = JSON.stringify(draft);
    }
    const response = await fetch(url, options);
    if (!response.ok) {
      const error = new Error("여행 초안 API 요청에 실패했습니다.");
      error.status = response.status;
      throw error;
    }
    return response.json();
  }

  async function save(draft) {
    const draftId = currentDraftId();
    let response;
    if (draftId) {
      try {
        response = await request("/api/v1/trip-drafts/" + encodeURIComponent(draftId), "PUT", draft);
      } catch (error) {
        if (error.status !== 404) {
          throw error;
        }
        localStorage.removeItem(PERSISTED_ID_KEY);
        sessionStorage.removeItem(META_KEY);
        response = await request("/api/v1/trip-drafts", "POST", draft);
      }
    } else {
      response = await request("/api/v1/trip-drafts", "POST", draft);
    }
    remember(response.data);
    return response;
  }

  async function restoreIfNeeded() {
    if (restorePromise) {
      return restorePromise;
    }
    restorePromise = (async function () {
      const current = readJson(sessionStorage, DRAFT_KEY);
      if (current && current.basic && current.style) {
        return current;
      }
      const draftId = currentDraftId();
      if (!draftId) {
        return current;
      }
      try {
        const response = await request("/api/v1/trip-drafts/" + encodeURIComponent(draftId), "GET");
        remember(response.data);
        return response.data.draft;
      } catch (error) {
        if (error.status === 404) {
          localStorage.removeItem(PERSISTED_ID_KEY);
          sessionStorage.removeItem(META_KEY);
          return current;
        }
        throw error;
      }
    })();
    try {
      return await restorePromise;
    } finally {
      restorePromise = null;
    }
  }

  function queueSave(draft) {
    if (!draft || !draft.basic || !draft.style || !currentDraftId()) {
      return;
    }
    if (syncTimer) {
      window.clearTimeout(syncTimer);
    }
    syncTimer = window.setTimeout(function () {
      save(draft).catch(function (error) {
        console.error("여행 초안 자동 저장 실패:", error);
      });
    }, 250);
  }

  window.AllMyTripsDraftStore = {
    save: save,
    restoreIfNeeded: restoreIfNeeded,
    queueSave: queueSave,
    currentDraftId: currentDraftId,
  };
})();
