package com.nutriapp.common.audit;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resuelve el usuario autenticado para poblar {@code createdBy}.
 * Toma el {@code sub} del JWT de Keycloak y lo parsea como UUID.
 * Si no hay autenticación (seeders, schedulers), devuelve {@code empty}.
 */
@Component("auditorAware")
public class JwtAuditorAware implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        if (!(auth.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        String sub = jwt.getSubject();
        if (sub == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(sub));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
