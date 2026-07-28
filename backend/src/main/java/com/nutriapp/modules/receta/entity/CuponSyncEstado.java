package com.nutriapp.modules.receta.entity;

public enum CuponSyncEstado {
    PENDIENTE,
    SINCRONIZADO,
    ERROR;

    /**
     * Mensaje humano de degradación del cupón para mostrar en la emisión (tarea 2.7).
     * {@code null} cuando el cupón sincronizó bien (no hay nada que avisar).
     */
    public String mensajeDegradacion() {
        return switch (this) {
            case PENDIENTE -> "El cupón quedó pendiente de registrarse en la tienda "
                    + "(TiendaNube no está disponible). Se reintenta automáticamente.";
            case ERROR -> "Hubo un error al registrar el cupón en la tienda. "
                    + "Se reintenta automáticamente.";
            case SINCRONIZADO -> null;
        };
    }
}
