/*
 * app.js의 화면 목록을 플러그인 코드에 심는다.
 *
 * 목록을 손으로 옮겨 적으면 화면이 늘 때마다 두 곳이 갈린다. 예전에
 * ALL_MY_TRIPS_SCREENS가 세 파일에 복사돼 22개 / 20개 / 20개로 갈렸던 적이 있다.
 * 그래서 여기서는 app.js를 읽어 code.js의 표시 구간만 갈아 끼운다.
 *
 *   node tools/figma-plugin/build-screens.js
 */
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const APP_JS = path.join(ROOT, "src/main/resources/static/js/app.js");
const CODE_JS = path.join(__dirname, "code.js");

const BEGIN = "// <screens>";
const END = "// </screens>";

function readScreens() {
    const source = fs.readFileSync(APP_JS, "utf8");
    const matched = /ALL_MY_TRIPS_SCREENS\s*=\s*\[([\s\S]*?)\n\];/.exec(source);
    if (!matched) throw new Error("app.js에서 ALL_MY_TRIPS_SCREENS를 찾지 못했습니다.");

    /* 항목 사이사이에 주석이 있다. 먼저 걷어내야 문자열만 순서대로 뽑을 수 있다. */
    const stripped = matched[1].replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/[^\n]*/g, "");
    const words = [...stripped.matchAll(/"([^"]*)"/g)].map((m) => m[1]);

    if (words.length === 0 || words.length % 3 !== 0) {
        /* 칸이 밀리면 이름이나 그룹 자리에 경로가 들어간다. 조용히 넘기면 안 된다. */
        throw new Error(`화면 목록의 칸 수가 3의 배수가 아닙니다: ${words.length}개`);
    }

    const screens = [];
    for (let i = 0; i < words.length; i += 3) {
        screens.push({ path: words[i], name: words[i + 1], group: words[i + 2] });
    }
    return screens;
}

function main() {
    const screens = readScreens();
    const code = fs.readFileSync(CODE_JS, "utf8");

    const begin = code.indexOf(BEGIN);
    const end = code.indexOf(END);
    if (begin < 0 || end < 0) throw new Error("code.js에서 화면 목록 표시 구간을 찾지 못했습니다.");

    const body = screens
        .map((s) => `  { path: ${JSON.stringify(s.path)}, `
            + `name: ${JSON.stringify(s.name)}, group: ${JSON.stringify(s.group)} },`)
        .join("\n");

    const replaced = code.slice(0, begin)
        + BEGIN + "\n"
        + "/* 손으로 고치지 마세요. build-screens.js가 app.js에서 만들어 넣습니다. */\n"
        + "const SCREENS = [\n" + body + "\n];\n"
        + code.slice(end);

    fs.writeFileSync(CODE_JS, replaced);
    console.log(`화면 ${screens.length}개를 code.js에 심었습니다.`);
}

main();
