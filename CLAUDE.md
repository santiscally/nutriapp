# NUTRIAPP — Guía para Claude Code

Webapp de **recetas digitales para nutricionistas**: el nutricionista emite una receta con descuento
sobre productos del catálogo del cliente (ERP Contabilium / tienda TiendaNube), el paciente la recibe
por mail —y por WhatsApp, que se lo manda la nutricionista con un link `wa.me`— con un código de
descuento único, compra en la tienda online y la receta queda trazada (pendiente → aplicada /
vencida). Cliente: Gon (jeianell / tienda TBC).

## Propiedad del repo

Este monorepo lo construyen dos personas:

- **`backend/`, `docker-compose*.yml`, `keycloak/`, `nginx/`, `db/`, `scripts/`, `.env.example`, raíz (README, CLAUDE.md), `instrucciones_claude/`** → **Santi** (backend / DevOps / DB / Keycloak).
- **`frontend/`** → **Fran** (React/TS/Vite). No tocar salvo pedido explícito del usuario. Leerlo está permitido.

Si una tarea implica modificar `frontend/` sin pedido explícito, **parar y avisar** antes de tocar nada.

## Stack (no negociable)

- Backend: Java 21 + Spring Boot 3.3.x + Spring Data JPA + Flyway + MapStruct + Spring Security OAuth2 Resource Server.
- DB: PostgreSQL 16.
- Auth: Keycloak 25 (OIDC/JWT). Realm `nutriapp`. Clients `nutriapp-frontend` (public SPA) y `nutriapp-backend` (confidential resource server).
- Frontend: React 19 / TypeScript / Vite (propiedad de Fran). Sin librería de estado ni cliente HTTP externo: fetch nativo envuelto en `api/client.ts` + hook `useFetch` (patrón imedba).
- Infra: Docker + Docker Compose (dev) + override prod con nginx TLS. Hosting a cargo del cliente.
- Integraciones: Contabilium (ERP), TiendaNube (tienda + cupones + webhooks), email (proveedor TBD). **Todas detrás de adapters con modo stub** — ver regla de oro. WhatsApp **no** es una integración: es un link `wa.me` que abre la nutricionista (decisión 2026-07-28, tarea 2.4).

## Regla de oro: nada mockeado

1. **Datos**: NUNCA datos hardcodeados en memoria ni fixtures en el front. Si la info no existe todavía,
   se carga en la DB por migración Flyway de seed o script SQL. El front SIEMPRE pega al backend real
   (no existe `VITE_USE_MOCK` en este proyecto — decisión explícita).
2. **APIs externas**: cada integración (Contabilium, TiendaNube, email) se implementa completa
   contra su API documentada (cliente HTTP real, DTOs, manejo de errores), pero se activa por configuración:
   - `stub` (default en dev): el adapter responde un error controlado tipo "integración no conectada"
     y el flujo degrada elegante — el cupón queda `PENDIENTE_SYNC`, la notificación queda `QUEUED`.
   - `live`: mismo código, credenciales reales por env. **Cambiar de stub a live no debe requerir tocar
     una línea de código de negocio.**
   La conexión real se hace al final del proyecto (Fase 2). Detalle en `instrucciones_claude/03-integraciones-apis.md`.

## Convenciones backend

- Paquete base: `com.nutriapp`.
- Cada módulo (en `modules/<nombre>/`) con subpaquetes `entity/ repository/ service/ controller/ dto/ mapper/`.
- `BaseEntity` con `id (UUID) / createdAt / updatedAt / createdBy / deletedAt` (soft delete). Nunca `DELETE` físico.
- DTOs: `CreateXxxRequest`, `UpdateXxxRequest`, `XxxResponse`. Mapeos con MapStruct (`componentModel=spring`).
- Paginación con `Pageable` y respuesta `PageResponse<T>`.
- Autorización por `@PreAuthorize("hasAuthority('<permiso>')")`.
- DB naming: snake_case. UUIDs en PKs. Migraciones Flyway `V0NN__descripcion.sql`.
- Enums en código y VARCHAR en DB: estado_receta, estado_validacion, canal_notificacion, estado_notificacion, origen_producto, estado_sync.
- Errores: `GlobalExceptionHandler` → `ApiError {timestamp, status, error, message, path, errors[]}` uniforme.
- Integraciones: paquete `integrations/<proveedor>/` con interfaz (port) + implementación HTTP real + implementación stub, seleccionadas por properties (`nutriapp.integrations.<proveedor>.mode=stub|live`).

## Entidades

Ver `instrucciones_claude/02-entidad-relacion.md` (DDL completo). Núcleo: `nutricionistas`, `pacientes`,
`productos` (catálogo local sincronizable), `recetas` + `receta_items`, `notificaciones` (cola),
`webhook_events` (idempotencia).

## Reglas de negocio clave

- Solo usuarios **nutricionistas validados** operan: el registro queda `PENDIENTE` hasta que un ADMIN
  (el cliente) lo aprueba. Usuario Keycloak nace deshabilitado, se habilita al aprobar.
- Receta: estados `PENDIENTE → APLICADA | VENCIDA | ANULADA`. Vigencia **30 días** desde la emisión
  (scheduler diario la pasa a VENCIDA; catch-up al startup, lección imedba).
- Cada receta genera un **código de cupón único** (lo generamos nosotros, ej. `RX-XXXXXX`) que se
  registra en TiendaNube (`max_uses: 1`, `end_date` = vencimiento, restringido a los productos de la receta).
- La receta pasa a APLICADA cuando una orden **pagada** de TiendaNube usó su cupón (webhook `order/paid`
  + polling de respaldo). Se persiste orden, total y monto de comisión.
- Dashboard del nutricionista: recetas pendientes/aplicadas + cierre mensual del $$$ (comisión sobre
  recetas convertidas). % de descuento y % de comisión: parámetros de configuración (valores reales TBD con Gon).
- Cantidad de productos por receta: el modelo soporta N items (`receta_items`); la UI del MVP arranca con 1.

## Fases

Plan completo en `instrucciones_claude/04-plan-de-fases.md`. Resumen:

0. Cimientos (17–21 jul): infra + contrato API + esqueleto back con seeds + **sprint frontend de Fran pre-vacaciones** ← **en curso**
1. Backend completo con stubs (21 jul – 8 ago, Santi solo — Fran de vacaciones hasta ~12 ago)
2. Integraciones reales (11 – 22 ago): TiendaNube, Contabilium, email (WhatsApp ✅ resuelto por link `wa.me`)
3. Pulido + hardening + deploy (24 ago – 11 sep)

## Coordinación entre los dos Claudes (Santi + Fran)

Dos personas trabajan en este repo con dos Claudes distintos. Para que no se pisen ni re-descubran cosas ya resueltas:

- **`instrucciones_claude/DIARIO.md`** — bitácora append-only. Al **cerrar una tarea no trivial**
  (feature, bug-fix, decisión arquitectónica, fix de build, cambio en infra), agregar una entrada con el
  formato del header del archivo. Al **arrancar sesión**, leer las últimas ~10 entradas.
- **`instrucciones_claude/ESTADO.md`** — snapshot del presente. Dos secciones separadas ("Santi / backend"
  y "Fran / frontend"). **Solo editar la sección del dueño activo.** Jamás tocar la sección del otro
  (causa merge conflicts). Al empezar/terminar tarea, sobreescribir la sección propia.
- **`PROMPT-BOOTSTRAP.md`** — prompt one-shot para que el Claude del otro dev quede sincronizado con las
  mismas reglas. Se corre una sola vez por máquina.
- **`instrucciones_claude/00-setup-claude.md`** — instructivo humano de setup y convención de uso.

Reglas duras:
- Si te piden tocar archivos fuera del área de propiedad del usuario activo, **parar y avisar** antes de modificar nada.
- No editar entradas viejas del DIARIO. Si algo cambió, agregar entrada nueva de "corrección".
- Respuestas concisas. Si algo se dice en 2 líneas, no decirlo en 10.

## Contrato front ↔ back

- **Paginación.** Respuesta unificada `PageResponse<T>` con `content, page, size, totalElements, totalPages, first, last`.
  El front mapea 1:1 — al crear un DTO nuevo en el back, Fran agrega un `type` espejo en `frontend/src/types/`.
  No hay codegen: se sincroniza a mano. Fuente de verdad: Swagger (`http://localhost:8080/swagger-ui.html`)
  + `instrucciones_claude/05-api-endpoints.md`.
- **JWT — dos namespaces de authorities.**
  - `realm_access.roles` → prefijo `ROLE_` (ej. `ROLE_ADMIN`, `ROLE_NUTRICIONISTA`).
  - `resource_access.nutriapp-backend.roles` → authority pelada (ej. `recetas:write`, `admin:manage`).
  Los endpoints usan `@PreAuthorize("hasAuthority('<permiso>')")` sobre el segundo namespace.
- **Errores.** El front parsea el `ApiError` del backend en `api/client.ts` y surfacea `message` al usuario
  (lección imedba: nunca mostrar "HTTP 409" pelado).
- **CORS.** Dev: `http://localhost:5173`. Prod: `APP_CORS_ALLOWED_ORIGINS`.

## Referencias

- `instrucciones_claude/00-setup-claude.md` — setup y convención de trabajo entre dos Claudes
- `instrucciones_claude/DIARIO.md` — bitácora compartida append-only
- `instrucciones_claude/ESTADO.md` — snapshot de trabajo en curso
- `PROMPT-BOOTSTRAP.md` — prompt de bootstrap para el segundo dev
- `instrucciones_claude/01-arquitectura-sistema.md` — arquitectura completa + flujo receta→cupón→conversión
- `instrucciones_claude/02-entidad-relacion.md` — DDL de las entidades
- `instrucciones_claude/03-integraciones-apis.md` — investigación Contabilium + TiendaNube + patrón stub/live
- `instrucciones_claude/04-plan-de-fases.md` — plan de fases + asignación Santi/Fran + preguntas abiertas para Gon
- `instrucciones_claude/05-api-endpoints.md` — contrato REST completo
- `instrucciones_claude/06-cambios-post-demo-2026-07-31.md` — los 17 cambios de la demo con Gon + Leo, en 4 olas
- `instrucciones_claude/07-maestro-articulos-y-catalogo.md` — maestro de artículos de TBC + ajustes de catálogo (Ola 3)
- `presupuesto_nutriapp.pdf` — presupuesto firmado con el cliente (alcance comprometido)

## Comandos comunes

### Backend / infra

- `docker compose up -d --build` / `docker compose down` / `docker compose logs -f --tail=200` — ciclo dev.
- `docker compose down -v` — reset total (borra volúmenes, re-corre init de Postgres y seeds).
- `docker compose exec db psql -U nutriapp -d nutriapp` — shell psql.
- `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` — backend fuera de Docker.
- `cd backend && ./mvnw test` — tests (requiere Java 21 en el host; si no, compilar en contenedor).
- Prod: `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build`.

### Frontend

- `cd frontend && npm install` / `npm run dev` (5173) / `npm run build` (tsc + vite) / `npm run lint`.

### Puertos de desarrollo

| Servicio           | URL                                      |
| ------------------ | ---------------------------------------- |
| Frontend (Vite)    | `http://localhost:5173`                  |
| Backend (Spring)   | `http://localhost:8080`                  |
| Swagger UI         | `http://localhost:8080/swagger-ui.html`  |
| Keycloak           | `http://localhost:8081`                  |
| Postgres           | `localhost:5432` (user `nutriapp`)       |

> Ojo en la máquina de Santi: el 8080 puede estar ocupado por otro proyecto Docker (plataforma GIA).
> Si pasa, usar `BACKEND_PORT` en `.env` (patrón imedba: backend en 8088).

## Secretos

- No commitear `.env`. Usar `.env.example` como plantilla.
- **La API key de Contabilium y los accesos de TiendaNube que pasó Gon por WhatsApp van SOLO en `.env`**
  (jamás en código, docs, seeds ni commits). Si alguna credencial se filtra a un commit, se rota con el cliente.
- Token de acceso TiendaNube (OAuth): se persiste cifrado o va por env; nunca en el repo.
- En prod: secret manager del hosting.
