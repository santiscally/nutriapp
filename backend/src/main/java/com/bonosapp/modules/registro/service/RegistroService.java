package com.bonosapp.modules.registro.service;

import com.bonosapp.common.error.ConflictException;
import com.bonosapp.common.error.UnprocessableException;
import com.bonosapp.integrations.keycloak.KeycloakAdminClient;
import com.bonosapp.modules.notificacion.service.NotificacionService;
import com.bonosapp.modules.nutricionista.NutricionistaProperties;
import com.bonosapp.modules.nutricionista.entity.EstadoValidacion;
import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import com.bonosapp.modules.nutricionista.entity.TipoArchivo;
import com.bonosapp.modules.nutricionista.service.ArchivoService;
import com.bonosapp.modules.nutricionista.repository.NutricionistaRepository;
import com.bonosapp.modules.profesion.service.ProfesionService;
import com.bonosapp.modules.registro.dto.RegistroRequest;
import com.bonosapp.modules.registro.dto.RegistroResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta pública de nutricionista. Crea el usuario en Keycloak DESHABILITADO (se habilita al
 * aprobar) y persiste el perfil en estado PENDIENTE. El usuario no puede loguearse hasta que
 * un ADMIN lo apruebe (regla de negocio clave de CLAUDE.md).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistroService {

    private final NutricionistaRepository repository;
    private final KeycloakAdminClient keycloak;
    private final ArchivoService archivoService;
    private final NutricionistaProperties props;
    private final NotificacionService notificaciones;
    private final ProfesionService profesiones;
    private final com.bonosapp.modules.registro.RegistroProperties registroProps;

    /** S-10: 24 h para validar el mail. El alta ya quedó hecha; esto no puede voltearla. */
    private static final int VERIFICACION_VIGENCIA_SEGUNDOS = 86400;

    @Transactional
    public RegistroResponse registrar(RegistroRequest req, MultipartFile matricula) {
        // Mensaje idéntico al del backstop de Keycloak: no diferenciar filtra existencia (enumeración).
        repository.findByEmailIgnoreCaseAndDeletedAtIsNull(req.email()).ifPresent(n -> {
            throw new ConflictException("Ese email ya está registrado");
        });

        // C-08: el DNI es justamente para detectar a la misma persona dos veces (Gon, 40:40).
        repository.findByDniAndDeletedAtIsNull(req.dni()).ifPresent(n -> {
            throw new ConflictException("Ese DNI ya está registrado");
        });

        // Antes de crear nada en Keycloak: un alta inválida no debe dejar un usuario huérfano.
        exigirDatosProfesionales(req);
        String profesion = profesiones.validar(req.profesion());

        // Keycloak es la fuente de verdad de identidad: crea el usuario deshabilitado + rol.
        // (También valida unicidad de email a nivel realm como backstop de la carrera.)
        String keycloakUserId = keycloak.registrarNutricionista(
                req.email(), req.nombre(), req.apellido(), req.password());

        try {
            Nutricionista n = new Nutricionista();
            n.setKeycloakUserId(keycloakUserId);
            n.setNombre(req.nombre());
            n.setApellido(req.apellido());
            n.setEmail(req.email());
            n.setTelefono(req.telefono());
            n.setMatricula(req.matricula());
            n.setJurisdiccionMatricula(req.jurisdiccion());
            n.setProfesion(profesion);
            n.setDni(req.dni());
            n.setCuit(req.cuitNormalizado());
            n.setCondicionFiscal(req.condicionFiscal());
            n.setEstadoValidacion(EstadoValidacion.PENDIENTE);
            // V011: los % son obligatorios y propios de cada una. Acá van los de arranque; el admin
            // los ajusta al aprobarla (que es cuando recién puede emitir algo).
            n.setDescuentoPct(props.descuentoPctDefault());
            n.setComisionPct(props.comisionPctDefault());
            Nutricionista saved = repository.save(n);
            // El adjunto es parte del alta: si falla, falla el registro entero y se compensa
            // Keycloak igual que con cualquier otro error — no queremos una solicitud sin respaldo
            // que el admin no pueda validar.
            if (matricula != null && !matricula.isEmpty()) {
                archivoService.guardar(saved.getId(), TipoArchivo.MATRICULA, matricula);
            }
            // Misma tx que el alta: o queda la solicitud con sus avisos encolados, o no queda nada.
            // El envío es asíncrono (dispatcher), así que un proveedor caído no frena el registro.
            notificaciones.encolarRegistro(saved);
            enviarVerificacion(keycloakUserId, saved.getEmail());
            log.info("Registro de nutricionista {} (keycloak {}) — PENDIENTE de aprobación",
                    saved.getEmail(), keycloakUserId);
            return new RegistroResponse(saved.getId(), saved.getEstadoValidacion().name());
        } catch (RuntimeException ex) {
            // Compensación: si el perfil local no se persistió, borrar el usuario Keycloak recién
            // creado para no dejar un huérfano deshabilitado que bloquee el email en reintentos.
            log.error("Falló el alta local de {} tras crear el usuario Keycloak {}; compensando",
                    req.email(), keycloakUserId, ex);
            keycloak.deleteUser(keycloakUserId);
            throw ex;
        }
    }

    /**
     * S-11 — con el interruptor prendido, profesión, jurisdicción y una matrícula sólo de dígitos son
     * obligatorias. Junta todos los faltantes en un solo mensaje: rechazar de a uno obliga a mandar el
     * formulario tres veces para enterarse de tres errores.
     */
    private void exigirDatosProfesionales(RegistroRequest req) {
        if (!registroProps.exigirDatosProfesionales()) {
            return;
        }
        java.util.List<String> faltan = new java.util.ArrayList<>();
        if (req.profesion() == null || req.profesion().isBlank()) {
            faltan.add("la profesión");
        }
        if (req.jurisdiccion() == null || req.jurisdiccion().isBlank()) {
            faltan.add("la jurisdicción de la matrícula");
        }
        if (req.matricula() != null && !req.matricula().trim().matches("\\d+")) {
            faltan.add("el número de matrícula, sólo con dígitos");
        }
        if (!faltan.isEmpty()) {
            throw new UnprocessableException("Falta completar " + String.join(", ", faltan) + ".");
        }
    }

    /**
     * S-10 — el mail de "validá tu mail". Si falla (realm sin SMTP, proveedor caído) el registro
     * igual queda hecho: la solicitud tiene que llegarle al admin aunque el mail no salga, y para
     * eso está el reenvío.
     */
    private void enviarVerificacion(String keycloakUserId, String email) {
        try {
            keycloak.enviarMailDeVerificacion(keycloakUserId, VERIFICACION_VIGENCIA_SEGUNDOS);
        } catch (RuntimeException ex) {
            log.error("No se pudo enviar el mail de verificación a {}: {}", email, ex.getMessage());
        }
    }

    /**
     * Reenvía la verificación. Como el recupero de contraseña, no dice si la cuenta existe: el
     * controller responde 204 siempre.
     */
    @Transactional(readOnly = true)
    public void reenviarVerificacion(String email) {
        repository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).ifPresentOrElse(n -> {
            if (n.getKeycloakUserId() == null) {
                log.warn("Reenvío de verificación de {}: no tiene usuario en Keycloak", n.getEmail());
                return;
            }
            enviarVerificacion(n.getKeycloakUserId(), n.getEmail());
        }, () -> log.info("Reenvío de verificación pedido para un email que no está registrado"));
    }
}
