/*
 * 로그인한 채로 열려 있는 Chrome 창을 조종해 화면을 찍는다.
 *
 *   node tools/figma-plugin/capture.js <shotlist가 있는 폴더>
 *
 * headless로 따로 띄우지 않는 이유는, 세션 쿠키가 브라우저를 닫는 순간 사라지기 때문이다.
 * 로그인한 그 창을 그대로 써야 로그인이 필요한 화면이 찍힌다.
 */
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const DIR = process.argv[2] || __dirname;
const SHOTS = path.join(DIR, "shots");
const LIST = JSON.parse(fs.readFileSync(path.join(DIR, "shotlist.json"), "utf8"));

const MOBILE = new Set(["/admin/scan"]);
const DESKTOP_SIZE = { width: 1440, height: 1010 };
const MOBILE_SIZE = { width: 390, height: 844 };

function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

async function target() {
    const res = await fetch("http://localhost:9222/json");
    const pages = (await res.json()).filter((t) => t.type === "page" && t.webSocketDebuggerUrl);
    if (pages.length === 0) throw new Error("조종할 탭이 없습니다. 창이 닫혔는지 확인하세요.");
    return pages[0];
}

function connect(url) {
    return new Promise((resolve, reject) => {
        const socket = new WebSocket(url);
        socket.onopen = () => resolve(socket);
        socket.onerror = (e) => reject(new Error("연결 실패: " + (e.message || "")));
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
    const page = await target();
    const socket = await connect(page.webSocketDebuggerUrl);
    await send(socket, "Page.enable");
    /*
     * 창을 앞으로 가져온다. 뒤에 있으면 Chrome이 그리기를 늦춰, 스스로 크기를 재는
     * 위젯(토스 결제창 등)이 높이 0으로 남는다. 창은 떴는데 속이 빈 채로 찍힌다.
     */
    await send(socket, "Page.bringToFront");

    const seen = {};
    let done = 0;

    for (const row of LIST) {
        const size = MOBILE.has(row.path) ? MOBILE_SIZE : DESKTOP_SIZE;
        await send(socket, "Emulation.setDeviceMetricsOverride", {
            width: size.width, height: size.height,
            deviceScaleFactor: 1, mobile: MOBILE.has(row.path),
        });

        await send(socket, "Page.navigate", { url: "http://localhost:8080" + row.path });
        /* 화면 대부분이 뜬 뒤 데이터를 부른다. 바로 찍으면 빈 목록이 찍힌다. */
        await sleep(3500);

        const shot = await send(socket, "Page.captureScreenshot", { format: "png" });
        const file = path.join(SHOTS, row.file);
        fs.writeFileSync(file, Buffer.from(shot.data, "base64"));

        const hash = crypto.createHash("md5").update(shot.data).digest("hex");
        (seen[hash] = seen[hash] || []).push(row);
        done += 1;
        process.stdout.write(`\r${done}/${LIST.length} ${row.no} ${row.name}            `);
    }

    await send(socket, "Emulation.clearDeviceMetricsOverride");
    socket.close();

    console.log("\n\n" + done + "장 저장했습니다.");

    /* 같은 그림이 여러 장이면 로그인 화면으로 튕긴 것이다. 조용히 넘기면 안 된다. */
    const dupes = Object.values(seen).filter((g) => g.length > 1);
    if (dupes.length === 0) {
        console.log("모두 서로 다른 화면입니다.");
        return;
    }
    for (const group of dupes) {
        console.log("\n같은 그림 " + group.length + "장:");
        console.log("  " + group.map((r) => r.no + " " + r.name).join("\n  "));
    }
}

main().catch((error) => { console.error("\n" + error.message); process.exitCode = 1; });
