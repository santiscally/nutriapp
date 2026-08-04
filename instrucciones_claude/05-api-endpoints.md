# 05 — Contrato REST (front ↔ back)

Base: `/api/v1`. Auth: JWT Keycloak (Bearer). Paginación: `PageResponse<T>` `{content, page, size,
totalElements, totalPages, first, last}`. Errores: `ApiError {timestamp, status, error, message, path, errors[]}`.
Fuente de verdad viva: Swagger (`http://localhost:8080/swagger-ui.html`). Este doc fija el contrato inicial
para que Fran construya sin esperar al back; cambios → entrada en DIARIO con "Impacto para el otro".

Roles: `ROLE_NUTRICIONISTA`, `ROLE_ADMIN`. Authorities (resource `nutriapp-backend`): `pacientes:read`,
`pacientes:write`, `productos:read`, `recetas:read`, `recetas:write`, `dashboard:read`, `admin:manage`.

**Scoping**: todo lo de nutricionista (pacientes, recetas, dashboard) filtra server-side por el usuario del
JWT. ADMIN ve todo.

---

## Público (sin token)

### POST /registro
Solicitud de alta de nutricionista. Crea `nutricionistas` en `PENDIENTE` + usuario Keycloak deshabilitado.
```json
// Request
{ "nombre": "Ana", "apellido": "García", "email": "ana@x.com", "telefono": "+5491155551234",
  "matricula": "MN 1234", "password": "..." }
// 201 → { "id": "...", "estadoValidacion": "PENDIENTE" }
// 409 si el email ya existe
```

### POST /webhooks/tiendanube
Recibe webhooks (payload `{store_id, event, id}`). Verifica HMAC (`x-linkedstore-hmac-sha256` = HMAC-SHA256 **hex**
del body crudo con el secreto de la app; comparación tiempo-constante), persiste en `webhook_events` (idempotente por
origen+evento+recurso), responde `200` inmediato; el procesamiento es async (lee la orden en TiendaNube → si su cupón
matchea una receta PENDIENTE la pasa a **APLICADA** + comisión). Sin auth JWT (la firma ES la auth). Firma inválida/ausente → **401**.

### POST /dev/tiendanube/orden-pagada — SÓLO perfil dev
Simulador de conversión para la demo/tests en stub (en stub no llega webhook real). Fabrica una orden pagada con el
cupón de una receta y corre el mismo procesamiento. **No existe en prod.**
```json
// Request (sólo recetaCodigo es obligatorio)
{ "recetaCodigo": "RX-7K2M4X", "ordenNumero": 306, "ordenTiendanubeId": 770077, "ordenTotal": 31500.00 }
// 200 → { "recetaCodigo": "...", "ordenTiendanubeId": 770077, "ordenNumero": 306, "ordenTotal": 31500.00, "recetasAplicadas": 1 }
```

---

## Sesión

### GET /me — cualquier autenticado
```json
// 200 → { "id": "...", "nombre": "Ana", "apellido": "García", "email": "...", "roles": ["NUTRICIONISTA"],
//          "authorities": ["recetas:write", "..."], "estadoValidacion": "APROBADA" }
```

---

## Pacientes — `pacientes:read` / `pacientes:write`

| Método | Path | Notas |
|---|---|---|
| GET | `/pacientes?q=&page=&size=` | `q` busca en nombre/apellido/email (unaccent) |
| POST | `/pacientes` | email y whatsapp **obligatorios** |
| GET | `/pacientes/{id}` | |
| PUT | `/pacientes/{id}` | |
| DELETE | `/pacientes/{id}` | soft delete; 409 si tiene recetas PENDIENTES |

```json
// PacienteCreateRequest
{ "nombre": "Juan", "apellido": "Pérez", "email": "juan@x.com", "whatsapp": "+5491144443333",
  "fechaNacimiento": "1990-05-12", "notas": "..." }
// PacienteResponse = lo mismo + id, createdAt, cantidad de recetas (recetasTotal, recetasPendientes)
```

## Productos — `productos:read`

| Método | Path | Notas |
|---|---|---|
| GET | `/productos?q=&marca=&departamento=&categoria=&subcategoria=&laboratorio=&tag=&conStock=&precioMin=&precioMax=&page=&size=` | `q` texto libre sobre nombre + descripción + SKU + **código de barras** + **tags** (unaccent), **rankeado**: nombre → descripción → tag. Los demás filtros son ILIKE contains; `tag` es exacto |
| GET | `/productos/{id}` | |
| GET | `/productos/filtros` | listas para los dropdowns + `taxonomia` (árbol departamento→categoría→subcategoría) para encadenarlos |

```json
// ProductoResponse — los campos del maestro pueden venir null (producto que no está en la planilla)
{ "id": "...", "sku": "3", "codigoBarras": "7798349830060", "nombre": "ON-ROLL FLOW X 60G",
  "descripcion": "...", "descripcionWeb": "texto largo para el botón 'más info'",
  "precio": 13500.00, "stock": 2309, "imagenUrl": "https://dcdn-us.mitiendanube.com/...",
  "marca": "ON-ROLL", "departamento": "SALUD Y BIENESTAR", "categoria": "TERAPIAS NATURALES",
  "subcategoria": "FLEBOTONICOS TOPICOS", "laboratorio": "JEIANELL",
  "tags": ["bienestar", "circulación", "magnesio"],
  "principioActivo": null, "presentacion": null, "publicado": true, "origen": "CONTABILIUM" }

// ProductoFiltrosResponse
{ "marcas": ["..."], "categorias": ["..."], "departamentos": ["..."], "subcategorias": ["..."],
  "laboratorios": ["..."],
  "taxonomia": [ { "nombre": "SALUD Y BIENESTAR",
                   "categorias": [ { "nombre": "SUPLEMENTOS DIETARIOS",
                                     "subcategorias": ["MULTIVITAMINICOS", "..."] } ] } ] }
```

> **Cambio de significado, no de forma (2026-08-03):** `categoria` traía el Rubro de Contabilium, que
> valía "Producto terminado" para el 99,8 % del catálogo. Ahora trae la CATEGORIA del maestro de TBC
> (23 valores reales). El campo es el mismo; los datos, otros. `principioActivo` y `presentacion`
> siguen en el contrato pero son **siempre null**: no existen en ninguna de las dos fuentes — esa
> búsqueda se resuelve por `tags`. Detalle en `07-maestro-articulos-y-catalogo.md`.

## Recetas — `recetas:read` / `recetas:write`

| Método | Path | Notas |
|---|---|---|
| POST | `/recetas` | emite: crea receta+items, cupón (o lo deja PENDIENTE de sync), encola el mail. WhatsApp no se encola: viene `waMeUrl` para mandarlo a mano |
| GET | `/recetas?estado=&pacienteId=&desde=&hasta=&q=&page=&size=` | `q` busca por código o nombre de paciente |
| GET | `/recetas/{id}` | detalle con items + notificaciones + datos de conversión |
| POST | `/recetas/{id}/anular` | solo PENDIENTE; intenta borrar el cupón en TiendaNube |
| POST | `/recetas/{id}/reenviar` | re-encola el mail (solo PENDIENTE). No manda WhatsApp: eso es el link `waMeUrl` |

```json
// RecetaCreateRequest — el % de descuento NO viaja: es fijo global, lo define el admin (ver Configuración).
{ "pacienteId": "...", "items": [ { "productoId": "...", "cantidad": 1, "indicaciones": "1 medida post-entreno" } ] }

// RecetaResponse
{ "id": "...", "codigo": "RX-7K2M4X", "estado": "PENDIENTE",
  "paciente": { "id": "...", "nombre": "Juan", "apellido": "Pérez", "email": "...", "whatsapp": "..." },
  "items": [ { "producto": { ...ProductoResponse }, "cantidad": 1, "precioLista": 45000.00, "indicaciones": "..." } ],
  "descuentoPct": 30.0, "emitidaAt": "2026-07-17T15:30:00-03:00", "venceAt": "2026-08-16",
  "cuponSyncEstado": "PENDIENTE",
  // 2.4: link para que la nutricionista mande la receta por SU WhatsApp. null si la receta ya no
  // está PENDIENTE o el paciente no tiene teléfono utilizable. El único canal automático es el mail.
  "waMeUrl": "https://wa.me/5491144443333?text=Hola%20Juan%21%20...",
  "notificaciones": [ { "canal": "EMAIL", "estado": "QUEUED", "sentAt": null } ],
  "conversion": null }
// cuando APLICADA:
// "conversion": { "ordenNumero": 306, "ordenTotal": 31500.00, "paidAt": "...", "comisionPct": 10.0, "comisionMonto": 3150.00 }
```

## Dashboard — `dashboard:read`

### GET /dashboard/resumen
Primera pantalla post-login.
```json
{ "recetasPendientes": 4, "recetasAplicadasMes": 7, "recetasVencidasMes": 2,
  "comisionMesActual": 22050.00, "ventasGeneradasMesActual": 220500.00,
  "ultimasRecetas": [ { ...RecetaResponse resumida, 8 items } ] }
```

### GET /dashboard/cierre-mensual?year=2026&month=7
```json
{ "year": 2026, "month": 7, "recetasEmitidas": 15, "recetasAplicadas": 7, "tasaConversion": 0.47,
  "ventasGeneradas": 220500.00, "comisionTotal": 22050.00,
  "detalle": [ { "recetaCodigo": "RX-...", "paciente": "Juan Pérez", "ordenTotal": 31500.00,
                  "comisionMonto": 3150.00, "paidAt": "..." } ] }
```

### GET /dashboard/estadisticas?meses=6
Serie mensual para los gráficos del dashboard (barras de recetas por mes + tendencia de comisión).
`meses` opcional (default 6, acotado a [1, 24]). Orden **cronológico ascendente** — el último es el mes en curso.
Todo dato real; el front deriva ticket promedio (`ventas/aplicadas`) y el delta vs mes anterior.
```json
{ "meses": [
    { "year": 2026, "month": 2, "recetasEmitidas": 18, "recetasAplicadas": 11,
      "ventasGeneradas": 210000.00, "comisionTotal": 21000.00 },
    { "year": 2026, "month": 7, "recetasEmitidas": 40, "recetasAplicadas": 28,
      "ventasGeneradas": 612900.00, "comisionTotal": 91935.00 }
] }
```

## Configuración — parámetros de negocio

Los % de **descuento** (fijo global, el nutricionista no lo elige) y **comisión** los define el admin en runtime
(tabla `configuracion_sistema`, seed inicial 15/10). El emisor de recetas lee el descuento de acá (read-only).

| Método | Path | Auth | Notas |
|---|---|---|---|
| GET | `/configuracion` | autenticado | `{ "descuentoPct": 15.0, "comisionPct": 10.0 }` |
| PUT | `/admin/configuracion` | `admin:manage` | body `{ "descuentoPct": 25.0, "comisionPct": 12.0 }` (ambos [0,100]); comisión afecta solo conversiones futuras |

## Admin — `admin:manage`

| Método | Path | Notas |
|---|---|---|
| GET | `/admin/nutricionistas?estado=&q=&page=` | bandeja de validación |
| POST | `/admin/nutricionistas/{id}/aprobar` | habilita el usuario Keycloak; 409 si no está PENDIENTE |
| POST | `/admin/nutricionistas/{id}/rechazar` | body `{ "motivo": "..." }` |

### Admin — resiliencia de integraciones (2.7–2.9)

| Método | Path | Notas |
|---|---|---|
| GET | `/admin/integraciones/estado` | `{ "integraciones": [ { "proveedor":"tiendanube", "modo":"stub\|live", "disponible":bool\|null, "pendientes":n, "ultimoError":str\|null, "ultimoErrorAt":ts\|null, "ultimaSync":ts\|null } ... ] }` (3 proveedores: contabilium/tiendanube/mail — whatsapp salió en 2.4, es un link manual). `disponible` es `false` en stub, `null` en live sin interacción aún. `pendientes` = cupones sin sync (tiendanube) / notifs QUEUED (mail) / 0 (contabilium) |
| POST | `/admin/tiendanube/resync-cupones` | reintenta el registro de cupones de recetas PENDIENTES sin sync → `{ "intentados":n, "sincronizados":n, "pendientes":n }`. En stub siguen pendientes |
| POST | `/admin/contabilium/sync-productos` | fuerza la sync del catálogo por SKU → `{ "revisados":n, "creados":n, "actualizados":n, "sinCambios":n, "syncedAt":ts }`. **En stub → 503 "Contabilium no conectada"** |

### Admin — maestro de artículos de TBC (C-12) — `admin:manage`

| Método | Path | Notas |
|---|---|---|
| POST | `/admin/productos/importar-maestro` | **multipart**, campo `archivo` (.xlsx). Sube el maestro de TBC y aplica sus 9 columnas al catálogo cruzando por SKU. Síncrono. `422` si el archivo no es un xlsx legible o le faltan columnas (el mensaje dice cuáles) |
| GET | `/admin/productos/maestro/estado` | última importación para el panel; todo `null` si nunca se importó |

```json
// ImportarMaestroResponse
{ "filasLeidas": 2225, "filasMatcheadas": 2163, "filasActualizadas": 2163,
  "filasSinMatch": 62, "filasRechazadas": 0,
  "skusSinMatch": ["108", "109"], "rechazos": [],   // recortados a 50; los totales van en los contadores
  "publicados": 0, "despublicados": 26, "importadoAt": "...",
  "mensaje": "Archivo importado: 2163 de 2225 filas aplicadas al catálogo; 62 sin producto en el catálogo; 26 dejaron de estar disponibles para recetar." }

// MaestroEstadoResponse
{ "importadoAt": "...", "nombreArchivo": "maestro.xlsx", "filasLeidas": 2225, "filasMatcheadas": 2163,
  "filasSinMatch": 62, "filasRechazadas": 0, "catalogoActualizadoAt": "..." }
```

> El `mensaje` es el "archivo importado correctamente" que pidió Gon, pero con los números adentro: si
> de 2225 filas matchean 300, un cartel de éxito pelado sería engañoso. El front debe mostrar el
> `mensaje` y, si `filasSinMatch > 0`, ofrecer ver `skusSinMatch`.

> Nota: además, `RecetaResponse` (emisión y detalle) incluye ahora **`cuponSyncMensaje`** (string nullable):
> mensaje humano de degradación del cupón cuando quedó `PENDIENTE`/`ERROR`; `null` cuando sincronizó bien.
> Los 503 de integración caída ahora traen texto claro por proveedor (código `INTEGRATION_UNAVAILABLE`).

---

## Mapa front (para Fran)

| Página | Endpoints |
|---|---|
| Login | Keycloak ROPC (`nutriapp-frontend`) + `GET /me` |
| Registro (pública) | `POST /registro` |
| Dashboard | `GET /dashboard/resumen` + `GET /dashboard/estadisticas?meses=6` (gráficos) |
| Cierre mensual | `GET /dashboard/cierre-mensual?year=&month=` (selector de mes) |
| Pacientes | CRUD `/pacientes` |
| Emitir Receta | `GET /pacientes?q=` (picker) + `GET /productos?...` + `GET /productos/filtros` + `GET /configuracion` (descuento) + `POST /recetas` |
| Recetas | `GET /recetas` + detalle + anular/reenviar |
| Configuración (admin) | `GET /configuracion` + `PUT /admin/configuracion` |
| Admin Nutricionistas | `GET /admin/nutricionistas` + aprobar/rechazar |
| Admin Integraciones (`/integraciones`) | `GET /admin/integraciones/estado` + `POST /admin/tiendanube/resync-cupones` + `POST /admin/contabilium/sync-productos` (panel de resiliencia, solo admin; página hecha, degrada en stub) |
