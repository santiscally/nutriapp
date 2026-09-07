package com.bonosapp.modules.notificacion.entity;

/** Evento que originó la notificación. Define destinatario y template. */
public enum TipoNotificacion {
    EMISION_RECETA,
    REGISTRO_RECIBIDO,
    REGISTRO_APROBADO,
    REGISTRO_RECHAZADO,
    ADMIN_NUEVA_SOLICITUD
}
