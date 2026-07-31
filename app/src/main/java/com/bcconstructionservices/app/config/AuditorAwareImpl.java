package com.bcconstructionservices.app.config;

import com.bcconstructionservices.user.entity.AppUser;
import com.bcconstructionservices.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the "current auditor" for Spring Data JPA Auditing.
 * <p>
 * This is what powers {@code @CreatedBy} / {@code @LastModifiedBy} fields on
 * other modules' entities (e.g. {@code PurchaseReceipt.createdBy} in the
 * inventory module). It resolves the caller's <strong>local</strong>
 * {@link AppUser#getId()} — not the raw Keycloak subject UUID — so that
 * {@code created_by} / {@code updated_by} foreign key columns stay {@code Long}
 * and remain fast and directly joinable to {@code app_user.id}.
 * <p>
 * The Keycloak subject ("sub" claim) is read from the validated {@link Jwt}
 * already present in the security context (populated by Spring Security's
 * OAuth2 resource server support), then resolved to a local {@link AppUser}
 * row via {@link UserRepository#findByKeycloakId(UUID)}.
 */
@Component("auditorAwareImpl")
@RequiredArgsConstructor
public class AuditorAwareImpl implements AuditorAware<Long> {

    private final UserRepository userRepository;

    /**
     * Reentrancy guard. Resolving the auditor queries {@link UserRepository},
     * which can make Hibernate auto-flush other still-dirty entities before
     * running that query. If one of those entities is itself audited, its
     * pre-update callback calls back into {@link #getCurrentAuditor()} from
     * inside this very call, before the per-request cache below has anything
     * to return — without this guard, that reentrant call repeats the same
     * query, triggers another auto-flush, and recurses until
     * {@link StackOverflowError}.
     */
    private static final ThreadLocal<Boolean> RESOLVING = ThreadLocal.withInitial(() -> false);

    @Override
    public Optional<Long> getCurrentAuditor() {
        if (RESOLVING.get()) {
            return Optional.empty();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }

        UUID keycloakId = UUID.fromString(jwt.getSubject());

        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            Object cached = attrs.getAttribute("auditorId:" + keycloakId, RequestAttributes.SCOPE_REQUEST);
            if (cached != null) {
                return Optional.of((Long) cached);
            }
        }

        RESOLVING.set(true);
        try {
            Optional<Long> resolved = userRepository.findByKeycloakId(keycloakId).map(AppUser::getId);

            if (attrs != null) {
                resolved.ifPresent(id ->
                        attrs.setAttribute("auditorId:" + keycloakId, id, RequestAttributes.SCOPE_REQUEST));
            }
            return resolved;
        } finally {
            RESOLVING.remove();
        }
    }
}