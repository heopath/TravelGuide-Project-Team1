/* 테마 여행 전용 JavaScript */
document.addEventListener("DOMContentLoaded", function () {
  document.querySelectorAll("[data-theme-card]").forEach(function (card) {
    card.addEventListener("click", function () {
      sessionStorage.setItem("all-my-trips-trip-draft", JSON.stringify({
        title: card.dataset.title,
        destinationName: card.dataset.destination,
        destinationLabel: card.dataset.destination,
        purpose: card.dataset.purpose,
        themeNights: Number(card.dataset.nights) || 2,
        source: "MANUAL",
      }));
      sessionStorage.removeItem("all-my-trips-destination");
      window.location.href = "/trips/new/basic";
    });
  });
  document.body.dataset.pageReady = "true";
});
