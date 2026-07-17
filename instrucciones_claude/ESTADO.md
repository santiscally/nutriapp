# ESTADO — Snapshot de trabajo en curso

> **Qué es esto.** Foto corta y actualizable de **en qué está cada uno ahora mismo**. No es historia
> (eso va en `DIARIO.md`), es el presente.
>
> **Regla de uso para el Claude activo:**
> - **Solo tocar la sección del dueño activo.** Santi edita "Santi / backend", Fran edita "Fran / frontend".
>   Nunca tocar la sección del otro (evita merge conflicts).
> - **Sobreescribir, no appendear.** Esta es una foto, no un log.
> - Actualizar al **empezar** una tarea nueva y al **terminarla**.
> - Si algo está **bloqueado esperando al otro**, dejarlo explícito en la sub-sección "Bloqueado por el otro".

---

## Santi / backend / infra / db / auth

**Fase actual:** Fase 0 — Cimientos (17–21 jul). **Scaffolding Fase 0 cerrado y verificado e2e (2026-07-17).**

**En qué estoy ahora:**
- **Backend seeded arriba y andando** contra el stack real. Todo lo mínimo de Fase 0 hecho: infra (docker-compose,
  realm `nutriapp`, .env), esqueleto Spring Boot portado de imedba, migraciones V001–V003 + `DevDataSeeder`,
  y endpoints `GET /me`, `/productos` (+filtros), CRUD `/pacientes`, `GET/POST /recetas`, `GET /dashboard/resumen`.
- Integraciones como ports+stubs (`mode=stub`); emitir receta degrada el cupón a PENDIENTE sin romper. Detalle
  + los 2 bugs de runtime resueltos (bean `mailSender`, claim `sub`/scope `basic` de Keycloak) en DIARIO 2026-07-17.
- **Por ahora un solo usuario** (indicación del cliente): el seed crea 1 nutricionista (`nutri@nutriapp.dev`).

**Próximo paso (Fase 1, no bloquea a Fran):**
- Notificaciones (cola + dispatcher + templates), webhook TiendaNube (HMAC + idempotencia + polling respaldo),
  scheduler de vencimiento (diario + catch-up al startup), anular/reenviar receta, registro público + validación admin,
  clientes HTTP reales de las 4 integraciones (aún en stub). Tests unit + Testcontainers.

**Bloqueado por el otro:** nada.

**Notas para Fran:**
- El stack levanta con `docker compose up -d db keycloak backend`. **En la máquina de Santi el backend queda en
  `:8088`** (el 8080 lo ocupa plataforma GIA) — en una máquina limpia el default del compose es `:8080`. Ajustá
  `VITE_API_BASE_URL` según tu `.env`.
- Login: ROPC contra client `nutriapp-frontend`, realm `nutriapp`, `http://localhost:8081`. Usuario de prueba:
  `nutri@nutriapp.dev` / `test1234` (rol NUTRICIONISTA, ya aprobado, con 4 pacientes + 6 recetas seedeadas).
- Contrato REST en `05-api-endpoints.md` (congelado durante tus vacaciones). NO hay mocks: todo va al backend real.
- Tu plan de sprint está en `04-plan-de-fases.md` §Fase 0, ordenado por prioridad (F.1 auth+layout → F.2 Emitir Receta
  → F.3 Dashboard → F.4 Pacientes → F.5 Recetas → F.6 Registro).

---

## Fran / frontend

**Fase actual:** Fase 0 — Cimientos (17–21 jul). **Me voy de vacaciones el 22-07, vuelvo ~12-08.**

**En qué estoy ahora:**
- (sin arrancar — esperando el scaffolding del backend de Santi)

**Próximo paso:**
- Sprint pre-vacaciones según `04-plan-de-fases.md` §Fase 0: esqueleto SPA (auth + layout + routing) →
  Emitir Receta → Dashboard → Pacientes → Registro público.

**Bloqueado por el otro:** stack dev levantando (`docker compose up`) + endpoints mínimos seeded.
