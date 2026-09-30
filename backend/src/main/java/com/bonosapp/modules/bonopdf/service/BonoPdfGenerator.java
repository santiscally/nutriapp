package com.bonosapp.modules.bonopdf.service;

import com.bonosapp.modules.receta.dto.RecetaResponse;

/**
 * Port de generación del PDF del bono profesional (F-20).
 *
 * <p>Existe como interfaz porque <b>el diseño del bono lo manda el cliente</b>: la plantilla llegó
 * el 2026-09-30 y la implementa {@link PlantillaBonoPdfGenerator}, que la usa de
 * fondo y escribe los datos encima. Si el cliente manda un diseño nuevo, se reemplaza el JPG (y se
 * vuelven a medir las posiciones); si algún día hace falta algo que un JPG de fondo no permita, se
 * escribe otra implementación de este port sin tocar el endpoint ni el mail.
 */
public interface BonoPdfGenerator {

    /**
     * PDF del bono listo para descargar o adjuntar.
     *
     * @param firmante quién emitió el bono, para el "Firmado electrónicamente por:" que la plantilla
     *                 deja abierto. Si viene null o vacío, el renglón queda sin completar.
     */
    byte[] generar(RecetaResponse receta, String linkCupon, String firmante);
}
