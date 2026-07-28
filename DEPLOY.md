# NUTRIAPP — Despliegue en producción

Stack prod = `docker-compose.yml` + override `docker-compose.prod.yml`. nginx termina TLS y es
el **único** servicio público (80/443); db, keycloak y backend quedan en loopback (127.0.0.1) +
red interna `nutriapp-net`. La SPA se sirve estática desde `frontend/dist` (build de Fran).

Topología (single domain, path-based):

| Ruta      | Destino                          |
| --------- | -------------------------------- |
| `/`       | SPA estática (`frontend/dist`)   |
| `/api/`   | backend Spring Boot (`/api/v1`)  |
| `/auth/`  | Keycloak (`KC_HTTP_RELATIVE_PATH=/auth`) |

> **TLS elegido: bring-your-own-cert.** nginx lee `nginx/certs/{fullchain,privkey}.pem`. No hay
> automatización de emisión/renovación (Let's Encrypt queda para cuando el hosting/dominio esté
> confirmado con Gon — pregunta abierta #8 del plan).

---

## Pre-requisitos (una vez)

1. **Dominio + DNS** apuntando al host, puertos 80/443 abiertos.
2. **Docker + Docker Compose v2** en el host.
3. **Node** (para compilar la SPA) — en el host o en un CI que deje el `dist/` listo.

## Pasos de despliegue

### 1. Certificados TLS → `nginx/certs/`
- **Prod (cert real):** copiar `fullchain.pem` + `privkey.pem` del dominio a `nginx/certs/`.
- **Staging (placeholder):** `bash scripts/gen-selfsigned-cert.sh app.midominio.com`
  (el navegador advertirá; sirve para probar el pipeline TLS).

### 2. Compilar la SPA
```
cd frontend
# Los VITE_* se hornean en el build: apuntarlos al dominio prod (mismo origen).
VITE_API_BASE_URL=https://app.midominio.com/api \
VITE_KEYCLOAK_URL=https://app.midominio.com/auth \
VITE_KEYCLOAK_REALM=nutriapp \
VITE_KEYCLOAK_CLIENT_ID=nutriapp-frontend \
npm ci && npm run build          # genera frontend/dist (lo sirve nginx)
```
> `frontend/` es de Fran. Estos son sólo los env de build documentados; no se modifica su código.

### 3. Regenerar el secret del client `nutriapp-backend` (realm de prod)
El realm de dev trae un secret **placeholder** (`*-dev-secret-change-me`) que NO debe usarse en prod.
En la consola de Keycloak (realm `nutriapp` → Clients → `nutriapp-backend` → Credentials →
*Regenerate*), copiar el nuevo secret y ponerlo en `.env` como `KEYCLOAK_ADMIN_CLIENT_SECRET`.
El backend en prod **falla-cerrado** si queda vacío (503 en las ops de admin; el compose ni levanta).

### 4. `.env` de prod
Copiar `.env.example` → `.env` y completar el bloque **PRODUCCIÓN**. El compose **aborta**
(`:?`) si falta alguno de estos, pero **NO** detecta que sigan siendo los débiles de dev — es
tu responsabilidad regenerarlos:
- `KEYCLOAK_HOSTNAME` = origen público (ej. `https://app.midominio.com`).
- `KEYCLOAK_ADMIN_CLIENT_SECRET` = el regenerado en el paso 3.
- `POSTGRES_PASSWORD` = fuerte (el default de dev es público en este repo).
- `KEYCLOAK_ADMIN` + `KEYCLOAK_ADMIN_PASSWORD` = propios (el default de dev es `admin`/`admin`).
- (Opcional recomendado) `BACKUP_GPG_RECIPIENT` = para cifrar los dumps de DB.
- Integraciones (`*_MODE`, credenciales) según Fase 2; en stub degradan con mensaje.

### 5. Levantar
```
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```
Verificación:
- `https://<dominio>/` → SPA.
- `https://<dominio>/actuator/health` → `{"status":"UP"}` (único path de actuator expuesto).
- `https://<dominio>/auth/realms/nutriapp/.well-known/openid-configuration` → JSON de OIDC.
- Login en la SPA (ROPC contra `/auth`) → dashboard.

> **Keycloak hostname:** en KC 25 (hostname v2) el arranque prod es sensible a `KC_HOSTNAME` /
> `KC_PROXY_HEADERS`. Si el login o el `.well-known` fallan (URLs mal formadas), ajustar
> `KEYCLOAK_HOSTNAME` al origen exacto y revisar los headers `X-Forwarded-*` de nginx. **Validar
> contra el dominio real** — no se puede verificar sin dominio + stack corriendo.

> **Consola admin de Keycloak NO es pública:** nginx bloquea `/auth/admin` y `/auth/realms/master`
> (devuelve 404) — la app usa la Admin API server-side por la red interna, no la consola. Para
> entrar a la consola manualmente: túnel SSH al host y abrir `http://localhost:8081/auth/admin`
> (el puerto loopback del contenedor), o abrir temporalmente un `allow <CIDR>; deny all;` en ese
> `location` del nginx. Nunca dejarla abierta a internet.

---

## Backup / restore de la DB
```
# Cifrado (recomendado): setear BACKUP_GPG_RECIPIENT → dumps .dump.gpg
bash scripts/backup-db.sh                                       # → backups/{nutriapp,keycloak}-<ts>.dump[.gpg]
bash scripts/restore-db.sh backups/nutriapp-<ts>.dump.gpg --yes # DESTRUCTIVO (--clean); autodetecta .gpg; exige --yes
```
Los dumps traen **PII** (pacientes/recetas) + el **store de credenciales de Keycloak** → setear
`BACKUP_GPG_RECIPIENT` para cifrarlos antes de sacarlos del host. Los `.dump*` NO se commitean
(`.gitignore`). Guardar copias fuera del host para DR.

---

## Pendiente al confirmar hosting con Gon
- Let's Encrypt/renovación automática (hoy: bring-your-own-cert manual).
- Rate-limit de red fino, WAF/headers extra según hosting.
- Imagen Keycloak `--optimized` (build stage) para arranque más rápido, si el boot importa.
