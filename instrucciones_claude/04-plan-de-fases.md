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
| 1.7 | Tests: unit + integration Testcontainers (flujo emisión→webhook simulado→APLICADA→cierre mensual), cobertura ≥80% en `modules/` |
| 1.8 | CI GitHub Actions backend (port de imedba) + si sobra tiempo: frontend-ci (tsc + lint + build) |

**Demo intermedia con Gon (~fin jul)**: flujo completo en stub — emitir receta, simular webhook, ver
APLICADA + $$$ en dashboard. Sirve para validar UX y cerrar las preguntas abiertas (abajo).

## Fase 2 — Integraciones reales (lun 11 ago → vie 22 ago) — Santi + Gon; Fran vuelve ~12

| # | Tarea |
|---|---|
| 2.1 | App TiendaNube en Partner Portal + tienda demo + OAuth → token persistido. Flip `tiendanube.mode=live` contra la demo: sync productos, cupones reales, webhook registrado (HTTPS: túnel en dev, dominio en prod) |
| 2.2 | Contabilium live: validación credenciales (`obtenerinfo`), conciliación catálogo por SKU, sync nightly |
| 2.3 | Email live: definir proveedor con Gon (recomendación: SES SMTP), DNS (DKIM/SPF), salir de sandbox, template real |
| 2.4 | WhatsApp live: WABA + número + template aprobado por Meta (⚠️ la aprobación tarda — **iniciar el trámite en Fase 1**); fallback `wa.me` si se demora |
| 2.5 | Switch a la tienda TBC real + prueba end-to-end real (receta → mail/wa → compra de prueba → APLICADA) |
| 2.6 | Fran (desde el 12): pulido de las pantallas con data real, admin integraciones, F.6 si quedó pendiente |

## Fase 3 — Pulido + hardening + deploy (lun 24 ago → vie 11 sep)

- `docker-compose.prod.yml` + nginx TLS + rate limit + headers (port de imedba), backup/restore scripts.
- Hosting del cliente (a definir con Gon — presupuesto: infra a cargo del cliente) + dominio + certificados.
- E2E completo en staging, corrección de bugs, revisión de seguridad (webhook HMAC, scoping, secretos).
- Puesta en producción + soporte post-entrega (presupuesto §5).
- Buffer: el plan deja ~1 semana de margen sobre los 2 meses para absorber la aprobación de WhatsApp/DNS/hosting.

---

## Preguntas abiertas para Gon (cerrar en Fase 0/1 — ninguna bloquea el arranque)

1. **% de descuento**: ¿fijo global (ej. 30%)? ¿lo elige el nutricionista por receta con un tope? Default actual: parámetro global.
2. **Comisión del nutricionista**: ¿qué %? ¿sobre el total de la orden o solo sobre los productos recetados? (impacta el cálculo del cierre mensual)
3. **Metadata de productos** (principio activo, laboratorio, presentación): ¿está cargada en TiendaNube (tags/atributos)? Si no, ¿la cargan ellos en nuestra webapp o pasan planilla?
4. **Productos por receta**: arrancamos con 1 (el mail lo sugiere). ¿Confirmás? El modelo ya soporta N.
5. **Proveedor de email y WhatsApp** (costos a cargo del cliente): proponemos SES + Meta Cloud API. ¿Tienen WABA (WhatsApp Business) verificado? Si no, iniciar el trámite YA (tarda semanas).
6. **Validación de nutricionistas**: ¿qué chequean para aprobar (matrícula)? ¿Quiénes tienen usuario ADMIN?
7. **Cupón**: ¿el descuento aplica solo a los productos recetados (asumido) o a toda la compra? ¿Mínimo de compra?
8. **Hosting**: ¿dónde quieren la webapp? Necesitamos dominio con HTTPS para los webhooks de TiendaNube.
9. **Migración a Shopify**: confirmado fuera de alcance MVP; el patrón adapter deja el camino preparado.
