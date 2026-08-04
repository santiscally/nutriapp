-- V010 — WhatsApp deja de ser un canal de la cola de notificaciones (tarea 2.4).
--
-- El envío por WhatsApp pasó a ser un link wa.me que abre la nutricionista desde su propio
-- teléfono (decisión 2026-07-28: sin WABA, sin número de empresa y sin template aprobado por
-- Meta). Un link no se encola ni se reintenta, así que el único canal automático es el email.
--
-- Las filas WHATSAPP se borran FÍSICAMENTE, que es la excepción a la regla de soft-delete del
-- proyecto: el enum CanalNotificacion ya no tiene ese valor, y una fila soft-deleted seguiría
-- reventando cualquier lectura que no filtre por deleted_at (findById, findAll). Es una cola
-- operativa, no un registro de negocio: lo que se pierde son mensajes que nunca se enviaron
-- (en stub jamás salió uno) y su acuse. La receta, que es el dato real, no se toca.

DELETE FROM notificaciones WHERE canal = 'WHATSAPP';

ALTER TABLE notificaciones DROP CONSTRAINT IF EXISTS notificaciones_canal_check;
ALTER TABLE notificaciones ADD CONSTRAINT notificaciones_canal_check CHECK (canal IN ('EMAIL'));
