package com.bonosapp.modules.admin.service;

import com.bonosapp.common.auth.AuthUtils;
import com.bonosapp.common.error.ConflictException;
import com.bonosapp.common.error.NotFoundException;
import com.bonosapp.integrations.keycloak.KeycloakAdminClient;
import com.bonosapp.modules.admin.dto.NutricionistaResponse;
import com.bonosapp.modules.notificacion.service.NotificacionService;
import com.bonosapp.modules.nutricionista.service.ParametrosNegocioService;
import com.bonosapp.modules.nutricionista.entity.EstadoValidacion;
import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import com.bonosapp.modules.nutricionista.entity.TipoArchivo;
import com.bonosapp.modules.nutricionista.service.ArchivoService;
import com.bonosapp.modules.nutricionista.repository.NutricionistaRepository;
import com.bonosapp.modules.paciente.repository.PacienteRepository;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bandeja de validación de nutricionistas (ADMIN). Aprobar habilita el usuario Keycloak
 * (recién ahí puede loguearse); rechazar lo deja deshabilitado con el motivo registrado.
 * Ambas transiciones sólo aplican desde PENDIENTE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNutricionistaService {

    private final NutricionistaRepository repository;
    private final KeycloakAdminClient keycloak;
    private final ParametrosNegocioService parametros;
    private final ArchivoService archivoService;
    private final RecetaRepository recetaRepository;
    private final PacienteRepository pacienteRepository;
    private final NotificacionService notificaciones;

    @Transactional(readOnly = true)
    public Page<NutricionistaResponse> listar(EstadoValidacion estado, String q, Pageable pageable) {
        return repository.search(estado, q, pageable).map(this::toResponse);
    }

    @Transactional
    public NutricionistaResponse aprobar(UUID id) {
        Nutricionista n = getPendiente(id);
        habilitarEnKeycloak(n, true);
        n.setActivo(true);
        n.setEstadoValidacion(EstadoValidacion.APROBADA);
        n.setValidadoAt(Instant.now());
        n.setValidadoPor(validadorActual());
        n.setNotasValidacion(null);
        log.info("Nutricionista {} APROBADA por {}", n.getEmail(), n.getValidadoPor());
        Nutricionista aprobada = repository.save(n);
        notificaciones.encolarAprobacion(aprobada);
        return toResponse(aprobada);
    }

    @Transactional
    public NutricionistaResponse rechazar(UUID id, String motivo) {
        Nutricionista n = getPendiente(id);
        // El usuario Keycloak ya nace deshabilitado; nos aseguramos de que siga así.
        habilitarEnKeycloak(n, false);
        n.setActivo(false);
        n.setEstadoValidacion(EstadoValidacion.RECHAZADA);
        n.setValidadoAt(Instant.now());
        n.setValidadoPor(validadorActual());
        n.setNotasValidacion(motivo);
        log.info("Nutricionista {} RECHAZADA por {}", n.getEmail(), n.getValidadoPor());
        Nutricionista rechazada = repository.save(n);
        notificaciones.encolarRechazo(rechazada, motivo);
        return toResponse(rechazada);
    }

    private Nutricionista getPendiente(UUID id) {
        Nutricionista n = repository.findById(id)
                .filter(x -> !x.isDeleted())
                .orElseThrow(() -> new NotFoundException("Nutricionista no encontrado"));
        if (n.getEstadoValidacion() != EstadoValidacion.PENDIENTE) {
            throw new ConflictException("La solicitud ya está "
                    + n.getEstadoValidacion().name().toLowerCase() + ", no se puede cambiar");
        }
        return n;
    }

    private void habilitarEnKeycloak(Nutricionista n, boolean enabled) {
        if (n.getKeycloakUserId() == null) {
            log.warn("Nutricionista {} sin keycloakUserId — no se puede {} en Keycloak",
                    n.getEmail(), enabled ? "habilitar" : "deshabilitar");
            return;
        }
        keycloak.setEnabled(n.getKeycloakUserId(), enabled);
    }

    private String validadorActual() {
        return AuthUtils.currentUserId().map(UUID::toString).orElse(null);
    }

    /**
     * Setea el % de descuento y de comisión de una nutricionista. Ambos obligatorios desde V011
     * (no hay global al que volver). Se puede llamar sobre cualquier estado: el admin los fija al
     * aprobar (C-09) y los edita después.
     */
    @Transactional
    public NutricionistaResponse actualizarParametros(UUID id, BigDecimal descuentoPct, BigDecimal comisionPct) {
        Nutricionista n = getVigente(id);
        n.setDescuentoPct(descuentoPct);
        n.setComisionPct(comisionPct);
        log.info("Parámetros de {} actualizados: descuento={} comisión={}",
                n.getEmail(), descuentoPct, comisionPct);
        return toResponse(repository.save(n));
    }

    /**
     * Da de baja el acceso sin borrar nada: la nutricionista deja de poder entrar pero conserva su
     * perfil, sus pacientes y sus recetas, y el admin puede revertirlo. Es lo que hay que usar para
     * alguien que dejó de trabajar — a diferencia de rechazar, no toca el historial de validación.
     */
    @Transactional
    public NutricionistaResponse desactivar(UUID id) {
        Nutricionista n = getVigente(id);
        habilitarEnKeycloak(n, false);
        n.setActivo(false);
        log.info("Nutricionista {} DESACTIVADA por {}", n.getEmail(), validadorActual());
        return toResponse(repository.save(n));
    }

    /** Devuelve el acceso. Sólo tiene sentido sobre una solicitud ya aprobada. */
    @Transactional
    public NutricionistaResponse reactivar(UUID id) {
        Nutricionista n = getVigente(id);
        if (n.getEstadoValidacion() != EstadoValidacion.APROBADA) {
            throw new ConflictException("Sólo se puede reactivar una nutricionista aprobada (esta está "
                    + n.getEstadoValidacion().name().toLowerCase() + ")");
        }
        habilitarEnKeycloak(n, true);
        n.setActivo(true);
        log.info("Nutricionista {} REACTIVADA por {}", n.getEmail(), validadorActual());
        return toResponse(repository.save(n));
    }

    /**
     * Baja definitiva: borra el usuario de Keycloak y la fila local. Es para limpiar altas
     * equivocadas o de prueba, no para dar de baja gente que trabajó.
     *
     * <p>Por eso se niega si tiene recetas: esas recetas alimentan los cierres y las liquidaciones,
     * y borrar a su autora dejaría plata contabilizada sin nadie a quien atribuírsela. En ese caso
     * la respuesta es desactivar. Los pacientes sí se borran con ella (son suyos y de nadie más).
     */
    @Transactional
    public void eliminar(UUID id) {
        Nutricionista n = getVigente(id);
        long recetas = recetaRepository.countByNutricionistaId(n.getId());
        if (recetas > 0) {
            throw new ConflictException("No se puede borrar: tiene " + recetas
                    + (recetas == 1 ? " bono profesional emitido" : " bonos profesionales emitidos")
                    + " que forman parte de los cierres. Desactivala para quitarle el acceso.");
        }
        if (n.getKeycloakUserId() != null) {
            // Primero Keycloak: si falla, la fila local queda y se puede reintentar. Al revés
            // quedaría un usuario capaz de loguearse sin perfil.
            keycloak.deleteUserOrFail(n.getKeycloakUserId());
        }
        pacienteRepository.deleteByNutricionistaId(n.getId());
        archivoService.borrarTodos(n.getId());
        repository.delete(n);
        log.info("Nutricionista {} ELIMINADA por {}", n.getEmail(), validadorActual());
    }

    /**
     * Le pone una contraseña nueva. Es la única vía de recuperación que existe: no hay flujo de
     * "olvidé mi contraseña" por email, así que sin esto quien se equivoca al registrarse queda
     * afuera para siempre. Limpia además el contador de intentos fallidos, porque si llegó acá es
     * probable que haya reintentado hasta frenarse contra la protección de fuerza bruta.
     */
    @Transactional(readOnly = true)
    public void resetearPassword(UUID id, String password) {
        Nutricionista n = getVigente(id);
        if (n.getKeycloakUserId() == null) {
            throw new ConflictException("Esta nutricionista todavía no tiene usuario en el sistema de acceso");
        }
        keycloak.resetPassword(n.getKeycloakUserId(), password);
        keycloak.limpiarIntentosFallidos(n.getKeycloakUserId());
        log.info("Contraseña de {} reseteada por {}", n.getEmail(), validadorActual());
    }

    private Nutricionista getVigente(UUID id) {
        return repository.findById(id)
                .filter(x -> !x.isDeleted())
                .orElseThrow(() -> new NotFoundException("Nutricionista no encontrado"));
    }

    private NutricionistaResponse toResponse(Nutricionista n) {
        return new NutricionistaResponse(
                n.getId(),
                n.getNombre(),
                n.getApellido(),
                n.getEmail(),
                n.getTelefono(),
                n.getMatricula(),
                n.getDni(),
                n.getCuit(),
                n.getCondicionFiscal(),
                archivoService.buscar(n.getId(), TipoArchivo.MATRICULA).isPresent(),
                n.getEstadoValidacion().name(),
                n.getValidadoAt(),
                n.getNotasValidacion(),
                n.getCreatedAt(),
                parametros.descuentoPctDe(n),
                parametros.comisionPctDe(n),
                n.isActivo());
    }
}
