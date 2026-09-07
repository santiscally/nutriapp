package com.bonosapp.common.error;

/**
 * 422 — la petición está bien formada pero su contenido no se puede procesar: típicamente un archivo
 * que el usuario subió y no sirve (no es el Excel esperado, le faltan columnas, está dañado).
 *
 * <p>Distinto de {@link ConflictException} (409, choca con el estado actual del sistema) y de un 400
 * de validación de campos: acá el problema está adentro del payload y el mensaje tiene que decirle al
 * admin qué arreglar en el archivo.
 */
public class UnprocessableException extends RuntimeException {

    public UnprocessableException(String message) {
        super(message);
    }
}
