package com.bonosapp.modules.bonopdf.service;

import com.bonosapp.common.error.NotFoundException;
import com.bonosapp.modules.notificacion.service.BonoContenido;
import com.bonosapp.modules.nutricionista.repository.NutricionistaRepository;
import com.bonosapp.modules.receta.dto.RecetaResponse;
import com.bonosapp.modules.receta.entity.Receta;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import com.bonosapp.modules.receta.service.RecetaService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma el PDF de un bono ya emitido: para descargarlo desde la app (F-21 → F-15) y para adjuntarlo
 * al mail del paciente (F-20).
 *
 * <p><b>Dos entradas, y no es duplicación:</b> {@link #generar(UUID)} atiende a una persona logueada
 * y resuelve el bono por {@link RecetaService#get(UUID)}, que exige que el bono sea suyo (404 si no).
 * {@link #generarDeSistema(UUID)} lo atiende el dispatcher de notificaciones, que corre en un
 * scheduler <b>sin usuario</b>: ahí no hay dueño contra quien validar y esa misma llamada fallaría.
 * La pertenencia ya está garantizada antes: la notificación se encoló para el paciente de ese bono.
 */
@Service
@RequiredArgsConstructor
public class BonoPdfService {

    private final RecetaService recetas;
    private final RecetaRepository recetaRepository;
    private final NutricionistaRepository nutricionistas;
    private final BonoPdfGenerator generator;
    private final BonoContenido contenido;

    /** Descarga pedida por una persona logueada: valida que el bono sea suyo. */
    @Transactional(readOnly = true)
    public Bono generar(UUID recetaId) {
        return armar(recetas.get(recetaId), firmante(recetaId));
    }

    /**
     * La misma salida, para el scheduler que manda los mails. No valida dueño porque no hay sesión;
     * por eso <b>no se expone por HTTP</b>: quien la llama ya resolvió a quién le corresponde.
     */
    @Transactional(readOnly = true)
    public Bono generarDeSistema(UUID recetaId) {
        Receta receta = recetaRepository.findByIdInAndDeletedAtIsNull(List.of(recetaId)).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Bono no encontrado"));
        return armar(recetas.toResponse(receta), nombre(receta.getNutricionistaId()));
    }

    private Bono armar(RecetaResponse receta, String firmante) {
        byte[] pdf = generator.generar(receta, contenido.linkCupon(receta.codigo()), firmante);
        return new Bono("bono-" + receta.codigo() + ".pdf", pdf);
    }

    /** El profesional que emitió el bono, para la firma del PDF. */
    private String firmante(UUID recetaId) {
        return recetaRepository.findByIdInAndDeletedAtIsNull(List.of(recetaId)).stream()
                .findFirst()
                .map(r -> nombre(r.getNutricionistaId()))
                .orElse(null);
    }

    private String nombre(UUID nutricionistaId) {
        if (nutricionistaId == null) {
            return null;
        }
        return nutricionistas.findById(nutricionistaId)
                .map(n -> (safe(n.getNombre()) + " " + safe(n.getApellido())).trim())
                .filter(s -> !s.isBlank())
                .orElse(null);
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    /** PDF listo para servir: nombre de archivo sugerido + bytes. */
    public record Bono(String nombreArchivo, byte[] contenido) {}
}
