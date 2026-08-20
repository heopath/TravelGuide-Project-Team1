/*
 * 모달을 띄워 놓고 찍는다.
 *
 *   node tools/figma-plugin/capture-modals.js <저장할 폴더>
 *
 * 모달은 눌러야 뜨므로 주소만으로는 찍을 수 없다. modals.json에 적어 둔 열기 코드를
 * 화면에서 그대로 실행한 뒤 찍는다.
 *
 * 화면 캡처(capture.js)와 같은 이유로 로그인한 창을 조종한다. headless로 따로 띄우면
 * 세션 쿠키가 브라우저를 닫는 순간 사라진다.
 */
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const OUT = process.argv[2] || process.cwd();
/* 두 번째 인자로 번호를 주면 그것만 다시 찍는다. 한 장이 안 떴을 때 전부 다시 돌릴 이유가 없다. */
const ONLY = (process.argv[3] || "").toUpperCase();
const SHOTS = path.join(OUT, "shots");
const SPEC = JSON.parse(fs.readFileSync(path.join(__dirname, "modals.json"), "utf8"));

const SIZE = { width: 1440, height: 1010 };
const DEFAULT_WAIT = 1200;

function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

async function firstPage() {
    const res = await fetch("http://localhost:9222/json");
    const pages = (await res.json()).filter((t) => t.type === "page" && t.webSocketDebuggerUrl);
    if (pages.length === 0) throw new Error("조종할 탭이 없습니다. 로그인한 창이 닫혔는지 확인하세요.");
    return pages[0];
}

function connect(url) {
    return new Promise((resolve, reject) => {
        const socket = new WebSocket(url);
        socket.onopen = () => resolve(socket);
        socket.onerror = () => reject(new Error("연결 실패"));
    });
}

let nextId = 1;
function send(socket, method, params) {
    const id = nextId++;
    return new Promise((resolve, reject) => {
        const onMessage = (event) => {
            const data = JSON.parse(event.data);
            if (data.id !== id) return;
            socket.removeEventListener("message", onMessage);
            if (data.error) reject(new Error(method + ": " + data.error.message));
            else resolve(data.result);
        };
        socket.addEventListener("message", onMessage);
        socket.send(JSON.stringify({ id, method, params: params || {} }));
    });
}

async function main() {
    fs.mkdirSync(SHOTS, { recursive: true });
    const socket = await connect((await firstPage()).webSocketDebuggerUrl);
    await send(socket, "Page.enable");
    /*
     * 창을 앞으로 가져온다. 뒤에 있으면 Chrome이 그리기를 늦춰, 스스로 크기를 재는
     * 위젯(토스 결제창 등)이 높이 0으로 남는다. 창은 떴는데 속이 빈 채로 찍힌다.
     */
    await send(socket, "Page.bringToFront");
    await send(socket, "Runtime.enable");
    await send(socket, "Emulation.setDeviceMetricsOverride", {
        width: SIZE.width, height: SIZE.height, deviceScaleFactor: 1, mobile: false,
    });

    const seen = {};
    const failed = [];
    let done = 0;

    const targets = ONLY ? SPEC.modals.filter((m) => m.no === ONLY) : SPEC.modals;
    if (targets.length === 0) throw new Error(ONLY + " 를 목록에서 찾지 못했습니다.");

    for (const modal of targets) {
        /*
         * 모달마다 화면을 새로 연다. 앞의 모달을 닫는 방법이 제각각이라, 닫는 대신
         * 새로 여는 편이 확실하다. 남아 있으면 두 개가 겹쳐 찍힌다.
         */
        await send(socket, "Page.navigate", { url: "http://localhost:8080" + SPEC.page });
        await sleep(3000);

        const result = await send(socket, "Runtime.evaluate", {
            expression: "(function () { try { " + modal.open + "; return 'ok'; }"
                + " catch (e) { return 'ERR ' + e.message; } })()",
            awaitPromise: false, returnByValue: true,
        });
        const said = result && result.result && result.result.value;
        if (said !== "ok") failed.push({ modal, why: said || "결과 없음" });

        await sleep(modal.wait || DEFAULT_WAIT);

        const shot = await send(socket, "Page.captureScreenshot", { format: "png" });
        fs.writeFileSync(path.join(SHOTS, modal.no.toLowerCase() + ".png"),
            Buffer.from(shot.data, "base64"));

        const hash = crypto.createHash("md5").update(shot.data).digest("hex");
        (seen[hash] = seen[hash] || []).push(modal);
        done += 1;
        process.stdout.write(`\r${done}/${SPEC.modals.length} ${modal.no} ${modal.name}            `);
    }

    await send(socket, "Emulation.clearDeviceMetricsOverride");
    socket.close();
    console.log("\n\n" + done + "장 저장했습니다.");

    if (failed.length) {
        console.log("\n열지 못한 모달:");
        for (const f of failed) console.log("  " + f.modal.no + " " + f.modal.name + " — " + f.why);
    }

    /*
     * 같은 그림이 여러 장이면 모달이 안 뜨고 맨 화면만 찍힌 것이다. 열기 코드가 조용히
     * 실패했거나 뜨는 데 시간이 더 필요한 경우다.
     */
    const dupes = Object.values(seen).filter((g) => g.length > 1);
    if (dupes.length === 0 && failed.length === 0) {
        console.log("모두 서로 다른 그림입니다.");
        return;
    }
    for (const group of dupes) {
        console.log("\n같은 그림 " + group.length + "장 (안 떴을 수 있음):");
        console.log("  " + group.map((m) => m.no + " " + m.name).join("\n  "));
    }
    if (dupes.length || failed.length) process.exitCode = 1;
}

main().catch((error) => { console.error("\n" + error.message); process.exitCode = 1; });
