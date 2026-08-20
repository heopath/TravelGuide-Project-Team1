/* 시험이 마크업을 읽는 공통 도구
 *
 * 관리자 화면 시험 열 개가 같은 한 줄을 복사해 쓰고 있었다. 한 곳으로 모은다.
 */
const fs = require("fs");

/**
 * HTML 주석을 걷어낸다.
 *
 * <p>주석은 화면에 그려지지 않으므로, `마크업에 가짜 값이 박혀 있지 않은지` 보는 검사에서
 * 빼야 한다. 주석에 남긴 과거 사례까지 걸리면 안 된다.
 *
 * <p>한 번만 치환하지 않고 더 지울 것이 없을 때까지 돈다. 한 번 지운 결과가 새 주석
 * 모양을 만들 수 있어서다 — 그래서 CodeQL이 한 번짜리 치환을 불완전한 정리로 본다.
 * (js/incomplete-multi-character-sanitization)
 */
function stripHtmlComments(markup) {
  let cleaned = String(markup);
  let previous;

  do {
    previous = cleaned;
    cleaned = cleaned.replace(/<!--[\s\S]*?-->/, "");
  } while (cleaned !== previous);

  return cleaned;
}

/** 파일을 읽어 주석을 걷어낸 마크업을 돌려준다. */
function readMarkup(file) {
  return stripHtmlComments(fs.readFileSync(file, "utf8"));
}

module.exports = { stripHtmlComments, readMarkup };
