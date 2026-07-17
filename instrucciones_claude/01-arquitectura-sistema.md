# 01 — Arquitectura del sistema

## Visión

Monorepo con la misma arquitectura de imedba: SPA React contra API REST Spring Boot, Postgres, Keycloak
para identidad, todo orquestado por Docker Compose (dev) + override prod con nginx TLS como único punto
de entrada público.

```
                        ┌─────────────────────────── VPS / hosting del cliente ──┐
browser ── https ──► nginx (80/443, solo prod)                                    │
   │                    ├── /            → frontend (SPA React, nginx interno)    │
   │                    ├── /api/*       → backend  (Spring Boot :8080)           │
   │                    └── /auth/*      → keycloak (:8080, KC_HTTP_RELATIVE_PATH)│
   │                                                                              │
   │                 backend ──► postgres 16 (DBs: nutriapp, keycloak)            │
   │                    │                                                         │
   │                    ├── integrations/contabilium  ──►  API Contabilium (ERP)  │
   │                    ├── integrations/tiendanube   ──►  API TiendaNube         │
   │                    ├── integrations/mail         ──►  proveedor email (TBD)  │
   │                    └── integrations/whatsapp     ──►  proveedor WhatsApp(TBD)│
   │                                                                              │
TiendaNube ── webhook order/paid ──► POST /api/v1/webhooks/tiendanube ────────────┘
```

En dev no hay nginx: frontend 5173, backend 8080, keycloak 8081, postgres 5432, todo bindeado a `127.0.0.1`.

## Actores y roles

| Rol Keycloak | Quién | Qué hace |
|---|---|---|
| `NUTRICIONISTA` | profesional validado | alta de pacientes, emisión de recetas, su dashboard |
| `ADMIN` | el cliente (Gon/equipo) | valida registros de nutricionistas, ve estado de integraciones, parámetros |

El **paciente NO es usuario**: recibe la receta por mail/WhatsApp y compra en TiendaNube. No hay login de pacientes en el MVP.

## Flujo principal: receta → cupón → conversión

1. **Registro nutricionista** (público): formulario → usuario Keycloak deshabilitado + fila `nutricionistas`
   con `estado_validacion = PENDIENTE`. Un ADMIN aprueba → se habilita el usuario Keycloak y puede loguear.
2. **Alta de paciente**: mail y WhatsApp obligatorios (son el canal de entrega de la receta).
3. **Emisión de receta**: el nutricionista elige paciente + producto(s) del catálogo local (buscador por
   texto/marca/laboratorio/principio activo/presentación). Al confirmar, **en una transacción local**:
   - se persiste la receta `PENDIENTE` con vencimiento a +30 días y código único `RX-XXXXXX`;
   - se registra el cupón en TiendaNube (adapter): `max_uses: 1`, `end_date` = vencimiento, restringido a los
     productos de la receta. En modo stub el POST no sale: el cupón queda `cupon_sync = PENDIENTE` y un job lo
     reintenta cuando la integración esté live. **El código lo generamos nosotros**, así que la receta siempre
     tiene código aunque TiendaNube no esté conectada.
   - se encolan 2 notificaciones (`EMAIL` + `WHATSAPP`) en la tabla `notificaciones` (estado `QUEUED`).
4. **Despacho de notificaciones**: scheduler (estilo imedba `NotificationScheduler`) toma las `QUEUED` y las
   manda por el adapter del canal. En modo stub el intento queda logueado y la notificación sigue `QUEUED`
   (con `last_error = "integración no conectada"`); en live pasa a `SENT`/`FAILED`.
5. **Conversión**: TiendaNube dispara webhook `order/paid` → `POST /api/v1/webhooks/tiendanube` verifica el
   HMAC, persiste el evento en `webhook_events` (idempotencia por `(evento, orden_id)`), y en procesamiento:
   `GET /orders/{id}` → si `order.coupon[]` matchea un cupón nuestro y `payment_status = paid` → receta
   `APLICADA` + snapshot de orden/total/comisión. **Respaldo**: job de polling
   (`GET /orders?payment_status=paid&updated_at_min=...`) por si se pierde un webhook.
6. **Vencimiento**: job diario pasa a `VENCIDA` toda receta `PENDIENTE` con `vence_at < hoy`. Corre también
   catch-up al startup (lección imedba: el cron no corre si la app estaba apagada a esa hora).
7. **Dashboard**: pendientes/aplicadas + cierre mensual: `Σ comision_monto` de recetas `APLICADA` del mes.

## Catálogo de productos (decisión clave)

Los buscadores piden **principio activo, presentación, marca, laboratorio** — metadata que TiendaNube y
Contabilium **no modelan nativamente completa**. Por eso el catálogo vive en una tabla local `productos`:

- **Origen de los datos**: en Fase 0/1 se carga por **seed Flyway** con productos realistas de la tienda TBC
  (regla de oro: nada mockeado en memoria — la data vive en la DB). En Fase 2, un **job de sync** upsertea
  desde TiendaNube (id de producto/variante — necesario para restringir cupones — más nombre, precio, SKU,
  stock, imagen) y concilia contra Contabilium por SKU.
- **Metadata de búsqueda** (principio activo, laboratorio, presentación): columnas propias, pobladas por el
  sync si el cliente las carga en la tienda (tags/atributos) o por carga manual/planilla. Pregunta abierta
  para Gon en `04-plan-de-fases.md`.
- El buscador es server-side (Specifications JPA, patrón imedba `BookSpecs`), con `unaccent` + `ILIKE`.

## Integraciones: patrón port/adapter con modo stub→live

Paquete `com.nutriapp.integrations.<proveedor>/`:

```
integrations/tiendanube/
  TiendaNubeClient.java        ← interfaz (port): createCoupon, getOrder, listOrders, listProducts, registerWebhook
  HttpTiendaNubeClient.java    ← impl real (RestClient, auth, rate-limit backoff 429)
  StubTiendaNubeClient.java    ← impl stub: lanza IntegrationUnavailableException("tiendanube no conectada")
  TiendaNubeProperties.java    ← @ConfigurationProperties("nutriapp.integrations.tiendanube") { mode, storeId, accessToken, ... }
```

- Selección por properties: `mode=stub|live` (default `stub`). Un `@Configuration` registra el bean según el modo.
- **La lógica de negocio nunca sabe en qué modo está**: captura `IntegrationUnavailableException` y degrada
  (cupón `PENDIENTE` de sync, notificación `QUEUED`). Flip a live = setear env + reiniciar. Cero cambios de código.
- Jobs de reconciliación (`CuponSyncJob`, `NotificationScheduler`, `ProductoSyncJob`, `OrderPollingJob`) reintentan
  lo pendiente — son los mismos jobs en stub y live.
- Endpoint `GET /api/v1/admin/integraciones/estado` expone modo + última sync + pendientes de cada proveedor
  (para la pantalla de admin y para debugging).

Detalle por proveedor (auth, endpoints, shapes) en `03-integraciones-apis.md`.

## Módulos backend

```
com.nutriapp
├── config/          SecurityConfig, CorsConfig, OpenApiConfig, propiedades de integraciones
├── common/          BaseEntity, PageResponse, ApiError + GlobalExceptionHandler, AuthUtils, JwtAuditorAware
├── integrations/    contabilium/ tiendanube/ mail/ whatsapp/  (ports + adapters, ver arriba)
└── modules/
    ├── nutricionista/   registro público + validación admin (Keycloak Admin API para crear/habilitar usuarios)
    ├── paciente/        CRUD de pacientes del nutricionista (scoping: cada uno ve SOLO los suyos)
    ├── producto/        catálogo local + búsqueda + sync
    ├── receta/          emisión, estados, vencimiento (scheduler), cupón, anulación
    ├── notificacion/    cola + dispatcher + templates (port de imedba modules/notification)
    ├── webhook/         endpoint TiendaNube + procesamiento idempotente + polling de respaldo
    └── dashboard/       resumen + cierre mensual
```

Scoping por dueño: patrón imedba (`enrolled_by`) — acá `nutricionista_id` sale del JWT (`sub` →
`nutricionistas.keycloak_user_id`); pacientes/recetas/dashboard filtran SIEMPRE por el nutricionista logueado.
ADMIN ve todo.

## Frontend (propiedad de Fran — misma estructura que imedba)

```
frontend/src/
├── api/        client.ts (fetch + Bearer + ApiError) + un archivo por recurso
├── types/      espejo manual de los DTOs del back
├── pages/      Login, Registro, Dashboard, Pacientes, EmitirReceta, Recetas, AdminNutricionistas
├── components/ formularios, layout (Sidebar/Topbar/RequireAuth), EmptyState
├── hooks/      useFetch.ts
└── lib/        auth.ts (ROPC contra Keycloak, patrón imedba), access.ts, confirm.ts
```

Auth: mismo esquema que imedba — form propio email+password (ROPC / Direct Access Grants contra
`nutriapp-frontend`), tokens en localStorage con refresh coalescido. Sin `keycloak-js`.

Lección imedba a NO repetir: entrar a ruta protegida sin sesión debe hacer `<Navigate to="/">`,
no disparar el redirect PKCE.

## Decisiones tomadas (y por qué)

| Decisión | Razón |
|---|---|
| Catálogo de productos **local + sync**, no proxy en vivo a TiendaNube | buscadores por metadata que las APIs no tienen; rate limit (2 req/s); funciona en modo stub con seed |
| Código de cupón **generado por nosotros**, no por TiendaNube | la receta tiene trazabilidad aunque la integración esté stub; TiendaNube acepta el código que le mandemos |
| Webhook + polling de respaldo (no solo webhook) | los webhooks se pierden; el polling barato (`updated_at_min`) reconcilia |
| Notificaciones como **cola en DB** (QUEUED/SENT/FAILED) | idéntico a imedba; sobrevive reinicios; en stub queda encolado y sale solo al pasar a live |
| Keycloak (no auth casera) | mismo stack imedba; da admin de usuarios, roles y el flujo deshabilitado→habilitado para la validación |
| Paciente sin login | fuera de alcance MVP (el mail lo dice: seguimiento/historial es fase futura) |
| Modelo `receta_items` N productos, UI arranca con 1 | el mail dice "en principio 1... en todo caso 2 o 3 o más" — el schema no se migra, la UI sí |
