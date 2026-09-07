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

**Última actualización: 2026-09-07 (noche)** — 🚀 **`bonosapp.com.ar` ESTÁ EN VIVO**, en modo
pre-lanzamiento (`VITE_COMING_SOON=true`). La migración del VPS se ejecutó entera y verificada.
`nutriappok.com.ar` quedó como **301 permanente** (no se da de baja: ahí vive la casilla de contacto).

**Qué se mudó, todo en una ventana:** dominio + cert (ACME de Caddy), volumen de datos de Postgres,
base y rol (`nutriapp` → `bonosapp`), realm y clients de Keycloak, proyecto de compose, nombres de
contenedor y red, y el bundle de la SPA. **Sin pérdida de datos**: censo de filas idéntico, 3 usuarios
de Keycloak intactos, Flyway validó 13 migraciones sin checksum mismatch y aplicó la V013.

**Estado verificado:** `/` 200 con cert válido y security headers · `nutriappok` → 301 · health UP ·
issuer `https://bonosapp.com.ar/auth/realms/bonosapp` · `/auth/admin` 404 · login ROPC → token con
`resource_access.bonosapp-backend: ['admin:manage']` · `/api/v1/me` y `/api/v1/admin/nutricionistas`
**200** · `/registro` 415 con `ApiError` · haltcatch y jeianell del VPS intactos.

**🔴 LO ÚNICO URGENTE — hallazgo de seguridad abierto.** La credencial **seed de dev**
`admin@nutriapp.dev` / `test1234` **funciona en producción** con `ADMIN` + `admin:manage`. Esa
contraseña está en el realm JSON versionado en el repo. Viene del deploy de agosto (se importó el
realm de dev en prod). **Rotarla o borrar la cuenta antes del lanzamiento**; ídem `nutri@nutriapp.dev`.
No la toqué: son las únicas cuentas admin y la decisión es del usuario.

**Bugs encontrados y arreglados al ejecutar** (el runbook estaba mal en 4 puntos, uno destructivo —
detalle completo en el DIARIO y ya corregido en `DEPLOY.md`): el cambio de `name:` del compose movía
el nombre del **volumen** y `up -d` habría arrancado con una **base vacía**; el rename del realm tenía
que ir antes de levantar el stack nuevo; `rename-db.sh` moría en `ALTER ROLE` (session user) y su
verificación de dump nunca pasaba (`pg_restore -l -` no existe); `nginx/conf.d-proxied` seguía con
`server_name nutriappok.com.ar` (habría dado 444); y `KEYCLOAK_ISSUER_URI` vacío rechazaba **todos**
los tokens (la API autenticada de prod nunca había funcionado).

**Rollback disponible:** volumen `nutriapp_nutriapp_db_data` intacto, dumps cifrados en `backups/`
(`*-20260907-*.dump.gpg`), `frontend/dist-old-*`, `.env.bak-*` y la imagen `nutriapp/backend:prod`.
Conviene conservarlos unos días y después limpiarlos.

**Pendiente (sin cambios respecto de antes):** avisarle a Leo para que mude la casilla de contacto a
`bonosapp.com.ar` (hoy sale `info@nutriappok.com.ar` desde `VITE_CONTACTO_EMAIL`) · integraciones en
`stub` en prod (Contabilium, TiendaNube, mail) · instalar la app en la tienda TBC y correr el mapeo ·
proveedor de mail a elección de Gon · la decisión abierta de si un producto sin mapear sigue siendo
recetable.

## Fran / frontend

**Última actualización: 2026-08-13** — de vuelta de vacaciones y sincronizado con el pull.

**Contexto:** en mis 3 semanas Santi avanzó muchísimo (tocó `frontend/` con mi permiso, avisado en DIARIO): la app
está **EN PROD** (`nutriappok.com.ar`, pre-lanzamiento), con Fase 1, Fase 2 (Contabilium live; TiendaNube/email en
stub; WhatsApp por `wa.me`) y las **4 olas post-demo** hechas. El front del repo ya refleja todo eso (coming-soon,
taxonomía/catálogo admin, %-por-nutricionista, liquidación, archivos DNI/CUIT/matrícula, foto de perfil, precios
fuera de casi toda la app, admin sin emisión). Todo mi sprint pre-vacaciones (F.1–F.6) quedó absorbido y superado.

**Entorno local puesto a punto (2026-08-13):**
- Puertos alineados con Santi para esquivar imedba/GIA: `.env` local con `BACKEND_PORT=8088`, front `:5174`
  (`VITE_DEV_PORT`), keycloak `:8081`. `vite.config.ts` ahora lee el puerto de env.
- Dropeé mis 3 fixes CRLF locales (`.gitattributes` de Santi ya cubre mvnw/*.sh). **Queda un hueco:**
  `maven-wrapper.properties` no está cubierto → arreglo local + flag a Santi en DIARIO.
- Stack local levantado (back `:8088`, keycloak `:8081`, db `:5432`) + front `:5174`. Contabilium en stub →
  **catálogo local vacío** (se puebla sólo con credenciales + "Sincronizar catálogo").

**En qué estoy ahora / próximo:**
- **Verificación visual** de las pantallas nuevas que Santi dejó marcadas como "falta mirar" (C-02 precios, C-07
  admin sin emisión, registro de 11 campos, catálogo, rediseño R.1–R.7).
- **Funcionalidad pendiente de email del registro** (bloqueante funcional: proveedor mail en stub → nadie recibe
  el aviso de "solicitud recibida/aprobada").

**Bloqueado por el otro:** nada.
