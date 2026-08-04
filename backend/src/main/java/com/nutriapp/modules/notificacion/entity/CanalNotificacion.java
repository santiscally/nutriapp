package com.nutriapp.modules.notificacion.entity;

/**
 * Canal por el que se envía una notificación de receta al paciente.
 *
 * <p>Quedó sólo EMAIL: WhatsApp dejó de ser un canal automático (2.4, decisión del 2026-07-28) y
 * pasó a ser un link {@code wa.me} que la nutricionista abre para mandar el mensaje ella misma
 * (ver {@code WaMeLinkBuilder}). Lo que no se envía solo, no se encola.
 */
public enum CanalNotificacion {
    EMAIL
}
