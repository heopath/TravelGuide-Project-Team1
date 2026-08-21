/*
 * 캡처 목록을 만든다.
 *
 * 번호(SB-01…)와 파일 이름을 <b>플러그인 코드에서 그대로 가져온다.</b> 여기서 다시 정렬해
 * 번호를 매기면 두 곳이 갈리고, 어긋나는 순간 모든 캡처가 엉뚱한 카드에 붙는다. 화면이
 * 하나 늘거나 그룹 순서가 바뀌면 조용히 한 칸씩 밀린다.
 *
 *   node tools/figma-plugin/build-shotlist.js [출력폴더]
 *
 * 만들어지는 것
 *   shotlist.json  번호·파일·주소·이름·그룹
 *   shotlist.tsv   캡처 스크립트가 읽는 `파일<탭>주소`
 */
const fs = require("fs");
const path = require("path");
const vm = require("node:vm");

const OUT = process.argv[2] || process.cwd();

/*
 * 플러그인 코드는 Figma 안에서만 도는 전역(figma)을 쓴다. 목록을 뽑을 뿐이라 그 전역을
 * 흉내 내는 대신 시작할 때 부르는 것들만 지나가게 해 준다.
 */
const source = fs.readFileSync(path.join(__dirname, "code.js"), "utf8");
const sandbox = {
    figma: { showUI() {}, ui: { onmessage: null, postMessage() {} }, root: { children: [] } },
    __html__: "",
};
vm.runInContext(source + "\n;globalThis.__out = storyScreens();", vm.createContext(sandbox));
const rows = sandbox.__out;

if (!rows || rows.length === 0) throw new Error("화면 목록이 비어 있습니다.");

fs.writeFileSync(path.join(OUT, "shotlist.json"), JSON.stringify(rows, null, 2));
fs.writeFileSync(path.join(OUT, "shotlist.tsv"),
    rows.map((r) => [r.file, r.path].join("\t")).join("\n") + "\n");

console.log(rows.length + "개 캡처 목록을 만들었습니다: " + OUT);
console.log("  " + rows[0].no + " " + rows[0].name + "  →  " + rows[0].file);
console.log("  " + rows[rows.length - 1].no + " " + rows[rows.length - 1].name
    + "  →  " + rows[rows.length - 1].file);
