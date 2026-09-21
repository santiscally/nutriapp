package com.bonosapp.modules.bonopdf.service;

import com.bonosapp.modules.notificacion.service.BonoContenido;
import com.bonosapp.modules.receta.dto.RecetaResponse;
import com.bonosapp.modules.receta.service.RecetaService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma el PDF de un bono ya emitido, para descargarlo desde la app (F-21 → F-15) y —cuando el
 * {@code MailSender} sepa adjuntar— para mandarlo por mail (F-20).
 *
 * <p>La receta se resuelve por {@link RecetaService#get(UUID)}: ese método ya valida que el bono
 * sea de quien lo pide (404 si no), así que la autorización no se reimplementa acá.
 */
@Service
@RequiredArgsConstructor
public class BonoPdfService {

    private final RecetaService recetas;
    private final BonoPdfGenerator generator;
    private final BonoContenido contenido;

    @Transactional(readOnly = true)
    public Bono generar(UUID recetaId) {
        RecetaResponse receta = recetas.get(recetaId);
        byte[] pdf = generator.generar(receta, contenido.linkCupon(receta.codigo()));
        return new Bono("bono-" + receta.codigo() + ".pdf", pdf);
    }

    /** PDF listo para servir: nombre de archivo sugerido + bytes. */
    public record Bono(String nombreArchivo, byte[] contenido) {}
}
