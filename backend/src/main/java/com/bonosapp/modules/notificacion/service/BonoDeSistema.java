package com.bonosapp.modules.notificacion.service;

import com.bonosapp.modules.nutricionista.repository.NutricionistaRepository;
import com.bonosapp.modules.receta.dto.RecetaResponse;
import com.bonosapp.modules.receta.entity.Receta;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import com.bonosapp.modules.receta.service.RecetaService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve un bono <b>sin sesión</b>, para los procesos que corren en un scheduler: el dispatcher de
 * notificaciones no tiene usuario logueado y {@code RecetaService.get} exige que el bono sea del que
 * lo pide, así que desde ahí esa llamada falla siempre.
 *
 * <p><b>Deliberadamente no valida pertenencia</b>, y por eso nada de esto se expone por HTTP: quien
 * lo llama ya sabe a quién le corresponde el bono (la notificación se encoló para el paciente de esa
 * receta). Todo lo que atiende a una persona logueada sigue yendo por {@code RecetaService.get}.
 */
@Component
@RequiredArgsConstructor
public class BonoDeSistema {

    private final RecetaRepository recetas;
    private final RecetaService recetaService;
    private final NutricionistaRepository nutricionistas;

    @Transactional(readOnly = true)
    public Optional<Datos> buscar(UUID recetaId) {
        if (recetaId == null) {
            return Optional.empty();
        }
        return recetas.findByIdInAndDeletedAtIsNull(List.of(recetaId)).stream()
                .findFirst()
                .map(r -> new Datos(recetaService.toResponse(r), firmante(r)));
    }

    /** El profesional que emitió el bono: es quien lo firma. */
    private String firmante(Receta receta) {
        if (receta.getNutricionistaId() == null) {
            return null;
        }
        return nutricionistas.findById(receta.getNutricionistaId())
                .map(n -> (safe(n.getNombre()) + " " + safe(n.getApellido())).trim())
                .filter(s -> !s.isBlank())
                .orElse(null);
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    /** El bono y quién lo firma, que es lo que necesitan el mail y el PDF. */
    public record Datos(RecetaResponse receta, String firmante) {}
}
