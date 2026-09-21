package com.bonosapp.modules.bonopdf.service;

import com.bonosapp.modules.receta.dto.RecetaResponse;

/**
 * Port de generación del PDF del bono profesional (F-20).
 *
 * <p>Existe como interfaz porque <b>el diseño del bono lo manda el cliente</b> y todavía no llegó:
 * hoy lo implementa {@link PdfSimpleBonoGenerator}, que arma un PDF sobrio sin dependencias
 * nuevas. Cuando llegue la plantilla, se escribe otra implementación (probablemente con una
 * librería de PDF de verdad, lo que pide tocar el {@code pom.xml} — zona de Santi) y se cambia
 * el bean, sin tocar el endpoint ni el mail.
 */
public interface BonoPdfGenerator {

    /** PDF del bono listo para descargar o adjuntar. */
    byte[] generar(RecetaResponse receta, String linkCupon);
}
