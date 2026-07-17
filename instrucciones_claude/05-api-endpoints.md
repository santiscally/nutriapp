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
Recibe webhooks (payload `{store_id, event, id}`). Verifica HMAC (`x-linkedstore-hmac-sha256`), persiste en
`webhook_events`, responde `200` inmediato; el procesamiento es async. Sin auth JWT (la firma ES la auth).

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
| GET | `/productos?q=&marca=&laboratorio=&principioActivo=&presentacion=&page=&size=` | `q` texto libre sobre nombre+descripcion+sku (unaccent); los demás filtros exactos-ish (ILIKE) |
| GET | `/productos/{id}` | |
| GET | `/productos/filtros` | valores distintos de marca/laboratorio/presentacion para poblar los dropdowns de búsqueda |

```json
// ProductoResponse
{ "id": "...", "sku": "WHEY-CHOC-1KG", "nombre": "Whey Protein Chocolate 1kg", "descripcion": "...",
  "precio": 45000.00, "stock": 12, "imagenUrl": "...", "marca": "Star Nutrition",
  "laboratorio": "...", "principioActivo": "proteína de suero", "presentacion": "polvo 1kg",
  "publicado": true, "origen": "SEED" }
```

## Recetas — `recetas:read` / `recetas:write`

| Método | Path | Notas |
|---|---|---|
| POST | `/recetas` | emite: crea receta+items, cupón (o lo deja PENDIENTE de sync), encola mail+whatsapp |
| GET | `/recetas?estado=&pacienteId=&desde=&hasta=&q=&page=&size=` | `q` busca por código o nombre de paciente |
| GET | `/recetas/{id}` | detalle con items + notificaciones + datos de conversión |
| POST | `/recetas/{id}/anular` | solo PENDIENTE; intenta borrar el cupón en TiendaNube |
| POST | `/recetas/{id}/reenviar` | re-encola las notificaciones (solo PENDIENTE) |

```json
// RecetaCreateRequest
{ "pacienteId": "...", "items": [ { "productoId": "...", "cantidad": 1, "indicaciones": "1 medida post-entreno" } ],
  "descuentoPct": 30.0 }        // opcional: default del parámetro de config

// RecetaResponse
{ "id": "...", "codigo": "RX-7K2M4X", "estado": "PENDIENTE",
  "paciente": { "id": "...", "nombre": "Juan", "apellido": "Pérez", "email": "...", "whatsapp": "..." },
  "items": [ { "producto": { ...ProductoResponse }, "cantidad": 1, "precioLista": 45000.00, "indicaciones": "..." } ],
  "descuentoPct": 30.0, "emitidaAt": "2026-07-17T15:30:00-03:00", "venceAt": "2026-08-16",
  "cuponSyncEstado": "PENDIENTE",
  "notificaciones": [ { "canal": "EMAIL", "estado": "QUEUED", "sentAt": null },
                       { "canal": "WHATSAPP", "estado": "QUEUED", "sentAt": null } ],
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

## Admin — `admin:manage`

| Método | Path | Notas |
|---|---|---|
| GET | `/admin/nutricionistas?estado=&q=&page=` | bandeja de validación |
| POST | `/admin/nutricionistas/{id}/aprobar` | habilita el usuario Keycloak; 409 si no está PENDIENTE |
| POST | `/admin/nutricionistas/{id}/rechazar` | body `{ "motivo": "..." }` |
| GET | `/admin/integraciones/estado` | por proveedor: `{mode, ok, pendientes, lastSyncAt, lastError}` |
| POST | `/admin/integraciones/productos/sync` | fuerza sync de catálogo (en stub → 409 "no conectada") |

---

## Mapa front (para Fran)

| Página | Endpoints |
|---|---|
| Login | Keycloak ROPC (`nutriapp-frontend`) + `GET /me` |
| Registro (pública) | `POST /registro` |
| Dashboard | `GET /dashboard/resumen` (+ `GET /dashboard/cierre-mensual` para el detalle del mes) |
| Pacientes | CRUD `/pacientes` |
| Emitir Receta | `GET /pacientes?q=` (picker) + `GET /productos?...` + `GET /productos/filtros` + `POST /recetas` |
| Recetas | `GET /recetas` + detalle + anular/reenviar |
| Admin Nutricionistas | `GET /admin/nutricionistas` + aprobar/rechazar |
| Admin Integraciones | `GET /admin/integraciones/estado` + sync (Fase 2, baja prioridad) |
