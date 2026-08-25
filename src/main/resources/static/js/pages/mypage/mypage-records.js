/* 완료 여행의 기록만 모아 보여 주는 사진첩 진입점. 앨범 편집은 기존 여행 기록 화면에서 한다. */
export async function initTravelRecords() {
  const list = document.querySelector("[data-records-list]");
  if (!list || list.dataset.recordsReady) return;
  list.dataset.recordsReady = "1";

  try {
    const response = await fetch("/api/v1/travel-records/me", { credentials: "same-origin" });
    const result = await response.json().catch(() => null);
    if (!response.ok || !result?.success) throw new Error(result?.message || "여행 기록을 불러오지 못했습니다.");
    list.replaceChildren();
    if (!result.data?.length) {
      const empty = document.createElement("p");
      empty.className = "mypage-state";
      empty.textContent = "아직 만든 여행 사진첩이 없습니다. 완료된 여행에서 사진을 추가해보세요.";
      list.appendChild(empty);
      return;
    }

    result.data.forEach((record) => {
      const card = document.createElement("a");
      card.className = "travel-record-album-card";
      card.href = `/trips/${record.tripId}/record`;
      const cover = record.images?.find((image) => image.cover) || record.images?.[0];
      if (cover?.imageUrl) {
        const image = document.createElement("img");
        image.src = cover.imageUrl;
        image.alt = cover.altText || `${record.title} 대표 사진`;
        card.appendChild(image);
      } else {
        const placeholder = document.createElement("div");
        placeholder.className = "travel-record-album-placeholder";
        placeholder.textContent = "✦";
        card.appendChild(placeholder);
      }
      const body = document.createElement("div");
      body.className = "travel-record-album-copy";
      body.innerHTML = `<strong></strong><span></span><small>사진첩 열기 →</small>`;
      body.querySelector("strong").textContent = record.title;
      body.querySelector("span").textContent = record.content || "사진으로 남긴 여행의 순간들입니다.";
      card.appendChild(body);
      list.appendChild(card);
    });
  } catch (error) {
    const message = document.createElement("p");
    message.className = "mypage-state";
    message.textContent = error.message || "여행 기록을 불러오지 못했습니다.";
    list.replaceChildren(message);
  }
}
