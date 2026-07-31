document.addEventListener("DOMContentLoaded", function () {
    const loginButton = document.querySelector("[data-auth-login]");
    const userActions = document.querySelector("[data-auth-user]");
    const avatarButton = document.querySelector("[data-auth-avatar]");
    const logoutButton = document.querySelector("[data-auth-logout]");

    if (!loginButton || !userActions) {
        return;
    }

    function showAnonymous() {
        loginButton.hidden = false;
        userActions.hidden = true;
    }

    function showAuthenticated(member) {
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

    if (logoutButton) {
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
                logoutButton.textContent = "로그아웃";
            }
        });
    }

    loadAuthenticationState();
});