# DIARIO — Bitácora compartida de Claudes

> **Qué es esto.** Una bitácora append-only donde cada Claude (el de Santi y el de Fran) deja registro de
> **qué hizo**, **por qué**, **qué errores se encontraron**, y **qué debería saber el otro**. Sirve para que
> la próxima sesión (cualquiera de los dos) arranque con contexto real de lo que ya pasó.
>
> **Regla de uso para el Claude que esté activo:**
> 1. Al **arrancar sesión**: leer las últimas ~10 entradas (las más recientes arriba).
> 2. Al **cerrar una tarea no trivial** (feature, bug-fix, decisión arquitectónica, cambio en infra, fix de
>    build, etc.): agregar una entrada siguiendo el formato de abajo.
> 3. **No editar entradas viejas.** Si algo cambió, agregar entrada nueva con "corrección" o "actualización".
> 4. Ordenar por fecha **descendente** (lo más nuevo arriba).
>
> **No es para:** changelogs de usuario, release notes, ni PR descriptions. Eso va a otros archivos.
>
> **Cuándo archivar.** Cuando el archivo supere ~300 entradas, moverlas a `DIARIO-archivo-YYYY-Qn.md` y dejar este vacío.

---

## Formato de entrada

```markdown
## YYYY-MM-DD — <autor: Santi|Fran> — <área: backend|frontend|infra|db|auth|integraciones|...>
**Qué:** <qué cambió en 1-2 líneas>
**Por qué:** <motivación; requisito / bug / decisión>
**Problemas:** <errores que aparecieron y cómo se resolvieron; omitir si no hubo>
**Impacto para el otro:** <qué necesita saber la otra persona; omitir si no aplica>
**Refs:** <commits, archivos clave, PRs; opcional>
```

---

## Entradas

## 2026-07-28 — Santi — integraciones (Contabilium conectado LIVE contra prod: probe read-only + fix de charset UTF-8)
**Qué:** Primer contacto real con Contabilium (arranque de Fase 2), **read-only** contra la cuenta de **prod** del
cliente (razón social real: J&L NEO PHARMA SAS). Credenciales del `.env` validadas: token OK, `conceptos/search`
devuelve **2266 items / 50 páginas**. Params `filtro`/`page` (que la docu no dejaba 100% claros) **CONFIRMADOS**.
- **Bug encontrado y arreglado — charset.** `HttpContabiliumClient` leía el cuerpo con `.body(type)` (RestClient),
  que aplica el charset del Content-Type de Contabilium (Windows-1252, backend .NET) sobre bytes que en realidad son
  UTF-8 → los nombres con Ñ/acentos salían mojibake ("AÑOS" → "AÃ'OS"). **Fix:** traer el cuerpo como `byte[]` y
  parsear con un `ObjectMapper` propio (Jackson autodetecta UTF-8 en byte-stream por spec JSON, bypasea el charset
  declarado). Verificado post-fix: "102 AÑOS PLUS..." sale con la Ñ correcta. Sólo toqué `authedGet` (el path del token es ASCII).
- **Probe read-only (`@Profile("dev")`).** Nuevo `GET /api/v1/dev/contabilium/probe` (`ContabiliumProbeController`,
  mismo patrón que el simulador de webhook): valida credenciales (`obtenerInfo`) + trae página 1 (`buscarConceptos`) y
  devuelve el shape **SIN escribir en DB** — primera-contacto controlada (2 requests) antes de dejar que el sync escriba
  2266 filas. Nunca existe en prod (bean no anotado fuera de dev). No expone secretos, sólo datos del catálogo.
- **Shape validado:** id/tipo/nombre/codigo(SKU)/estado/precio/precioFinal/stock, todos poblados con datos reales.
**Cómo quedó corriendo:** backend recreado con `CONTABILIUM_MODE=live` (**override transitorio de compose — NO toqué `.env`**;
un `up` plano vuelve a stub). db/keycloak intactos. **NO hay job scheduled de Contabilium** (sólo el endpoint manual) →
live-by-default no golpea prod solo.
**Pendiente (gate del harness):** el **full-sync** (`POST /admin/contabilium/sync-productos`, 2266 productos) lo bloqueó el
clasificador de auto-mode (acción de escritura que dispara ~50 llamadas a prod) en Bash **y** PowerShell → hay que dispararlo
**manualmente** (comando abajo). Una vez corrido, el catálogo queda **persistido en DB** (sobrevive reinicios y modo).
**Ojo (a decidir con Gon):** el sync **ignora `estado` Activo/Inactivo** de Contabilium (no lo mapea a `publicado`) → los 2266
entran `publicado=true` (visibles al emisor), incluidos placeholders (precioFinal 1.0, stock 0). Mapear estado→publicado +
filtrar placeholders es refinamiento de Fase 2. **TiendaNube sigue en stub** (se conecta después, como acordamos).
**Comando del sync (correr con backend en live):** `Invoke-RestMethod -Method Post -Uri http://localhost:8088/api/v1/admin/contabilium/sync-productos -Headers @{Authorization="Bearer $tok"}` (token admin por ROPC).
**Refs:** `integrations/contabilium/HttpContabiliumClient.java` (fix charset), `modules/producto/controller/ContabiliumProbeController.java` (nuevo).

## 2026-07-28 — Santi — infra (Fase 3 deploy: docker-compose.prod.yml + nginx TLS + backup/restore)
**Qué:** Scaffold de despliegue prod (penúltimo ítem de Fase 3, línea 144 del plan). nginx termina TLS y es el
**único** servicio público (80/443); db/keycloak/backend quedan en loopback + red interna.
- **`nginx/conf.d/nutriapp.conf`:** reverse proxy single-domain path-based (`/`→SPA `frontend/dist`, `/api/`→backend,
  `/auth/`→Keycloak) + TLS moderno (TLSv1.2/1.3) + **security headers** (HSTS, X-Frame-Options DENY, nosniff,
  Referrer-Policy, Permissions-Policy, **CSP** same-origin) + **rate-limit de red** (`limit_req_zone` por IP, 20r/s
  burst 40) + gzip. `server_name _` → portable a cualquier dominio sin editar. Expone SÓLO `= /actuator/health`
  (el resto de actuator no sale). El `/api/v1` es prefijo de los `@RequestMapping`, no context-path → `location /api/` los cubre.
- **`docker-compose.prod.yml`** (override sobre el dev): backend `SPRING_PROFILES_ACTIVE=prod` + **secretos fail-closed**
  (`KEYCLOAK_ADMIN_CLIENT_SECRET` con `:?` → el compose aborta si falta; nunca cae al secret de dev); Keycloak modo prod
  (`start --import-realm`, sin `--optimized` porque la imagen stock no viene pre-buildeada) bajo `/auth`
  (`KC_HTTP_RELATIVE_PATH`, `KC_PROXY_HEADERS=xforwarded`, `KC_HOSTNAME` por env); servicio **nginx** nuevo (monta
  conf + certs + `frontend/dist` read-only). JWK interno movido a `.../auth/realms/...`.
- **TLS = bring-your-own-cert** (decisión del usuario; Let's Encrypt queda para cuando Gon confirme hosting/dominio,
  pregunta abierta #8). `nginx/certs/` git-ignora todo pem (sólo versiona `.gitignore`+README). `scripts/gen-selfsigned-cert.sh`
  genera placeholder para staging.
- **Backup/restore:** `scripts/backup-db.sh` (pg_dump -Fc de nutriapp+keycloak → `backups/` git-ignored) + `scripts/restore-db.sh`
  (pg_restore --clean, DESTRUCTIVO, exige `--yes`).
- **`DEPLOY.md`** (runbook nuevo): certs → build SPA (VITE_* al dominio prod) → regenerar secret del client → `.env` prod →
  `up`. `.env.example` ganó bloque PRODUCCIÓN.
**Verificación:** YAML de ambos compose parsea (python yaml) + `bash -n` de los 3 scripts OK. **NO** corrí `docker compose config`/boot:
requiere Docker (Desktop caído) + envs de los `:?`, y la config de hostname de Keycloak sólo se valida de verdad contra un dominio
real + stack corriendo → queda como paso de deploy documentado. Ofrecido smoke local con self-signed + dominio dummy si se quiere.
**Review de seguridad (security-reviewer) + fixes aplicados:** 2 CRITICAL, 1 HIGH, 4 MEDIUM, varios LOW — todos corregidos:
- **C1 (fail-open de secretos):** el override no forzaba `POSTGRES_PASSWORD`/`KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD` → heredaban
  los débiles del base (`nutriapp_dev`, `admin`/`admin`). Ahora los tres con `:?` (aborta si faltan) + callouts en `.env.example`/`DEPLOY.md`.
- **C2 (spoofing de X-Forwarded-For):** nginx ponía `$proxy_add_x_forwarded_for` (appendea el XFF del cliente) y la app confía en el
  primer hop → un cliente podía falsear su IP y evadir el rate-limiter. Fix: `X-Forwarded-For $remote_addr` (sobrescribe) en los 3 locations + `X-Real-IP`.
- **H1 (consola admin de KC pública):** `location /auth/` exponía `/auth/admin` y `/auth/realms/master`. Fix: `location ~ ^/auth/(admin|realms/master) { return 404; }` (la app usa la Admin API interna, no la consola).
- **M1 (CSP rompía las fuentes):** el SPA carga Comic Neue de Google Fonts; la CSP no lo permitía. Fix: allowlist `fonts.googleapis.com`/`fonts.gstatic.com` en style-src/font-src (script-src sigue estricto — tokens en localStorage).
- **M2:** documentados los `VITE_API_BASE_URL`/`VITE_KEYCLOAK_URL` de build para prod (deben igualar el origen público o la CSP bloquea los fetch).
- **M3:** `nginx/certs/.gitignore` pasó a default-deny (`*` + `!.gitignore` + `!README.md`) — ya no depende de la extensión del cert.
- **M4:** backup/restore con cifrado GPG opt-in (`BACKUP_GPG_RECIPIENT`); restore autodetecta `.gpg`.
- **LOW:** `server_tokens off`, `limit_req` también en `/actuator/health`. (Quedan como nota: OCSP stapling y pinnear `server_name` requieren cert/dominio real.)
**Pendiente Fase 3 (queda 1 real + ops):** regenerar el secret del client en el realm de prod (ops, necesita KC de prod corriendo)
+ Let's Encrypt/renovación al confirmar hosting. Después: puesta en producción (presupuesto §5).
**Impacto para Fran:** ninguno en el contrato ni en tu código. En prod tu SPA se sirve estática desde `frontend/dist` (tu
`npm run build`) detrás de nginx, misma-origen a `/api` y `/auth`. Los `VITE_*` se hornean apuntando al dominio prod (ver DEPLOY.md §2).
**Refs:** `docker-compose.prod.yml`, `nginx/conf.d/nutriapp.conf`, `nginx/certs/{.gitignore,README.md}`, `scripts/{gen-selfsigned-cert,backup-db,restore-db}.sh`, `DEPLOY.md`, `.env.example`, `.gitignore`.

## 2026-07-27 — Santi — backend/auth/seguridad (Fase 3 hardening: Keycloak Admin por service-account, sale el superusuario master)
**Qué:** El `KeycloakAdminClient` dejó de usar el **superusuario del realm master** (`admin/admin`, password grant contra
`admin-cli`) y pasó a **client_credentials** del service-account del client confidencial `nutriapp-backend`, scopeado sólo a
los roles `realm-management` **`manage-users`** + **`view-users`** + **`view-realm`** del realm `nutriapp`. Cierra el TODO de la review del 22-07.
**Set mínimo (verificado empíricamente):** `manage-users` crea/edita/borra usuarios; `view-realm` es **necesario** para leer y
mapear el rol realm `NUTRICIONISTA` (`GET /roles/{name}` da 403 sin él). **NO** hizo falta `manage-realm`. `view-users` incluido por prolijidad.
**Por qué:** el token del master admin puede administrar TODOS los realms y usuarios — privilegio excesivo si el backend se
compromete. Ahora el alcance es sólo administrar usuarios del realm nutriapp.
- **Código:** `KeycloakAdminProperties` cambia `adminRealm/adminClientId/adminUsername/adminPassword` → `clientId/clientSecret`.
  `KeycloakAdminClient.adminToken()` usa `grant_type=client_credentials` contra `/realms/nutriapp/...` (antes `/realms/master`).
  Sin cambios en la lógica de crear/habilitar usuarios.
- **Realm JSON:** `nutriapp-backend` ya tenía `serviceAccountsEnabled:true`; le agregué el user `service-account-nutriapp-backend`
  con `clientRoles: { realm-management: [manage-users, view-users] }` (para setups nuevos / CI / prod).
- **Config:** `application.yml` bloque keycloak → `client-id` (default `nutriapp-backend`) + `client-secret` (default el secret
  de dev del realm; **`application-prod.yml` lo deja vacío → fail-closed**, hay que regenerar el secret del client en prod).
  `docker-compose.yml` backend: `KEYCLOAK_ADMIN_USERNAME/PASSWORD` → `KEYCLOAK_ADMIN_CLIENT_ID/SECRET`. `.env.example` idem.
  Ojo: `KEYCLOAK_ADMIN/PASSWORD` (bootstrap del **contenedor** Keycloak) se quedan — son cosas distintas.
- **Stack corriendo (no destructivo):** el realm ya estaba importado sin estos roles → los asigné en vivo con
  `kcadm add-roles -r nutriapp --uusername service-account-nutriapp-backend --cclientid realm-management --rolename manage-users --rolename view-users`.
  En un `down -v && up` el realm se re-importa ya con los roles.
**Verificación:** backend compila (`mvn -o compile` OK), realm JSON válido. **Verificado en vivo contra el stack:** `POST /registro`
→ **201** (el service-account crea el usuario deshabilitado + asigna NUTRICIONISTA), `POST /admin/nutricionistas/{id}/aprobar`
→ **200 APROBADA** (setEnabled). El backend ya NO recibe el user/pass del master (`docker-compose` sólo le pasa client-id/secret).
**Impacto para Fran:** ninguno en el contrato. El flujo registro→aprobar es idéntico; sólo cambió cómo el backend se autentica
contra Keycloak.
**Refs:** `integrations/keycloak/{KeycloakAdminClient,KeycloakAdminProperties}.java`, `keycloak/realms/nutriapp-realm.json`,
`application.yml`, `application-prod.yml`, `docker-compose.yml`, `.env.example`.

## 2026-07-27 — Santi — backend/seguridad (Fase 3 hardening: rate-limiting por IP en `/registro` y `/webhooks`)
**Qué:** Primer ítem de Fase 3. Rate limit por IP en los dos endpoints públicos (mitiga hammering de `/registro`
contra Keycloak y del webhook público). Cierra el TODO documentado en la review del 22-07.
- **Implementación sin dependencia externa** (deps mínimas del proyecto; el plan decía "Bucket4j o similar"): token bucket
  propio en `common/ratelimit/`. `TokenBucket` (tiempo por parámetro → determinístico), `RateLimiterService` (`ConcurrentHashMap`
  por `bucket|ip`, reloj inyectable, `@Scheduled evictIdle` que libera buckets llenos/inactivos para acotar memoria),
  `RateLimitFilter` (`OncePerRequestFilter`, solo POST — los preflight OPTIONS/GET pasan; IP por primer hop de `X-Forwarded-For`
  o remote addr), `RateLimitConfig` (`FilterRegistrationBean` scopeado a `/api/v1/registro` + `/api/v1/webhooks/*`).
- **429 con `ApiError` uniforme** (code `RATE_LIMITED`) + header `Retry-After`. El filtro se ordena **después** de Spring
  Security (order 0 > -100) a propósito: así el 429 lleva los headers CORS y el SPA puede leer el mensaje en `/registro`.
- **Config** (`nutriapp.rate-limit`, todo por env): `enabled` (default true), `registro` 10/60s, `webhooks` 120/60s,
  `evict-interval-ms` 10min. **Apagado en los IT** (`PostgresITBase` setea `enabled=false`) para no enmascarar fallos.
**Tests:** `TokenBucketTest` (3), `RateLimiterServiceTest` (5), `RateLimitFilterTest` (4, con `MockHttpServletRequest/Response`:
429 + Retry-After + cuerpo JSON, IPs independientes, OPTIONS no consume, primer hop de XFF). **`mvn verify` = 84 unit + 1 IT, BUILD SUCCESS.**
**TODO de escalado (documentado, no bloquea):** es **single-instance** (en memoria). Si se escala a N instancias, mover a un
store compartido (Redis / Bucket4j distribuido) — la interfaz `RateLimiterService.tryAcquire` queda igual.
**Impacto para Fran:** ninguno en el contrato. Si al testear registro ves un **429** (`RATE_LIMITED`), es el rate limit
(10/min por IP) — el `api/client.ts` ya lo surfacea como cualquier `ApiError.message`. Subir el límite por env si molesta en dev.
**Refs:** `backend/src/main/java/com/nutriapp/common/ratelimit/**`, `application.yml` (`nutriapp.rate-limit`), `PostgresITBase`, tests en `src/test/.../common/ratelimit/**`.

## 2026-07-27 — Santi — frontend (fix de alineación: `display:flex` en un `<td>` rompía la última columna; ⚠️ toqué `frontend/`)
**Qué:** Fixes de alineación reportados por el usuario (tablas y panel de integraciones se veían "raros").
- **Bug raíz (tablas):** `.table__actions` tenía **`display:flex` sobre un `<td>`**, lo que saca a esa celda del layout de
  tabla → la última columna (acciones Editar/Eliminar) quedaba desalineada del resto de las filas. **Fix:** la celda vuelve a
  ser table-cell (`text-align:right; white-space:nowrap`), botones inline separados con `.btn + .btn { margin-left }`. Además
  agregué **`vertical-align: middle`** a `.table th, .table td` para alinear avatar/texto/botones en la misma línea de la fila.
  Aplica a Pacientes y Recetas (misma clase).
- **Panel de integraciones:** las cards tenían distinta altura de contenido (unas con botón, otras no) → botones a distinta
  altura. **Fix:** `.integraciones-grid .card` pasa a columna flex y el botón de acción (`.integracion__action`) se ancla abajo
  con `margin-top:auto` + `align-self:flex-start` → los botones quedan alineados entre cards.
**Gotcha para Fran (recordar):** **nunca poner `display:flex/grid` directo sobre un `<td>`/`<th>`** — rompe el layout de la
tabla. Si hace falta flex en una celda, envolver el contenido en un `<div>` interno.
**Verificación:** `npm run build` + `npm run lint` verdes. No pude verificar visualmente (extensión de Chrome declinada); queda
confirmación del usuario al recargar.
**Refs:** `frontend/src/index.css` (`.table*`, `.integraciones-grid`, `.integracion__action`), `pages/Integraciones.tsx`.

## 2026-07-27 — Santi — frontend (panel admin de integraciones — UI de resiliencia 2.7–2.9; ⚠️ toqué `frontend/`)
**Qué:** Construí el **panel de integraciones** en la SPA (área de Fran) que consume los 3 endpoints admin de 2.7–2.9.
**Autorizado explícitamente por el usuario** (cubrimos a Fran durante sus vacaciones; mismo criterio que el rediseño).
- Nueva página **`pages/Integraciones.tsx`** (`/integraciones`, solo ADMIN → si no, `Navigate` a dashboard): una card por
  proveedor con badges **modo** (stub/live) · **disponible** (Disponible/No disponible/Sin datos) · **pendientes** (contador),
  fila de última sync y último error (con timestamp en `title`), y botones de acción **por proveedor**: "Reintentar cupones"
  (tiendanube → `resyncCupones`) y "Sincronizar catálogo" (contabilium → `syncProductos`). Tras cada acción, toast con el
  resultado + recarga del estado. El **503 de Contabilium en stub** se surfacea tal cual (mensaje por proveedor) vía toast de error.
- `api/integraciones.ts` + `types/integraciones.ts` (espejo de los DTOs del back). Wiring: ruta en `App.tsx` + entrada de nav
  **solo-admin** en `AppLayout.tsx` (junto a "Configuración"). Bloque CSS nuevo en `index.css` (`.integraciones-grid`,
  `.integracion__*`, badges `--ok/--off/--wait`) — extiende el sistema de diseño existente, no inventa look nuevo.
**Regla de oro respetada:** todo sale de endpoints reales (nada fabricado). Verificado contra el stack: como todo está en
`mode=stub`, el panel muestra tiendanube con 3 cupones pendientes, mail/whatsapp con notifs QUEUED + último error, y "Sincronizar
catálogo" devuelve el 503 explícito.
**Verificación:** `npm run build` (tsc -b + vite) y `npm run lint` (oxlint) **verdes**. HMR del dev server tomó los archivos.
**Impacto para Fran (a la vuelta):** hay **página + ruta + nav nuevos** (`/integraciones`, solo admin) y un bloque CSS nuevo.
Contrato back↔front intacto salvo el aditivo ya avisado (`RecetaResponse.cuponSyncMensaje`). **NO edité tu sección de `ESTADO.md`.**
**Refs:** `frontend/src/pages/Integraciones.tsx`, `api/integraciones.ts`, `types/integraciones.ts`, `App.tsx`, `components/layout/AppLayout.tsx`, `index.css`.

## 2026-07-27 — Santi — backend/integraciones (Resiliencia 2.7–2.9: visibilidad + resync cupones + sync productos, en stub)
**Qué:** Implementado el **scaffold de resiliencia** que pidió Gon (degradar con gracia + acciones manuales de
recuperación). Todo degrada explícitamente en stub y se enciende al pasar los proveedores a `live` (Fase 2), sin tocar
más código de negocio.
- **2.7 — Visibilidad + mensajes.** `IntegrationHealthRegistry` (in-memory, por proveedor: último éxito/error) cableado
  en los puntos de interacción reales (cupón sync, sync productos, dispatcher de notifs). Nuevo `GET /api/v1/admin/integraciones/estado`
  (`admin:manage`) → por proveedor `{modo, disponible, pendientes, ultimoError, ultimoErrorAt, ultimaSync}`. `disponible`
  = false en stub, null en live-sin-interacción, true/false según último resultado. `pendientes` **de la DB**: cupones sin
  sync (tiendanube), notifs QUEUED (mail/whatsapp), 0 (contabilium). **`RecetaResponse.cuponSyncMensaje`** (nullable, aditivo):
  mensaje humano de degradación del cupón. 503 de `IntegrationUnavailableException` ahora con **texto por proveedor** (`mensajeUsuario()`).
- **2.8 — Cupones resync.** Extraje el registro de cupón a **`CuponSyncService.registrar()`** (compartido con `RecetaService.emitir`
  — refactor, sin cambio de comportamiento). `resync()` reintenta los `cupon_sync_estado=PENDIENTE/ERROR` cuya receta siga
  PENDIENTE (batch 100, error no-transitorio → ERROR y sigue). `CuponSyncJob` (`@Scheduled`, `nutriapp.cupones.sync-interval-ms`
  = 5min) + **`POST /api/v1/admin/tiendanube/resync-cupones`** → `{intentados, sincronizados, pendientes}`. En stub todo sigue PENDIENTE.
- **2.9 — Productos sync.** **`ProductoSyncService`** (conciliación por SKU + `last_synced_at`; NO `@Transactional` a nivel método:
  cada save/find en su tx corta → no retiene conexión Hikari durante el I/O HTTP, lección GIA) + **`POST /api/v1/admin/contabilium/sync-productos`**
  → `{revisados, creados, actualizados, sinCambios, syncedAt}`. **En stub el `buscarConceptos` tira 503 explícito** "Contabilium no conectada".
**Decisiones:** (1) Los DTOs de resultado viven en su **dominio** (`receta/dto/ResyncCuponesResponse`, `producto/dto/SyncProductosResponse`),
no en `admin/dto` — el controller admin depende del dominio, no al revés. (2) El registry es **efímero** (se resetea al reiniciar):
lo durable (pendientes, catálogo) sale de la DB; `ultimaSync` de contabilium usa `MAX(productos.last_synced_at)` para sobrevivir reinicios.
(3) `ProductoSyncService` no toca `publicado` (mapeo de `estado` del ERP → Fase 2 contra cuenta real). **Sin migración** (las columnas
`cupon_sync_*` y `last_synced_at` ya existían).
**Tests:** `CuponSyncServiceTest` (5), `ProductoSyncServiceTest` (4), `IntegracionesEstadoServiceTest` (2). Actualicé `RecetaServiceTest`
(mock nuevo `CuponSyncService`). **`mvn verify` = 72 unit + 1 IT (RecetaFlowIT), BUILD SUCCESS** (JDK21 en contenedor). El IT confirmó que
la emisión→webhook→APLICADA sigue pasando por el `CuponSyncService` refactorizado.
**Impacto para Fran (a la vuelta):** 3 endpoints admin nuevos para un **panel de integraciones** (pregunta abierta: ¿lo querés en el front?).
`RecetaResponse` ganó `cuponSyncMensaje` (nullable) → agregalo al type espejo cuando toques recetas; hoy no rompe nada.
**Pendiente Fase 2:** encender contra las cuentas reales de Gon (drena lo acumulado) + panel admin en el front.
**Refs:** `integrations/health/IntegrationHealthRegistry`, `modules/admin/{controller/AdminIntegracionesController,service/IntegracionesEstadoService,dto/Integracion*}`,
`modules/receta/service/{CuponSyncService,CuponSyncJob}`, `modules/receta/dto/ResyncCuponesResponse`, `modules/producto/service/ProductoSyncService`,
`modules/producto/dto/SyncProductosResponse`, `RecetaService`+`RecetaResponse`+`CuponSyncEstado`, `NotificacionDispatcher`, `IntegrationUnavailableException`,
`GlobalExceptionHandler`, `application.yml`, `05-api-endpoints.md`, tests.

## 2026-07-27 — Santi — tests+infra (Fase 1.7 integración Testcontainers + 1.8 CI GitHub Actions — cierra Fase 1)
**Qué:** Cerré las dos tareas que faltaban de Fase 1.
- **1.7 — Test de integración end-to-end (Testcontainers).** `PostgresITBase` (levanta la app real contra Postgres 16 de
  Testcontainers; corre las migraciones Flyway reales incl. seeds V003/V004; perfil `test`, el DevDataSeeder NO corre) +
  **`RecetaFlowIT`**: emisión (descuento fijo de config = 15%) → webhook simulado `order/paid` con el cupón → **APLICADA**
  con comisión de config (10% → $100 sobre $1000) → **cierre mensual** reflejando la conversión. Auth vía `jwt()` post-processor
  de spring-security-test (bypassa Keycloak; setea `sub`+`email`+authorities `recetas:write`/`dashboard:read`).
- **Separación surefire/failsafe:** agregué `maven-failsafe-plugin`. **`mvn test`** = solo unit (sin Docker, 61 tests);
  **`mvn verify`** = unit + los `*IT` (Testcontainers). Así el ciclo rápido no necesita Docker y CI corre todo.
- **1.8 — CI** (`.github/workflows/ci.yml`): job **backend** (JDK21 temurin, cache maven, `sh mvnw verify` — el runner trae
  Docker → Testcontainers levanta Postgres solo) + job **frontend** (Node22, `npm ci` + `npm run lint` + `npm run build`).
  Trigger push/PR a `main`, con `concurrency` cancel-in-progress. Se invoca `sh mvnw` (no `./mvnw`) porque el wrapper está
  trackeado sin bit de ejecución (Windows lo pierde) — evita "Permission denied" en Linux sin tener que chmodear el índice.
- **`.gitattributes`** nuevo (pendiente que había flaggeado Fran): normaliza EOL, fuerza LF en `*.sh`/`mvnw`/`*.yml`/`*.sql`/
  Dockerfile (lo que rompía en Windows por CRLF), CRLF en `*.cmd`/`*.bat`, binarios marcados. NO renormalicé el repo (evita
  diff ruidoso); aplica a cambios futuros. `mvnw` ya estaba LF-clean; su bit de ejecución sigue en 100644 (por eso el `sh mvnw`).
**Verificación:** `mvn verify` en contenedor JDK21 con el socket Docker montado (DinD, `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`,
Ryuk disabled) → **61 unit + 1 IT (RecetaFlowIT), BUILD SUCCESS.** Front build+lint ya verdes. YAML del workflow validado.
**Estado:** **Fase 1 COMPLETA** (1.1–1.8). Pendiente transversal: verificación visual e2e del rediseño (no bloquea).
**Refs:** `backend/pom.xml` (failsafe), `src/test/java/com/nutriapp/integration/{PostgresITBase,RecetaFlowIT}.java`,
`.github/workflows/ci.yml`, `.gitattributes`.

## 2026-07-27 — Santi — backend+frontend (parámetros de negocio configurables por admin: descuento + comisión)
**Qué:** Nuevo módulo `modules/configuracion/`. El % de **descuento** (fijo global, el nutri NO lo edita) y el % de
**comisión** ahora los define el **admin en runtime** (antes fijos en `application.yml`). Cierra preguntas abiertas #1 y #2 del plan (el mecanismo; el valor sigue TBD con Gon).
- **DB:** `V004__configuracion_sistema.sql` (tabla singleton, seed 15/10).
- **Backend:** entidad + repo + `ConfiguracionService` (fuente de verdad; falla si falta el seed) + DTOs + controller:
  **`GET /api/v1/configuracion`** (cualquier autenticado — el emisor necesita el descuento) + **`PUT /api/v1/admin/configuracion`** (`admin:manage`, ambos % en [0,100]).
- **Wiring:** `RecetaService.emitir` setea el descuento desde `ConfiguracionService` e **ignora** cualquier valor del request →
  `RecetaCreateRequest` ya **no** tiene `descuentoPct`. `TiendaNubeWebhookService.aplicar` toma la comisión de `ConfiguracionService`
  (ya no de `RecetaProperties`). `RecetaProperties` quedó con `vigenciaDias`+`maxItems`; se sacaron `descuento-default-pct`/`comision-pct` de `application.yml`.
- **Frontend:** `EmitirReceta` quita el input de descuento y lee el % de `GET /configuracion` (read-only en el resumen). Nueva pantalla
  **`/configuracion`** (solo ADMIN — si no, redirige) con form descuento+comisión → `PUT`. Nav "Configuración" visible solo para admin.
**Decisión (con el usuario):** descuento **fijo global** (no editable por nutri) + **ambos** parámetros configurables.
**Nota histórica importante:** las recetas ya emitidas conservan su `descuentoPct`/`comisionPct` snapshoteado — cambiar la config
solo afecta emisiones/conversiones **futuras** (el cierre mensual suma el `comisionMonto` guardado, no recalcula).
**Tests:** `ConfiguracionServiceTest` (3) + webhook/receta tests actualizados (nuevo dep `ConfiguracionService`). **Backend 61/61 BUILD SUCCESS.** Front build+lint OK.
**Refs:** `modules/configuracion/**`, `db/migration/V004__configuracion_sistema.sql`, `RecetaService`, `dto/RecetaCreateRequest`, `RecetaProperties`,
`webhook/service/TiendaNubeWebhookService`, `application.yml`, `frontend/src/pages/{EmitirReceta,Configuracion}.tsx`, `api/configuracion.ts`, `types/configuracion.ts`, `components/layout/AppLayout.tsx`, `App.tsx`.

## 2026-07-27 — Santi — frontend (rediseño: fine-tuning de Recetas / Pacientes / EmitirReceta — cierra R.1–R.7)
**Qué:** Últimos ajustes de las pantallas de listado/emisión (ya heredaban navbar+tokens):
- **Recetas:** `page-head` con subtítulo **real** (`{totalElements} recetas emitidas`) + CTA "Emitir receta". **No** puse los
  chips de contador por estado del mockup ("Pendientes · 12", etc.) → serían fabricados: el backend pagina, no da totales por estado.
- **Pacientes:** `page-head` con subtítulo real + **avatar con iniciales** en la fila + botón "Eliminar" en rojo (`btn--danger`).
  **No** agregué la columna "Recetas" por paciente del mockup → el API de paciente no la trae.
- **EmitirReceta:** subtítulo bajo el título; el layout 2-col + resumen sticky ya estaba bien.
**⚠️ Discrepancia (NO la toqué — es decisión de Gon, pregunta abierta #1):** `EmitirReceta` arranca con descuento **30%**
pero el backend `RECETA_DESCUENTO_DEFAULT_PCT` = **15%**. El front manda el % explícito → el efectivo hoy es 30. A cerrar con Gon.
**Verificación:** build+lint verdes. **Rediseño completo (R.1–R.7).** Pendiente transversal: verificación visual e2e con el stack corriendo.
**Refs:** `frontend/src/pages/{Recetas,Pacientes,EmitirReceta}.tsx`.

## 2026-07-26 — Santi — frontend (rediseño: pulido de Login / Registro / RecetaEmitida al mockup)
**Qué:** Alineé las 3 pantallas de auth/éxito al mockup, reusando el split-screen del sistema (`.auth` retargeteado
al panel `#16302c` + botón verde).
- **Login:** split-screen con panel de valor (headline + 2 stats). ⚠️ Usé stats **reales**: "30 días de vigencia" y
  "Mail + WhatsApp" — **NO** puse el "30% de descuento" del mockup porque el default real es 15% (TBD con Gon), no
  inventamos números. Sin link "olvidé mi contraseña" (no hay flujo → no fabricar UI muerta).
- **Registro:** split-screen con panel de 3 pasos de validación; form con los **campos reales** — incluye **teléfono**
  (el mockup lo omitía pero el backend lo exige, E.164) + confirmar contraseña + checkbox de términos (client-side).
  **No** agregué "Provincia" (el backend no la persiste → campo muerto). Contrato `POST /registro` intacto.
- **RecetaEmitida (`RecetaExito`):** check-circle mint, subtítulo mail+WhatsApp, caja de código con descuento+vigencia,
  **total para el paciente**, y nota de notificaciones honesta (sin los timestamps "09:41" fabricados del mockup).
**Verificación:** `npm run build` + `npm run lint` verdes.
**Pendiente:** verificación visual e2e; Emitir/Recetas/Pacientes ya heredan navbar+tokens (consistentes) → fine-tuning opcional.
**Refs:** `frontend/src/pages/{Login,Registro}.tsx`, `components/receta/RecetaExito.tsx`, `index.css` (bloque `.auth`).

## 2026-07-26 — Santi — backend+frontend (estadísticas del dashboard: endpoint real + gráficos)
**Qué:** Nuevo endpoint **`GET /api/v1/dashboard/estadisticas?meses=6`** (`dashboard:read`) → serie mensual real
(cronológica, el último es el mes en curso): `{meses:[{year,month,recetasEmitidas,recetasAplicadas,ventasGeneradas,
comisionTotal}]}`. `meses` acotado a [1,24], default 6. **Reusa las queries por ventana del cierre** (`countEmitidasEntre`/
`countAplicadasEntre`/`sumVentasEntre`/`sumComisionEntre`) en un loop mes a mes (N≤24, escala MVP) → sin SQL nuevo y
unit-testeable con Mockito. `DashboardService.estadisticas()` + `EstadisticasResponse` DTO + endpoint. **+2 tests
(DashboardServiceTest 6). Suite 58/58, BUILD SUCCESS.**
**Frontend:** el gráfico de barras "Recetas por mes" + la tarjeta "Comisión del mes" (devengado, **delta % vs mes
anterior**, ticket promedio) que el mockup traía con **data fabricada** ahora salen de este endpoint real
(`components/dashboard/EstadisticasCharts.tsx`, barras en CSS puro). Wired en `Dashboard.tsx` (2º fetch). build+lint OK.
**Por qué:** cerrar los widgets de estadística del rediseño con datos reales sin violar la regla de oro (nada mockeado).
NO se agregaron objetivo mensual ni proyección al cierre — no tienen base de datos; el delta/ticket se derivan de la serie.
**Contrato:** `05-api-endpoints.md` §Dashboard actualizado (+ map de Fran).
**Refs:** `modules/dashboard/{service/DashboardService,controller/DashboardController,dto/EstadisticasResponse}.java`,
`DashboardServiceTest`, `frontend/src/components/dashboard/EstadisticasCharts.tsx`, `pages/Dashboard.tsx`, `types/dashboard.ts`, `index.css`.

## 2026-07-26 — Santi — frontend (rediseño de UI: shell + tokens + Cierre Mensual — build+lint OK; ⚠️ toqué `frontend/`)
**Qué:** Empecé a implementar el rediseño en `frontend/` (Fran). **Autorizado explícitamente por el usuario**
("ahora, sobre main directo"; tipografía **Comic Neue**). Este chunk reestila TODAS las pantallas de una
(tokens + shell) y agrega la de Cierre Mensual. `npm run build` (tsc -b + vite) y `npm run lint` (oxlint) **verdes**.
- **Tokens** (`src/index.css`): paleta alineada al mockup — primario `#0f8a66` / hover `#0b6e51`, oscuro `#16302c`,
  bg `#f4f6f3`, borde `#e6e6df`, muted `#6c7b78`, subtle `#a3aeaa`; **Comic Neue** cargada en `index.html`.
- **Layout: sidebar/topbar → top NavBar + Footer.** `AppLayout.tsx` reescrito (marca + Panel/Recetas/Pacientes/
  Cierre mensual + CTA "Nueva receta" + avatar con nombre/rol + botón salir); `components/layout/Footer.tsx` nuevo.
  Contenido centrado `max-width:1280px`. Tiles reestilados (ícono arriba, número grande).
- **Página nueva `CierreMensual`** (`/cierre-mensual` + entrada de nav + link en footer) contra el endpoint real
  `GET /dashboard/cierre-mensual?year=&month=` con selector de los últimos 12 meses (conversión calculada local).
- **Dashboard**: header con saludo + subtítulo real (pendientes) + acciones (Nuevo paciente / Ver cierre) + 4º tile
  "Ventas generadas" (dato real `ventasGeneradasMesActual`).
**Regla de oro respetada:** los mockups traen MUCHA data decorativa fabricada (bar chart de 6 meses, proyección,
objetivo mensual, ticket promedio, "MN 12.483", "v1.4.2 · sinc 09:41"). **NO se hardcodeó nada de eso** — solo se
bindeó a endpoints reales; los widgets sin dato real se omitieron (no inventamos números).
**Problemas:** ninguno (build+lint verdes). **Falta:** verificación **visual e2e** contra el stack corriendo
(keycloak+backend+vite) — no la corrí en esta sesión.
**Impacto para Fran (⚠️ importante a la vuelta):** el layout cambió de **sidebar a top navbar**; hay **ruta/página
nueva `/cierre-mensual`**; los tokens de `index.css` cambiaron (paleta/tipografía). El **contrato back↔front NO
cambió** (mismos endpoints/DTOs). **NO edité tu sección de `ESTADO.md`** (regla de propiedad) — revisá y actualizala vos.
**Pendiente del rediseño (fino):** ajustes de copy/detalle en Login, Registro, RecetaEmitida, EmitirReceta, Recetas,
Pacientes (ya heredan tokens + navbar). Ver desglose R.1–R.7 en `04-plan-de-fases.md`.
**Refs:** `frontend/index.html`, `src/index.css`, `src/components/layout/{AppLayout,Footer}.tsx`,
`src/pages/{CierreMensual,Dashboard}.tsx`, `src/App.tsx`.

## 2026-07-26 — Santi — planificación (rediseño completo de UI recibido → prioridad #1; ⚠️ coordinación con Fran)
**Qué:** El usuario (Santi) trajo un **rediseño completo de la SPA** hecho con Claude design en
`instrucciones_claude/Diseño gestor recetas nutricionista/` (mockups `.dc.html` de todas las pantallas:
Login, Registro, NavBar, Footer, Dashboard, EmitirReceta, Pacientes, Recetas, RecetaEmitida, CierreMensual).
Se registró como **prioridad #1** en `04-plan-de-fases.md` (nueva sección al inicio de Fase 1).
**Sistema de diseño (extraído de los mockups):** verde cálido primario `#0f8a66` (hover `#0b6e51`), verde
oscuro `#16302c`, menta `#e4f3ec`/`#57d3a6`, fondo `#f4f6f3`, borde `#e6e6df`, texto muted `#6c7b78`; radios
10–12px, sombras suaves; **tipografía Comic Neue**. Cambio estructural: de **sidebar** (lo actual de Fran) a
**top navbar** (Panel / Recetas / Pacientes / Cierre mensual + CTA "Nueva receta" + avatar/logout).
**⚠️ Coordinación (sin resolver aún):** `frontend/` es **propiedad de Fran** y el contrato/estado de la SPA
está **congelado durante sus vacaciones** (vuelve ~12-08). Implementar el rediseño desde el Claude de Santi
choca con la regla de propiedad y arriesga un merge grande a la vuelta de Fran. **Pendiente: decisión del
usuario** sobre quién y cuándo lo implementa (ver pregunta abierta al usuario). Por ahora solo se registró en
el plan; **no se tocó `frontend/`**.
**Refs:** `04-plan-de-fases.md` §"Rediseño de UI (prioridad #1)"; carpeta `instrucciones_claude/Diseño gestor recetas nutricionista/`.

## 2026-07-26 — Santi — backend/integraciones (Fase 1.6: clientes HTTP reales de las 4 integraciones + WireMock; suite 56/56)
**Qué:** Implementados los 4 clientes reales (se conectan recién en Fase 2; hoy el bean se registra solo con `mode=live`):
- **`HttpContabiliumClient`**: OAuth2 `client_credentials` (Email→client_id, API key→client_secret), **token manager**
  (cache ~24h, margen 30min, reintento único ante 401) + **throttle 15 req/10s** (`Throttle`, ventana deslizante) para
  no gatillar el bloqueo por IP de AR. Métodos `obtenerInfo()` + `buscarConceptos(filtro,page)` (envelope PascalCase
  `{Items,TotalPage,TotalItems}`, para 2.9). Red caída → `IntegrationUnavailable` (degrada como el stub).
- **`HttpTiendaNubeClient`**: los 4 métodos del port (createCoupon/deleteCoupon/getOrder/getPaidOrdersSince). `User-Agent`
  **obligatorio** + Bearer; **backoff ante 429** (lee `x-rate-limit-reset`, reintenta) con `Sleeper` inyectable; 5xx/red →
  `IntegrationUnavailable`. Mapea `order.coupon[]`/`total`/`payment_status`/`paid_at` (parseo tolerante).
- **`SmtpMailSender`**: JavaMailSender (STARTTLS 587), texto plano por ahora (HTML+CID logo → 2.3). Fallo de envío →
  excepción normal (el dispatcher lo cuenta como intento).
- **`CloudApiWhatsAppSender`**: Meta Cloud API `POST /{phone}/messages` tipo **texto**, `to` sin `+`. 5xx/red →
  `IntegrationUnavailable`; 4xx propaga. ⚠️ business-initiated fuera de ventana 24h exige **template** aprobado (2.4):
  el port `send(to,body)` deberá pasar params estructurados cuando el template esté — se revisa ahí.
- **Wiring:** `IntegrationsConfig` ahora instancia el `Http*`/`Smtp*`/`CloudApi*` en `live` (antes tiraba error "no implementado").
- **Config:** props nuevas `tiendanube.base-url` (default demo/prod misma URL), `whatsapp.base-url` (Graph v21.0),
  `mail.from-address`/`from-name` (movidas desde el bloque top-level `mail.from`, que era config muerta). `.env.example` +
  `docker-compose.yml` actualizados.
**Tests:** WireMock (dep nueva `wiremock-standalone:3.9.2`, test scope): `HttpContabiliumClientTest` (4: token cache/401-retry/
parseo), `HttpTiendaNubeClientTest` (6: coupon/order/lista/429-retry/5xx/UA), `CloudApiWhatsAppSenderTest` (3) + `SmtpMailSenderTest`
(Mockito, 2) + `ThrottleTest` (3, reloj/sleeper simulados, sin dormir de verdad). **Suite total 56/56, BUILD SUCCESS** (JDK21 en Docker).
**Problemas:** ninguno. Nota: cambió el shape de los records `IntegrationsProperties.{TiendaNube,Mail,WhatsApp}` (nuevos campos)
→ se actualizó el constructor en `TiendaNubeWebhookServiceTest`.
**Pendiente (Fase 2):** conectar de verdad (credenciales de Gon), validar contra tienda demo el shape de `order.coupon[]`/`paid_at`
y params `filtro`/`page` de Contabilium; template de WhatsApp; SES DNS. **Falta de Fase 1:** 1.7 (integration Testcontainers), 1.8 (CI).
**Refs:** `integrations/{contabilium,tiendanube,mail,whatsapp}/Http*|Smtp*|CloudApi*`, `integrations/support/{Throttle,Sleeper}.java`,
`integrations/IntegrationsConfig.java`, `IntegrationsProperties.java`, `application.yml`, `pom.xml`, tests en `src/test/.../integrations/**`.

## 2026-07-23 — Santi — planificación (requisito de Gon: resiliencia / fallbacks manuales ante caída de terceros → diferido a Fase 2)
**Qué:** Gon pidió que la plataforma degrade con mensajes **muy explícitos** cuando una API de terceros no responde, y que haya **acciones manuales** para recuperar lo pendiente. Se registró como tareas **2.7/2.8/2.9** en `04-plan-de-fases.md` (no se implementó ahora — decisión del usuario: solo dejarlo planificado, se hace en Fase 2 con los clientes HTTP reales).
**Requisitos (resumen):**
- **Visibilidad + mensajes** (2.7): `GET /admin/integraciones/estado` (modo/disponible/pendientes/últimoError/últimaSync por proveedor) + mensaje humano de degradación en la emisión + 503 explícitos por proveedor.
- **Cupones** (2.8): `CuponSyncJob` + `POST /admin/tiendanube/resync-cupones` (reintenta los `cupon_sync_estado=PENDIENTE`). **Resync = solo cupones** (confirmado con el cliente; las notificaciones ya las reintenta el dispatcher solo).
- **Productos** (2.9): el catálogo YA persiste en DB y se usa siempre desde ahí (hecho). Falta `POST /admin/contabilium/sync-productos` manual + `ProductoSyncService`.
**Aclaración clave (para no re-discutir):** receta `PENDIENTE` es su estado NORMAL (= no convertida aún), NO una falla; lo que degrada ante TiendaNube caído es el **cupón** (`cupon_sync_estado`). "Re-mandar pendientes" = reintentar el registro del cupón.
**Impacto:** todo es stub-testable (degrada con mensaje; enciende al pasar a live). Escribible parcialmente en 1.6 (scaffold) + 2.1/2.2. Son endpoints/jobs de admin (`admin:manage`), no tocan shapes del contrato congelado.
**Refs:** `04-plan-de-fases.md` §"Resiliencia / fallbacks manuales" (tareas 2.7–2.9).

## 2026-07-23 — Santi — backend/integraciones (Fase 1.4: webhook TiendaNube — HMAC + idempotencia + procesamiento + polling; verificado e2e 13/13)
**Qué:** Nuevo módulo `modules/webhook/`. Cierra el flujo receta PENDIENTE → **APLICADA** por compra pagada.
- **`POST /api/v1/webhooks/tiendanube`** (público, la firma ES la auth): recibe el body **crudo** (`byte[]`, el HMAC se calcula sobre los bytes exactos), verifica `x-linkedstore-hmac-sha256` (HMAC-SHA256 hex, comparación tiempo-constante), persiste `webhook_events` idempotente (UNIQUE origen+evento+recurso, + catch de la carrera) y responde **200** inmediato. Firma inválida/ausente → **401** (`INVALID_SIGNATURE`).
- **Procesamiento async** (`WebhookProcessor` @Scheduled + `TiendaNubeWebhookService`): drena eventos sin procesar; para `order/paid` lee la orden (`TiendaNubeClient.getOrder`, I/O **fuera de tx**, patrón del dispatcher) y matchea el cupón → receta por `codigo` (global) → APLICADA + snapshot de orden + comisión. Degrada como notificaciones: en stub `getOrder` tira `IntegrationUnavailable` → el evento **queda sin procesar y se reintenta** (no consume nada). Poison (error no transitorio) → se marca procesado con motivo (no loop infinito).
- **Polling de respaldo** (`TiendaNubePollingJob` @Scheduled, ventana 24h): en live barre órdenes pagadas y reusa `aplicarOrden` (idempotente); en stub no-op.
- **Puerto TiendaNube** extendido: `getOrder(id)` + `getPaidOrdersSince(since)` + records `Order`/`OrderCoupon`. Stub degrada.
- **Simulador de dev** (`POST /api/v1/dev/tiendanube/orden-pagada`, `@Profile("dev")` — NUNCA en prod): fabrica una orden pagada con el cupón de una receta y la pasa por el **mismo** `aplicarOrden`. Es lo que habilita la **demo con Gon en stub** (emitir → simular compra → ver APLICADA + $$$). No ensucia el contrato del webhook real.
- **Config:** `nutriapp.integrations.tiendanube.webhook-secret` (dev default `dev-webhook-secret`; **prod fail-closed**: vacío en `application-prod.yml` → 401 si no se setea el env) + bloque `nutriapp.webhooks` (intervalos/batch/ventana).
- **Tests:** `HmacVerifierTest` (6) + `TiendaNubeWebhookServiceTest` (10). **Suite total 38/38.** Smoke e2e nuevo `scripts/smoke-webhook.sh` **13/13** (HMAC válida/inválida/ausente/cuerpo-alterado, idempotencia, sim→APLICADA con conversión+comisión, guard anular-APLICADA→409). Fase 1 smoke sigue **17/17** (sin regresión).
**Problemas:** el sim endpoint tiraba 500 (`LazyInitializationException` sobre `receta.items` con `open-in-view=false`) → resuelto anotando el método `@Transactional`.
**Pendiente (Fase 2, con credenciales de Gon):** `HttpTiendaNubeClient` real (createCoupon/getOrder/getPaidOrdersSince) + **confirmar contra la tienda demo que el HMAC viene en hex** (asumido) y el shape de `order.coupon[]`. **Hardening (Fase 3):** rate-limiting del endpoint público (como `/registro`).
**Impacto para Fran (a la vuelta):** el detalle de receta ya puede venir **APLICADA con `conversion`** poblada (ordenNumero/ordenTotal/paidAt/comisionPct/comisionMonto) — tu UI ya lo maneja. Para probar conversión en dev sin TiendaNube: `POST /api/v1/dev/tiendanube/orden-pagada {"recetaCodigo":"RX-..."}`.
**Refs:** `backend/src/main/java/com/nutriapp/modules/webhook/**`, `integrations/tiendanube/{TiendaNubeClient,StubTiendaNubeClient}.java`, `integrations/IntegrationsProperties.java`, `modules/receta/repository/RecetaRepository.java`, `common/error/GlobalExceptionHandler.java`, `application.yml`, `application-prod.yml`, `scripts/smoke-webhook.sh`.

## 2026-07-22 — Santi — backend (code+security review de Fase 1: fixes aplicados + suite de tests + TODOs de hardening)
**Qué:** Pasé code-reviewer y security-reviewer sobre la Fase 1. 0 CRITICAL. Apliqué los fixes de mayor valor y agregué tests. Todo re-verificado: **22/22 unit + 17/17 e2e**.
**Fixes aplicados:**
- **Dispatcher: I/O fuera de transacción** (patrón de pool-exhaustion de GIA/auth-manager). El lote se lee en tx corta, el envío mail/WhatsApp ocurre sin tx, y cada resultado se persiste en su tx (`NotificacionService.tomarLote/marcarEnviada/marcarSinConexion/marcarFallo`).
- **Anular cancela las notificaciones QUEUED** de la receta (`cancelarPendientes`, soft-delete) — evita que salga un cupón que la anulación ya invalidó. Verificado: emitir→2 QUEUED, anular→`[]`.
- **N+1: `toResponse` partido** — la lista (`GET /recetas`, dashboard) ya NO consulta notificaciones por receta; sólo el detalle (`toResponseDetalle` en `GET /{id}`, emitir, anular, reenviar). El front sólo usa notifs en el modal (que hace su propio `GET /{id}`).
- **Registro: compensación** — si el `save` local falla tras crear el usuario en Keycloak, se borra el user KC (`KeycloakAdminClient.deleteUser`) para no dejar huérfanos que bloqueen el email.
- **KeycloakAdminClient: timeouts** (connect 3s / read 10s — es invocado desde el endpoint público `/registro`) + **cache del admin token** (con `expires_in`).
- **Enumeración**: mensaje del 409 de `/registro` unificado ("Ese email ya está registrado") entre el pre-check local y el backstop de Keycloak.
- **Prod**: `server.error.include-message: never` en `application-prod.yml`.
- **Tests unitarios** (Mockito, sin DB): `RecetaServiceTest` (guards anular/reenviar, degradación de cupón, cancelación de notifs), `DashboardServiceTest` (tasa conversión, mes inválido, detalle), `NotificacionServiceTest` (encolar/upsert/cancelar/estados dispatch), `PacienteServiceTest` (409), `RecetaVencimientoJobTest`. Corren con `mvn test` (JDK21 en contenedor).
**TODOs de hardening (Fase 3, documentados — NO bloquean):**
- **Keycloak Admin usa el superusuario del realm master** (`admin/admin` ROPC, default de dev, override por env). Prod: service-account confidencial scopeado a `realm-management` (`manage-users`/`view-users`) del realm nutriapp con `client_credentials`. Nota en el javadoc de `KeycloakAdminClient`.
- **`/registro` sin rate limiting** (endpoint público que pega a Keycloak). Agregar Bucket4j o similar.
- **Secret placeholder del realm** (`nutriapp-backend-dev-secret-change-me` en `keycloak/realms/nutriapp-realm.json`): regenerar/templatizar al armar el realm de prod. Hoy sin uso en el código.
- **`RecetaVencimientoJob` sin lock multi-instancia** (idempotente, inofensivo con 1 instancia; si se escala, agregar ShedLock).
**Refs:** `modules/notificacion/service/*`, `modules/receta/service/RecetaService.java`, `integrations/keycloak/KeycloakAdminClient.java`, `modules/registro/service/RegistroService.java`, `application-prod.yml`, `backend/src/test/**`.

## 2026-07-22 — Santi — backend (Fase 1 núcleo: notificaciones + ciclo receta + registro/admin Keycloak; verificado e2e 17/17)
**Qué:** Cerré el grueso de la Fase 1. Todo lo que Fran había flaggeado en 500 ahora anda (verificado con smoke e2e contra el stack real, 17/17 OK):
- **Módulo `notificacion`** (nuevo): entity/enums (`CanalNotificacion` EMAIL|WHATSAPP, `EstadoNotificacion` QUEUED|SENT|FAILED), repo, `NotificacionTemplates`, `NotificacionService` (encolar/reencolar idempotente por receta+canal), `NotificacionDispatcher` (poller `@Scheduled` cada 30s que drena la cola contra los ports mail/WhatsApp). En stub degrada: la notificación **sigue QUEUED sin consumir intentos** (se reintenta al pasar a live); error no transitorio → suma intento, al superar `max-intentos` pasa a FAILED.
- **Ciclo de receta:** `emitir` ahora encola EMAIL+WHATSAPP; **`RecetaResponse` incluye `notificaciones`** (poblado en `toResponse`); nuevos `POST /recetas/{id}/anular` (solo PENDIENTE, borra cupón TN degradando en stub, → ANULADA) y `POST /recetas/{id}/reenviar` (reencola, solo PENDIENTE). Guard: anular/reenviar sobre no-PENDIENTE → 409.
- **`DELETE /pacientes/{id}` → 409** si el paciente tiene recetas PENDIENTES (era el gap que Fran vio devolver 204).
- **Scheduler de vencimiento** (`RecetaVencimientoJob`): cron diario 03:00 AR + **catch-up al `ApplicationReadyEvent`** (lección imedba). PENDIENTE con `venceAt < hoy` → VENCIDA.
- **`GET /dashboard/cierre-mensual?year=&month=`**: tasa de conversión + ventas + comisión del mes + detalle de convertidas. (Smoke: jul-2026 → emitidas 7, aplicadas 2, tasa 0.29, comisión $5593.)
- **Registro + Admin (Keycloak Admin API):** `integrations/keycloak/KeycloakAdminClient` (RestClient, password grant contra realm master admin-cli). `POST /registro` (público) crea el usuario **deshabilitado** + asigna el rol compuesto NUTRICIONISTA y persiste el perfil PENDIENTE. `GET /admin/nutricionistas` (+filtros estado/q) y `POST .../{id}/aprobar|rechazar` (`admin:manage`): aprobar **habilita** el usuario en Keycloak; rechazar lo deja deshabilitado con motivo. Verificado el flujo completo: registrar → login FALLA → admin aprueba → login FUNCIONA → re-aprobar 409.
**Hallazgo clave:** el rol realm **NUTRICIONISTA es composite** (arrastra los client roles `recetas:*`, `pacientes:*`, etc.). Así el alta solo necesita crear el usuario + asignar ese rol; no hay que mapear client roles uno por uno. **Para prod: incluir siempre el composite (y el scope `basic`, ver entrada del 17).**
**Problemas:** ninguno de runtime en el backend. (Sí un bug en mi propio script de smoke: `-H "Authorization: Bearer $T"` sin comillas hace word-splitting del token → 401 espurios; corregido en la versión final `scripts/smoke-fase1.sh`.)
**Sobre `GET /pacientes` fechaNacimiento/notas (hallazgo Fran 07-18):** `PacienteResponse` **ya trae ambos** y el mapper los mapea; lo que Fran vio null era la **seed** (los pacientes demo se crean sin esos campos). No es bug del mapper. Si quieren datos, se agregan en el seed.
**Pendiente Fase 1/2 (no bloquea a Fran):** webhook TiendaNube (HMAC+idempotencia+polling) y los clientes HTTP reales de las 4 integraciones — necesitan credenciales de Gon (Fase 2). Tests unit/Testcontainers: quedé con smoke e2e; falta la suite formal.
**Impacto para Fran (a la vuelta):** **F.5 acciones (anular/reenviar), notificaciones/conversión en el detalle, `POST /registro`, y la bandeja admin F.6 YA tienen backend real** — re-verificá contra el contrato. El shape de `NutricionistaResponse` quedó `{id,nombre,apellido,email,telefono,matricula,estadoValidacion,validadoAt,notasValidacion,createdAt}`. `RecetaResponse.notificaciones[]` = `{canal,estado,sentAt}`.
**Refs:** `backend/src/main/java/com/nutriapp/modules/{notificacion,registro,admin}/**`, `integrations/keycloak/**`, `modules/receta/**`, `modules/dashboard/**`, `modules/paciente/service/PacienteService.java`, `application.yml`, `docker-compose.yml`, `scripts/smoke-fase1.sh`.

## 2026-07-22 — Santi — frontend (excepción autorizada: tipografía Comic Sans, pedido del cliente)
**Qué:** Cambié la tipografía global a **Comic Sans** (`--font-body` en `frontend/src/index.css`) y redondeé un poco más los radios (`--radius` 14→18, `--radius-sm` 10→12) para un aire más juguetón. Los códigos `RX-` conservan monoespaciado (clase `.mono` intacta).
**Por qué:** pedido explícito del cliente (Gon), transmitido por Santi. Excepción autorizada a la regla de propiedad de `frontend/` (área de Fran) — cambio puramente de tokens CSS, no toca lógica, contrato ni componentes.
**Impacto para Fran:** cosmético. Si querés otra fuente juguetona open-source (Comic Neue) como fallback web bundleado, avisá; por ahora usa la Comic Sans del sistema con fallback a cursiva.
**Refs:** `frontend/src/index.css` (`:root`).
**Qué:** Pulido de UX transversal, todo verificable (sin backend nuevo):
- **Skeletons** de carga (`components/ui/Skeleton`: `Skeleton`/`TableSkeleton`/`TilesSkeleton`) en Dashboard/Pacientes/Recetas.
- **Empty states** con ícono + acción (`components/ui/EmptyState`) en Pacientes y Recetas (distinguen "sin datos" vs "sin resultados de filtro").
- **Toasts** (`components/ui/Toast`: `ToastProvider` en la raíz + `useToast()`) para feedback: alta/edición/baja de paciente,
  anular/reenviar receta. Reemplacé los `actionError` inline por toasts.
- Animación de entrada del `Modal` (fade + scale).
**Por qué:** pedido de Fran (round 2 de UI). Cosmético/UX — no toca lógica ni contrato.
**Verificado:** `build`+`lint` verdes.
**Impacto para el otro:** ninguno. Para futuras pantallas: reusar `TableSkeleton`/`EmptyState`/`useToast`.
**Refs:** `frontend/src/components/ui/{Skeleton,EmptyState,Toast}.tsx`, `App.tsx`, pages Dashboard/Pacientes/Recetas, `components/receta/RecetaDetalle.tsx`.

## 2026-07-18 — Fran — frontend (F.3 parcial: dashboard tabla de últimas recetas; cierre mensual bloqueado)
**Qué:** Completé el Dashboard: saludo con el nombre, y la sección "Últimas recetas" pasó de lista simple a
**tabla clickeable** (código/paciente/estado/emitida/vence/total) que abre el detalle de receta (reusa el modal de
F.5) + link "Ver todas" a /recetas. `ultimasRecetas` de `/dashboard/resumen` viene como `RecetaResponse` completa
(verificado) → calculo el total con descuento client-side.
**⚠️ Hallazgo para Santi:** **`GET /dashboard/cierre-mensual?year=&month=` devuelve 500** (tu Fase 1.5). Por eso la
vista de cierre mensual (tasa de conversión + comisión del mes + detalle) **queda pendiente** — no la construí a ciegas.
Ya dejé el type `CierreMensual` en `types/dashboard.ts` listo para cuando el endpoint ande; confirmá el shape.
(Nota: `resumen.ultimasRecetas[].paciente` SÍ trae fechaNacimiento/notas, a diferencia de `GET /pacientes` — otro mapper.)
**Verificado:** `build`+`lint` verdes; tabla contra `/dashboard/resumen` real.
**Refs:** `frontend/src/pages/Dashboard.tsx`, `types/dashboard.ts`.

## 2026-07-18 — Fran — frontend (UI refresh: design tokens + set de íconos)
**Qué:** Refresh visual (más cálido/friendly): nueva paleta de tokens en `index.css` (verde/teal fresco `--primary`
+ acentos amber/blue + neutrales cálidos + sombras + radios), sidebar con gradiente teal e íconos por ítem, topbar
con avatar de iniciales, tiles del dashboard con chips de ícono a color, badges/inputs/botones repulidos (hover,
focus ring, sombras). Nuevo componente **`components/ui/Icon.tsx`** (set SVG inline estilo lucide, **sin dependencia
externa** — respeta la cultura de deps mínimas del proyecto). Login split ya se había hecho aparte.
**Por qué:** pedido de Fran (UI más cozy, íconos, colores). Puramente cosmético — no toca lógica ni contrato.
**Verificado:** `build`+`lint` verdes.
**Impacto para el otro:** ninguno (frontend). Para futuras pantallas: usar `<Icon name=…>` y los tokens CSS existentes.
**Refs:** `frontend/src/index.css`, `components/ui/Icon.tsx`, `components/layout/AppLayout.tsx`, `pages/Dashboard.tsx`.

## 2026-07-18 — Fran — frontend (F.6 parcial: registro público; bandeja admin diferida a Fase 3)
**Qué:** Form **público de registro** de nutricionista (`/registro`, sin auth) → `POST /registro`, con validación
(nombre/apellido/email/teléfono E.164/matrícula/password ≥8) y pantalla de "solicitud enviada, queda PENDIENTE".
Link cruzado Login↔Registro. `pages/Registro.tsx`, `api/registro.ts`, `types/registro.ts`.
**Decisión:** hice **solo la parte pública**. La **bandeja admin de aprobación se difiere a Fase 3** (su fallback en
el plan) porque el backend no está: `GET /admin/nutricionistas` da 500, así que no puedo ni ver el `NutricionistaResponse`
real para tipar la tabla sin adivinar (y el doc ya falló 3 veces esta sesión). Prefiero no construir a ciegas.
**⚠️ Hallazgos para Santi (Fase 1.3, ambos 500):**
1. **`POST /registro` → 500** (`ApiError` "Error interno"). El form está listo y surfacea tu message; cuando implementes
   el alta vía Keycloak Admin API (usuario deshabilitado + `estadoValidacion=PENDIENTE`) funciona sin tocar el front.
   Confirmá el shape de respuesta (asumí `{id, estadoValidacion}`).
2. **`GET /admin/nutricionistas` → 500** (con token ADMIN válido — `admin@nutriapp.dev` tiene `admin:manage`, la auth
   está OK; el endpoint no está implementado). Cuando lo tengas, con el `NutricionistaResponse` real armo la bandeja
   admin (aprobar/rechazar) a la vuelta de vacaciones.
**Verificado:** `build`+`lint` verdes. Happy-path del POST NO verificable hasta tu 1.3 (hoy 500).
**Refs:** `frontend/src/pages/Registro.tsx`, `api/registro.ts`, `types/registro.ts`, link en `pages/Login.tsx`.

## 2026-07-18 — Fran — frontend (F.5: Recetas lista+detalle + 3 hallazgos backend Fase 1)
**Qué:** Pantalla **Recetas** (`/recetas`): lista con filtros (estado/q/desde/hasta) + paginación + detalle en modal
(`GET /recetas/{id}`) con items, notificaciones, conversión y acciones **anular/reenviar** (solo PENDIENTE).
Componentes: `pages/Recetas.tsx`, `components/receta/RecetaDetalle.tsx`, `components/ui/EstadoBadge.tsx` (badge
compartido, refactoricé el Dashboard para usarlo). Helper `fechaHora` en `lib/format`. `api/recetas.ts` +
`listar/get/anular/reenviar`. Borré `pages/Placeholder.tsx` (ya no queda ninguna ruta placeholder).
**Verificado e2e:** `build`+`lint` verdes. Lista + filtro `estado=PENDIENTE` OK contra la seed (2 pendientes).
**⚠️ Hallazgos para Santi (frontend listo contra el contrato; backend pendiente — son tareas de tu Fase 1.1):**
1. **`POST /recetas/{id}/anular` y `/reenviar` devuelven HTTP 500** (`{error:"INTERNAL_ERROR", message:"Error interno"}`).
   Los botones ya están y la UI surfacea tu `ApiError.message` limpio (no muestra "500 pelado"). Cuando implementes
   la lógica, funcionan sin tocar el front.
2. **`GET /recetas/{id}` NO trae `notificaciones` ni `conversion`** (vienen `undefined`), aunque el contrato dice que
   el detalle los incluye. La UI degrada elegante ("Sin notificaciones registradas" / oculta conversión). Al poblarlos
   en el response, se renderizan solos.
3. (menor) El 500 sí viene con el `ApiError` uniforme y `content-type: application/json` → bien, mi client lo parsea.
**Impacto para el otro:** nada rompe; son features tuyas de Fase 1 que el front ya espera. Cuando las cierres, avisá por DIARIO.
**Refs:** `frontend/src/pages/Recetas.tsx`, `components/receta/RecetaDetalle.tsx`, `components/ui/EstadoBadge.tsx`, `api/recetas.ts`.

## 2026-07-18 — Fran — frontend (F.4: Pacientes CRUD + hallazgo backend sobre DELETE)
**Qué:** Pantalla **Pacientes** (`/pacientes`): lista paginada con búsqueda debounced (`GET /pacientes?q=&page=&size=`),
alta/edición en modal (`POST` / `PUT /pacientes/{id}`), baja (`DELETE`, soft). Validación: nombre/apellido/email/whatsapp
obligatorios; **whatsapp en E.164** (`/^\+[1-9]\d{7,14}$/`); email con regex. Componentes: `pages/Pacientes.tsx`,
`components/paciente/PacienteForm.tsx`, `components/ui/Modal.tsx`. Nuevos métodos en `api/pacientes.ts` + request
types `PacienteCreateRequest`/`PacienteUpdateRequest`.
**Verificado e2e:** `build`+`lint` verdes. `POST` 201, `PUT` 200, `DELETE` (sin recetas) 204.
**⚠️ Hallazgos para Santi (2 gaps del backend vs contrato `05-api-endpoints.md`):**
1. **`DELETE /pacientes/{id}` NO valida el 409 "tiene recetas PENDIENTES".** Borré (soft) a Juan Pérez que tenía
   una receta PENDIENTE (`RX-3V737V`) y devolvió **204**, no 409. La UI ya maneja el 409 (surfacea el message) para
   cuando lo implementes, pero hoy no llega. Además queda la receta colgada apuntando a un paciente borrado.
2. **`GET /pacientes` (list y `/{id}`) no devuelve `fechaNacimiento` ni `notas`**, aunque el `POST` sí los acepta y
   echoea. Por eso el form de edición no puede precargarlos → en edición solo expongo los 4 campos que round-trippean
   (nombre/apellido/email/whatsapp); fecha/notas se capturan solo en el alta. Si agregás esos campos al response mapper,
   habilito editarlos.
**Nota:** durante la verificación quedó ensuciada la seed dev (Juan Pérez soft-deleted + 1 receta de prueba). Se limpia
con `docker compose down -v && docker compose up -d db keycloak backend` (reset total + re-seed).
**Refs:** `frontend/src/pages/Pacientes.tsx`, `components/paciente/PacienteForm.tsx`, `components/ui/Modal.tsx`, `api/pacientes.ts`, `types/paciente.ts`.

## 2026-07-18 — Fran — frontend (F.2: Emitir Receta — pantalla core, verificada e2e)
**Qué:** Construida la pantalla **Emitir Receta** (`/recetas/nueva`, la más compleja del MVP):
- Picker de paciente (búsqueda debounced → `GET /pacientes?q=`), buscador de productos (texto libre `q` +
  dropdowns marca/laboratorio/presentación desde `GET /productos/filtros`), selección N items con cantidad +
  indicaciones (UI permite varios; arranca en 1 como pide CLAUDE.md), input de descuento, resumen
  subtotal/descuento/total, `POST /recetas` → pantalla de éxito con el código `RX-XXXXXX`.
- Nuevos types espejo (verificados contra el backend real, no solo el doc): `paciente`, `producto`
  (+`ProductoFiltros`/`ProductoQuery`), `receta` (request+response). Módulos `api/pacientes|productos|recetas`.
  Helpers `hooks/useDebounce`, `lib/format` (money/fecha). Refactoricé el `money` inline del Dashboard a `lib/format`.
**Por qué:** F.2 del sprint pre-vacaciones — el corazón del producto.
**Problemas:** deltas doc↔backend real: `/pacientes` no devuelve fechaNacimiento/notas/contadores en el list;
  `/productos/filtros` NO trae lista de principioActivo (solo marcas/laboratorios/presentaciones) → principio
  activo queda como texto libre vía `q`. Ajusté los types a la realidad.
**Verificado e2e:** `build`+`lint` verdes; `POST /recetas` con el shape exacto que manda la UI → **HTTP 201**,
  `RX-3V737V`, PENDIENTE, vence a 30 días, `cuponSyncEstado=PENDIENTE` (degradación del stub OK), items con
  cantidad+indicaciones. (Quedó 1 receta de prueba extra en el seed dev — inocua.)
**Impacto para el otro:** ninguno para Santi (todo dentro de `frontend/`, consumo el contrato tal cual).
**Refs:** `frontend/src/pages/EmitirReceta.tsx`, `components/receta/*`, `api/{pacientes,productos,recetas}.ts`, `types/{paciente,producto,receta}.ts`.

## 2026-07-18 — Fran — infra (⚠️ PARA SANTI: varios archivos con CRLF rompen el stack en Windows — falta .gitattributes)
**Qué:** Al levantar el stack en la máquina de Fran (Windows, `git core.autocrlf=true`), **3 archivos** quedaron
con line endings **CRLF** al checkoutear y rompen el build/arranque en los contenedores Linux:
1. `backend/mvnw` → `./mvnw: not found` (exit 127): el `#!/bin/sh\r` hace que Linux busque el intérprete `/bin/sh\r`.
2. `backend/.mvn/wrapper/maven-wrapper.properties` → `HTTP 400` al bajar el wrapper: el `\r` final se cuela en la
   `wrapperUrl` y la URL de descarga queda malformada.
3. `db/init/01-keycloak-db.sh` (lo ejecuta Postgres en el init para crear la DB de Keycloak) → CRLF ahí cascadea en
   "el realm nutriapp no existe". Lo normalicé preventivamente.
   (`backend/Dockerfile` también tiene CRLF pero BuildKit lo parsea igual — no lo toqué.)
**Fix aplicado (local, autorizado por Fran):** convertí esos 3 archivos a LF localmente (`sed -i 's/\r$//'`) solo
para desbloquear esta máquina. **NO commiteo archivos de tu área** — los cambios quedan locales.
**Fix durable pendiente (tu área, Santi):** falta un `.gitattributes` en la raíz que fuerce LF en scripts/infra.
  Recomendado:
```
* text=auto eol=lf
mvnw        text eol=lf
*.sh        text eol=lf
*.properties text eol=lf
*.cmd       text eol=crlf
*.bat       text eol=crlf
```
  Y re-normalizar (`git add --renormalize .`). Sin esto, cualquier dev en Windows con autocrlf=true se topa con lo mismo.
  Ojo: con autocrlf=true, git puede volver a mostrar estos archivos como modificados tras un checkout.
**Impacto para el otro:** vos (Santi) probablemente tenés `autocrlf=false`, por eso te buildeó sin problema. En una
  máquina limpia Windows revienta. Es 100% tuyo el fix definitivo (`.gitattributes` es infra/raíz).
**Refs:** `backend/mvnw`, `backend/.mvn/wrapper/maven-wrapper.properties`, `db/init/01-keycloak-db.sh`, falta `.gitattributes` (raíz).

## 2026-07-18 — Fran — frontend (F.1: scaffolding SPA + auth ROPC + layout + routing)
**Qué:** Scaffoldeado `frontend/` desde cero (Vite + React 19 + TS + react-router v7). Plumbing base:
- `config.ts` (lee `VITE_*`; base API = `${VITE_API_BASE_URL}/api/v1`), `api/client.ts` (fetch nativo, Bearer,
  parseo `ApiError`→`message`, 401→clearSession), `lib/auth.ts` (ROPC contra client público `nutriapp-frontend`
  + refresh + tokens en localStorage), `hooks/useFetch.ts` (loading/error/data + AbortController), `auth/AuthContext`.
- Layout Sidebar/Topbar + `RequireAuth` (sin sesión → `<Navigate to="/">`, no PKCE). Routing: `/` login público,
  rutas protegidas `/dashboard` (real, pega a `GET /dashboard/resumen`) + placeholders receta/pacientes.
- `types/` espejo del contrato: `common` (`PageResponse`/`ApiError`), `session` (`Me`), `dashboard`. `.env.example` + `.env.local`.
**Por qué:** F.1 del sprint pre-vacaciones (`04-plan-de-fases.md`). Desbloquea F.2 Emitir Receta.
**Problemas:** CORS — el back fija `http://localhost:5173`; Vite servido en `127.0.0.1` daría origin distinto y
  rompería CORS. Fijé `server.host=localhost` + `strictPort`. `build`+`lint`+`dev` verdes; login e2e sin verificar
  (Docker estaba abajo al scaffoldear).
**Impacto para el otro (Santi):** nuevo dir `frontend/` (mi propiedad). El front asume base `/api/v1` y CORS
  `localhost:5173`. Si el backend NO sirve bajo `/api/v1`, avisá por DIARIO. Sigo consumiendo el contrato de `05-api-endpoints.md`.
**Refs:** `frontend/**` (src/api, src/lib, src/auth, src/hooks, src/components/layout, src/pages, src/types).

## 2026-07-17 — Santi — infra+backend (scaffolding Fase 0: stack levanta + backend seeded, verificado e2e)
**Qué:** Scaffolding completo de Fase 0, portado de imedba y verificado contra el stack real:
- **Infra**: `docker-compose.yml` (db + keycloak + backend; frontend comentado hasta que Fran lo scaffoldee),
  `.env` + `.env.example`, `.gitignore`, `db/init/01-keycloak-db.sh`, realm `nutriapp` con clients
  `nutriapp-frontend` (public+DAG) / `nutriapp-backend` (confidential) + roles ADMIN/NUTRICIONISTA + usuarios
  dev `admin@nutriapp.dev` / `nutri@nutriapp.dev` (pass `test1234`).
- **Backend** (`com.nutriapp`): `pom.xml` (Boot 3.3.5, JPA, Flyway, MapStruct, Lombok, oauth2-resource-server,
  springdoc, testcontainers), Dockerfile multi-stage, `common/` (BaseEntity/PageResponse/ApiError/
  GlobalExceptionHandler/AuthUtils/JwtAuditorAware), `config/` (Security doble-namespace, Cors, OpenApi, JpaAuditing),
  `integrations/` (ports + stubs de tiendanube/contabilium/mail/whatsapp + `IntegrationsConfig` que elige adapter por
  `mode=stub|live`), módulos `nutricionista/paciente/producto/receta/dashboard` + `MeController`.
- **Migraciones** V001 (extensiones + trigger updated_at), V002 (7 tablas núcleo), V003 (seed 12 productos).
  `DevDataSeeder` (@Profile dev, idempotente): 1 nutricionista aprobado + 4 pacientes + 6 recetas en estados variados.
- **Endpoints Fase 0**: `GET /me`, `GET /productos` (+filtros +unaccent), `GET/POST/PUT/DELETE /pacientes`,
  `GET/POST /recetas` (emisión con código único + degradación de cupón), `GET /dashboard/resumen`.
**Por qué:** desbloquear a Fran: contrato REST vivo + backend seeded contra el que construye su sprint antes de irse.
**Decisión:** por indicación del usuario, **por ahora un solo usuario** → el seed crea 1 nutricionista, no 2.
**Problemas (2 bugs de runtime que el compile no atrapó, ambos verificados y resueltos):**
1. **Colisión de bean `mailSender`**: mi `@Bean MailSender mailSender()` chocaba con el `mailSender` (JavaMailSender)
   que autoconfigura `spring-boot-starter-mail`. Renombrado a `@Bean("nutriappMailSender")`. Además el health
   indicator de mail marcaba `/actuator/health` DOWN al no haber SMTP → `management.health.mail.enabled=false` (stub hasta Fase 2).
2. **Token sin claim `sub`**: en Keycloak 25 el `sub` lo aporta el client scope `basic` (mapper `oidc-sub-mapper`).
   Al declarar `clientScopes`/`defaultDefaultClientScopes` explícitos sin `basic`, el access token salía sin `sub`
   → el linkeo nutricionista↔Keycloak fallaba y todo endpoint con scoping tiraba 404. **Fix**: agregué el scope `basic`
   al realm + lo incluí en los default scopes; además hice `NutricionistaService.findCurrent()` robusto (linkea por
   email aunque falte el sub). **OJO para el realm de prod / futuros realms: incluir siempre el scope `basic`.**
**Verificado e2e** (stack real, backend en :8088 porque el 8080 lo ocupa GIA): token ROPC con `sub` → `/me` (estado
APROBADA, 9 authorities) → productos 12 → filtros → pacientes 4 → dashboard (pend 2/aplic 2/venc 1/comisión $5593) →
**POST paciente + POST receta**: código `RX-XXXXXX` generado, estado PENDIENTE, `cuponSync=PENDIENTE` (degradación del
stub OK), vencimiento a 30 días, snapshot de precio. Build en contenedor Maven JDK21 (host tiene Java 17).
**Impacto para el otro (Fran):** el backend levanta con `docker compose up -d db keycloak backend`. En la máquina de
Santi el backend queda en **:8088** (por eso el `.env` local tiene `BACKEND_PORT=8088` y `VITE_API_BASE_URL=...:8088`);
en una máquina limpia el default del compose es 8080. Login ROPC contra `nutriapp-frontend`, realm `nutriapp`,
usuario `nutri@nutriapp.dev` / `test1234`. Contrato en `05-api-endpoints.md`. **Pendiente Fase 1** (Santi, sin bloquear
a Fran): notificaciones (cola+dispatcher), webhook TiendaNube, scheduler de vencimiento, anular/reenviar, registro público.
**Refs:** `backend/**`, `docker-compose.yml`, `keycloak/realms/nutriapp-realm.json`, migraciones `V001-V003`.

## 2026-07-17 — Santi — docs (kickoff: alcance, arquitectura y plan de fases)
**Qué:** Proyecto inicializado. Investigadas las APIs de Contabilium y TiendaNube, mapeada la arquitectura
de imedba (que replicamos acá: mismo stack, mismo docker, mismo sistema de coordinación de dos Claudes) y
escritas todas las docs de diseño: `CLAUDE.md`, `01-arquitectura-sistema.md`, `02-entidad-relacion.md`,
`03-integraciones-apis.md`, `04-plan-de-fases.md`, `05-api-endpoints.md`.
**Por qué:** Presupuesto aprobado por Gon (ver `presupuesto_nutriapp.pdf` + mail de requerimientos).
Fran se va de vacaciones el miércoles 22-07 por 3 semanas → la Fase 0 prioriza dejarle el contrato API y
un backend seeded contra el que pueda construir las pantallas core antes de irse.
**Impacto para el otro:** Fran: leé `04-plan-de-fases.md` §Fase 0 — tus tareas están ordenadas por prioridad
para tu sprint de 3 días. El contrato REST que tenés que consumir está en `05-api-endpoints.md`.
**Refs:** `instrucciones_claude/*.md`, `presupuesto_nutriapp.pdf`.
