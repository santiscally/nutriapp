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
> la **renovación sigue siendo manual** (paso 1). **En el VPS del cliente esto probablemente no se
> use**: ahí Caddy ya termina TLS y emite/renueva solo — ver punto 2 de la sección DNS.

> **Pre-lanzamiento:** para publicar el dominio con una pantalla "Próximamente" y sólo el registro
> habilitado, ver [Modo pre-lanzamiento](#modo-pre-lanzamiento-próximamente). DNS concreto de
> `nutriapp.com.ar` en [DNS](#dns--nutriappcomar).

---

## Pre-requisitos (una vez)

1. **Dominio + DNS** apuntando al host, puertos 80/443 abiertos → ver [DNS — nutriapp.com.ar](#dns--nutriappcomar).
2. **Docker + Docker Compose v2** en el host.
3. **Node** (para compilar la SPA) — en el host o en un CI que deje el `dist/` listo.
4. **Definir quién termina TLS.** En el VPS del cliente los 80/443 ya los tiene **Caddy** (sirve la
   landing `haltcatch.com.ar`), así que el override tal como está —bindeado a `80:80`/`443:443`— **no
   levanta ahí**. Ver el punto 2 de la sección de DNS antes de intentar el `up`.

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

**Archivo listo para importar: [`nutriapp.com.ar.zone`](nutriapp.com.ar.zone)** (formato BIND, mismo
criterio que `haltcatch.com.ar.zone`). Los registros activos son sólo estos tres:

| Tipo    | Nombre | Valor                    | TTL |
| ------- | ------ | ------------------------ | --- |
| `A`     | `@`    | `187.127.36.153`         | 300 |
| `AAAA`  | `@`    | `2a02:4780:6e:84b8::1`   | 300 |
| `CNAME` | `www`  | `nutriapp.com.ar`        | 300 |

Con eso alcanza. No hay subdominios: `/api` y `/auth` son paths del mismo dominio, no hosts.
El bloque de correo y el de anti-spoofing quedaron comentados en el archivo (ver ahí cuándo usar
cada uno; son excluyentes entre sí).

**Es el mismo VPS que la landing `haltcatch.com.ar`**, y es un VPS de Hostinger:
`187.127.36.153` y `2a02:4780:6e:84b8::1` resuelven por PTR **los dos** a `srv1786758.hstgr.cloud`
→ una sola máquina dual-stack, así que el `AAAA` va igual que en la landing (verificado 2026-08-11).

**Tres cosas a resolver antes de importar / deployar:**

1. **La zona de `nutriapp.com.ar` no existe todavía.** Al 2026-08-11 el dominio devuelve **SERVFAIL**
   (no NXDOMAIN) desde `1.1.1.1`: hay delegación en nic.ar pero los nameservers no sirven la zona.
   Orden correcto: agregar el dominio en hPanel (crea la zona) → poner en nic.ar el par de NS que
   hPanel muestre para *este* dominio → importar el `.zone`. El archivo trae
   `lunar/solar.dns-parking.com`, el par de `haltcatch.com.ar`, por estar en la misma cuenta/VPS —
   pero **el par lo define hPanel, no el `.zone`**: el de haltcatch listaba `ns1/ns2` y la delegación
   real quedó igual en `lunar/solar`, o sea que el importador ignora esas líneas. Si hPanel muestra
   otro par, ese manda. (`jeianell.com.ar`, otra cuenta, quedó en `ns1/ns2.dns-parking.com`.)
2. **En el VPS los 80/443 los tiene Caddy, no nginx.** `haltcatch.com.ar` responde
   `Server: Caddy` en `:80` (308 → HTTPS) y en `:443` devuelve `Via: 1.1 Caddy` +
   `Server: nginx/1.27.5` → Caddy termina TLS y proxea a un nginx que sirve la landing. Entonces
   **el stack prod de nutriapp NO puede bindear `80:80`/`443:443`**: el `up` falla por puerto ocupado.
   Lo natural es sumarse a ese esquema — nutriapp escucha en un puerto alto de loopback y Caddy le
   pasa el dominio:
   ```caddyfile
   nutriapp.com.ar, www.nutriapp.com.ar {
       reverse_proxy 127.0.0.1:<puerto-alto>
   }
   ```
   **Si va detrás de Caddy, todo el trámite de certificados del paso 1 no hace falta**: Caddy emite y
   renueva solo (ACME automático). Lo que sí hay que hacer es adaptar el override para publicar HTTP
   plano en un puerto alto en vez de TLS en 443, y decidir dónde viven los security headers y el
   rate-limit (hoy están en el `server{}` de TLS de `nginx/conf.d/nutriapp.conf`) para no perderlos
   ni duplicarlos. El webroot ACME queda igual, inofensivo, para el caso de frontear directo.
3. **`server_name _` es catch-all.** Sólo importa si el nginx de nutriapp llegara a quedar expuesto
   en 80/443: ahí responde también para `haltcatch.com.ar` y cualquier `Host` que apunte a esa IP.
   En ese caso endurecerlo:
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
Resolve-DnsName nutriapp.com.ar     -Server 1.1.1.1 -Type AAAA
Resolve-DnsName www.nutriapp.com.ar -Server 1.1.1.1 -Type CNAME
curl.exe -I https://nutriapp.com.ar
```
Propagación: con TTL 300 son minutos, pero la delegación en nic.ar + la creación de la zona en
hPanel pueden tardar bastante más. Mientras siga dando SERVFAIL, el problema está antes del `.zone`.

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
- **Integración con el Caddy del VPS** (nutriapp en puerto alto detrás de Caddy, headers y
  rate-limit reubicados) — ver punto 2 de la sección DNS. Con eso, el cert lo maneja Caddy.
- Rate-limit de red fino, WAF/headers extra según hosting.
- Imagen Keycloak `--optimized` (build stage) para arranque más rápido, si el boot importa.
