package com.bonosapp.modules.registro.service;

import com.bonosapp.integrations.keycloak.KeycloakAdminClient;
import com.bonosapp.modules.nutricionista.entity.EstadoValidacion;
import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import com.bonosapp.modules.nutricionista.repository.NutricionistaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S-09 — recupero de contraseña por mail. El link de un solo uso lo emite y lo valida Keycloak
 * ({@code execute-actions-email} con {@code UPDATE_PASSWORD}), así que acá no hay tokens propios
 * que guardar, expirar ni invalidar: ese es justo el código que conviene no escribir.
 *
 * <p>Reemplaza al blanqueo manual del admin, que hasta ahora era la única vía de recuperación.
 * El admin lo conserva para los casos en que la persona tampoco tiene acceso a su casilla.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecuperoPasswordService {

    /** Media hora: suficiente para ir al mail, corto para un link que cambia una credencial. */
    private static final int LINK_VIGENCIA_SEGUNDOS = 1800;

    private final NutricionistaRepository repository;
    private final KeycloakAdminClient keycloak;

    /**
     * Nunca falla hacia afuera: el controller responde 204 exista o no la cuenta. Todo lo que
     * distinga un caso del otro —un error, una demora— le dice al que prueba mails si acertó.
     */
    @Transactional(readOnly = true)
    public void enviarMail(String email) {
        Nutricionista n = repository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElse(null);
        if (n == null) {
            log.info("Recupero de contraseña pedido para un email que no está registrado");
            return;
        }
        if (n.getEstadoValidacion() != EstadoValidacion.APROBADA || !n.isActivo()) {
            // Sin acceso a la cuenta, cambiar la contraseña no la habilita: el mail sólo confunde.
            log.info("Recupero de contraseña de {} ignorado: la cuenta no está activa", n.getEmail());
            return;
        }
        String userId = n.getKeycloakUserId() != null
                ? n.getKeycloakUserId()
                : keycloak.buscarUserIdPorEmail(n.getEmail()).orElse(null);
        if (userId == null) {
            log.warn("Recupero de contraseña de {}: no tiene usuario en Keycloak", n.getEmail());
            return;
        }
        try {
            keycloak.enviarMailDeReseteo(userId, LINK_VIGENCIA_SEGUNDOS);
            log.info("Mail de recupero de contraseña enviado a {}", n.getEmail());
        } catch (RuntimeException ex) {
            // Típico: el realm quedó sin SMTP. Se registra y no se propaga, para no filtrar por el
            // código de error si la cuenta existe.
            log.error("No se pudo enviar el mail de recupero a {}: {}", n.getEmail(), ex.getMessage());
        }
    }
}
