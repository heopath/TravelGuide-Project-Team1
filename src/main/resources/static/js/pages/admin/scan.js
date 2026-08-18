/* 현장 검표 · 카메라 스캔 (#266)
 *
 * 검표 API는 관리자 대시보드의 검표 탭과 같은 것을 쓴다. 이 화면이 하는 일은 코드를
 * 읽어 들이는 방법 하나뿐이고, 판정·이력 기록은 서버가 이미 한다.
 *
 * 스캔은 BarcodeDetector로 한다. 안드로이드 크롬은 기본으로 지원해서 라이브러리가 필요 없다.
 * iOS 사파리와 데스크톱 크롬(Windows)에는 없으므로, 없으면 수동 입력만 남긴다.
 */
(function () {
    "use strict";

    /* 같은 코드를 이 시간 안에 다시 읽으면 무시한다. 카메라가 초당 여러 장을 잡기 때문이다. */
    const REPEAT_BLOCK_MS = 4000;
    const RECENT_LIMIT = 10;
    const SCAN_INTERVAL_MS = 250;

    const resultLabels = {
        SUCCESS: "입장",
        ALREADY_USED: "이미 사용됨",
        EXPIRED: "기간이 아님",
        CANCELLED: "취소된 티켓",
        NOT_FOUND: "확인되지 않음",
    };

    const notice = document.querySelector("[data-scan-notice]");
    const cameraBox = document.querySelector("[data-scan-camera]");
    const video = document.querySelector("[data-scan-video]");
    const startButton = document.querySelector("[data-scan-start]");
    const stopButton = document.querySelector("[data-scan-stop]");
    const form = document.querySelector("[data-scan-form]");
    const input = document.querySelector("[data-scan-input]");
    const submit = document.querySelector("[data-scan-submit]");
    const resultBox = document.querySelector("[data-scan-result]");
    const recentList = document.querySelector("[data-scan-recent]");
    const recentEmpty = document.querySelector("[data-scan-recent-empty]");
    if (!form || !resultBox) return;

    let detector = null;
    let stream = null;
    let timer = null;
    let busy = false;
    /* 최근에 읽은 코드와 시각. 같은 QR을 연속으로 보내지 않으려고 둔다. */
    const seen = new Map();

    /*
     * 지워지면 안 되는 안내가 있다. 카메라를 못 쓰는 이유가 그렇다 — 검표를 한 번 하면
     * 사라져서, 비활성된 버튼만 남고 왜 안 되는지는 알 수 없게 된다.
     */
    let standingNotice = "";

    function say(message, isError) {
        if (!notice) return;
        const text = message || standingNotice;
        notice.textContent = text;
        notice.hidden = !text;
        notice.dataset.error = (message ? isError : Boolean(standingNotice)) ? "true" : "";
    }

    function keepSaying(message) {
        standingNotice = message;
        say(message, true);
    }

    function getCookie(name) {
        const target = encodeURIComponent(name) + "=";
        const found = document.cookie.split(";")
            .map((item) => item.trim())
            .find((item) => item.startsWith(target));
        return found ? decodeURIComponent(found.substring(target.length)) : "";
    }

    async function validate(token, channel) {
        const csrfToken = getCookie("CSRF-TOKEN");
        const response = await fetch("/api/v1/admin/ticket-validations", {
            method: "POST",
            credentials: "same-origin",
            headers: Object.assign(
                { "Content-Type": "application/json", Accept: "application/json" },
                csrfToken ? { "X-CSRF-TOKEN": csrfToken } : {}
            ),
            body: JSON.stringify({ token, channel, deviceId: null }),
        });
        const payload = await response.json().catch(() => null);
        if (response.status === 401) {
            window.location.href = "/auth/login?redirect=/admin/scan";
            throw new Error("로그인이 필요합니다.");
        }
        if (!response.ok || payload?.success === false) {
            throw new Error(payload?.message || "검표하지 못했습니다.");
        }
        return payload.data;
    }

    /*
     * 현장에서는 화면을 볼 새가 없다. 통과와 거부를 소리·진동으로 먼저 알린다.
     * 지원하지 않는 기기도 있으므로 실패해도 흐름을 막지 않는다.
     */
    function feedback(admitted) {
        try {
            if (navigator.vibrate) navigator.vibrate(admitted ? 60 : [80, 60, 80]);
        } catch (error) { /* 진동은 없어도 된다. */ }
        try {
            const AudioContextClass = window.AudioContext || window.webkitAudioContext;
            if (!AudioContextClass) return;
            const context = new AudioContextClass();
            const oscillator = context.createOscillator();
            const gain = context.createGain();
            oscillator.frequency.value = admitted ? 880 : 220;
            gain.gain.value = 0.05;
            oscillator.connect(gain).connect(context.destination);
            oscillator.start();
            oscillator.stop(context.currentTime + (admitted ? 0.12 : 0.3));
            oscillator.onended = () => context.close();
        } catch (error) { /* 소리도 없어도 된다. */ }
    }

    function line(label, value) {
        if (!value) return null;
        const item = document.createElement("p");
        const name = document.createElement("span");
        name.textContent = label;
        const text = document.createElement("strong");
        text.textContent = value;
        item.append(name, text);
        return item;
    }

    function timeText(value) {
        if (!value) return "";
        const date = new Date(value);
        return `${date.getMonth() + 1}월 ${date.getDate()}일 `
            + `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
    }

    function showResult(result) {
        resultBox.hidden = false;
        resultBox.dataset.admitted = result.admitted ? "true" : "false";
        resultBox.replaceChildren();

        const headline = document.createElement("strong");
        headline.className = "scan-result-headline";
        headline.textContent = resultLabels[result.result] || result.result;
        resultBox.appendChild(headline);

        const message = document.createElement("p");
        message.className = "scan-result-message";
        message.textContent = result.message || "";
        resultBox.appendChild(message);

        /* 왜 안 되는지가 현장에서 가장 중요하다. 찾은 티켓이면 근거를 함께 보여준다. */
        [
            line("상품", result.productName),
            line("옵션", result.optionName),
            line("이용일", result.usageDate),
            line("사용 시각", timeText(result.usedAt)),
            line("티켓 번호", result.ticketNumber),
        ].filter(Boolean).forEach((item) => resultBox.appendChild(item));

        feedback(result.admitted);
        addRecent(result);
    }

    function addRecent(result) {
        if (!recentList) return;
        const item = document.createElement("li");
        item.dataset.recentResult = result.result;

        const badge = document.createElement("span");
        badge.textContent = resultLabels[result.result] || result.result;

        const detail = document.createElement("small");
        const now = new Date();
        detail.textContent = [
            `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`,
            result.ticketNumber || "코드 확인 안 됨",
            result.productName,
        ].filter(Boolean).join(" · ");

        item.append(badge, detail);
        recentList.prepend(item);
        while (recentList.children.length > RECENT_LIMIT) {
            recentList.removeChild(recentList.lastChild);
        }
        if (recentEmpty) recentEmpty.hidden = true;
    }

    /** 같은 코드를 짧은 사이에 다시 보내지 않는다. 두 번째부터는 `이미 사용됨`이 떠서 오해를 부른다. */
    function recentlySeen(token) {
        const now = Date.now();
        for (const [code, at] of seen) {
            if (now - at > REPEAT_BLOCK_MS) seen.delete(code);
        }
        if (seen.has(token)) return true;
        seen.set(token, now);
        return false;
    }

    async function handle(token, channel) {
        const trimmed = String(token || "").trim();
        if (!trimmed || busy) return;
        if (recentlySeen(trimmed)) return;
        busy = true;
        if (submit) submit.disabled = true;
        try {
            showResult(await validate(trimmed, channel));
            say("");
        } catch (error) {
            say(error.message || "검표하지 못했습니다.", true);
        } finally {
            busy = false;
            if (submit) submit.disabled = false;
        }
    }

    /* ── 카메라 ── */

    function cameraSupported() {
        return Boolean(
            window.isSecureContext
            && navigator.mediaDevices?.getUserMedia
            && window.BarcodeDetector
        );
    }

    function unsupportedReason() {
        if (!window.isSecureContext) {
            return "카메라는 https 또는 localhost에서만 열 수 있어요. 주소를 확인하거나 코드를 직접 입력해 주세요.";
        }
        if (!navigator.mediaDevices?.getUserMedia) {
            return "이 브라우저에서는 카메라를 쓸 수 없어요. 코드를 직접 입력해 주세요.";
        }
        return "이 브라우저는 QR 스캔을 지원하지 않아요. 안드로이드 크롬을 쓰거나 코드를 직접 입력해 주세요.";
    }

    async function tick() {
        if (!detector || !video || video.readyState < 2 || busy) return;
        try {
            const found = await detector.detect(video);
            if (found.length) await handle(found[0].rawValue, "MOCK_SCANNER");
        } catch (error) {
            /* 한 프레임 실패는 흔하다. 멈추지 않는다. */
        }
    }

    async function startCamera() {
        if (!cameraSupported()) {
            keepSaying(unsupportedReason());
            return;
        }
        try {
            /* 뒷면 카메라를 우선한다. 앞면이 잡히면 손님 코드를 비출 수 없다. */
            stream = await navigator.mediaDevices.getUserMedia({
                video: { facingMode: { ideal: "environment" } },
                audio: false,
            });
        } catch (error) {
            say("카메라를 열지 못했어요. 권한을 허용했는지 확인해 주세요.", true);
            return;
        }
        detector = new window.BarcodeDetector({ formats: ["qr_code"] });
        video.srcObject = stream;
        cameraBox.hidden = false;
        startButton.hidden = true;
        stopButton.hidden = false;
        say("코드를 화면 안에 비춰 주세요.");
        timer = window.setInterval(tick, SCAN_INTERVAL_MS);
    }

    function stopCamera() {
        if (timer) window.clearInterval(timer);
        timer = null;
        detector = null;
        if (stream) stream.getTracks().forEach((track) => track.stop());
        stream = null;
        if (video) video.srcObject = null;
        cameraBox.hidden = true;
        startButton.hidden = false;
        stopButton.hidden = true;
        say("");
    }

    startButton?.addEventListener("click", startCamera);
    stopButton?.addEventListener("click", stopCamera);
    form.addEventListener("submit", (event) => {
        event.preventDefault();
        const token = input.value;
        input.value = "";
        /* 손으로 넣은 것은 스캐너로 읽은 것과 구분해 기록한다. */
        handle(token, "ADMIN_WEB");
    });

    /* 화면을 벗어나면 카메라를 끈다. 켜둔 채 두면 배터리가 빠지고 표시등이 계속 켜져 있다. */
    window.addEventListener("pagehide", stopCamera);
    document.addEventListener("visibilitychange", () => {
        if (document.hidden && stream) stopCamera();
    });

    if (!cameraSupported()) {
        keepSaying(unsupportedReason());
        if (startButton) startButton.disabled = true;
    }

    window.__ticketScan = { handle, startCamera, stopCamera };
})();
