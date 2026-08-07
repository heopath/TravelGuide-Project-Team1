import {
    request,
    showToast,
} from "./mypage-common.js";

export function initAccount() {
    const profileCard =
        document.querySelector(
            "[data-profile-card]",
        );

    const avatar =
        document.querySelector(
            "[data-profile-avatar]",
        );

    const nicknameText =
        document.querySelector(
            "[data-profile-nickname]",
        );

    const emailText =
        document.querySelector(
            "[data-profile-email]",
        );

    const settingsEmail =
        document.querySelector(
            "[data-settings-email]",
        );

    const settingsNickname =
        document.querySelector(
            "[data-settings-nickname]",
        );

    const settingsNicknameForm =
        document.querySelector(
            "[data-settings-nickname-form]",
        );

    const settingsNicknameError =
        document.querySelector(
            "[data-settings-nickname-error]",
        );

    const settingsPasswordForm =
        document.querySelector(
            "[data-settings-password-form]",
        );

    const currentPasswordInput =
        settingsPasswordForm
            ?.elements
            .namedItem(
                "currentPassword",
            );

    const newPasswordInput =
        settingsPasswordForm
            ?.elements
            .namedItem(
                "newPassword",
            );

    const confirmPasswordInput =
        settingsPasswordForm
            ?.elements
            .namedItem(
                "confirmPassword",
            );

    const passwordSubmitButton =
        settingsPasswordForm
            ?.querySelector(
                "button[type='submit']",
            );

    const currentPasswordError =
        document.querySelector(
            "[data-current-password-error]",
        );

    const newPasswordError =
        document.querySelector(
            "[data-new-password-error]",
        );

    const confirmPasswordError =
        document.querySelector(
            "[data-confirm-password-error]",
        );

    const deleteAccountOpenButton =
        document.querySelector(
            "[data-delete-account-open]",
        );

    const deleteAccountConfirm =
        document.querySelector(
            "[data-delete-account-confirm]",
        );

    const deleteAccountCancelButton =
        document.querySelector(
            "[data-delete-account-cancel]",
        );

    const deleteAccountForm =
        document.querySelector(
            "[data-delete-account-form]",
        );

    const deleteAccountError =
        document.querySelector(
            "[data-delete-account-error]",
        );

    function renderMember(member) {
        const nickname =
            member.nickname ||
            member.email ||
            "사용자";

        const initial =
            nickname
                .trim()
                .charAt(0) ||
            "사";

        if (avatar) {
            avatar.textContent =
                initial;
        }

        if (nicknameText) {
            nicknameText.textContent =
                nickname;
        }

        if (emailText) {
            emailText.textContent =
                member.email || "";
        }

        if (settingsEmail) {
            settingsEmail.value =
                member.email || "";
        }

        if (settingsNickname) {
            settingsNickname.value =
                nickname;
        }

        profileCard?.setAttribute(
            "aria-busy",
            "false",
        );
    }

    function setFieldError(
        input,
        errorElement,
        message,
    ) {
        if (errorElement) {
            errorElement.textContent =
                message;
        }

        input?.classList.add(
            "is-error",
        );
    }

    function clearFieldError(
        input,
        errorElement,
    ) {
        if (errorElement) {
            errorElement.textContent =
                "";
        }

        input?.classList.remove(
            "is-error",
        );
    }

    function clearPasswordErrors() {
        clearFieldError(
            currentPasswordInput,
            currentPasswordError,
        );

        clearFieldError(
            newPasswordInput,
            newPasswordError,
        );

        clearFieldError(
            confirmPasswordInput,
            confirmPasswordError,
        );
    }

    function updatePasswordSubmitButton() {
        if (!passwordSubmitButton) {
            return;
        }

        const currentPassword =
            String(
                currentPasswordInput
                    ?.value || "",
            );

        const newPassword =
            String(
                newPasswordInput
                    ?.value || "",
            );

        const confirmPassword =
            String(
                confirmPasswordInput
                    ?.value || "",
            );

        const isValid =
            currentPassword.length > 0 &&
            newPassword.length >= 8 &&
            newPassword !==
            currentPassword &&
            confirmPassword.length >= 8 &&
            newPassword ===
            confirmPassword;

        passwordSubmitButton.disabled =
            !isValid;
    }

    settingsNicknameForm
        ?.addEventListener(
            "submit",
            async (event) => {
                event.preventDefault();

                const nickname =
                    settingsNickname
                        ?.value
                        .trim() ||
                    "";

                if (
                    nickname.length < 2 ||
                    nickname.length > 20
                ) {
                    if (
                        settingsNicknameError
                    ) {
                        settingsNicknameError
                            .textContent =
                            "닉네임은 2자 이상 20자 이하여야 합니다.";
                    }

                    return;
                }

                const submitButton =
                    settingsNicknameForm
                        .querySelector(
                            "button[type='submit']",
                        );

                if (submitButton) {
                    submitButton.disabled =
                        true;
                }

                if (
                    settingsNicknameError
                ) {
                    settingsNicknameError
                        .textContent =
                        "";
                }

                try {
                    const member =
                        await request(
                            "/api/v1/members/me",
                            {
                                method:
                                    "PATCH",

                                body:
                                    JSON.stringify({
                                        nickname,
                                    }),
                            },
                        );

                    renderMember(
                        member,
                    );

                    showToast(
                        "닉네임이 변경되었습니다.",
                    );
                } catch (error) {
                    if (
                        settingsNicknameError
                    ) {
                        settingsNicknameError
                            .textContent =
                            error.message;
                    }
                } finally {
                    if (submitButton) {
                        submitButton.disabled =
                            false;
                    }
                }
            },
        );

    updatePasswordSubmitButton();

    currentPasswordInput
        ?.addEventListener(
            "input",
            () => {
                clearFieldError(
                    currentPasswordInput,
                    currentPasswordError,
                );

                updatePasswordSubmitButton();
            },
        );

    newPasswordInput
        ?.addEventListener(
            "input",
            () => {
                clearFieldError(
                    newPasswordInput,
                    newPasswordError,
                );

                if (
                    confirmPasswordInput?.value &&
                    newPasswordInput.value !==
                    confirmPasswordInput.value
                ) {
                    setFieldError(
                        confirmPasswordInput,
                        confirmPasswordError,
                        "비밀번호가 일치하지 않습니다.",
                    );
                } else {
                    clearFieldError(
                        confirmPasswordInput,
                        confirmPasswordError,
                    );
                }

                updatePasswordSubmitButton();
            },
        );

    confirmPasswordInput
        ?.addEventListener(
            "input",
            () => {
                if (
                    confirmPasswordInput.value &&
                    confirmPasswordInput.value !==
                    newPasswordInput?.value
                ) {
                    setFieldError(
                        confirmPasswordInput,
                        confirmPasswordError,
                        "비밀번호가 일치하지 않습니다.",
                    );
                } else {
                    clearFieldError(
                        confirmPasswordInput,
                        confirmPasswordError,
                    );
                }

                updatePasswordSubmitButton();
            },
        );

    settingsPasswordForm
        ?.addEventListener(
            "submit",
            async (event) => {
                event.preventDefault();

                clearPasswordErrors();

                const currentPassword =
                    String(
                        currentPasswordInput
                            ?.value ||
                        "",
                    );

                const newPassword =
                    String(
                        newPasswordInput
                            ?.value ||
                        "",
                    );

                const confirmPassword =
                    String(
                        confirmPasswordInput
                            ?.value ||
                        "",
                    );

                let hasError =
                    false;

                if (!currentPassword) {
                    setFieldError(
                        currentPasswordInput,
                        currentPasswordError,
                        "현재 비밀번호를 입력해주세요.",
                    );

                    hasError = true;
                }

                if (!newPassword) {
                    setFieldError(
                        newPasswordInput,
                        newPasswordError,
                        "새 비밀번호를 입력해주세요.",
                    );

                    hasError = true;
                } else if (
                    newPassword.length < 8
                ) {
                    setFieldError(
                        newPasswordInput,
                        newPasswordError,
                        "새 비밀번호는 8자 이상 입력해주세요.",
                    );

                    hasError = true;
                }

                if (
                    currentPassword &&
                    newPassword &&
                    currentPassword ===
                    newPassword
                ) {
                    setFieldError(
                        newPasswordInput,
                        newPasswordError,
                        "새 비밀번호는 현재 비밀번호와 다르게 설정해주세요.",
                    );

                    hasError = true;
                }

                if (!confirmPassword) {
                    setFieldError(
                        confirmPasswordInput,
                        confirmPasswordError,
                        "새 비밀번호를 다시 입력해주세요.",
                    );

                    hasError = true;
                } else if (
                    newPassword !==
                    confirmPassword
                ) {
                    setFieldError(
                        confirmPasswordInput,
                        confirmPasswordError,
                        "비밀번호가 일치하지 않습니다.",
                    );

                    hasError = true;
                }

                if (hasError) {
                    updatePasswordSubmitButton();
                    return;
                }

                if (
                    passwordSubmitButton
                ) {
                    passwordSubmitButton
                        .disabled =
                        true;
                }

                try {
                    await request(
                        "/api/v1/members/me/password",
                        {
                            method:
                                "PATCH",

                            body:
                                JSON.stringify({
                                    currentPassword,
                                    newPassword,
                                }),
                        },
                    );

                    settingsPasswordForm
                        .reset();

                    clearPasswordErrors();

                    updatePasswordSubmitButton();

                    showToast(
                        "비밀번호가 변경되었습니다.",
                    );
                } catch (error) {
                    console.error(
                        "비밀번호 변경 실패:",
                        error,
                    );

                    const message =
                        error?.message ||
                        "비밀번호 변경에 실패했습니다.";

                    if (
                        error?.code ===
                        "PASSWORD_MISMATCH"
                    ) {
                        setFieldError(
                            currentPasswordInput,
                            currentPasswordError,
                            "현재 비밀번호가 일치하지 않습니다.",
                        );

                        currentPasswordInput
                            ?.focus();

                        return;
                    }

                    if (
                        error?.code ===
                        "NEW_PASSWORD_SAME_AS_CURRENT"
                    ) {
                        setFieldError(
                            newPasswordInput,
                            newPasswordError,
                            "새 비밀번호는 현재 비밀번호와 다르게 설정해주세요.",
                        );

                        newPasswordInput
                            ?.focus();

                        return;
                    }

                    setFieldError(
                        currentPasswordInput,
                        currentPasswordError,
                        message,
                    );
                } finally {
                    updatePasswordSubmitButton();
                }
            },
        );

    deleteAccountOpenButton
        ?.addEventListener(
            "click",
            () => {
                if (
                    !deleteAccountConfirm
                ) {
                    return;
                }

                deleteAccountConfirm.hidden =
                    false;

                if (
                    deleteAccountError
                ) {
                    deleteAccountError
                        .textContent =
                        "";
                }

                const passwordInput =
                    deleteAccountForm
                        ?.elements
                        .namedItem(
                            "password",
                        );

                passwordInput
                    ?.focus();
            },
        );

    deleteAccountCancelButton
        ?.addEventListener(
            "click",
            () => {
                if (
                    !deleteAccountConfirm
                ) {
                    return;
                }

                deleteAccountConfirm.hidden =
                    true;

                deleteAccountForm
                    ?.reset();

                if (
                    deleteAccountError
                ) {
                    deleteAccountError
                        .textContent =
                        "";
                }
            },
        );

    deleteAccountForm
        ?.addEventListener(
            "submit",
            async (event) => {
                event.preventDefault();

                const passwordInput =
                    deleteAccountForm
                        .elements
                        .namedItem(
                            "password",
                        );

                const password =
                    String(
                        passwordInput
                            ?.value ||
                        "",
                    );

                if (
                    deleteAccountError
                ) {
                    deleteAccountError
                        .textContent =
                        "";
                }

                passwordInput
                    ?.classList
                    .remove(
                        "is-error",
                    );

                if (!password) {
                    if (
                        deleteAccountError
                    ) {
                        deleteAccountError
                            .textContent =
                            "현재 비밀번호를 입력해주세요.";
                    }

                    passwordInput
                        ?.classList
                        .add(
                            "is-error",
                        );

                    return;
                }

                const submitButton =
                    deleteAccountForm
                        .querySelector(
                            "button[type='submit']",
                        );

                if (submitButton) {
                    submitButton.disabled =
                        true;
                }

                try {
                    await request(
                        "/api/v1/members/me",
                        {
                            method:
                                "DELETE",

                            body:
                                JSON.stringify({
                                    password,
                                }),
                        },
                    );

                    alert(
                        "회원 탈퇴가 완료되었습니다.",
                    );

                    window.location.href =
                        "/auth/login";
                } catch (error) {
                    if (
                        deleteAccountError
                    ) {
                        deleteAccountError
                            .textContent =
                            error?.message ||
                            "회원 탈퇴에 실패했습니다.";
                    }

                    passwordInput
                        ?.classList
                        .add(
                            "is-error",
                        );
                } finally {
                    if (submitButton) {
                        submitButton.disabled =
                            false;
                    }
                }
            },
        );

    return request(
        "/api/v1/members/me",
    )
        .then(
            renderMember,
        )
        .catch(
            (error) => {
                profileCard
                    ?.setAttribute(
                        "aria-busy",
                        "false",
                    );

                if (nicknameText) {
                    nicknameText.textContent =
                        "회원정보를 불러오지 못했습니다";
                }

                if (emailText) {
                    emailText.textContent =
                        error.message;
                }

                throw error;
            },
        );
}