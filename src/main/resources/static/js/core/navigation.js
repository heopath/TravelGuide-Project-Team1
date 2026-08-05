const ALL_MY_TRIPS_TEMPLATE_ROUTES = {
  "/home": "home/home",
  "/auth/login": "auth/login",
  "/auth/signup": "auth/signup",
  "/trips/new/plan": "trips/plan",
  "/trips/new/basic": "trips/basic",
  "/trips/new/style": "trips/style",
  "/guide": "guide/guide",
  "/guide/places/haeundae": "guide/place-detail",
  "/trips/busan/schedule": "trips/schedule",
  "/trips/busan/map": "trips/map",
  "/ai-guide": "guide/ai-guide",
  "/trips/busan/optimize": "trips/optimize",
  "/guide/themes": "guide/themes",
  "/booking": "booking/booking",
  "/booking/tickets/blueline": "booking/ticket",
  "/booking/hotels": "booking/hotels",
  "/booking/flights": "booking/flights",
  "/booking/queue": "booking/queue",
  "/mypage": "mypage/mypage",
  "/trips/busan/record": "trips/record",
  "/admin": "admin/admin"
};

function navigateTo(route) {
  let destination = route || "/home";
  if (window.location.protocol === "file:") {
    const template = ALL_MY_TRIPS_TEMPLATE_ROUTES[route || "/home"];
    if (template) {
      destination = new URL("../" + template + ".html", window.location.href).href;
    }
  }
  if (window.AllMyTripsLoading) {
    window.AllMyTripsLoading.show();
    window.setTimeout(function () {
      window.location.href = destination;
    }, 1400);
    return;
  }
  window.location.href = destination;
}

document.addEventListener("click", function (event) {
  const routeButton = event.target.closest("[data-route]:not(body)");
  if (routeButton) {
    navigateTo(routeButton.dataset.route);
    return;
  }

  const toggle = event.target.closest("[data-toggle]");
  if (toggle) {
    toggle.classList.toggle("selected");
    return;
  }

  const group = event.target.closest("[data-toggle-group]");
  if (group) {
    group.parentElement.querySelectorAll("button").forEach(function (button) {
      button.classList.remove("selected");
    });
    group.classList.add("selected");
  }
});

document.addEventListener("submit", function (event) {
  if (event.target.matches("[data-demo-form]")) {
    event.preventDefault();
    window.AllMyTripsModal.showToast("입력 내용을 저장했습니다.");
    if (event.target.dataset.next) navigateTo(event.target.dataset.next);
  }
  if (event.target.matches("[data-chat-form]")) {
    event.preventDefault();
    const input = event.target.querySelector("input");
    if (input && input.value.trim()) {
      event.target.insertAdjacentHTML("beforebegin", '<div class="user-message">' + input.value + "</div>");
      input.value = "";
    }
  }
});
