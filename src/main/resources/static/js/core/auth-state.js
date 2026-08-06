document.addEventListener("DOMContentLoaded", function () {
    const loginButton = document.querySelector("[data-auth-login]");
    const userActions = document.querySelector("[data-auth-user]");
    const avatarButton = document.querySelector("[data-auth-avatar]");
    // 헤더 말고도 로그아웃 버튼을 두는 화면(마이페이지 사이드바)이 있어 전부 연결한다.
    const logoutButtons = document.querySelectorAll("[data-auth-logout]");

    if (!loginButton || !userActions) {
        return;
    }

    function showAnonymous() {
        document.documentElement.dataset.authenticated = "false";
        document.documentElement.dataset.userRole = "";

        loginButton.hidden = false;
        userActions.hidden = true;
    }

    function showAuthenticated(member) {
        document.documentElement.dataset.authenticated = "true";
        document.documentElement.dataset.userRole = (member.role || "USER").toUpperCase();

        const nickname = member.nickname || member.email || "사용자";
        const initial = nickname.trim().charAt(0) || "사";

        loginButton.hidden = true;
        userActions.hidden = false;

        if (avatarButton) {
            avatarButton.textContent = initial;
            avatarButton.title = nickname + "님의 마이페이지";
            avatarButton.setAttribute(
                "aria-label",
                nickname + "님의 마이페이지"
            );
        }
    }

    async function loadAuthenticationState() {
        try {
            const response = await fetch("/api/v1/members/me", {
                method: "GET",
                headers: {
                    "Accept": "application/json"
                },
                credentials: "same-origin"
            });

            if (response.status === 401) {
                showAnonymous();
                return;
            }

            const result = await response.json().catch(function () {
                return null;
            });

            if (!response.ok || !result?.success || !result.data) {
                showAnonymous();
                return;
            }

            showAuthenticated(result.data);
        } catch (error) {
            console.warn("인증 상태를 확인하지 못했습니다.", error);
            showAnonymous();
        }
    }

    logoutButtons.forEach(function (logoutButton) {
        // 아이콘 등 자식 요소를 가진 버튼도 있으므로 원래 마크업을 보관했다가 실패 시 되돌린다.
        const originalMarkup = logoutButton.innerHTML;

        logoutButton.addEventListener("click", async function () {
            logoutButton.disabled = true;
            logoutButton.textContent = "처리 중...";

            try {
                const response = await fetch("/api/v1/auth/logout", {
                    method: "POST",
                    headers: {
                        "Accept": "application/json"
                    },
                    credentials: "same-origin"
                });

                const result = await response.json().catch(function () {
                    return null;
                });

                if (!response.ok || !result?.success) {
                    throw new Error(
                        result?.message || "로그아웃에 실패했습니다."
                    );
                }

                window.location.href = "/home";
            } catch (error) {
                if (window.AllMyTripsModal?.showToast) {
                    window.AllMyTripsModal.showToast(error.message);
                } else {
                    alert(error.message);
                }

                logoutButton.disabled = false;
                logoutButton.innerHTML = originalMarkup;
            }
        });
    });

    loadAuthenticationState();
});