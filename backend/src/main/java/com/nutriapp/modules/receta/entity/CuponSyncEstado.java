package com.nutriapp.modules.receta.entity;

public enum CuponSyncEstado {
    PENDIENTE,
    SINCRONIZADO,
    ERROR;

    /** Marca del error cuando el cupón no salió porque un producto no está mapeado a la tienda. */
    public static final String SIN_MAPEO = "Sin mapeo a TiendaNube";

    /**
     * Mensaje humano de degradación del cupón para mostrar en la emisión (tarea 2.7).
     * {@code null} cuando el cupón sincronizó bien (no hay nada que avisar).
     *
     * <p>Distingue la caída de la integración —transitoria, se reintenta sola— del producto que no
     * existe en la tienda, que no se arregla solo: hasta que el admin no corra el mapeo, el reintento
     * va a fallar siempre. Decir "se reintenta automáticamente" ahí sería mentirle a la nutricionista.
     */
    public String mensajeDegradacion(String error) {
        if (error != null && error.startsWith(SIN_MAPEO)) {
            return "El cupón no se pudo crear porque el producto todavía no está publicado en la "
                    + "tienda online. Avisale al administrador: se registra solo en cuanto lo habilite.";
        }
        return switch (this) {
            case PENDIENTE -> "El cupón quedó pendiente de registrarse en la tienda "
                    + "(TiendaNube no está disponible). Se reintenta automáticamente.";
            case ERROR -> "Hubo un error al registrar el cupón en la tienda. "
                    + "Se reintenta automáticamente.";
            case SINCRONIZADO -> null;
        };
    }
}
