package com.bonosapp.modules.notificacion.entity;

/**
 * Estado de una notificación en la cola.
 * QUEUED → pendiente de envío (o el proveedor está en stub/caído: reintenta el dispatcher).
 * SENT   → entregada al proveedor con éxito.
 * FAILED → agotó los reintentos con un error no transitorio.
 */
public enum EstadoNotificacion {
    QUEUED,
    SENT,
    FAILED
}
