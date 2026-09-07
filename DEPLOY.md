# BONOSAPP — Despliegue en producción

> **Rebranding (2026-09-03).** NutriApp pasó a llamarse **BonosApp** antes del lanzamiento y el
> dominio productivo pasa a ser **`bonosapp.com.ar`**. Los pasos de abajo ya apuntan al dominio
> nuevo; `nutriappok.com.ar` sigue en vivo y **queda** redirigiendo. La mudanza todavía no está
> hecha — checklist y estado verificado en
> [Migración a bonosapp.com.ar](#migración-a-bonosappcomar). Los identificadores internos
> (paquete `com.nutriapp`, realm `nutriapp`, clients `nutriapp-*`, red `nutriapp-net`, DBs,
> nombres de contenedor) **no** cambian: son infraestructura ya desplegada, no marca visible.

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
> habilitado, ver [Modo pre-lanzamiento](#modo-pre-lanzamiento-próximamente). DNS en
> [DNS](#dns--nutriappcomar): `bonosapp.com.ar.zone` es el dominio nuevo, `nutriappok.com.ar.zone`
> el viejo (sigue activo: ahí vive la casilla de contacto).

---

## Pre-requisitos (una vez)

1. **Dominio + DNS** apuntando al host, puertos 80/443 abiertos → ver [DNS — nutriappok.com.ar](#dns--nutriappcomar).
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
bash scripts/gen-selfsigned-cert.sh bonosapp.com.ar     # placeholder para poder arrancar
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
certbot certonly --webroot -w ./nginx/acme \
  -d bonosapp.com.ar -d www.bonosapp.com.ar -m <mail> --agree-tos
cp /etc/letsencrypt/live/bonosapp.com.ar/{fullchain,privkey}.pem nginx/certs/
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec nginx nginx -s reload
```

> Renovación: sigue siendo **manual** (copiar el par renovado + `nginx -s reload`). Automatizarla
> con un `--deploy-hook` es tarea de Fase 3.

### 2. Compilar la SPA

**En el VPS no hay `node` instalado**, así que el build va en un contenedor descartable
(no ensucia el host y da la misma versión de Node siempre):

```
docker run --rm -v "$PWD/frontend:/app" -w /app \
  -e VITE_API_BASE_URL=https://bonosapp.com.ar \
  -e VITE_KEYCLOAK_URL=https://bonosapp.com.ar/auth \
  -e VITE_KEYCLOAK_REALM=nutriapp \
  -e VITE_KEYCLOAK_CLIENT_ID=nutriapp-frontend \
  -e VITE_COMING_SOON=true \
  -e VITE_CONTACTO_EMAIL=info@nutriappok.com.ar \
  node:22-alpine sh -c 'npm ci --no-audit --no-fund && npm run build'
```

Genera `frontend/dist` (lo sirve nginx read-only). Con node en el host es lo mismo con
`cd frontend && VITE_...=... npm ci && npm run build`. Verificar que el flag quedó horneado:

```
grep -o 'VITE_COMING_SOON:`[^`]*`' frontend/dist/assets/*.js   # → VITE_COMING_SOON:`true`
```

> **Ojo con `VITE_API_BASE_URL`:** el código le concatena `/api/v1`, así que el valor correcto es el
> **origen pelado** (`https://bonosapp.com.ar`), **sin** `/api` — con `/api` quedaría `/api/api/v1`.
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
- `KEYCLOAK_HOSTNAME` = URL pública **con `/auth`** (ej. `https://bonosapp.com.ar/auth`). Ver la
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
> concatena**. Con `https://bonosapp.com.ar` (pelado) el `.well-known` responde igual bajo `/auth`,
> pero publica adentro `"issuer":"https://bonosapp.com.ar/realms/nutriapp"` — sin el prefijo. Ese
> path nginx no lo rutea: cae en el `try_files` de la SPA y devuelve `index.html` con 200, así que
> el login falla con un error de parseo en vez de un 404 honesto. El valor correcto es
> `https://bonosapp.com.ar/auth`. Chequeo rápido:
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

Para publicar `bonosapp.com.ar` antes de que la app esté terminada. Se activa con **un solo flag de
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
sirve `haltcatch.com.ar` y `jeianell.com.ar`. BonosApp se suma a ese esquema en vez de pelearle
el puerto.

**El detalle que importa: Caddy no proxea a `127.0.0.1:<puerto>`, proxea por nombre de contenedor
sobre la red docker externa `web`.** `hac_frontend` y `jeianell_frontend` no publican un solo
puerto al host. BonosApp hace lo mismo: `nutriapp-nginx` entra a `web`, **sin `ports:`**, y Caddy
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
bonosapp.com.ar, www.bonosapp.com.ar {
    encode zstd gzip
    reverse_proxy nutriapp-nginx:80
}

# El dominio viejo queda redirigiendo: los links ya compartidos por WhatsApp/mail no se rompen.
nutriappok.com.ar, www.nutriappok.com.ar {
    redir https://bonosapp.com.ar{uri} permanent
}
```

Recargar **validando primero** — un Caddyfile roto se lleva puestos los otros dos sitios:

```
docker exec edge-caddy-1 caddy validate --config /etc/caddy/Caddyfile
docker exec edge-caddy-1 caddy reload  --config /etc/caddy/Caddyfile
```

`reload` es en caliente (sin cortar conexiones) y **no toca los certs de los otros dominios**. Si
`bonosapp.com.ar` todavía no resuelve, Caddy loguea el fallo de ACME y reintenta con backoff; los
demás sitios siguen sirviendo normal.

> ⚠️ **Trampa del bind-mount de archivo suelto (pisada el 2026-08-11).** El compose de Caddy monta
> `./Caddyfile:/etc/caddy/Caddyfile:ro` — un **archivo**, no un directorio. Docker lo ata al
> **inodo**, así que cualquier editor que reemplace el archivo en vez de escribirlo in-place (`sed -i`,
> la mayoría de los editores, las herramientas de Claude) deja al contenedor viendo la versión
> **vieja**. `caddy reload` contesta `"config is unchanged"` y **no pasa nada** — un no-op que parece
> un éxito.
>
> Peor todavía: probar con `curl -I http://127.0.0.1 -H 'Host: nutriappok.com.ar'` da `308 → https`
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

> ⚠️ **La misma trampa, con git, sobre el `conf.d` de nginx (pisada el 2026-08-11).** El mount
> `${NGINX_CONF_DIR}:/etc/nginx/conf.d:ro` es un **directorio**, así que editar los `.conf` adentro
> sí se ve. Lo que **no** sobrevive es que el directorio entero se borre y se recree: un
> `git rebase`/`checkout` que pase por commits donde `nginx/conf.d-proxied/` todavía no existe hace
> exactamente eso, y el contenedor queda pegado al directorio viejo — **vacío**.
>
> Es silencioso al principio: nginx sigue sirviendo con la config que tiene en memoria. Explota
> recién en el próximo `nginx -s reload`, y explota feo — se queda **sin ningún `server{}`** y deja
> de escuchar, o sea *connection refused*, no un error de config. `nginx -t` pasa igual, porque una
> config vacía es válida. Chequeo:
>
> ```
> docker exec nutriapp-nginx ls /etc/nginx/conf.d/     # vacío = mount roto
> ```
>
> Se arregla recreando el contenedor, no recargándolo:
> `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --force-recreate nginx`.
> **Regla práctica: después de cualquier operación de git que toque `nginx/conf.d*/`, recrear nginx.**

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

## Migración a bonosapp.com.ar

Rebranding pedido por el cliente el **2026-09-03**, con el sitio todavía en modo pre-lanzamiento
(`VITE_COMING_SOON=true`) — el momento barato para hacerlo: no hay cuentas activas ni links
repartidos más allá de las demos. El cliente ya delegó `bonosapp.com.ar` a los nameservers de
Hostinger y mandó el kit de marca (`brand/`).

**Qué cambia y qué no.** Cambia lo que ve el usuario: nombre en la SPA, `<title>` y metadatos OG,
imagen de compartir, textos de los mails, `MAIL_FROM_NAME`, User-Agent de TiendaNube y el dominio.
**No** cambia nada interno: paquete `com.nutriapp`, realm `nutriapp`, clients `nutriapp-frontend` /
`nutriapp-backend`, red `nutriapp-net`, nombres de contenedor, DBs y el nombre del repo. Son
identificadores de infraestructura ya desplegada: renombrarlos obliga a re-importar el realm y a
re-emitir credenciales, sin que nadie lo vea.

### Estado verificado al 2026-09-07 (tarde)

**El par de NS de `bonosapp.com.ar` es `helios` + `aster`.dns-parking.com, NO nova/cosmos.** Se
confirmó de nuevo la regla de agosto: hPanel asigna el par **por dominio, no por cuenta**. La
delegación en nic.ar se cargó copiando la de nutriappok (nova/cosmos) y por eso el dominio daba
SERVFAIL. Se alinea **la delegación al par que muestra hPanel**, nunca al revés.

| Qué | Estado |
| --- | --- |
| Zona de `bonosapp.com.ar` en hPanel | ✅ **existe** — helios (`172.64.52.58`) y aster (`172.64.53.70`) contestan autoritativamente, SOA serial `2026090701` |
| Contenido de la zona | ❌ **vacía** — `A`/`AAAA`/`MX`/`TXT` dan NODATA, `www` da NXDOMAIN. Faltan los 3 registros del `.zone` |
| ¿`A` de parking autopoblado? | ✅ no hay — esta vez no autopobló nada, no hay que borrar |
| Delegación en nic.ar | ❌ todavía en nova/cosmos — pedido el cambio a helios/aster |
| `bonosapp.com.ar` en resolvers públicos | ❌ `SERVFAIL` (consecuencia de la delegación desalineada) |
| `nutriappok.com.ar` | ✅ en vivo, `A → 187.127.36.153` |

**Hacen falta las dos cosas, y son independientes:** alinear la delegación **y** cargar los
registros. Con la delegación arreglada pero la zona vacía, el dominio resuelve a nada y Caddy
tampoco emite el cert. Cargar los registros no depende de la delegación: la zona vive en hPanel.

Diagnóstico **por IP**, nunca por nombre (el resolver local cachea la resolución del nombre del
nameserver y devuelve estado viejo). Cómo leer la respuesta:

- `REFUSED` → el nameserver **no es autoritativo**: la zona no existe ahí (o la delegación apunta al
  par equivocado, que es el mismo síntoma visto desde afuera).
- Sólo `SOA` en la respuesta (NODATA) → la zona **existe pero está vacía** de ese tipo de registro.
- Datos → listo.

```powershell
Resolve-DnsName helios.dns-parking.com -Server 1.1.1.1 -Type A    # → 172.64.52.58
Resolve-DnsName bonosapp.com.ar -Server 172.64.52.58 -Type SOA    # ¿existe la zona?
Resolve-DnsName bonosapp.com.ar -Server 172.64.52.58 -Type A      # ¿tiene los registros?
Resolve-DnsName nutriappok.com.ar -Server 172.64.52.46 -Type A    # control contra nova (172.64.52.46)
```

**Pasos, en orden:**

1. **DNS.** Son **dos tareas independientes** y hacen falta las dos; no se esperan entre sí.
   - **1a. Delegación en nic.ar → `helios` + `aster`.dns-parking.com** (la carga el cliente, que es
     el titular). Estaba en nova/cosmos por haberla copiado de nutriappok, y por eso el dominio da
     SERVFAIL. Se alinea la delegación **al par que muestra hPanel**, nunca al revés: ese
     desalineamiento es el que produce el `409 "Domain is pending verification"`, que es circular
     (hPanel verifica la titularidad resolviendo los NS, y mientras dé SERVFAIL no puede pasar nunca).
   - **1b. Cargar los registros en la zona** — importar [`bonosapp.com.ar.zone`](bonosapp.com.ar.zone)
     (`A`, `AAAA`, `www` al VPS). La zona ya existe en hPanel pero está **vacía**, y esto **no
     depende de la delegación**: se puede hacer ya. Si sólo se arregla la delegación, el dominio
     resuelve a nada y Caddy tampoco emite el cert.
   - **La zona de `nutriappok.com.ar` NO se toca ni se borra.** Son zonas independientes, una por
     dominio; y ese dominio tiene que seguir vivo igual, porque ahí queda el `redir` y vive la
     casilla de contacto `info@nutriappok.com.ar`.
   - **El importador hace merge, no reemplazo**: en agosto Hostinger autopobló con un `A` de parking
     más `MX` y `SPF` propios. Esta vez no autopobló nada, pero revisar igual después de importar:
     `A`/`AAAA` tienen que apuntar al VPS y no debe quedar `MX`/`SPF` de Hostinger — ese SPF autentica
     al remitente equivocado y manda los mails de los bonos a spam.
   - Más contexto y el resto de las trampas de agosto: [DNS](#dns--nutriappcomar).
2. **Caddy** — agregar el site block de `bonosapp.com.ar` y dejar `nutriappok.com.ar` como `redir`
   permanente (ver [Site block](#site-block-en-rootstackcaddyfile)). `caddy validate` **antes** del
   `reload`, y confirmar contra la Admin API que el host quedó cargado: el bind-mount de archivo
   suelto muerde. Caddy emite el cert nuevo solo, por ACME.
3. **Rebuild de la SPA** con `VITE_API_BASE_URL` / `VITE_KEYCLOAK_URL` apuntando a
   `https://bonosapp.com.ar` (paso 2 del despliegue). Es obligatorio: esos valores se hornean en el
   bundle y la CSP tiene `connect-src 'self'` — con el origen viejo horneado, los fetch se bloquean.
4. **Redirect URIs del realm** — el client `nutriapp-frontend` del Keycloak de **prod** todavía sólo
   conoce el dominio viejo; sin esto el login rompe con `invalid_redirect_uri`. El JSON del repo no
   sirve acá: sólo se importa en realms nuevos. Por `kcadm`, sin entrar a la consola:

   ```
   set -a; . ./.env; set +a
   KC=/opt/keycloak/bin/kcadm.sh
   docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T keycloak sh -c "
     $KC config credentials --server http://localhost:8080/auth --realm master \
         --user '$KEYCLOAK_ADMIN' --password '$KEYCLOAK_ADMIN_PASSWORD' &&
     ID=\$($KC get clients -r nutriapp -q clientId=nutriapp-frontend --fields id --format csv --noquotes) &&
     $KC update clients/\$ID -r nutriapp \
       -s 'redirectUris=[\"https://bonosapp.com.ar/*\",\"https://www.bonosapp.com.ar/*\"]' \
       -s 'webOrigins=[\"https://bonosapp.com.ar\",\"https://www.bonosapp.com.ar\"]' &&
     $KC get clients/\$ID -r nutriapp --fields clientId,redirectUris,webOrigins"
   ```

   > ⚠️ **`kcadm update -s <array>=[...]` REEMPLAZA el array entero, no appendea.** Lo de arriba deja
   > **sólo** el dominio nuevo, que es lo correcto una vez que Caddy redirige el viejo con 301: el
   > callback OIDC nunca aterriza en `nutriappok.com.ar`. Si se hace **antes** de poner el redirect,
   > agregar también `"https://nutriappok.com.ar/*"` a las dos listas, o el login del dominio viejo
   > queda roto en la ventana intermedia. El `get` final imprime cómo quedó: mirarlo, no asumir.
5. **Rebuild del backend** — los textos de los mails y `MAIL_FROM_NAME` viven en el jar.
6. **`APP_PUBLIC_URL=https://bonosapp.com.ar`** en el `.env` del VPS: de ahí salen los links de los
   mails de registro.
7. **Avisarle a Leo** para que mude la casilla de contacto. Hasta que lo haga, la landing muestra
   `info@nutriappok.com.ar` (pedido explícito del cliente) — cuando exista la nueva, alcanza con
   `VITE_CONTACTO_EMAIL=info@bonosapp.com.ar` + rebuild de la SPA, sin tocar código.

### Runbook del VPS

La secuencia de arriba en comandos, partida en dos fases por una razón concreta: **el origen se
hornea en el bundle de la SPA y en `KC_HOSTNAME`**. Si se apunta al dominio nuevo antes de que
resuelva, se rompe el sitio que HOY está en vivo en `nutriappok.com.ar`. Todo lo demás no depende
del DNS y conviene dejarlo hecho mientras propaga.

#### Fase A — ahora, sin esperar al DNS

```
cd /root/nutriapp        # ajustar si el checkout está en otro lado
git pull origin main
```

**A1. Caddy** — editar `/root/stack/Caddyfile` con los dos site blocks de
[Site block](#site-block-en-rootstackcaddyfile), y después:

```
docker exec edge-caddy-1 caddy validate --config /etc/caddy/Caddyfile
docker exec edge-caddy-1 caddy reload  --config /etc/caddy/Caddyfile
docker exec edge-caddy-1 wget -qO- http://127.0.0.1:2019/config/ | tr '}' '\n' | grep -o '"host":\[[^]]*\]'
```

El tercer comando **no es opcional**: el Caddyfile se monta como archivo suelto y un editor que
reemplace el inodo deja al contenedor viendo la versión vieja, con `reload` contestando
`"config is unchanged"` — un no-op que parece un éxito. Si `bonosapp.com.ar` no aparece en esa
salida, el reload no aplicó: `docker restart edge-caddy-1`.

Mientras el dominio no resuelva, Caddy loguea el fallo de ACME y reintenta con backoff. Es
esperable y **no afecta a los otros sitios del VPS**.

**A2. `.env`** — agregar/ajustar sólo esto (el `KEYCLOAK_HOSTNAME` va en la fase B):

```
APP_PUBLIC_URL=https://bonosapp.com.ar
```

**A3. Rebuild del backend** — los textos de los mails y `MAIL_FROM_NAME` viajan dentro del jar:

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build backend
```

`--build` es obligatorio, no alcanza `up -d`: `application.yml` viaja **dentro** del jar, así que
sin rebuild el contenedor toma el env nuevo pero corre el yml viejo — y el síntoma engaña, porque
`printenv` muestra los valores correctos. Sin riesgo para el sitio en vivo: el mail está en `stub`,
no sale nada.

**A4. Redirect URIs** — el comando del paso 4, pero **incluyendo también el dominio viejo**, porque
en esta ventana el `redir` todavía no está y el login de `nutriappok.com.ar` tiene que seguir
andando:

```
-s 'redirectUris=[\"https://bonosapp.com.ar/*\",\"https://www.bonosapp.com.ar/*\",\"https://nutriappok.com.ar/*\"]'
-s 'webOrigins=[\"https://bonosapp.com.ar\",\"https://www.bonosapp.com.ar\",\"https://nutriappok.com.ar\"]'
```

#### Puerta entre las dos fases

No arrancar la fase B hasta que esto devuelva la IP del VPS contra un resolver público:

```
Resolve-DnsName bonosapp.com.ar -Server 1.1.1.1 -Type A     # → 187.127.36.153
```

Si da SERVFAIL, el problema está antes: preguntarle **por IP** a helios (`172.64.52.58`) para
separar "falta la delegación" de "falta el contenido de la zona" — ver
[Estado verificado](#estado-verificado-al-2026-09-07-tarde).

#### Fase B — con el dominio resolviendo

**B1. Rebuild de la SPA** con el origen nuevo (paso 2 del despliegue, ya con
`VITE_API_BASE_URL=https://bonosapp.com.ar`). Verificar que quedó horneado:

```
grep -o 'bonosapp\.com\.ar' frontend/dist/assets/*.js | head -1
```

**B2. `KEYCLOAK_HOSTNAME`** → `https://bonosapp.com.ar/auth` (con `/auth`, ver la nota de hostname
v2) y reiniciar Keycloak:

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d keycloak
```

**B3. Verificación de punta a punta:**

```
curl -I https://bonosapp.com.ar                          # 200, cert válido
curl -I https://nutriappok.com.ar                        # 301 → bonosapp
curl -s https://bonosapp.com.ar/auth/realms/nutriapp/.well-known/openid-configuration | grep -o '"issuer":"[^"]*"'
curl -s https://bonosapp.com.ar/actuator/health
```

El `issuer` tiene que decir `https://bonosapp.com.ar/auth/realms/nutriapp` — **con** el `/auth`. Si
sale sin el prefijo, `KEYCLOAK_HOSTNAME` quedó pelado y el login del SPA rompe.

**B4. Limpieza (opcional)** — una vez confirmado el `301`, sacar `nutriappok.com.ar` de
`redirectUris` y `webOrigins` con el mismo comando del paso 4, esta vez sólo con el dominio nuevo.

**El dominio viejo no se da de baja.** Ahí vive la casilla de contacto y los links ya compartidos
por WhatsApp apuntan a él; queda redirigiendo con `301`. Su zona sigue siendo
[`nutriappok.com.ar.zone`](nutriappok.com.ar.zone).

---

## DNS — nutriappok.com.ar

**Dos zonas, dos archivos:** [`bonosapp.com.ar.zone`](bonosapp.com.ar.zone) (dominio nuevo, el que
sirve la app) y [`nutriappok.com.ar.zone`](nutriappok.com.ar.zone) (viejo, sigue activo: redirige y
aloja la casilla de contacto). Formato BIND, mismo criterio que `haltcatch.com.ar.zone`. Los dos
apuntan al mismo VPS y los registros activos son los mismos tres — abajo, los de `nutriappok`:

| Tipo    | Nombre | Valor                    | TTL |
| ------- | ------ | ------------------------ | --- |
| `A`     | `@`    | `187.127.36.153`         | 300 |
| `AAAA`  | `@`    | `2a02:4780:6e:84b8::1`   | 300 |
| `CNAME` | `www`  | `nutriappok.com.ar`        | 300 |

Con eso alcanza. No hay subdominios: `/api` y `/auth` son paths del mismo dominio, no hosts.
El bloque de correo y el de anti-spoofing quedaron comentados en el archivo (ver ahí cuándo usar
cada uno; son excluyentes entre sí).

**Es el mismo VPS que la landing `haltcatch.com.ar`**, y es un VPS de Hostinger:
`187.127.36.153` y `2a02:4780:6e:84b8::1` resuelven por PTR **los dos** a `srv1786758.hstgr.cloud`
→ una sola máquina dual-stack, así que el `AAAA` va igual que en la landing (verificado 2026-08-11).

**Tres cosas a resolver antes de importar / deployar** — al 2026-08-11 queda **sólo la primera**;
las otras dos se resolvieron al desplegar:

1. ~~DNS~~ **✅ RESUELTO — el sitio está en vivo desde el 2026-08-11.** `nutriappok.com.ar` y `www`
   resuelven al VPS y Caddy emitió los certs. Lo que costó, por si hay que repetirlo en otro dominio:

   - **El par de NS se asigna POR DOMINIO, no por cuenta** — `haltcatch.com.ar` en `lunar/solar`,
     `jeianell.com.ar` en `ns1/ns2`, `nutriappok.com.ar` en `nova/cosmos`. Siempre mirar el par que
     hPanel muestra **para ese** dominio; copiar el de otro deja la delegación apuntando a NS que
     rechazan la zona.
   - **`409 "Domain is pending verification"` al importar es circular.** hPanel verifica la titularidad
     resolviendo los `NS` del dominio por DNS. Si el padre delega a NS que rechazan la zona, eso da
     **`SERVFAIL`** —no "apunta a otro lado"— y la verificación pide una respuesta que sólo existiría
     si la zona ya estuviera publicada. **No es propagación y esperar no lo arregla**: se destraba
     alineando la delegación al par correcto.
   - **`404` en `PATCH /api/dns/v1/direct/zone/resource-records`** al agregar registros a mano: la zona
     existe en los nameservers pero el panel no la encuentra. Se destrabó solo al alinear la delegación.
   - **Al importar, Hostinger autopobla la zona con su hosting compartido** (`A → 212.1.211.163`,
     `AAAA`, más `MX` y `SPF` propios) y el importador **hace merge, no reemplazo**: no pisa lo que ya
     estaba. Hay que corregir `A`/`AAAA` a mano al VPS y borrar el `MX`/`SPF` (el mail de la app no sale
     por Hostinger; ese SPF autenticaría al remitente equivocado en Fase 2 y las recetas irían a spam).

   > ⚠️ **Contra un servidor autoritativo, consultar por IP, no por nombre.** `dig @nova.dns-parking.com`
   > puede devolver datos viejos porque el resolver local cachea la resolución del **nombre** del
   > nameserver; `dig @172.64.52.46` (la misma máquina) devuelve el estado real. Con esto casi
   > diagnosticamos que un import no había entrado cuando sí.

   Caddy validó por **`tls-alpn-01`**, así que el webroot ACME de `nginx/acme/` no intervino: es
   material del modo front único solamente.

   **Al importar, revisar que no quede un `A` de parking.** Hostinger puede autopoblar la zona
   apuntando el dominio a su hosting compartido cuando detecta la delegación. El estado final tiene
   que ser el VPS: `A → 187.127.36.153` y `AAAA → 2a02:4780:6e:84b8::1`, sin registros duplicados.

   **Caddy ya está esperando ese momento**: el site block de `nutriappok.com.ar` está cargado y
   reintentando el cert; hoy falla con `"DNS problem: SERVFAIL"`. Cuando la zona resuelva, emite
   solo y el sitio queda arriba sin tocar nada más.

   > ⚠️ **El dominio es `nutriappOK.com.ar`, no `nutriapp.com.ar`.** El 2026-08-11 se configuró y se
   > dio de alta en hPanel el segundo por error. **`nutriapp.com.ar` no es nuestro**: su delegación
   > en el registro `.ar` apunta a `ns1/ns2.donweb.com`. La zona que quedó creada en hPanel para ese
   > nombre es huérfana (existe en `orbit/horizon` pero nadie le delega) y **conviene borrarla** —
   > no sirve para nada y confunde. Todo el repo, el `.env`, el Caddyfile y el bundle de la SPA ya
   > están renombrados; el `.zone` pasó a llamarse `nutriappok.com.ar.zone`.
2. **En el VPS los 80/443 los tiene Caddy, no nginx.** ✅ Resuelto (2026-08-11) — ver
   [Detrás del Caddy del VPS](#detrás-del-caddy-del-vps-topología-actual).
3. **`server_name _` era catch-all.** ✅ Resuelto en `nginx/conf.d-proxied/nutriapp.conf`:
   `server_name` fijo + un `server{}` `default_server` que descarta con `return 444`. La variante
   `nginx/conf.d/nutriapp.conf` (modo front único) sigue con `server_name _` — si alguna vez se usa
   en un host compartido, endurecerla igual.

**Email (`@nutriappok.com.ar`): sólo si se va a mandar mail desde ese dominio.** Hoy no hace falta
(email en `stub` hasta Fase 2) y no tiene relación con servir la app. Cuando se defina el proveedor
en Fase 2, ahí van `MX` + `SPF` + `DKIM` + `DMARC` **del proveedor que se elija** — copiar los de
Hostinger de la landing sólo tiene sentido si el mail de nutriapp también va a Hostinger, y si no,
autentica al remitente equivocado y los mails de recetas van a spam. Nota aparte: el `_dmarc` de la
landing es `p=none` (sólo monitorea, no protege); para un dominio que va a mandar mails
transaccionales conviene arrancar en `p=none` y endurecer a `quarantine` cuando SPF/DKIM alineen.

**Verificación** (desde PowerShell, contra un resolver público para saltear caché local):
```powershell
Resolve-DnsName nutriappok.com.ar     -Server 1.1.1.1 -Type A
Resolve-DnsName nutriappok.com.ar     -Server 1.1.1.1 -Type AAAA
Resolve-DnsName www.nutriappok.com.ar -Server 1.1.1.1 -Type CNAME
curl.exe -I https://nutriappok.com.ar
```

**¿La delegación ya está publicada?** Mientras el dominio dé **SERVFAIL** los resolvers públicos no
sirven para diagnosticar (van a los NS viejos, que responden `REFUSED`, y eso se ve igual esté el
cambio pendiente o mal guardado). Hay que preguntarle al **registro `.ar`**, que es el padre:
```powershell
Resolve-DnsName <dominio> -Server 192.140.126.50 -Type NS   # d.dns.ar (TLD .ar)
Resolve-DnsName <dominio> -Server 130.59.31.20   -Type NS   # f.dns.ar (segunda opinión)
```
> ⚠️ Desde la máquina de Santi estos dos servidores del TLD dieron `Error de servidor DNS` incluso
> para un dominio que resuelve bien, así que **no sirven para descartar nada desde acá**. Para saber
> si el problema es la delegación o la zona, preguntarle **por IP al nameserver autoritativo**:
> `REFUSED` = la zona no existe ahí; una respuesta con datos = la zona existe y el problema está en
> el padre. Ver la tabla de [Migración a bonosapp.com.ar](#migración-a-bonosappcomar).

Si eso devuelve `ns1/ns2.donweb.com`, el cambio **no está en el padre**: o nic.ar todavía no publicó,
o se cargaron los `NS` dentro del editor de zona de nic.ar en vez de cambiar la *delegación* del
dominio (error clásico: no toca el padre). Cuando devuelva `orbit`/`horizon`, los resolvers públicos
lo siguen en minutos y la app responde enseguida, porque la zona en Hostinger ya está autoritativa.
Propagación: con TTL 300 son minutos, pero la delegación en nic.ar + la creación de la zona en
hPanel pueden tardar bastante más. Mientras siga dando SERVFAIL, el problema está antes del `.zone`.

---

## Backup / restore de la DB
```
bash scripts/backup-db.sh                                       # → backups/{nutriapp,keycloak}-<ts>.dump.gpg
bash scripts/restore-db.sh backups/nutriapp-<ts>.dump.gpg --yes # DESTRUCTIVO (--clean); autodetecta .gpg; exige --yes
```
Los dumps traen **PII** (pacientes/recetas: DNI, CUIT, matrícula, archivos) + el **store de
credenciales de Keycloak**. Los `.dump*` NO se commitean (`.gitignore`). Guardar copias fuera del
host para DR.

**Cifrado: ✅ activo en el VPS desde el 2026-08-11.** `BACKUP_GPG_RECIPIENT=backups@nutriappok.com.ar`
en el `.env`, contra la clave `ed25519/CEE22F19C64220E5` generada en el host. Verificado de punta a
punta: cifra → descifra → `pg_restore -l` lista 79 objetos con las tablas reales.

> ⚠️ **La clave privada está en el VPS y hay que sacarla de ahí.** Exportada en
> `/root/nutriapp-backup-gpg-PRIVATE.asc` (fuera del repo, `chmod 600`). Guardarla en un gestor de
> contraseñas o un disco offline y después borrarla del host:
>
> ```
> gpg --batch --yes --delete-secret-keys CEE22F19C64220E5   # deja sólo la pública: sigue cifrando
> rm -f /root/nutriapp-backup-gpg-PRIVATE.asc
> ```
>
> Cifrar sólo hace falta la clave **pública**, así que los backups siguen funcionando igual. Mientras
> la privada viva en el host, un compromiso del VPS descifra también las copias que estén afuera.
> **Sin la privada no hay restore posible** — si se pierde el export, los dumps son papel picado.

Para restaurar en una máquina nueva, importar la privada primero:
`gpg --import nutriapp-backup-gpg-PRIVATE.asc`.

> **Los scripts leen el `.env`** (agregado 2026-08-11). Antes no lo hacían: `BACKUP_GPG_RECIPIENT`
> seteado ahí no tenía ningún efecto y los dumps salían en **texto plano** con sólo un aviso por
> stderr — invisible desde cron. Lo que ya venga del entorno le gana al `.env`.

---

## Pendiente

- ~~Integración con el Caddy del VPS~~ **✅ hecho (2026-08-11)** — headers y rate-limit reubicados, cert
  a cargo de Caddy. La renovación automática dejó de ser un pendiente: la hace Caddy.
- ~~DNS de `nutriappok.com.ar`~~ **✅ hecho (2026-08-11)** — el dominio resuelve al VPS y Caddy emitió
  el cert.
- **Mudanza a `bonosapp.com.ar`** (rebranding del 2026-09-03): importar la zona, agregar el site block
  en Caddy con el `redir` del dominio viejo, rebuild de la SPA y agregar el redirect URI en el realm de
  prod. Checklist completo en [Migración a bonosapp.com.ar](#migración-a-bonosappcomar).
- **Avisos por mail del registro** (Fase 2) — hoy no sale ninguno; ver
  [Modo pre-lanzamiento](#modo-pre-lanzamiento-próximamente).
- **`BACKUP_GPG_RECIPIENT`** antes de abrir el registro: los dumps traen PII desde la primera solicitud.
- Rate-limit de red fino, WAF/headers extra según hosting.
- Imagen Keycloak `--optimized` (build stage) para arranque más rápido: el arranque actual corre el build
  cada vez (~50 s de augmentation). El propio Keycloak lo sugiere en el log
  (`kc.sh start --import-realm --optimized`).
- Sacar `KC_PROXY: edge` del compose base cuando se pase a Keycloak 26 (ahí deja de existir).
- Renovación manual del cert / webroot ACME: **sólo** aplica si alguna vez se usa el modo front único.
