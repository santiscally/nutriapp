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

> **TLS elegido: bring-your-own-cert.** nginx lee `nginx/certs/{fullchain,privkey}.pem`. Para
> Let's Encrypt hay webroot ACME servido en `:80` (`nginx/acme/`) → emisión con certbot `--webroot`;
> la **renovación sigue siendo manual** (paso 1).

> **Pre-lanzamiento:** para publicar el dominio con una pantalla "Próximamente" y sólo el registro
> habilitado, ver [Modo pre-lanzamiento](#modo-pre-lanzamiento-próximamente). DNS concreto de
> `nutriapp.com.ar` en [DNS](#dns--nutriappcomar).

---

## Pre-requisitos (una vez)

1. **Dominio + DNS** apuntando al host, puertos 80/443 abiertos → ver [DNS — nutriapp.com.ar](#dns--nutriappcomar).
2. **Docker + Docker Compose v2** en el host.
3. **Node** (para compilar la SPA) — en el host o en un CI que deje el `dist/` listo.
4. **Los puertos 80/443 del host libres** — ver la advertencia de convivencia con la landing del
   cliente en la sección de DNS. Si ya hay un webserver ahí, esto NO levanta.

## Pasos de despliegue

### 1. Certificados TLS → `nginx/certs/`
- **Prod (cert real):** copiar `fullchain.pem` + `privkey.pem` del dominio a `nginx/certs/`.
- **Staging (placeholder):** `bash scripts/gen-selfsigned-cert.sh app.midominio.com`
  (el navegador advertirá; sirve para probar el pipeline TLS).

**Let's Encrypt (http-01, manual).** nginx sirve `/.well-known/acme-challenge/` desde
`nginx/acme/` por HTTP **sin redirigir** (todo el resto de :80 sigue yendo a 301 → HTTPS), así que
certbot puede validar con el stack ya arriba. Orden de arranque: primero un self-signed placeholder
(nginx no levanta sin cert), después emitir el real y recargar:

```
bash scripts/gen-selfsigned-cert.sh nutriapp.com.ar     # placeholder para poder arrancar
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
certbot certonly --webroot -w ./nginx/acme \
  -d nutriapp.com.ar -d www.nutriapp.com.ar -m <mail> --agree-tos
cp /etc/letsencrypt/live/nutriapp.com.ar/{fullchain,privkey}.pem nginx/certs/
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec nginx nginx -s reload
```

> Renovación: sigue siendo **manual** (copiar el par renovado + `nginx -s reload`). Automatizarla
> con un `--deploy-hook` es tarea de Fase 3.

### 2. Compilar la SPA
```
cd frontend
# Los VITE_* se hornean en el build: apuntarlos al dominio prod (mismo origen).
VITE_API_BASE_URL=https://app.midominio.com \
VITE_KEYCLOAK_URL=https://app.midominio.com/auth \
VITE_KEYCLOAK_REALM=nutriapp \
VITE_KEYCLOAK_CLIENT_ID=nutriapp-frontend \
VITE_COMING_SOON=true \
npm ci && npm run build          # genera frontend/dist (lo sirve nginx)
```

> **Ojo con `VITE_API_BASE_URL`:** el código le concatena `/api/v1`, así que el valor correcto es el
> **origen pelado** (`https://nutriapp.com.ar`), **sin** `/api` — con `/api` quedaría `/api/api/v1`.
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

## Modo pre-lanzamiento ("Próximamente")

Para publicar `nutriapp.com.ar` antes de que la app esté terminada. Se activa con **un solo flag de
build**, `VITE_COMING_SOON=true`:

| Ruta        | Con el flag                                   | Sin el flag (normal) |
| ----------- | --------------------------------------------- | -------------------- |
| `/`         | landing **Próximamente** + CTA a `/registro`   | login                |
| `/registro` | igual (form de solicitud de acceso)            | igual                |
| `/ingresar` | login, **sin link desde ningún lado**          | login (alias de `/`)  |
| resto       | igual (el área autenticada sigue montada)      | igual                |

Apagarlo: `VITE_COMING_SOON=false` (o borrar la variable) + `npm run build` + `nginx -s reload`.
**No hay que tocar código.** El flag se hornea en el bundle, no es runtime.

**Qué NO es esto:** no es una barrera de seguridad. Esconde la puerta de entrada de la vista
pública, nada más — `/ingresar` y `/auth/realms/nutriapp/...` siguen respondiendo. La barrera real
es la de siempre: toda cuenta nueva nace `PENDIENTE` y **deshabilitada en Keycloak** hasta que un
admin la aprueba, así que un registro público no da acceso a nada.

**Antes de abrir el registro al público, tener en cuenta:**
- **No sale ningún mail.** `RegistroService` no encola notificación, y el proveedor de email es
  `stub` hasta Fase 2. La landing y la pantalla de éxito dicen "te avisamos por email" → hoy eso lo
  tiene que hacer una persona. Nadie avisa al admin de que entró una solicitud tampoco.
- **La bandeja de aprobación del admin está diferida a Fase 3** (ESTADO/DIARIO): aprobar hoy es por
  API (`/api/v1/admin/nutricionistas`) o SQL. Si Gon espera aprobar por pantalla, no lo tiene aún.
- **Rate limit:** `/api/v1/registro` está limitado a **10 req/min por IP** (`nutriapp.rate-limit`,
  `enabled=true` por default) + el limitador grueso de nginx. Alcanza para hammering básico, no es
  anti-spam: no hay captcha ni verificación de email, así que las solicitudes basura hay que
  filtrarlas a ojo (para eso está el adjunto obligatorio de matrícula).
- Los datos del form son **PII real** (DNI, CUIT, matrícula, archivo) desde el minuto uno → los
  backups cifrados (`BACKUP_GPG_RECIPIENT`) dejan de ser opcionales.

---

## DNS — nutriapp.com.ar

El host es el **mismo servidor donde está la landing del cliente** (`haltcatch.com.ar`), cuya zona
resuelve a `187.127.36.153`. Registros mínimos a crear en la zona de `nutriapp.com.ar`:

| Tipo    | Nombre | Valor               | TTL |
| ------- | ------ | ------------------- | --- |
| `A`     | `@`    | `187.127.36.153`    | 300 |
| `CNAME` | `www`  | `nutriapp.com.ar`   | 300 |

Con eso alcanza para servir la app por HTTPS (nginx atiende cualquier `Host`). Nada más es
necesario: no hay subdominios (`/api` y `/auth` son paths del mismo dominio, no hosts).

**Tres cosas a verificar antes de tocar la zona:**

1. **NO copiar el `AAAA` de la landing.** La zona de `haltcatch.com.ar` tiene
   `A @ → 187.127.36.153` (Telecom AR) y `AAAA @ → 2a02:4780:6e:84b8::1` (rango de Hostinger):
   **son dos servidores distintos**. Si se replica ese `AAAA` en `nutriapp.com.ar` y la app corre en
   el `187.127.36.153`, los clientes con IPv6 (que lo prefieren por Happy Eyeballs) van a pegarle al
   host equivocado → sitio incorrecto o timeouts intermitentes, imposibles de debuggear desde
   Argentina si tu ISP no tiene IPv6. Poner `AAAA` **sólo** si se confirma que el mismo host que
   sirve nutriapp responde en esa IPv6. De paso: esa inconsistencia en la zona de la landing
   conviene revisarla con el cliente, puede estar sirviendo la landing desde otro lado del esperado.
2. **Los puertos 80/443 del host.** El stack prod de nutriapp levanta su propio nginx bindeado a
   `80:80` y `443:443`. Si la landing ya se sirve desde ese mismo servidor, **el `up` va a fallar
   por puerto ocupado** (o va a robarle el tráfico). Hay que decidir cuál de las dos:
   - **nginx de nutriapp como front único**: agregarle un `server{}` para `haltcatch.com.ar` que
     sirva/proxee la landing. Más simple si la landing es estática.
   - **el webserver existente como front**: nutriapp bindea a puertos altos (`8443:443` en el
     override) y el nginx de afuera proxea `nutriapp.com.ar` hacia ahí. Mantiene la landing intacta.
3. **`server_name _` es catch-all.** Mientras el nginx de nutriapp sea el único en 80/443, va a
   responder también para `haltcatch.com.ar` y cualquier otro `Host` que apunte a esa IP. En
   convivencia, endurecerlo:
   ```nginx
   server_name nutriapp.com.ar www.nutriapp.com.ar;
   # + un server{} catch-all con `return 444;` para Hosts desconocidos
   ```

**Email (`@nutriapp.com.ar`): sólo si se va a mandar mail desde ese dominio.** Hoy no hace falta
(email en `stub` hasta Fase 2) y no tiene relación con servir la app. Cuando se defina el proveedor
en Fase 2, ahí van `MX` + `SPF` + `DKIM` + `DMARC` **del proveedor que se elija** — copiar los de
Hostinger de la landing sólo tiene sentido si el mail de nutriapp también va a Hostinger, y si no,
autentica al remitente equivocado y los mails de recetas van a spam. Nota aparte: el `_dmarc` de la
landing es `p=none` (sólo monitorea, no protege); para un dominio que va a mandar mails
transaccionales conviene arrancar en `p=none` y endurecer a `quarantine` cuando SPF/DKIM alineen.

**Verificación** (desde PowerShell, contra un resolver público para saltear caché local):
```powershell
Resolve-DnsName nutriapp.com.ar     -Server 1.1.1.1 -Type A
Resolve-DnsName www.nutriapp.com.ar -Server 1.1.1.1 -Type CNAME
curl.exe -I https://nutriapp.com.ar
```
Propagación: con TTL 300 son minutos, pero el registrante puede tardar más en publicar la zona.

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
- **Renovación automática del cert** (la emisión ya está: webroot ACME en `nginx/acme/`; renovar y
  recargar nginx sigue siendo manual).
- **Convivencia con la landing en 80/443** y `server_name` endurecido — ver sección DNS.
- Rate-limit de red fino, WAF/headers extra según hosting.
- Imagen Keycloak `--optimized` (build stage) para arranque más rápido, si el boot importa.
