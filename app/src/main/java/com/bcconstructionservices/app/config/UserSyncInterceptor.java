package com.bcconstructionservices.app.config;

import com.bcconstructionservices.user.entity.AppUser;
import com.bcconstructionservices.user.service.UserSyncService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Syncs the caller's local AppUser row from their JWT at the start of every
 * authenticated /api/** request — after Spring Security has validated the
 * token, before the controller runs. Without this, the row was only created
 * by GET /api/users/me, so any other request that arrived first found no
 * local user: CurrentUserService threw, and audited saves stored created_by
 * as null.
 *
 * <p>The sync commits in its own transaction before the controller starts,
 * and the resolved id is stored as AuditorAwareImpl's request-scoped cache
 * entry, so auditing never needs its own lookup. That also keeps the cache
 * warm for every request, which is the workaround for the reentrant
 * auto-flush bug described in CLAUDE.md.
 *
 * <p>Requests authenticated some other way (no JWT principal) are skipped.
 */
@Component
@RequiredArgsConstructor
public class UserSyncInterceptor implements HandlerInterceptor {

    private final UserSyncService userSyncService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            AppUser user = userSyncService.syncFromToken(jwt);
            request.setAttribute(AuditorAwareImpl.cacheKey(user.getKeycloakId()), user.getId());
        }
        return true;
    }
}
