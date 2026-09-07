-- V013 — la cola de notificaciones deja de ser exclusiva de recetas: entra el alta pública.

ALTER TABLE notificaciones ADD COLUMN tipo VARCHAR(30);
UPDATE notificaciones SET tipo = 'EMISION_RECETA' WHERE tipo IS NULL;
ALTER TABLE notificaciones ALTER COLUMN tipo SET NOT NULL;
ALTER TABLE notificaciones ADD CONSTRAINT notificaciones_tipo_check
    CHECK (tipo IN ('EMISION_RECETA','REGISTRO_RECIBIDO','REGISTRO_APROBADO',
                    'REGISTRO_RECHAZADO','ADMIN_NUEVA_SOLICITUD'));

-- CASCADE: borrar una nutricionista es para altas equivocadas o de prueba (las que emitieron
-- recetas dan 409), y su acuse de registro es cola operativa, no historial de negocio.
ALTER TABLE notificaciones ADD COLUMN nutricionista_id UUID
    REFERENCES nutricionistas(id) ON DELETE CASCADE;

CREATE INDEX idx_notificaciones_nutricionista ON notificaciones(nutricionista_id, tipo);
