package com.nutriapp.modules.admin.service;

import com.nutriapp.common.auth.AuthUtils;
import com.nutriapp.common.error.ConflictException;
import com.nutriapp.common.error.NotFoundException;
import com.nutriapp.integrations.keycloak.KeycloakAdminClient;
import com.nutriapp.modules.admin.dto.NutricionistaResponse;
import com.nutriapp.modules.configuracion.service.ParametrosNegocioService;
import com.nutriapp.modules.nutricionista.entity.EstadoValidacion;
import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import com.nutriapp.modules.nutricionista.entity.TipoArchivo;
import com.nutriapp.modules.nutricionista.service.ArchivoService;
import com.nutriapp.modules.nutricionista.repository.NutricionistaRepository;
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

    @Transactional(readOnly = true)
    public Page<NutricionistaResponse> listar(EstadoValidacion estado, String q, Pageable pageable) {
        return repository.search(estado, q, pageable).map(this::toResponse);
    }

    @Transactional
    public NutricionistaResponse aprobar(UUID id) {
        Nutricionista n = getPendiente(id);
        habilitarEnKeycloak(n, true);
        n.setEstadoValidacion(EstadoValidacion.APROBADA);
        n.setValidadoAt(Instant.now());
        n.setValidadoPor(validadorActual());
        n.setNotasValidacion(null);
        log.info("Nutricionista {} APROBADA por {}", n.getEmail(), n.getValidadoPor());
        return toResponse(repository.save(n));
    }

    @Transactional
    public NutricionistaResponse rechazar(UUID id, String motivo) {
        Nutricionista n = getPendiente(id);
        // El usuario Keycloak ya nace deshabilitado; nos aseguramos de que siga así.
        habilitarEnKeycloak(n, false);
        n.setEstadoValidacion(EstadoValidacion.RECHAZADA);
        n.setValidadoAt(Instant.now());
        n.setValidadoPor(validadorActual());
        n.setNotasValidacion(motivo);
        log.info("Nutricionista {} RECHAZADA por {}", n.getEmail(), n.getValidadoPor());
        return toResponse(repository.save(n));
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
     * C-01 — setea (o limpia) el % de descuento y de comisión propios de una nutricionista.
     * {@code null} en un campo = vuelve al valor global. Se puede llamar sobre cualquier estado:
     * el admin fija los porcentajes al aprobar (C-09) y los edita después.
     */
    @Transactional
    public NutricionistaResponse actualizarParametros(UUID id, BigDecimal descuentoPct, BigDecimal comisionPct) {
        Nutricionista n = repository.findById(id)
                .filter(x -> !x.isDeleted())
                .orElseThrow(() -> new NotFoundException("Nutricionista no encontrado"));
        n.setDescuentoPct(descuentoPct);
        n.setComisionPct(comisionPct);
        log.info("Parámetros de {} actualizados: descuento={} comisión={} (null = global)",
                n.getEmail(), descuentoPct, comisionPct);
        return toResponse(repository.save(n));
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
                n.getDescuentoPct(),
                n.getComisionPct(),
                parametros.descuentoPctDe(n),
                parametros.comisionPctDe(n));
    }
}
