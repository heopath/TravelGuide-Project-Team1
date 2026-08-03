package org.example.all_my_trip_project.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

/** Resolves the authenticated member from the server-managed session. */
@Component
public class SessionUserResolver {

    public Long requiredUserId(HttpServletRequest request) {
        if (request.getUserPrincipal() != null) {
            try {
                return Long.valueOf(request.getUserPrincipal().getName());
            } catch (NumberFormatException ignored) {
                // Fall through to the application's session attributes.
            }
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            for (String attributeName : new String[]{"userId", "LOGIN_USER_ID", "USER_ID"}) {
                Long userId = toLong(session.getAttribute(attributeName));
                if (userId != null && userId > 0) {
                    return userId;
                }
            }
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String string) {
            try {
                return Long.valueOf(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
