package com.nutriapp.modules.registro.service;

import com.nutriapp.common.error.ConflictException;
import com.nutriapp.integrations.keycloak.KeycloakAdminClient;
import com.nutriapp.modules.nutricionista.NutricionistaProperties;
import com.nutriapp.modules.nutricionista.entity.EstadoValidacion;
import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import com.nutriapp.modules.nutricionista.entity.TipoArchivo;
import com.nutriapp.modules.nutricionista.service.ArchivoService;
import com.nutriapp.modules.nutricionista.repository.NutricionistaRepository;
import com.nutriapp.modules.registro.dto.RegistroRequest;
import com.nutriapp.modules.registro.dto.RegistroResponse;
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
}
