# 04 — Plan de fases y asignación Santi / Fran

Plazo presupuestado: **2 meses** desde firma + accesos (presupuesto §2). Accesos ya recibidos
(Contabilium API key + TiendaNube). Arranque: **2026-07-17**.

**Restricción dura de calendario**: Fran trabaja **hasta el martes 21-07 inclusive** y vuelve **~12-08**
(3 semanas de vacaciones). Por eso la Fase 0 concentra las tareas frontend más clave AL PRINCIPIO, contra un
contrato API congelado (`05-api-endpoints.md`) y un backend seeded — así el hueco de Fran no bloquea el proyecto.

```
jul 17 ──── jul 21 │ jul 22 ─────────────── ago 8 │ ago 11 ──── ago 22 │ ago 24 ──── sep 11
   FASE 0          │        FASE 1                │      FASE 2        │     FASE 3
 Cimientos +       │  Backend completo (stubs)    │  Integraciones     │  Pulido + deploy
 sprint de Fran    │  Santi solo (Fran vacaciones)│  reales            │  (Fran de vuelta)
```

---

## ⭐ Prioridad #1 — Rediseño de UI (recibido 2026-07-26)

El usuario trajo un **rediseño completo de la SPA** hecho con Claude design. Mockups en
`instrucciones_claude/Diseño gestor recetas nutricionista/` (`.dc.html`, uno por pantalla:
Login, Registro, NavBar, Footer, Dashboard, EmitirReceta, Pacientes, Recetas, RecetaEmitida, CierreMensual).
**Es la prioridad #1**: manda sobre el resto del backlog de Fase 1 en adelante.

**Sistema de diseño (extraído de los mockups):**
- Paleta: primario `#0f8a66` (hover `#0b6e51`), verde oscuro `#16302c`, menta `#e4f3ec` / acento `#57d3a6`,
  fondo `#f4f6f3`, borde `#e6e6df`, texto `#16302c` / muted `#6c7b78` / sutil `#8b9793`.
- Formas: radios 10–12px, sombras suaves (`0 2px 8px rgba(15,138,102,.24)` en CTAs), inputs con focus-ring verde.
- Tipografía: **Comic Neue** (redondeada, amigable). ⚠️ Confirmar con el usuario que es intencional (estética Comic Sans).
- **Cambio estructural:** de **sidebar** (layout actual de Fran) a **top navbar** (Panel / Recetas / Pacientes /
  Cierre mensual + CTA "Nueva receta" + avatar con nombre/matrícula + botón salir).
- Login: split-screen (panel verde oscuro con value-prop a la izquierda + formulario a la derecha).

**⚠️ Coordinación / propiedad (a resolver con el usuario antes de implementar):**
`frontend/` es **propiedad de Fran** y su SPA + el contrato están **congelados durante sus vacaciones** (vuelve ~12-08).
Un rediseño total desde el Claude de Santi entra en conflicto con la regla de propiedad y arriesga un **merge grande**
a la vuelta de Fran (Fran ya construyó las 6 pantallas con un refresh visual propio: sidebar + paleta cálida + íconos).
**Decisión pendiente del usuario:** quién implementa (Santi ahora / esperar a Fran / juntos) y si se autoriza tocar
`frontend/`. Hasta esa decisión: **solo planificado, `frontend/` intacto.**

**Desglose tentativo (cuando se apruebe implementación):**
| # | Tarea |
|---|---|
| R.1 | Tokens del sistema de diseño (CSS vars: paleta, radios, sombras, fuente) + reset global + carga de Comic Neue |
| R.2 | Layout: reemplazar Sidebar/Topbar por **top NavBar** + Footer (nav activo, avatar, CTA "Nueva receta") |
| R.3 | Login + Registro (split-screen; pantalla "pendiente de aprobación") |
| R.4 | Dashboard (tiles pendientes/aplicadas/comisión + tabla últimas recetas) |
| R.5 | Emitir Receta (2 columnas: picker paciente + buscador productos + resumen sticky) y pantalla "Receta emitida" (código grande) |
| R.6 | Pacientes (lista+búsqueda+alta/edición) y Recetas (lista+filtros+detalle) |
| R.7 | Cierre mensual |
> Nota: los mockups son estáticos (`.dc.html` + `support.js` es el runtime del design tool, no React). Implementar =
> **portar el lenguaje visual a la SPA React de Fran** (`frontend/src/`), reutilizando su `api/client.ts`/`useFetch`/rutas.
> No cambia el contrato back↔front (mismos endpoints/DTOs); es capa de presentación.

## Fase 0 — Cimientos + sprint pre-vacaciones de Fran (vie 17 → mar 21 jul)

**Objetivo:** al final del martes 21, el stack levanta con `docker compose up`, el backend expone los
endpoints mínimos con data seeded real en DB, y las 4 pantallas core existen y pegan al backend real.

### Santi (backend/infra) — ordenado; 0.1–0.3 son URGENTES porque desbloquean a Fran

| # | Tarea | Detalle |
|---|---|---|
| 0.1 | Scaffolding repo + Docker | estructura monorepo, `docker-compose.yml` (db + keycloak + backend + frontend, puertos 127.0.0.1: 5173/8080/8081/5432), `.env.example`, Dockerfiles multi-stage (port de imedba), realm `nutriapp` (clients `nutriapp-frontend` public+DAG / `nutriapp-backend` confidential, roles ADMIN/NUTRICIONISTA, authorities, usuarios dev: `admin@nutriapp.dev` / `nutri@nutriapp.dev`, password `test1234`) |
| 0.2 | Esqueleto Spring Boot | `pom.xml` (Boot 3.3.x, JPA, Flyway, MapStruct, Lombok, oauth2-resource-server, springdoc, Testcontainers), `BaseEntity`, `PageResponse`, `ApiError` + `GlobalExceptionHandler`, `SecurityConfig` (doble mapeo de authorities, cadena swagger), `CorsConfig`, `JwtAuditorAware` — **port directo de imedba, no reinventar** |
| 0.3 | Migraciones + seeds + endpoints mínimos | V001–V003 (`02-entidad-relacion.md`), `DevDataSeeder` (2 nutricionistas + 4 pacientes + 6 recetas en estados variados), endpoints: `GET /me`, `GET /productos` (+filtros), `GET/POST /pacientes`, `POST /recetas` (versión mínima: crea receta+items+código, sin cupón/notifs), `GET /recetas`, `GET /dashboard/resumen` — **avisar a Fran por DIARIO apenas esté arriba** |
| 0.4 | Puertos/adapters de integraciones (esqueleto) | interfaces + stubs + properties `mode=stub|live` + `IntegrationUnavailableException` + `GET /admin/integraciones/estado` |

### Fran (frontend) — EN ESTE ORDEN (lo más clave primero; lo que no llegue pasa a Fase 3)

| # | Tarea | Detalle |
|---|---|---|
| F.1 | Esqueleto SPA + auth + layout | Vite + React 19 + TS, `api/client.ts` (Bearer + parseo `ApiError` — surfacear `message`, nunca "HTTP 409" pelado), `lib/auth.ts` ROPC (port de imedba; ruta protegida sin sesión → `<Navigate to="/">`, NO redirect PKCE), `types/common.ts` (`PageResponse`), layout Sidebar/Topbar/RequireAuth, routing |
| F.2 | **Emitir Receta** (la pantalla más compleja — el corazón del producto) | picker de paciente (búsqueda rápida), buscador de productos (texto libre + dropdowns marca/laboratorio/principio activo/presentación desde `/productos/filtros`), selección de producto(s) con cantidad+indicaciones, resumen con descuento, confirmar → `POST /recetas` → pantalla de éxito con el código |
| F.3 | **Dashboard** (primera pantalla post-login) | tiles: pendientes / aplicadas mes / comisión $$$ mes, tabla últimas recetas con estado (badge por estado), acceso al cierre mensual |
| F.4 | **Pacientes** | lista con búsqueda + form alta/edición (email y whatsapp obligatorios, validación E.164) |
| F.5 | **Recetas** | lista con filtros (estado/paciente/fechas) + detalle (items, notificaciones, conversión, botones anular/reenviar) |
| F.6 | Registro público + bandeja admin | form `POST /registro` + pantalla admin de aprobación (si no llega, Fase 3) |

**Contrato congelado durante las vacaciones de Fran**: los shapes de `05-api-endpoints.md` no se cambian
incompatiblemente mientras Fran no está; si es inevitable, entrada de DIARIO bien visible + versionar el DTO.

## Fase 1 — Backend completo con stubs (mié 22 jul → vie 8 ago) — Santi solo

| # | Tarea |
|---|---|
| 1.1 | Módulo receta completo: transacción de emisión (cupón local + `cupon_sync=PENDIENTE`), anular, reenviar, scheduler de vencimiento (diario + catch-up al startup) |
| 1.2 | Módulo notificaciones: cola + dispatcher + templates de mail (HTML, logo CID) y WhatsApp (texto template) — port de imedba |
| 1.3 | Registro + validación de nutricionistas: `POST /registro` (Keycloak Admin API, usuario deshabilitado), bandeja admin aprobar/rechazar (habilita usuario KC) |
| 1.4 | Webhook TiendaNube: endpoint + verificación HMAC + `webhook_events` idempotente + procesamiento (matcheo cupón → APLICADA + comisión) + job de polling de respaldo. **Testeable en stub simulando el POST del webhook** |
| 1.5 | Dashboard cierre-mensual + `productos/filtros` + refinamiento de búsqueda (unaccent) |
| 1.6 | Clientes HTTP reales (sin conectar): `HttpContabiliumClient` (token manager 24h + throttle 15 req/10s), `HttpTiendaNubeClient` (User-Agent, backoff 429), `SmtpMailSender`, `CloudApiWhatsAppSender` — con tests unit contra WireMock |
| 1.7 | ✅ Tests: unit + integration Testcontainers (`RecetaFlowIT`: emisión→webhook simulado→APLICADA→cierre mensual). Separación surefire (`mvn test`, sin Docker) / failsafe (`mvn verify`, con Testcontainers) |
| 1.8 | ✅ CI GitHub Actions (`.github/workflows/ci.yml`): backend `mvn verify` + frontend tsc/lint/build. Push/PR a `main`. + `.gitattributes` (EOL LF para scripts) |

**Demo intermedia con Gon (~fin jul)**: flujo completo en stub — emitir receta, simular webhook, ver
APLICADA + $$$ en dashboard. Sirve para validar UX y cerrar las preguntas abiertas (abajo).

## Fase 2 — Integraciones reales (lun 11 ago → vie 22 ago) — Santi + Gon; Fran vuelve ~12

| # | Tarea |
|---|---|
| 2.1 | App TiendaNube en Partner Portal + tienda demo + OAuth → token persistido. Flip `tiendanube.mode=live` contra la demo: sync productos, cupones reales, webhook registrado (HTTPS: túnel en dev, dominio en prod) |
| 2.2 | Contabilium live: validación credenciales (`obtenerinfo`), conciliación catálogo por SKU, sync nightly |
| 2.3 | Email live: definir proveedor con Gon (recomendación: SES SMTP), DNS (DKIM/SPF), salir de sandbox, template real |
| 2.4 | ✅ **HECHO (2026-08-04) — WhatsApp por link `wa.me`** (decisión 2026-07-28): el nutri manda el mensaje a mano desde su WhatsApp; **NO** Cloud API (sin WABA ni template de Meta). Se sacaron `integrations/whatsapp/`, el canal `WHATSAPP` de la cola (migración `V010`), la config `WHATSAPP_*` y el proveedor del panel de integraciones; se agregó `waMeUrl` en `RecetaResponse` (`WaMeLinkBuilder`) + botón en receta emitida y en el detalle. El único canal automático es el email. |
| 2.5 | Switch a la tienda TBC real + prueba end-to-end real (receta → mail/wa → compra de prueba → APLICADA) |
| 2.6 | Fran (desde el 12): pulido de las pantallas con data real, admin integraciones, F.6 si quedó pendiente |

### Resiliencia / fallbacks manuales ante caída de terceros (pedido de Gon, 2026-07-23)

Requisito del cliente: que la plataforma **degrade con gracia y mensajes muy explícitos** cuando una API de
terceros no responde, y que haya **acciones manuales** para recuperar lo que quedó pendiente. Todo esto es
**construible y testeable en stub desde ya** (degrada con mensaje claro; al pasar a `live` drena lo acumulado) —
se difirió a Fase 2 por decisión del usuario, junto con los clientes HTTP reales.

> **Aclaración semántica (no confundir):** la receta **siempre** se emite y queda en estado `PENDIENTE` — ese es su
> estado normal (= todavía no convertida en compra pagada), **no** es una falla. Lo que "se cae" ante TiendaNube
> caído es la **sincronización del cupón** (`cupon_sync_estado=PENDIENTE`). El "re-mandar todas las pendientes" es
> **reintentar el registro del cupón** de las que no sincronizaron — NO reenviar notificaciones (esas ya las reintenta
> solo el `NotificacionDispatcher` cada 30s).

| # | Tarea | Detalle |
|---|---|---|
| 2.7 | ✅ **Visibilidad + mensajes explícitos** (scaffold hecho, 2026-07-27) | `GET /admin/integraciones/estado` (por proveedor: `modo` stub/live, `disponible`, `pendientes` [cupones sin sync / notifs QUEUED], `ultimoError`, `ultimaSync`). En la emisión, devolver un **mensaje humano** de degradación ("El cupón quedó pendiente: TiendaNube no está disponible, se reintentará solo") además del enum `cuponSyncEstado`. Consolidar los 503 de `IntegrationUnavailableException` con texto claro por proveedor. |
| 2.8 | ✅ **Cupones: reconciliación + resync manual** (scaffold hecho, 2026-07-27) | `CuponSyncJob` (`@Scheduled`): reintenta `createCoupon` para recetas con `cupon_sync_estado=PENDIENTE`/`ERROR` cuya receta siga `PENDIENTE`. `POST /admin/tiendanube/resync-cupones` (admin): dispara la reconciliación manual y devuelve `{intentados, sincronizados, pendientes}`. En stub sigue degradando con mensaje explícito. **Resync = solo cupones** (confirmado con el cliente). |
| 2.9 | ✅ **Productos Contabilium: sync manual + persistente** (scaffold hecho, 2026-07-27) | El catálogo **ya vive en la DB y se usa siempre** desde ahí (no depende de la API en runtime) — esa parte está hecha. Falta: `POST /admin/contabilium/sync-productos` (manual, admin) + `ProductoSyncService` (conciliación por SKU, `last_synced_at`). En stub → 503 explícito "Contabilium no conectada"; en live (2.2) hace el full-scan nightly + on-demand. |

Notas de implementación cuando se encare: las 3 son endpoints/jobs de **admin** (`admin:manage`); nuevos, no tocan
shapes existentes del contrato congelado. Escribible en 1.6 (scaffold de jobs/endpoints degradando en stub) y se
enciende en 2.1/2.2 al conectar los clientes HTTP reales.

> **Estado (2026-07-27):** el **scaffold de las 3 está implementado y verde en stub** (`mvn verify`: 72 unit + IT).
> Endpoints/jobs vivos, degradan con mensaje explícito; al pasar TiendaNube/Contabilium a `live` (2.1/2.2) drenan lo
> acumulado sin tocar más código. Único añadido al contrato: `RecetaResponse.cuponSyncMensaje` (nullable, aditivo).
> **Front: panel admin `/integraciones` HECHO** (2026-07-27, cubriendo a Fran) consumiendo los 3 endpoints.
> Pendiente de Fase 2: encenderlos contra las cuentas reales de Gon.

## Fase 3 — Pulido + hardening + deploy (lun 24 ago → vie 11 sep)

- ✅ **Rate-limiting por IP en `/registro` y `/webhooks`** (backend, hecho 2026-07-27 — token bucket propio, 429 + `Retry-After`, config `nutriapp.rate-limit`). Single-instance; escalado a store compartido documentado.
- ✅ **Keycloak Admin por service-account** (hecho 2026-07-27) — `client_credentials` del client `nutriapp-backend` scopeado a `realm-management` `manage-users`/`view-users`; sale el superusuario del realm master. Secret fail-closed en prod.
- ✅ **`docker-compose.prod.yml` + nginx TLS + rate limit de red + security headers + backup/restore scripts** (hecho 2026-07-28) — nginx único servicio público (80/443), reverse proxy single-domain (`/`→SPA, `/api/`→backend, `/auth/`→Keycloak), **bring-your-own-cert** (Let's Encrypt diferido hasta confirmar hosting), secretos fail-closed, `scripts/{gen-selfsigned-cert,backup-db,restore-db}.sh`, runbook `DEPLOY.md`. Boot real = paso de deploy (necesita Docker + dominio).
- Regenerar el secret del client `nutriapp-backend` para el realm de prod (hoy placeholder de dev) — **ops, al desplegar** (necesita el Keycloak de prod corriendo).
- Hosting del cliente (a definir con Gon — presupuesto: infra a cargo del cliente) + dominio + certificados.
- E2E completo en staging, corrección de bugs, revisión de seguridad (webhook HMAC, scoping, secretos).
- Puesta en producción + soporte post-entrega (presupuesto §5).
- Buffer: el plan deja ~1 semana de margen sobre los 2 meses para absorber la aprobación de WhatsApp/DNS/hosting.

---

## Preguntas abiertas para Gon (cerrar en Fase 0/1 — ninguna bloquea el arranque)

1. **% de descuento**: ✅ RESUELTO (2026-07-27) — **fijo global, configurable por el admin** en runtime (el nutricionista NO lo elige). Implementado: `configuracion_sistema` + `GET /configuracion` / `PUT /admin/configuracion` + pantalla admin. El **valor** concreto sigue TBD con Gon (seed inicial 15%).
2. **Comisión del nutricionista**: ✅ el % es **configurable por el admin** (mismo módulo; seed 10%). Pendiente con Gon: el **valor** y si es sobre el total de la orden o solo sobre los productos recetados (hoy: sobre el total de la orden).
3. **Metadata de productos** (principio activo, laboratorio, presentación): ¿está cargada en TiendaNube (tags/atributos)? Si no, ¿la cargan ellos en nuestra webapp o pasan planilla?
4. **Productos por receta**: arrancamos con 1 (el mail lo sugiere). ¿Confirmás? El modelo ya soporta N.
5. **Proveedor de email y WhatsApp** (costos a cargo del cliente): proponemos SES + Meta Cloud API. ¿Tienen WABA (WhatsApp Business) verificado? Si no, iniciar el trámite YA (tarda semanas).
6. **Validación de nutricionistas**: ¿qué chequean para aprobar (matrícula)? ¿Quiénes tienen usuario ADMIN?
7. **Cupón**: ¿el descuento aplica solo a los productos recetados (asumido) o a toda la compra? ¿Mínimo de compra?
8. **Hosting**: ¿dónde quieren la webapp? Necesitamos dominio con HTTPS para los webhooks de TiendaNube.
9. **Migración a Shopify**: confirmado fuera de alcance MVP; el patrón adapter deja el camino preparado.
