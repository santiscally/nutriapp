package com.bonosapp.modules.bonopdf.service;

import com.bonosapp.common.error.NotFoundException;
import com.bonosapp.modules.notificacion.service.BonoContenido;
import com.bonosapp.modules.notificacion.service.BonoDeSistema;
import com.bonosapp.modules.receta.dto.RecetaResponse;
import com.bonosapp.modules.receta.service.RecetaService;
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
    private final BonoDeSistema deSistema;
    private final BonoPdfGenerator generator;
    private final BonoContenido contenido;

    /** Descarga pedida por una persona logueada: valida que el bono sea suyo. */
    @Transactional(readOnly = true)
    public Bono generar(UUID recetaId) {
        String firmante = deSistema.buscar(recetaId).map(BonoDeSistema.Datos::firmante).orElse(null);
        return armar(recetas.get(recetaId), firmante);
    }

    /**
     * La misma salida, para el scheduler que manda los mails. No valida dueño porque no hay sesión;
     * por eso <b>no se expone por HTTP</b>: quien la llama ya resolvió a quién le corresponde.
     */
    @Transactional(readOnly = true)
    public Bono generarDeSistema(UUID recetaId) {
        BonoDeSistema.Datos datos = deSistema.buscar(recetaId)
                .orElseThrow(() -> new NotFoundException("Bono no encontrado"));
        return armar(datos.receta(), datos.firmante());
    }

    private Bono armar(RecetaResponse receta, String firmante) {
        byte[] pdf = generator.generar(receta, contenido.linkCupon(receta.codigo()), firmante);
        return new Bono("bono-" + receta.codigo() + ".pdf", pdf);
    }

    /** PDF listo para servir: nombre de archivo sugerido + bytes. */
    public record Bono(String nombreArchivo, byte[] contenido) {}
}
