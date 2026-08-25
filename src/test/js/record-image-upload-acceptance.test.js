const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const read = (p) => fs.readFileSync(path.resolve(__dirname, p), "utf8");
const template = read("../../main/resources/templates/trips/record.html");
const script = read("../../main/resources/static/js/pages/trips/record.js");
const prodProperties = read("../../main/resources/application-prod.properties");

assert.match(template, /type="file"[^>]+accept="image\/jpeg,image\/png,image\/webp"/,
  "여행 기록 화면은 외부 URL 대신 PC 이미지 선택을 제공해야 한다");
assert.doesNotMatch(template, /name="imageUrl"/,
  "새 사진을 URL로 직접 입력하는 필드는 노출하지 않는다");
assert.match(script, /\/images\/upload/,
  "선택한 파일을 multipart 업로드 API로 보내야 한다");
assert.match(script, /options\.body instanceof FormData/,
  "multipart 요청에 JSON Content-Type을 강제로 붙이면 안 된다");
assert.match(script, /URL\.createObjectURL\(file\)/,
  "기록을 처음 작성할 때도 저장 전 사진 미리보기를 제공해야 한다");
assert.match(prodProperties, /\/opt\/all-my-trips\/shared\/uploads\/travel-record-images/,
  "배포 교체 후에도 사진이 남는 shared 경로를 운영 기본값으로 사용해야 한다");

console.log("record image upload acceptance checks passed");
