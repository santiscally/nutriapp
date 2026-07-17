-- Núcleo del modelo: nutricionistas, pacientes, productos, recetas, notificaciones, webhooks.
-- Convenciones: PK UUID, audit columns en todas las tablas, soft delete (deleted_at), enums como VARCHAR+CHECK.

CREATE TABLE nutricionistas (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id    VARCHAR(64) UNIQUE,
    nombre              VARCHAR(120) NOT NULL,
    apellido            VARCHAR(120) NOT NULL,
    email               VARCHAR(255) NOT NULL UNIQUE,
    telefono            VARCHAR(40),
    matricula           VARCHAR(60),
    estado_validacion   VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE'
                        CHECK (estado_validacion IN ('PENDIENTE','APROBADA','RECHAZADA')),
    validado_at         TIMESTAMPTZ,
    validado_por        VARCHAR(64),
    notas_validacion    TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          UUID,
    deleted_at          TIMESTAMPTZ
);
CREATE TRIGGER trg_nutricionistas_updated BEFORE UPDATE ON nutricionistas
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TABLE pacientes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nutricionista_id    UUID NOT NULL REFERENCES nutricionistas(id),
    nombre              VARCHAR(120) NOT NULL,
    apellido            VARCHAR(120) NOT NULL,
    email               VARCHAR(255) NOT NULL,
    whatsapp            VARCHAR(40)  NOT NULL,
    fecha_nacimiento    DATE,
    notas               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          UUID,
    deleted_at          TIMESTAMPTZ,
    UNIQUE (nutricionista_id, email)
);
CREATE INDEX idx_pacientes_nutricionista ON pacientes(nutricionista_id);
CREATE TRIGGER trg_pacientes_updated BEFORE UPDATE ON pacientes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TABLE productos (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    origen                  VARCHAR(20) NOT NULL DEFAULT 'SEED'
                            CHECK (origen IN ('SEED','TIENDANUBE','CONTABILIUM')),
    tiendanube_product_id   BIGINT,
    tiendanube_variant_id   BIGINT,
    contabilium_id          BIGINT,
    sku                     VARCHAR(80),
    nombre                  VARCHAR(255) NOT NULL,
    descripcion             TEXT,
    precio                  NUMERIC(12,2) NOT NULL,
    stock                   INTEGER,
    imagen_url              VARCHAR(500),
    publicado               BOOLEAN NOT NULL DEFAULT TRUE,
    marca                   VARCHAR(120),
    laboratorio             VARCHAR(120),
    principio_activo        VARCHAR(255),
    presentacion            VARCHAR(120),
    last_synced_at          TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ,
    created_by              UUID,
    deleted_at              TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_productos_tn_variant ON productos(tiendanube_variant_id)
    WHERE tiendanube_variant_id IS NOT NULL;
CREATE INDEX idx_productos_sku ON productos(sku);
CREATE TRIGGER trg_productos_updated BEFORE UPDATE ON productos
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TABLE recetas (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo                  VARCHAR(20) NOT NULL UNIQUE,
    nutricionista_id        UUID NOT NULL REFERENCES nutricionistas(id),
    paciente_id             UUID NOT NULL REFERENCES pacientes(id),
    estado                  VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
                            CHECK (estado IN ('PENDIENTE','APLICADA','VENCIDA','ANULADA')),
    descuento_pct           NUMERIC(5,2) NOT NULL,
    emitida_at              TIMESTAMPTZ NOT NULL,
    vence_at                DATE NOT NULL,
    aplicada_at             TIMESTAMPTZ,
    anulada_at              TIMESTAMPTZ,
    cupon_tiendanube_id     BIGINT,
    cupon_sync_estado       VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
                            CHECK (cupon_sync_estado IN ('PENDIENTE','SINCRONIZADO','ERROR')),
    cupon_sync_error        TEXT,
    orden_tiendanube_id     BIGINT,
    orden_numero            INTEGER,
    orden_total             NUMERIC(12,2),
    orden_paid_at           TIMESTAMPTZ,
    comision_pct            NUMERIC(5,2),
    comision_monto          NUMERIC(12,2),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ,
    created_by              UUID,
    deleted_at              TIMESTAMPTZ
);
CREATE INDEX idx_recetas_nutricionista_estado ON recetas(nutricionista_id, estado);
CREATE INDEX idx_recetas_estado_vence ON recetas(estado, vence_at);
CREATE UNIQUE INDEX uq_recetas_orden ON recetas(orden_tiendanube_id)
    WHERE orden_tiendanube_id IS NOT NULL;
CREATE TRIGGER trg_recetas_updated BEFORE UPDATE ON recetas
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TABLE receta_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receta_id       UUID NOT NULL REFERENCES recetas(id),
    producto_id     UUID NOT NULL REFERENCES productos(id),
    cantidad        INTEGER NOT NULL DEFAULT 1 CHECK (cantidad > 0),
    precio_lista    NUMERIC(12,2) NOT NULL,
    indicaciones    TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    created_by      UUID,
    deleted_at      TIMESTAMPTZ,
    UNIQUE (receta_id, producto_id)
);
CREATE TRIGGER trg_receta_items_updated BEFORE UPDATE ON receta_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TABLE notificaciones (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receta_id       UUID REFERENCES recetas(id),
    canal           VARCHAR(20) NOT NULL CHECK (canal IN ('EMAIL','WHATSAPP')),
    destinatario    VARCHAR(255) NOT NULL,
    asunto          VARCHAR(255),
    cuerpo          TEXT NOT NULL,
    estado          VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
                    CHECK (estado IN ('QUEUED','SENT','FAILED')),
    intentos        INTEGER NOT NULL DEFAULT 0,
    last_error      TEXT,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    created_by      UUID,
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_notificaciones_estado ON notificaciones(estado);
CREATE TRIGGER trg_notificaciones_updated BEFORE UPDATE ON notificaciones
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TABLE webhook_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    origen          VARCHAR(20) NOT NULL DEFAULT 'TIENDANUBE',
    evento          VARCHAR(60) NOT NULL,
    recurso_id      BIGINT NOT NULL,
    payload         JSONB NOT NULL,
    procesado       BOOLEAN NOT NULL DEFAULT FALSE,
    procesado_at    TIMESTAMPTZ,
    error           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    created_by      UUID,
    deleted_at      TIMESTAMPTZ,
    UNIQUE (origen, evento, recurso_id)
);
CREATE TRIGGER trg_webhook_events_updated BEFORE UPDATE ON webhook_events
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
