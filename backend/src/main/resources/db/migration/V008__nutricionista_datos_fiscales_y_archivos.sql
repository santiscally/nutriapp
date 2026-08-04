-- C-08 + C-17 (post-demo 2026-07-31): datos fiscales del registro y archivos adjuntos.
--
-- Campos nuevos (call 40:05–42:34):
--   dni             — Gon: la mejor forma de detectar que una persona no esté dos veces.
--   cuit            — para liquidarle la factura. Gon corrigió a Leo: NO se pide CUIL, ese es
--                     para relación de dependencia. Un solo campo, y la condición fiscal aparte.
--   condicion_fiscal— desplegable (Responsable Inscripto, Monotributo, Exento…).
-- El nombre del campo `matricula` queda como está: Leo se llevó para confirmar si corresponde
-- llamarla "matrícula nacional" (42:34). Renombrarlo después es una migración de una línea.

ALTER TABLE nutricionistas ADD COLUMN dni VARCHAR(20);
ALTER TABLE nutricionistas ADD COLUMN cuit VARCHAR(13);
ALTER TABLE nutricionistas ADD COLUMN condicion_fiscal VARCHAR(40);

-- Un DNI no puede estar dos veces entre las nutricionistas vivas (soft delete: parcial).
CREATE UNIQUE INDEX ux_nutricionistas_dni ON nutricionistas(dni) WHERE dni IS NOT NULL AND deleted_at IS NULL;

-- Archivos: matrícula/título que sube al registrarse (C-08) y foto de perfil (C-17).
--
-- Los bytes van en la DB y no en un volumen a propósito: son pocos y chicos (una nutricionista
-- sube un PDF y una foto), y así entran solos en el backup que ya existe (scripts/backup-db.sh).
-- Un volumen aparte sería una segunda cosa que acordarse de respaldar.
CREATE TABLE nutricionista_archivos (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nutricionista_id    UUID NOT NULL REFERENCES nutricionistas(id) ON DELETE CASCADE,
    tipo                VARCHAR(20) NOT NULL CHECK (tipo IN ('MATRICULA','FOTO_PERFIL')),
    nombre_original     VARCHAR(255),
    content_type        VARCHAR(100) NOT NULL,
    tamano_bytes        INTEGER NOT NULL,
    contenido           BYTEA NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          UUID,
    deleted_at          TIMESTAMPTZ
);

-- Un archivo vigente por tipo y por nutricionista: subir de nuevo reemplaza al anterior.
CREATE UNIQUE INDEX ux_nutricionista_archivo_tipo
    ON nutricionista_archivos(nutricionista_id, tipo) WHERE deleted_at IS NULL;
