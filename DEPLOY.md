# NUTRIAPP — Despliegue en producción

Stack prod = `docker-compose.yml` + override `docker-compose.prod.yml`. db, keycloak y backend
quedan en loopback (127.0.0.1) + red interna `nutriapp-net`; nginx es el reverse proxy de la app.
La SPA se sirve estática desde `frontend/dist` (build de Fran).

**En el VPS actual, nginx NO publica puertos: corre detrás del Caddy del host.** Los 80/443 los
tiene `edge-caddy-1` (stack `/root/stack`), que ya sirve `haltcatch.com.ar` y `jeianell.com.ar`.
Ver [Detrás del Caddy del VPS](#detrás-del-caddy-del-vps-topología-actual) — es la topología por
defecto del override. El modo "nginx como front único con TLS propio" quedó como opción, detrás
del override extra `docker-compose.edge.yml`.

Topología (single domain, path-based):

| Ruta      | Destino                          |
| --------- | -------------------------------- |
| `/`       | SPA estática (`frontend/dist`)   |
| `/api/`   | backend Spring Boot (`/api/v1`)  |
| `/auth/`  | Keycloak (`KC_HTTP_RELATIVE_PATH=/auth`) |

> **TLS: lo termina Caddy.** En el VPS actual el paso 1 (certbot / `nginx/certs/`) **no se usa**:
> Caddy emite y renueva solo por ACME. El material de bring-your-own-cert queda documentado para
> el modo front único (`docker-compose.edge.yml`), donde la renovación **sí es manual**.

> **Pre-lanzamiento:** para publicar el dominio con una pantalla "Próximamente" y sólo el registro
> habilitado, ver [Modo pre-lanzamiento](#modo-pre-lanzamiento-próximamente). DNS concreto de
> `nutriapp.com.ar` en [DNS](#dns--nutriappcomar).

---

## Pre-requisitos (una vez)

1. **Dominio + DNS** apuntando al host, puertos 80/443 abiertos → ver [DNS — nutriapp.com.ar](#dns--nutriappcomar).
2. **Docker + Docker Compose v2** en el host.
3. **Node**: no hace falta en el host — el build de la SPA va en un contenedor (paso 2).
4. **La red docker `web` tiene que existir** (la crea el stack de Caddy). `docker network ls | grep web`;
   si no está: `docker network create web`.
5. **Definir quién termina TLS.** En este VPS lo hace Caddy y el override ya viene configurado para
   eso → [Detrás del Caddy del VPS](#detrás-del-caddy-del-vps-topología-actual).

## Pasos de despliegue

### 1. Certificados TLS → `nginx/certs/`

> **Detrás de Caddy este paso entero se saltea.** Caddy pide el cert a Let's Encrypt la primera vez
> que llega tráfico para el dominio y lo renueva solo. Lo de abajo aplica sólo al modo front único
> (`docker-compose.edge.yml`).

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

**En el VPS no hay `node` instalado**, así que el build va en un contenedor descartable
(no ensucia el host y da la misma versión de Node siempre):

```
docker run --rm -v "$PWD/frontend:/app" -w /app \
  -e VITE_API_BASE_URL=https://nutriapp.com.ar \
  -e VITE_KEYCLOAK_URL=https://nutriapp.com.ar/auth \
  -e VITE_KEYCLOAK_REALM=nutriapp \
  -e VITE_KEYCLOAK_CLIENT_ID=nutriapp-frontend \
  -e VITE_COMING_SOON=true \
  node:22-alpine sh -c 'npm ci --no-audit --no-fund && npm run build'
```

Genera `frontend/dist` (lo sirve nginx read-only). Con node en el host es lo mismo con
`cd frontend && VITE_...=... npm ci && npm run build`. Verificar que el flag quedó horneado:

```
grep -o 'VITE_COMING_SOON:`[^`]*`' frontend/dist/assets/*.js   # → VITE_COMING_SOON:`true`
```

> **Ojo con `VITE_API_BASE_URL`:** el código le concatena `/api/v1`, así que el valor correcto es el
> **origen pelado** (`https://nutriapp.com.ar`), **sin** `/api` — con `/api` quedaría `/api/api/v1`.
> `frontend/` es de Fran. Estos son sólo los env de build documentados; no se modifica su código.

### 3. Fijar el secret del client `nutriapp-backend` (realm de prod)
El realm importado trae un secret **placeholder** (`*-dev-secret-change-me`) que NO debe usarse en
prod. El backend en prod **falla-cerrado** si queda vacío (503 en las ops de admin; el compose ni
levanta).

**Hay un huevo-y-gallina**: el compose exige `KEYCLOAK_ADMIN_CLIENT_SECRET` en `.env` *antes* de
arrancar, pero el secret vive dentro de Keycloak, que todavía no existe. Se resuelve al revés de
como suena — se genera el valor primero y se le *impone* al client una vez que Keycloak levantó,
en lugar de dejar que Keycloak lo genere y después copiarlo a mano:

```
# 1) generar y poner en .env ANTES del up:  KEYCLOAK_ADMIN_CLIENT_SECRET=<valor>
openssl rand -base64 24 | tr -d '/+=' | head -c 32

# 2) con el stack ya arriba, imponérselo al client (idempotente, se puede repetir):
set -a; . ./.env; set +a
KC=/opt/keycloak/bin/kcadm.sh
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T keycloak sh -c "
  $KC config credentials --server http://localhost:8080/auth --realm master \
      --user '$KEYCLOAK_ADMIN' --password '$KEYCLOAK_ADMIN_PASSWORD' &&
  ID=\$($KC get clients -r nutriapp -q clientId=nutriapp-backend --fields id --format csv --noquotes) &&
  $KC update clients/\$ID -r nutriapp -s secret='$KEYCLOAK_ADMIN_CLIENT_SECRET'"

# 3) reiniciar el backend para que tome el secret bueno
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart backend
```

Alternativa por consola (realm `nutriapp` → Clients → `nutriapp-backend` → Credentials →
*Regenerate*) y copiar el valor a `.env`: mismo resultado, pero necesita el túnel SSH del final de
esta sección y un `restart backend` igual.

### 4. `.env` de prod
Copiar `.env.example` → `.env` y completar el bloque **PRODUCCIÓN**. El compose **aborta**
(`:?`) si falta alguno de estos, pero **NO** detecta que sigan siendo los débiles de dev — es
tu responsabilidad regenerarlos:
- `KEYCLOAK_HOSTNAME` = URL pública **con `/auth`** (ej. `https://nutriapp.com.ar/auth`). Ver la
  nota de hostname v2 más abajo — sin el `/auth` el login rompe.
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

> **Keycloak hostname — el `/auth` va en `KEYCLOAK_HOSTNAME`.** En KC 25 (hostname v2), si el valor
> es una URL completa, el context path sale de esa URL y **`KC_HTTP_RELATIVE_PATH` no se le
> concatena**. Con `https://nutriapp.com.ar` (pelado) el `.well-known` responde igual bajo `/auth`,
> pero publica adentro `"issuer":"https://nutriapp.com.ar/realms/nutriapp"` — sin el prefijo. Ese
> path nginx no lo rutea: cae en el `try_files` de la SPA y devuelve `index.html` con 200, así que
> el login falla con un error de parseo en vez de un 404 honesto. El valor correcto es
> `https://nutriapp.com.ar/auth`. Chequeo rápido:
>
> ```
> curl -s http://127.0.0.1:8081/auth/realms/nutriapp/.well-known/openid-configuration \
>   | grep -o '"issuer":"[^"]*"'      # → .../auth/realms/nutriapp
> ```
>
> `KC_PROXY: edge` (del compose base) queda deprecado en KC 25 y loguea un WARN; funciona igual
> porque el override agrega `KC_PROXY_HEADERS: xforwarded`. En KC 26 hay que sacarlo.

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

## Detrás del Caddy del VPS (topología actual)

Los 80/443 del host los tiene `edge-caddy-1` (`caddy:2-alpine`, stack en `/root/stack`), que ya
sirve `haltcatch.com.ar` y `jeianell.com.ar`. NutriApp se suma a ese esquema en vez de pelearle
el puerto.

**El detalle que importa: Caddy no proxea a `127.0.0.1:<puerto>`, proxea por nombre de contenedor
sobre la red docker externa `web`.** `hac_frontend` y `jeianell_frontend` no publican un solo
puerto al host. NutriApp hace lo mismo: `nutriapp-nginx` entra a `web`, **sin `ports:`**, y Caddy
lo alcanza por DNS interno de docker. Así el host no suma superficie de red y el TLS lo maneja
Caddy (emite y renueva solo por ACME).

```
internet :443 → edge-caddy-1 (TLS, red `web`) → nutriapp-nginx:80 (red `web` + `nutriapp-net`)
                                                   ├── /      SPA (frontend/dist)
                                                   ├── /api/  backend:8080   ┐ sólo en
                                                   └── /auth/ keycloak:8080  ┘ nutriapp-net
```

`db`, `keycloak` y `backend` **no** están en `web`: los otros sitios del VPS no tienen ruta hacia
ellos. El único puente es nginx.

### Site block en `/root/stack/Caddyfile`

```caddyfile
nutriapp.com.ar, www.nutriapp.com.ar {
    encode zstd gzip
    reverse_proxy nutriapp-nginx:80
}
```

Recargar **validando primero** — un Caddyfile roto se lleva puestos los otros dos sitios:

```
docker exec edge-caddy-1 caddy validate --config /etc/caddy/Caddyfile
docker exec edge-caddy-1 caddy reload  --config /etc/caddy/Caddyfile
```

`reload` es en caliente (sin cortar conexiones) y **no toca los certs de los otros dominios**. Si
`nutriapp.com.ar` todavía no resuelve, Caddy loguea el fallo de ACME y reintenta con backoff; los
demás sitios siguen sirviendo normal.

> ⚠️ **Trampa del bind-mount de archivo suelto (pisada el 2026-08-11).** El compose de Caddy monta
> `./Caddyfile:/etc/caddy/Caddyfile:ro` — un **archivo**, no un directorio. Docker lo ata al
> **inodo**, así que cualquier editor que reemplace el archivo en vez de escribirlo in-place (`sed -i`,
> la mayoría de los editores, las herramientas de Claude) deja al contenedor viendo la versión
> **vieja**. `caddy reload` contesta `"config is unchanged"` y **no pasa nada** — un no-op que parece
> un éxito.
>
> Peor todavía: probar con `curl -I http://127.0.0.1 -H 'Host: nutriapp.com.ar'` da `308 → https`
> **aunque la ruta no exista**, porque es el redirect HTTP→HTTPS genérico de Caddy. No sirve como
> verificación. Lo único concluyente es preguntarle a Caddy qué tiene cargado:
>
> ```
> docker exec edge-caddy-1 wget -qO- http://127.0.0.1:2019/config/ | tr '}' '\n' | grep -o '"host":\[[^]]*\]'
> ```
>
> Si el dominio no aparece ahí, el reload no aplicó: `docker restart edge-caddy-1` (re-resuelve el
> bind mount; ~1-2 s de corte para los otros sitios). Ojo que `wget http://localhost:2019` da
> *connection refused* dentro del contenedor — el admin escucha en `127.0.0.1`, hay que usar la IP.

### Lo que cambia en la config de nginx

`NGINX_CONF_DIR` elige la variante (default `./nginx/conf.d-proxied`):

| | `conf.d-proxied` (default, detrás de Caddy) | `conf.d` (front único, + `docker-compose.edge.yml`) |
| --- | --- | --- |
| Puertos | ninguno publicado | `80:80`, `443:443` |
| TLS | lo hace Caddy | nginx, certs en `nginx/certs/` |
| Redirect 80→443 | lo hace Caddy | `return 301` en nginx |
| ACME | Caddy, automático | certbot `--webroot`, **renovación manual** |
| `server_name` | fijo + catch-all `444` | `_` (catch-all permisivo) |
| Red | `nutriapp-net` + `web` | sólo `nutriapp-net` |

Dos cosas fáciles de romper al pasar de una a la otra:

- **Los security headers y el `limit_req` viven en el `server{}`**, no en Caddy. Caddy no agrega
  HSTS ni CSP por su cuenta: si se editan en una variante hay que replicarlo en la otra o se
  pierden en silencio.
- **`set_real_ip_from` + `real_ip_header X-Forwarded-For`** son obligatorios detrás de Caddy. Sin
  eso `$remote_addr` es la IP del contenedor de Caddy y **todo internet cuenta como una sola IP**:
  el `limit_req zone=perip` y el rate-limit por IP de `/api/v1/registro` dejan de servir, y un
  visitante solo puede dejar afuera al resto. Va con `real_ip_recursive off` (toma el último valor
  de la cadena, que es el que appendea Caddy) para que un `X-Forwarded-For` inyectado por el
  cliente no lo pise.

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

**Tres cosas a resolver antes de importar / deployar** — al 2026-08-11 queda **sólo la primera**;
las otras dos se resolvieron al desplegar:

1. **Migración de DNS de DonWeb a Hostinger (en curso al 2026-08-11).** Estado verificado:
   - Delegación en nic.ar: `ns1/ns2.donweb.com`, que responden **`Query refused`** para el dominio
     (no tienen la zona) → de ahí el **SERVFAIL**. No hay ningún registro ni mail viviendo ahí, así
     que el cambio no rompe nada.
   - El alta del sitio en hPanel ya creó la zona en `orbit/horizon.dns-parking.com`
     (SOA serial `2026081101`), pero está **vacía**: sin `A`, sin `AAAA`, sin `www`, sin `MX`.

   Orden correcto — **los NS primero**: hPanel **no habilita el import de zona hasta que la
   delegación apunte a Hostinger** (probado 2026-08-11). No hay nada que cuidar en el medio: el
   dominio ya no resuelve, así que la ventana con la zona vacía no rompe nada.
   **(1)** cambiar los nameservers a `orbit.dns-parking.com` / `horizon.dns-parking.com` donde esté
   la delegación — nic.ar (Clave Fiscal del CUIT titular) o el panel de DonWeb si el dominio se
   gestiona desde ahí → **(2)** esperar que propague → **(3)** importar `nutriapp.com.ar.zone`.
   El par de NS lo asigna hPanel **por dominio**, no por cuenta: `haltcatch.com.ar` quedó en
   `lunar/solar` y `jeianell.com.ar` en `ns1/ns2`, de ahí que este sea un tercer par.

   **Al importar, revisar que no quede un `A` de parking.** Hostinger puede autopoblar la zona
   apuntando el dominio a su hosting compartido cuando detecta la delegación. El estado final tiene
   que ser el VPS: `A → 187.127.36.153` y `AAAA → 2a02:4780:6e:84b8::1`, sin registros duplicados.

   **Caddy ya está esperando ese momento**: el site block de `nutriapp.com.ar` está cargado y
   reintentando el cert; hoy falla con `"DNS problem: SERVFAIL"`. Cuando la zona resuelva, emite
   solo y el sitio queda arriba sin tocar nada más.
2. **En el VPS los 80/443 los tiene Caddy, no nginx.** ✅ Resuelto (2026-08-11) — ver
   [Detrás del Caddy del VPS](#detrás-del-caddy-del-vps-topología-actual).
3. **`server_name _` era catch-all.** ✅ Resuelto en `nginx/conf.d-proxied/nutriapp.conf`:
   `server_name` fijo + un `server{}` `default_server` que descarta con `return 444`. La variante
   `nginx/conf.d/nutriapp.conf` (modo front único) sigue con `server_name _` — si alguna vez se usa
   en un host compartido, endurecerla igual.

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

**¿La delegación ya está publicada?** Mientras el dominio dé **SERVFAIL** los resolvers públicos no
sirven para diagnosticar (van a los NS viejos, que responden `REFUSED`, y eso se ve igual esté el
cambio pendiente o mal guardado). Hay que preguntarle al **registro `.ar`**, que es el padre:
```powershell
Resolve-DnsName nutriapp.com.ar -Server 192.140.126.50 -Type NS   # d.dns.ar (TLD .ar)
Resolve-DnsName nutriapp.com.ar -Server 130.59.31.20   -Type NS   # f.dns.ar (segunda opinión)
```
Si eso devuelve `ns1/ns2.donweb.com`, el cambio **no está en el padre**: o nic.ar todavía no publicó,
o se cargaron los `NS` dentro del editor de zona de nic.ar en vez de cambiar la *delegación* del
dominio (error clásico: no toca el padre). Cuando devuelva `orbit`/`horizon`, los resolvers públicos
lo siguen en minutos y la app responde enseguida, porque la zona en Hostinger ya está autoritativa.
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

## Pendiente

- ~~Integración con el Caddy del VPS~~ **✅ hecho (2026-08-11)** — headers y rate-limit reubicados, cert
  a cargo de Caddy. La renovación automática dejó de ser un pendiente: la hace Caddy.
- **DNS**: la delegación de `nutriapp.com.ar` todavía apunta a DonWeb y la zona de Hostinger está vacía
  → el dominio no resuelve, el sitio no es alcanzable y Caddy no puede emitir el cert. Es lo único que
  falta para que quede arriba. Ver [DNS](#dns--nutriappcomar).
- **Avisos por mail del registro** (Fase 2) — hoy no sale ninguno; ver
  [Modo pre-lanzamiento](#modo-pre-lanzamiento-próximamente).
- **`BACKUP_GPG_RECIPIENT`** antes de abrir el registro: los dumps traen PII desde la primera solicitud.
- Rate-limit de red fino, WAF/headers extra según hosting.
- Imagen Keycloak `--optimized` (build stage) para arranque más rápido: el arranque actual corre el build
  cada vez (~50 s de augmentation). El propio Keycloak lo sugiere en el log
  (`kc.sh start --import-realm --optimized`).
- Sacar `KC_PROXY: edge` del compose base cuando se pase a Keycloak 26 (ahí deja de existir).
- Renovación manual del cert / webroot ACME: **sólo** aplica si alguna vez se usa el modo front único.
