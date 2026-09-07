package com.bonosapp.modules.nutricionista.service;

import com.bonosapp.common.auth.AuthUtils;
import com.bonosapp.common.error.ConflictException;
import com.bonosapp.common.error.NotFoundException;
import com.bonosapp.modules.nutricionista.entity.EstadoValidacion;
import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import com.bonosapp.modules.nutricionista.repository.NutricionistaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NutricionistaService {

    private final NutricionistaRepository repository;

    /**
     * Nutricionista del JWT actual. Primero por keycloak_user_id; si no existe todavía
     * (usuario recién importado del realm), se linkea por email y se persiste el sub —
     * así el seed no necesita conocer los UUIDs que Keycloak genera al importar.
     */
    @Transactional
    public Nutricionista getCurrent() {
        return findCurrent().orElseThrow(() ->
                new NotFoundException("El usuario logueado no tiene perfil de nutricionista"));
    }

    /** Como {@link #getCurrent()} pero exige perfil APROBADO (guard para operar). */
    @Transactional
    public Nutricionista getCurrentAprobado() {
        Nutricionista n = getCurrent();
        if (n.getEstadoValidacion() != EstadoValidacion.APROBADA) {
            throw new ConflictException("El perfil de nutricionista está " +
                    n.getEstadoValidacion().name().toLowerCase() + " — requiere aprobación del administrador");
        }
        return n;
    }

    @Transactional
    public Optional<Nutricionista> findCurrent() {
        if (AuthUtils.currentJwt().isEmpty()) {
            return Optional.empty();
        }
        String sub = AuthUtils.currentJwt().map(jwt -> jwt.getSubject()).orElse(null);

        if (sub != null) {
            Optional<Nutricionista> bySub = repository.findByKeycloakUserIdAndDeletedAtIsNull(sub);
            if (bySub.isPresent()) {
                return bySub;
            }
        }

        // Lookup por email. Funciona aunque el token no traiga `sub` (robustez); si hay sub
        // y el perfil no está linkeado todavía, lo linkeamos de paso (primer login del seed).
        return AuthUtils.currentEmail()
                .flatMap(repository::findByEmailIgnoreCaseAndDeletedAtIsNull)
                .map(n -> {
                    if (sub != null && n.getKeycloakUserId() == null) {
                        n.setKeycloakUserId(sub);
                        log.info("Nutricionista {} linkeado a keycloak user {}", n.getEmail(), sub);
                        return repository.save(n);
                    }
                    return n;
                });
    }
}
