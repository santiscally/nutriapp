package com.bonosapp.common.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Helpers para leer la identidad y los permisos del usuario autenticado.
 */
public final class AuthUtils {

    private AuthUtils() {}

    /** UUID del {@code sub} del JWT, o vacío si no hay auth o no es parseable. */
    public static Optional<UUID> currentUserId() {
        return currentJwt()
                .map(Jwt::getSubject)
                .flatMap(sub -> {
                    try {
                        return Optional.of(UUID.fromString(sub));
                    } catch (IllegalArgumentException ex) {
                        return Optional.empty();
                    }
                });
    }

    public static Optional<Jwt> currentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return Optional.of(jwt);
    }

    /** Email del claim `email` del JWT (scope email del realm). */
    public static Optional<String> currentEmail() {
        return currentJwt().map(jwt -> jwt.getClaimAsString("email"));
    }

    /** Realm roles crudos del JWT (sin prefijo ROLE_). */
    public static List<String> currentRoles() {
        return currentJwt()
                .map(jwt -> {
                    Object realmAccess = jwt.getClaim("realm_access");
                    if (realmAccess instanceof Map<?, ?> realmMap
                            && realmMap.get("roles") instanceof List<?> roles) {
                        return roles.stream().map(Object::toString).toList();
                    }
                    return List.<String>of();
                })
                .orElse(List.of());
    }

    /** True si el usuario tiene el realm role (mapeado como {@code ROLE_<name>}). */
    public static boolean hasRole(String role) {
        return hasAuthority("ROLE_" + role);
    }

    /** True si el usuario tiene el permiso granular (client role del bonosapp-backend). */
    public static boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (authority.equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
