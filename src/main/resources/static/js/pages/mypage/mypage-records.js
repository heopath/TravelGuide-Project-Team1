/* 완료된 모든 여행을 보여 주고, 기록이 없으면 사진 선택 단계로 바로 연결한다. */
async function fetchData(url) {
  const response = await fetch(url, { credentials: "same-origin" });
  const result = await response.json().catch(() => null);
  if (!response.ok || !result?.success) {
    throw new Error(result?.message || "여행 사진첩을 불러오지 못했습니다.");
  }
  return result.data;
}
function periodLabel(trip) {
  const dot = (value) => String(value || "").replaceAll("-", ".");
  if (!trip.startDate) return "";
  const start = dot(trip.startDate);
  const end = dot(trip.endDate);
  return start === end || !end ? start : `${start} – ${end}`;
}

function createAlbumCard(trip, record) {
  const card = document.createElement("a");
  card.className = "travel-record-album-card" + (record ? " has-album" : " needs-photos");
  card.href = `/trips/${trip.tripId}/record`;

  const cover = record?.images?.find((image) => image.cover) || record?.images?.[0];
  const visual = document.createElement("div");
  visual.className = "travel-record-album-visual";
  if (cover?.imageUrl) {
    const image = document.createElement("img");
    image.src = cover.imageUrl;
    image.alt = cover.altText || `${trip.title || trip.destinationName} 대표 사진`;
    visual.appendChild(image);
  } else {
    const placeholder = document.createElement("div");
    placeholder.className = "travel-record-album-placeholder";
    placeholder.innerHTML = "<span>＋</span><small>사진을 골라<br>앨범 만들기</small>";
    visual.appendChild(placeholder);
  }

  const tape = document.createElement("span");
  tape.className = "travel-record-album-tape";
  tape.setAttribute("aria-hidden", "true");
  visual.appendChild(tape);

  const body = document.createElement("div");
  body.className = "travel-record-album-copy";

  const state = document.createElement("small");
  state.className = "travel-record-album-state";
  state.textContent = record ? `완성된 앨범 · 사진 ${record.images?.length || 0}장` : "새 앨범 만들기";

  const title = document.createElement("strong");
  title.textContent = trip.title || trip.destinationName || "이름 없는 여행";

  const meta = document.createElement("span");
  meta.className = "travel-record-album-meta";
  meta.textContent = [trip.destinationName, periodLabel(trip)].filter(Boolean).join(" · ");

  const action = document.createElement("b");
  action.className = "travel-record-album-action";
  action.textContent = record ? "사진첩 열기" : "사진 선택하기";
  const arrow = document.createElement("i");
  arrow.setAttribute("aria-hidden", "true");
  arrow.textContent = "→";
  action.appendChild(arrow);

  body.append(state, title, meta, action);
  card.append(visual, body);
  return card;
}

export async function initTravelRecords() {
  const list = document.querySelector("[data-records-list]");
  const count = document.querySelector("[data-records-count]");
  if (!list || list.dataset.recordsReady) return;
  list.dataset.recordsReady = "1";

  try {
    const [trips, records] = await Promise.all([
      fetchData("/api/v1/trips"),
      fetchData("/api/v1/travel-records/me"),
    ]);
    const recordByTrip = new Map((records || []).map((record) => [Number(record.tripId), record]));
    const finishedTrips = (trips || [])
      .filter((trip) => window.AllMyTripsTripStatus?.isTripFinished(trip) === true)
      .sort((left, right) => String(right.endDate || "").localeCompare(String(left.endDate || "")));
    if (count) count.textContent = String(finishedTrips.length);

    list.replaceChildren();
    if (!finishedTrips.length) {
      const empty = document.createElement("div");
      empty.className = "travel-record-empty";
      empty.innerHTML = "<span>◇</span><strong>아직 완료된 여행이 없습니다</strong><p>여행을 다녀온 뒤 원하는 사진만 고르면 일정과 예약을 자동으로 엮어드려요.</p>";
      list.appendChild(empty);
      return;
    }

    finishedTrips.forEach((trip) => {
      list.appendChild(createAlbumCard(trip, recordByTrip.get(Number(trip.tripId))));
    });
  } catch (error) {
    if (count) count.textContent = "–";
    const message = document.createElement("p");
    message.className = "mypage-state error";
    message.textContent = error.message || "여행 사진첩을 불러오지 못했습니다.";
    list.replaceChildren(message);
  }
}
