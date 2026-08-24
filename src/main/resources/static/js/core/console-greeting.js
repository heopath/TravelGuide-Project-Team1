/* 개발자도구를 연 사람에게 건네는 인사
 *
 * 콘솔은 화면을 어지럽히지 않고 "만든 사람"을 남길 수 있는 유일한 자리다. 코드가
 * 궁금해서 열어본 사람에게 마이티가 인사하고 저장소 주소를 알려준다.
 *
 * 인사가 전부는 아니다. 뒤에 붙는 경고가 오히려 본론에 가깝다. 콘솔에 무언가를
 * 붙여넣게 만들어 로그인된 세션을 통째로 가져가는 수법(self-XSS)이 흔하고, 우리
 * 손님도 로그인한 채로 이 창을 연다. 페이스북·구글이 콘솔에 경고를 띄우는 이유가
 * 같다. 막을 수단이 없으니 알려주기라도 한다.
 *
 * 화면에는 아무것도 그리지 않는다. 콘솔이 없는 환경이면 조용히 지나간다.
 */
(function greetInConsole() {
  "use strict";

  if (!window.console || typeof window.console.log !== "function") return;

  /* 푸터에 이미 찍혀 있는 값을 그대로 쓴다. 여기서 따로 관리하면 언젠가 어긋난다. */
  const badge = document.querySelector(".footer-version");
  const version = badge && badge.textContent ? badge.textContent.trim() : "";

  /*
   * 마이티를 글자로 옮긴 것이다. 마스코트 SVG(fragments/mighty.html)와 같은 얼굴 —
   * 둥근 몸, 두 눈, 웃는 입, 머리 위를 도는 종이비행기.
   */
  const mighty = [
    "        ✈",
    "    ╭───────╮",
    "    │ ●   ● │",
    "    │   ‿   │",
    "    ╰───────╯",
  ].join("\n");

  const mascotStyle = "color:#5c68ff;font-size:13px;line-height:1.3;font-weight:700";
  const titleStyle = "color:#0b1533;font-size:16px;font-weight:800;letter-spacing:-.03em";
  const bodyStyle = "color:#68728a;font-size:12px;line-height:1.8";
  const alarmStyle = "color:#f45866;font-size:18px;font-weight:800";
  const warningStyle = "color:#1b2540;font-size:13px;line-height:1.8";

  console.log("%c" + mighty, mascotStyle);
  console.log(
    "%cAll My Trips" + (version ? " " + version : "") + "%c\n여행의 모든 것, 마이티와 함께.",
    titleStyle,
    bodyStyle,
  );
  console.log(
    "%c여기까지 열어보셨군요. 코드는 여기 있습니다\n" +
      "https://github.com/heopath/TravelGuide-Project-Team1\n\n" +
      "그리고 푸터의 버전 번호도 한번 눌러보세요. 다섯 번쯤.",
    bodyStyle,
  );

  console.log("%c⚠ 잠깐만요", alarmStyle);
  console.log(
    "%c누가 \"이걸 여기에 붙여넣으면 된다\"고 했다면 멈추세요.\n" +
      "콘솔에 붙여넣은 코드는 지금 로그인한 당신 자격으로 그대로 실행됩니다.\n" +
      "예약 내역도, 결제 수단도, 계정도 함께 넘어갑니다.\n\n" +
      "무슨 코드인지 스스로 읽어낼 수 있을 때만 붙여넣으세요.",
    warningStyle,
  );
})();
