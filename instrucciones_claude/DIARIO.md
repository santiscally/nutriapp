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

## 2026-09-07 (3) — Santi — infra/auth/db (⚑ bonosapp.com.ar EN VIVO: la migración ejecutada en el VPS)
**Qué:** Se ejecutó la migración entera en producción. `https://bonosapp.com.ar` sirve la SPA con
cert propio y `VITE_COMING_SOON=true`; `nutriappok.com.ar` quedó como **301 permanente**. Se mudaron
en la misma ventana el dominio, el volumen de datos, la base y el rol de Postgres, el realm y los
clients de Keycloak, y los nombres de contenedor y de red. **Cero pérdida de datos** (censo de filas
idéntico antes y después, 3 usuarios de Keycloak intactos, Flyway validó las 13 migraciones sin
checksum mismatch y aplicó la V013 nueva).
**Por qué:** el DNS ya resolvía (`bonosapp.com.ar` y `www` → 187.127.36.153, delegación helios/aster
alineada), o sea la puerta entre las fases A y B del runbook estaba abierta.
**Problemas — el runbook de `DEPLOY.md` estaba MAL en cuatro puntos, uno de ellos destructivo:**
1. 🔴 **El rename cambió `name:` en `docker-compose.yml`, o sea el nombre del PROYECTO de compose, y
   con él el del volumen** (`nutriapp_nutriapp_db_data` → `bonosapp_bonosapp_db_data`). Siguiendo el
   runbook al pie de la letra, `down` no ve el stack viejo (sigue corriendo) y `up -d` levanta uno
   nuevo **con un volumen vacío**: Postgres corre su init y arranca una base en blanco. Hubo que
   migrar el volumen a mano (`docker run ... cp -a`, con el stack abajo). **Es el paso que faltaba
   y era el que borraba todo.** El volumen viejo se conservó como rollback.
2. **El rename del realm tiene que ir ANTES de levantar el stack nuevo**, contra el Keycloak viejo.
   Si arranca primero el nuevo, `--import-realm` no encuentra el realm `bonosapp` y lo **crea desde
   el JSON del repo** (secret placeholder de dev + usuarios seed), y después el rename choca por
   nombre duplicado. Hecho en el orden correcto, el import lo saltea.
3. **`rename-db.sh` moría a mitad de camino**: `ALTER ROLE ... RENAME` falla con `session user cannot
   be renamed` porque la sesión es `psql -U nutriapp`, justo el rol que se renombra. Dejó la base
   renombrada, el rol sin renombrar y el `.env` sin tocar. Se completó creando un superusuario
   temporal, renombrando desde su sesión y borrándolo.
4. **`pg_restore -l -` no existe** (pg_restore no lee el formato custom de stdin), así que la
   verificación del dump fallaba **siempre** y abortaba el script con un backup sano. Arreglado en
   `scripts/rename-db.sh`: el dump se copia al contenedor y se verifica por path.
**Dos bugs más, ajenos al runbook:**
5. 🔴 **`KEYCLOAK_ISSUER_URI` vacío rompía TODA la API autenticada.** `application-prod.yml` hace
   `issuer-uri: ${KEYCLOAK_ISSUER_URI:}` → string vacío, y Spring arma igual el `JwtIssuerValidator`
   con issuer `""`: **todo token se rechaza** con `401 The iss claim is not valid`. El comentario del
   `.env` afirmaba lo contrario ("vacío = sólo se valida la firma"). **Venía así desde el deploy de
   agosto**: la API autenticada de prod nunca había respondido 200; no se notó porque el sitio está
   en pre-lanzamiento. Resuelto seteando el issuer público exacto.
6. **`nginx/conf.d-proxied/bonosapp.conf` seguía con `server_name nutriappok.com.ar`** y el `server`
   catch-all devuelve `444`. Caddy preserva el `Host`, así que el dominio nuevo habría caído en el
   catch-all y cerrado la conexión sin responder. Corregido a `bonosapp.com.ar www.bonosapp.com.ar`.
**Además:** el client `nutriapp-frontend` de prod tenía todavía los `redirectUris` de dev
(`http://localhost:5173/*`), invisible porque el login es ROPC y no usa redirect — ya apuntan al
dominio nuevo. Y el mapper de audiencia **no cuelga del client sino del client scope**
`nutriapp-audience`, con `included.client.audience: nutriapp-backend`; se renombró el scope y se
recreó el mapper (Keycloak ignora en silencio el cambio de `name` de un mapper existente).
**Verificado de punta a punta:** `https://bonosapp.com.ar` 200 con cert válido y los security headers;
`nutriappok.com.ar` → 301; `/actuator/health` UP; issuer `https://bonosapp.com.ar/auth/realms/bonosapp`;
`/auth/admin` 404; login ROPC → token con `aud: bonosapp-backend`, `scope: bonosapp-audience` y
`resource_access.bonosapp-backend: ['admin:manage']`; `/api/v1/me` y `/api/v1/admin/nutricionistas`
**200**; `/api/v1/registro` devuelve el 415 con `ApiError`; haltcatch y jeianell intactos.
**⚠️ HALLAZGO DE SEGURIDAD, sin resolver (decisión del usuario):** la credencial **seed de dev**
`admin@nutriapp.dev` / `test1234` **funciona en producción** y trae `ADMIN` + `admin:manage`. La
contraseña está en el JSON del realm versionado en el repo y en este mismo DIARIO. Viene de haber
importado el realm de dev en prod en agosto. **Hay que rotarla o borrar la cuenta antes del
lanzamiento** — y lo mismo con `nutri@nutriapp.dev`.
**Impacto para el otro (Fran):** el sitio en vivo pasó a `https://bonosapp.com.ar`; el viejo redirige.
El contrato REST no cambió. En local, lo más rápido sigue siendo `docker compose down -v && up -d --build`.
**Refs:** `DEPLOY.md` (runbook corregido, fase B + B0 nuevo), `scripts/rename-db.sh`,
`nginx/conf.d-proxied/bonosapp.conf`, `.env` y `/root/stack/Caddyfile` del VPS.
## 2026-09-07 (4) — Fran — integraciones/email + dns (Resend: dominio bonosapp.com.ar dado de alta; faltan 3 registros en Hostinger)
**Qué:** Decisión de remitente productivo + alta del dominio en Resend para pasar el mail a prod.
- **Dominio remitente = `bonosapp.com.ar`** (NO nutriappok): cambiar de dominio más adelante obliga a
  re-verificar todo en Resend (3 registros + Verify), así que se elige el dominio final ahora y se evita
  el doble trabajo. **From = `no-reply@bonosapp.com.ar`**; **Reply-To = `info@nutriappok.com.ar`** (única
  casilla viva hoy) hasta que Gon dé de alta `info@bonosapp.com.ar` → ahí es cambio de una línea, sin DNS.
- El mailbox NO bloquea: Resend solo ENVÍA, se manda desde `@bonosapp.com.ar` aunque la casilla no exista.
- Dominio agregado en Resend, región **São Paulo (sa-east-1)** → usa el setup nuevo con **CNAMEs a
  `forge.rmta.net`** (no el MX+TXT clásico). Más seguro: no toca la raíz.
**⚠️ Para Santi (infra) — cargar en hPanel Hostinger → ZONA DNS de `bonosapp.com.ar`, SOLO AGREGAR:**
  | Tipo | Nombre | Contenido | TTL |
  |------|--------|-----------|-----|
  | CNAME | `send` | `send.forge.rmta.net` | 3600 |
  | CNAME | `rsend` | `rsend-sae1.forge.rmta.net` | 3600 |
  | TXT | `resend._domainkey` | `p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDQO2VECzbFEtoqCvPt4l21Bmy7rm+b/pwcPByAwuR+ZeR/UXTZ3W0NMiX/Pk/UaZfP+Qz7s6/PQkNzVdabahSx+5sEScWSrS6CYj+QPcUy5FnC/xoKP7sg+DHyuJZusK5rJJi7wacVItTpv3xw8c5glR8L1NB1m8Krgq2QxokgbwIDAQAB` | Auto |
  - **NO cargar el `_dmarc` de Resend:** la zona YA tiene `v=DMARC1; p=none` (uno solo por dominio, cumple igual).
  - **NO tocar** MX raíz (mx1/mx2.hostinger.com), SPF raíz (`_spf.mail.hostinger.com`) ni el `_dmarc` actual.
    El correo Hostinger vivo no se afecta (Resend va sobre subdominios `send`/`rsend`).
  - Nombres en Hostinger: solo `send` / `rsend` / `resend._domainkey`, NO el dominio completo.
**Secuencia (brief Resend, punto 10):** cargar registros → verificar propagación (nslookup) → Verify en Resend
  → **recién ahí** cambiar `MAIL_FROM_ADDRESS` en el `.env` del SERVIDOR (VPS) a `no-reply@bonosapp.com.ar`.
  Poner el remitente antes de verificar = Resend rechaza los envíos.
**Refs:** `.env` (local, sigue en sandbox `onboarding@resend.dev`), brief de Resend.

## 2026-09-07 (2) — Santi — backend/infra/auth (el rename interno: todo pasa a bonosapp menos el repo)
**Qué:** Segunda mitad del rebranding. La primera tanda había dejado los identificadores internos en
`nutriapp` a propósito; por decisión del usuario ahora **también se renombran**. Lo único que sigue
diciendo `nutriapp` es el **nombre del repo** (`santiscally/nutriapp`) y la carpeta local, para no
mover el remote ni el path del proyecto.
- **Backend:** paquete `com.nutriapp` → `com.bonosapp` (183 archivos), `artifactId` y `finalName`
  → `bonosapp-backend` (con el `cp target/...jar` del Dockerfile), los 8 `@ConfigurationProperties`
  y la clave raíz del `application.yml` → `bonosapp.*`, `NutriappApplication` → `BonosappApplication`.
- **Keycloak:** realm y clients → `bonosapp` / `bonosapp-frontend` / `bonosapp-backend`, el mapper de
  audiencia, los usuarios seed y el `.json` renombrado a `bonosapp-realm.json`.
- **Infra:** red `bonosapp-net`, contenedores `bonosapp-*`, upstreams de nginx, los dos `.conf`
  renombrados, `POSTGRES_DB`/`POSTGRES_USER`, y los scripts de smoke/backup/restore.
- **Front:** `VITE_KEYCLOAK_REALM`, `VITE_KEYCLOAK_CLIENT_ID` y la clave de localStorage
  (`nutriapp.tokens` → `bonosapp.tokens`, que desloguea las sesiones locales — irrelevante en
  pre-lanzamiento).
- **`scripts/rename-db.sh`** (nuevo): la migración de la base, automática y fail-closed.
**Por qué:** el usuario lo pidió explícitamente. Mi objeción era el costo de tocar infra desplegada;
decidió que la coherencia vale más, y en pre-lanzamiento el costo es bajo.
**Problemas (el primero es el que hay que recordar):**
- ⚠️ **El `sed` masivo tocó dos migraciones Flyway YA APLICADAS** (`V004` y `V009`), donde `nutriapp`
  aparecía en un comentario. Flyway valida **checksum** de las migraciones aplicadas: eso habría
  hecho que el backend **no arranque** en prod, con un error que no se parece en nada a un rename.
  Se detectó comparando `git diff` archivo por archivo bajo `db/migration/` y se revirtieron byte a
  byte. **Regla: una migración aplicada no se toca nunca, ni un comentario.** Quedan tres menciones
  a "Nutriapp" a propósito: esas dos migraciones y dos citas literales de la planilla de Gon.
- **Renombrar el rol de Postgres puede romper la contraseña.** Un hash `md5` incluye el nombre de
  usuario, así que `ALTER ROLE ... RENAME` lo invalida. Con `scram-sha-256` (el default de PG16) no
  pasa. `rename-db.sh` lo chequea antes y aborta pidiendo `--no-role` si encuentra md5.
- **El Caddyfile del VPS acopla por nombre de contenedor** (`reverse_proxy nutriapp-nginx:80`). Al
  recrear el stack el contenedor pasa a `bonosapp-nginx` → 502 si no se edita el Caddyfile en la
  misma ventana. Anotado en el runbook.
- El `down` es obligatorio para recrear red y contenedores: `up -d` no los renombra en caliente.
**Sobre `rename-db.sh`:** hace preflight (base vieja presente, nueva libre, rol renombrable),
backup con `backup-db.sh` y **verificación real del dump con `pg_restore -l`** —que exista y pese no
alcanza, un dump truncado pesa—, censo de filas por tabla, baja de servicios, `ALTER DATABASE` +
`ALTER ROLE`, **recenso contra la base nueva abortando si no coincide**, y actualización de `.env`
con copia previa. Idempotente y fail-closed: ante cualquier problema no renombra nada y dice dónde
quedó el dump. `pg_restore` corre **dentro** del contenedor: el VPS no tiene cliente de Postgres.
**Verificado:** suite **199/199** con el paquete nuevo (contenedor JDK 21), `tsc -b && vite build` y
`oxlint` limpios, y ninguna migración de `db/migration/` modificada.
**Impacto para el otro (Fran):** cambian `VITE_KEYCLOAK_REALM` y `VITE_KEYCLOAK_CLIENT_ID` — hay que
actualizar el `.env.local`. Lo más rápido en local es `docker compose down -v && up -d --build`: en
dev no hay datos que preservar. El contrato REST no cambió. Si ves `nutriapp` en el nombre del repo,
**está bien así**: es lo único que queda.
**Refs:** `scripts/rename-db.sh`, `keycloak/realms/bonosapp-realm.json`, `backend/**`,
`nginx/conf.d*/bonosapp.conf`, `DEPLOY.md` (runbook, fase B), `CLAUDE.md`.

## 2026-09-07 — Santi — marca/frontend/infra (rebranding NutriApp → BonosApp)
**Qué:** El cliente (Leo, 2026-09-03) decidió el cambio de nombre **antes del lanzamiento**, después de
analizar a la competencia (Avanter): muere NutriApp, nace **BonosApp**, dominio **bonosapp.com.ar** (ya
delegado a los mismos NS de Hostinger). Mandó el kit por mail; quedó en `brand/` (horizontal, vertical y
wordmark, en png y jpg) más una variante de wordmark blanco que generé para fondos oscuros.
1. **El isotipo NO cambia.** Lo verifiqué comparando el mark recortado del lockup nuevo contra
   `frontend/src/assets/logo.png`: es el mismo dibujo de Gon. Por eso `logo.png`, `favicon.png` y
   `apple-touch-icon.png` quedaron intactos — sólo cambia el wordmark.
2. **Front:** marca en navbar, footer, login, registro y landing; `<title>`, `description` y metadatos OG.
   `og-image.png` regenerada con el **lockup oficial** (wordmark en blanco) sobre el verde `--ink` — es lo
   que se ve cuando comparten el link por WhatsApp, así que ahí sí va la tipografía real de la marca.
3. **Casilla de contacto**, pedido explícito del cliente: la landing y el registro muestran
   "Por cualquier consulta, envianos un mail a …". Sale de **`VITE_CONTACTO_EMAIL`**, no hardcodeada.
4. **Backend:** textos de los mails, `MAIL_FROM_NAME`, User-Agent de TiendaNube, título de OpenAPI y los
   tests que asertaban sobre esos strings.
5. **Dominio:** `bonosapp.com.ar.zone` nuevo, sección "Migración a bonosapp.com.ar" en `DEPLOY.md` con el
   checklist, y el site block de Caddy con el `redir` permanente del dominio viejo.
**Por qué:** el momento barato para hacerlo es ahora — el sitio está en pre-lanzamiento
(`VITE_COMING_SOON=true`), no hay cuentas activas ni links repartidos más allá de las demos.
**Decisión que importa: el rename es SÓLO de cara al usuario.** Los identificadores internos siguen
diciendo `nutriapp` **a propósito**: paquete `com.nutriapp`, realm y clients de Keycloak, red
`nutriapp-net`, nombres de contenedor, DBs, `artifactId` y el nombre del repo. Renombrarlos obliga a
re-importar el realm y re-emitir credenciales del client en un Keycloak que ya está desplegado, sin que
nadie lo vea. Está anotado en `CLAUDE.md` para que no venga alguien "a terminar el rename".
**Ojo con el mail:** el cliente pidió mostrar **`info@nutriappok.com.ar`** —el dominio viejo— porque la
casilla nueva todavía no existe; la muda cuando el sitio esté en `bonosapp.com.ar`. Por eso el dominio
viejo **no se da de baja**: queda redirigiendo y aloja el correo. Cuando exista la nueva alcanza con
`VITE_CONTACTO_EMAIL=info@bonosapp.com.ar` + rebuild de la SPA, sin tocar código.
**Verificado:** `tsc -b && vite build` limpio, `oxlint` sin hallazgos, y el bundle sale con `BonosApp`,
el `og:image` en `bonosapp.com.ar` y las dos clases nuevas de CSS.
**Falta (ops, no código):** importar la zona en hPanel, agregar el site block en el Caddy del VPS,
rebuild de la SPA con `VITE_API_BASE_URL=https://bonosapp.com.ar`, **agregar `https://bonosapp.com.ar/*`
a los redirect URIs del client `nutriapp-frontend` en el Keycloak de prod** (si no, el login rompe con
`invalid_redirect_uri`) y `APP_PUBLIC_URL` en el `.env` del VPS.
**Impacto para el otro (Fran):** cambió el nombre visible y hay una entrada nueva en `config.ts`
(`contactoEmail`). El contrato REST no se tocó. Los identificadores internos siguen siendo `nutriapp`:
si ves `nutriapp-frontend` o `com.nutriapp` en el código, **está bien así**.
**Refs:** `brand/`, `frontend/index.html`, `frontend/src/{config.ts,index.css}`,
`frontend/src/pages/{Proximamente,Registro,Login}.tsx`, `frontend/src/components/layout/*`,
`frontend/public/og-image.png`, `backend/**/NotificacionTemplates.java`, `bonosapp.com.ar.zone`,
`DEPLOY.md`, `CLAUDE.md`, `README.md`.

## 2026-08-25 — Santi — backend/infra (el pipeline de mail se ejecutó de verdad por primera vez + 415 en vez de 500)
**Qué:** El mail era la única integración en stub, y el stub tira excepción **antes** de tocar nada: o sea
`SmtpMailSender` **nunca había enviado un mensaje** y el camino de éxito del dispatcher (QUEUED→SENT) nunca
había corrido. Se cerró eso sin depender del proveedor del cliente:
1. **Mailpit en `docker-compose.yml`** detrás de `profiles: [mail]` — `docker compose --profile mail up -d`.
   El profile es deliberado: prod se arma como **override de este archivo**, así que un servicio suelto
   correría también en prod, y un catcher SMTP allá **se tragaría los mails reales**.
2. **`auth` y `starttls` pasaron a ser configurables** (`MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS`); estaban
   hardcodeadas en `true`, lo que hacía imposible apuntar a cualquier SMTP local. Default `true` = el de prod.
3. **`HttpMediaTypeNotSupportedException` → 415.** No tenía handler: caía en el catch-all y `/registro`
   —endpoint **público**— devolvía **500 "Error interno"** ante un Content-Type equivocado. Un error del
   cliente reportado como falla del servidor (y encima dispara alertas de 5xx).
**Por qué:** el mail es el **único canal automático** (WhatsApp es manual desde 2.4). Estaba 100% sin
ejercitar, y era el bloqueante funcional que Fran dejó anotado en ESTADO. Ahora, cuando Gon elija proveedor,
2.3 es cambiar env vars sobre un camino ya probado — no estrenar código en producción.
**Problemas (los tres cuestan tiempo si se repiten):**
- **`docker compose up -d` NO alcanza para un cambio en `application.yml`**: el yml viaja **dentro del JAR**,
  así que hace falta `--build`. Sin eso el contenedor toma el env nuevo pero corre el yml viejo — y el síntoma
  engaña, porque `printenv` muestra los valores correctos.
- **Spring intenta AUTH aunque `mail.smtp.auth=false`** si `spring.mail.username` es string vacío:
  `JavaMailSenderImpl` llama a `transport.connect(host, port, user, pass)` y `""` no es `null`. Se resolvió
  del lado del catcher (`--smtp-auth-accept-any --smtp-auth-allow-insecure`), que además deja el backend con
  la **misma config que producción** (auth encendida) en vez de un camino especial que en prod no se ejercita.
- El Mailpit de otro proyecto ya ocupa 1025/8025 → **el 1025 no se publica** (el backend le habla por nombre
  de servicio dentro de `nutriapp-net`) y la UI va a `MAILPIT_UI_PORT=8026`. Mismo caso que `BACKEND_PORT`.
**Verificado de punta a punta:** cola de **13 notificaciones** viejas → `enviadas=13`, todas `SENT` y visibles
en Mailpit. **Registro nuevo** (multipart) → acuse a quien se registra **+ aviso al admin**; **aprobación** →
mail de cuenta activa. Acentos **correctos** en el mail (`charset=UTF-8`, quoted-printable, cero doble-encoding;
verificado sobre los bytes crudos del `.eml`, no de ojo: el mojibake que se ve al imprimir en la terminal es de
la consola de Windows). JSON a `/registro` → **415** con el tipo esperado en el mensaje. Suite **192/192**.
**Ojo:** `/api/v1/registro` es **multipart** (`datos` como JSON + `matricula` como archivo), no JSON plano.
**Impacto para el otro (Fran):** el bloqueante funcional del mail del registro **está destrabado en local** —
levantá Mailpit con `docker compose --profile mail up -d` y mirá http://localhost:8025 (8026 en la máquina de
Santi). Nada del contrato cambió.
**Refs:** `docker-compose.yml`, `backend/src/main/resources/application.yml`, `.env.example`,
`common/error/GlobalExceptionHandler.java`, `backend/src/test/**/GlobalExceptionHandlerTest.java`.

## 2026-08-25 — Santi — backend (link a la tienda en los mensajes del bono; fuera el emoji)
**Qué:** Los dos mensajes que le llegan a la paciente —el de WhatsApp (`WaMeLinkBuilder`) y el mail
(`NotificacionTemplates`)— ahora cierran con el **link a la tienda**. La URL sale de una property nueva
`nutriapp.integrations.tiendanube.store-url` (env **`TIENDANUBE_STORE_URL`**), al lado de `store-id`.
Vacía = el mensaje sale como antes, sin link cortado. Antes de eso se **sacó el emoji** 🌱 del saludo.
**Por qué:** el mensaje decía "comprá en la tienda online" sin decir dónde. El emoji lo pidió el usuario
tras verlo como `�`.
**Problemas:**
- **El emoji NO era un bug nuestro**: los bytes en el fuente eran `F0 9F 8C B1` (UTF-8 correcto), el pom
  compila en UTF-8 y la URL lo mandaba como `%F0%9F%8C%B1`. El `�` era del visor. Se sacó igual porque un
  emoji que no esté en la fuente del cliente de la paciente se ve como caja vacía justo en el saludo.
- `GET /{store}/store` devuelve `url: null` y la vitrina sólo aparece en `original_domain` → **no** se
  toma de la API, va por config (además evita depender de un request para un dato estático).
- Sumar un campo a `IntegrationsProperties.TiendaNube` rompe **4** constructores de test, no 2: uno usa
  `new TiendaNube(...)` con import estático y no aparece si se grepea `new IntegrationsProperties.TiendaNube(`.
- ⚠️ **Trampa de tooling (para el próximo que edite con scripts):** en los heredocs de este entorno un
  `\n` dentro de un string de Python llega colapsado a `
` y matchea un salto de línea real en vez del
  literal. Para patrones con backslashes hay que usar **raw strings** (`r'''...'''`).
**Verificado en vivo:** WhatsApp → `"...(válido hasta el 24/09/2026). Usalo al comprar acá:
https://thebcompanydemo.mitiendanube.com"`. Mail (bono RX-NZGCS5) → el link en su propia línea antes de
la firma. Tests nuevos: link presente, sin tienda configurada no queda link vacío, barra final normalizada,
y el mensaje no lleva emojis. Suite **189/189**.
**Impacto para el otro:** ninguno en el front.
**Refs:** `integrations/IntegrationsProperties.java`, `modules/receta/service/WaMeLinkBuilder.java`,
`modules/notificacion/service/NotificacionTemplates.java`, `application.yml`, `docker-compose.yml`,
`.env.example`.

## 2026-08-25 — Santi — frontend (columna "Acciones" alineada; la clase perdía por especificidad)
**Qué:** ⚠️ Toca `frontend/` (área de Fran), a pedido explícito del usuario. Ajuste de las dos tablas
(Pacientes y Bonos): el último `<th>` pasó de vacío (`aria-label`) a decir **"Acciones"**, y ahora
encabezado y botones comparten alineación y la columna se encoge a su contenido.
**Por qué:** el encabezado vacío dejaba un bloque blanco a la derecha que hacía ver la tabla corrida
hacia la izquierda, y la columna se llevaba parte del ancho sobrante, separando los botones del borde.
**Problemas:** `.table__actions { text-align: right }` **no se aplicaba al `<th>`**: pierde por
especificidad contra `.table th, .table td { text-align: left }` (0,1,0 vs 0,1,1). Hizo falta
`.table th.table__actions, .table td.table__actions`. Se suma `width: 1%` en el `th` para que el
auto-layout le dé el ancho mínimo y reparta el sobrante entre las columnas de datos.
**Verificado:** con Chrome headless sobre una página que carga el `index.css` real y el markup real de
ambas tablas — cada encabezado cae sobre su contenido y los botones llegan al borde derecho.
`tsc` + `oxlint` + `build` OK.
**Impacto para el otro (Fran):** si agregás una columna de acciones, poné `className="table__actions"`
**también en el `<th>`**, no sólo en el `<td>`.
**Refs:** `frontend/src/pages/{Pacientes,Recetas}.tsx`, `frontend/src/index.css`.

## 2026-08-25 — Santi — backend + frontend (el bono ya no lleva cantidades + acciones con íconos)
**Qué:** ⚠️ **Esta entrada toca `frontend/` (área de Fran), a pedido explícito del usuario.**
1. **El bono aplica a productos, no a cantidades.** `RecetaCreateRequest.Item.cantidad` ahora es
   `@Min(1) @Max(1)` con mensaje propio; el emisor ya no tiene input de cantidad y manda siempre 1.
   El campo se conserva en la entidad y en la DB por los bonos viejos, y el detalle sigue mostrando
   el `N×` **sólo si es > 1** (para los nuevos sería ruido).
2. **Bug real en la tabla de pacientes:** el `<thead>` declaraba 6 columnas (incluía WhatsApp) y cada
   fila renderizaba 5 `<td>` — faltaba la celda de WhatsApp, así que todo quedaba corrido y la última
   columna no llegaba al borde. Agregada la celda.
3. **Acciones con íconos.** Nuevo `.btn-icon` (cuadradito 32px, sólo ícono, con `title` + `aria-label`).
   Pacientes: lápiz / tacho. Listado de bonos: columna de acciones con WhatsApp, reenviar mail y anular,
   visibles sólo en `PENDIENTE`. Íconos nuevos en `Icon.tsx`: `pencil`, `trash`, `mail`, `whatsapp`, `ban`.
4. **El botón de WhatsApp deja el verde de WhatsApp** (`#25d366`) y pasa a outline de marca.
5. **`scripts/simular-compra.sh`**: envoltorio del endpoint dev `POST /dev/tiendanube/orden-pagada`
   (saca el token de Keycloak solo). Sirve para ver `PENDIENTE → APLICADA` sin comprar de verdad.
**Por qué:** (1) lo pidió el usuario tras probar la compra en la demo: el cupón de TiendaNube **no
tiene forma de limitar unidades** — con 2 recetadas y 3 en el carrito, las 3 salían con descuento.
Existe `max_discount_amount` (topea el descuento en plata) y **la API lo acepta**, pero está
**indocumentado** y, como decidió el usuario, el tope se calcularía con el precio del día de emisión:
si TBC cambia el precio dentro de los 30 días de vigencia, el descuento queda mal. Se descartó.
**Problemas:** ninguno. Ojo al probar: buscar "ON-ROLL" matchea primero un COMBO que no está mapeado,
y el guard frena el cupón — es el comportamiento correcto, no un bug.
**Verificado:** `cantidad: 2` → **400** con el mensaje nuevo. Bono con **dos productos** →
`SINCRONIZADO`, y el cupón en la tienda trae los **dos** product ids. `simular-compra.sh RX-EGBBCG` →
**APLICADA**, orden #67645, comisión 8% = $2.367,34. Back **185/185**; front `tsc` + `oxlint` + `build` OK.
**Impacto para el otro (Fran):** revisá los 5 archivos de `frontend/` que toqué. El input de cantidad
ya no existe y `ItemDraft` perdió el campo; `RecetaItemInput.cantidad` **sigue en el contrato** (se manda
1 fijo), así que `types/receta.ts` no cambió. `.btn--whatsapp` cambió de color y hay una clase nueva
`.btn-icon` para acciones de tabla.
**Refs:** `modules/receta/dto/RecetaCreateRequest.java`, `frontend/src/pages/{Pacientes,Recetas,EmitirReceta}.tsx`,
`frontend/src/components/receta/{RecetaDetalle,RecetaExito}.tsx`, `frontend/src/components/ui/Icon.tsx`,
`frontend/src/index.css`, `scripts/simular-compra.sh`.

## 2026-08-25 — Santi — integraciones (🎯 cupón real emitido en la demo; el `products[]` iba con el ID EQUIVOCADO)
**Qué:** Reinstalada la app con todos los permisos y token nuevo en `.env` (scopes: `write_products`,
`read_coupons`, `write_coupons`, `read_orders`, + los de draft orders que TiendaNube agrega solos). Con
`coupons` accesible por fin se pudo validar contra la API lo que estaba asumido, y **estaba mal**:
1. **`coupons.products[]` espera PRODUCT ids, no VARIANT ids.** Con variant id la API responde
   **422** `"The following IDs do not match valid products in the store"`. `CuponSyncService` mandaba
   `tiendanubeVariantId` → **ningún cupón se habría creado nunca en producción**. Ahora manda
   `tiendanubeProductId`; el contador `countPublicadosSinMapear` también pasó a mirar product id.
2. **Una colección vacía responde 404, no un array vacío** (`GET /orders` con 0 resultados →
   `404 "Last page is 0"` con `x-total-count: 0`). `getPaidOrdersSince` no lo contemplaba: el
   `TiendaNubePollingJob` habría logueado **ERROR cada 5 minutos** en cualquier tienda sin ventas en la
   ventana de 24h — o sea casi todas las noches, tapando errores de verdad. Ahora 404 = lista vacía.
**Por qué:** eran las dos incógnitas que `03-integraciones-apis.md` §2 dejaba anotadas como "a validar
contra la tienda demo". Las dos estaban mal asumidas, y ninguna se ve en stub ni con mocks — sólo pegándole
a la API real.
**Verificado de punta a punta contra la demo:** mapeo (4 productos) → resync → **cupón real creado**
(`RX-FJT6J9`, id 68525667) **restringido al producto correcto** (363154002 ON-ROLL FEM), `max_uses=1`,
15% (el % de esa nutricionista) y `end_date` = vencimiento. En la misma corrida **5 recetas quedaron
PENDIENTE con el motivo explícito** por tener productos sin mapear — el guard de la entrada anterior
haciendo su trabajo sobre datos reales. Suite **185/185**.
**Problemas:** ninguno pendiente. Nota: desinstalar la app revoca el token al instante (queda
`401 Invalid access token`), y **no hace falta desinstalar para re-autorizar**: entrar a
`https://www.tiendanube.com/apps/40301/authorize` emite un `code` nuevo con los scopes vigentes.
**Pendiente para la tienda del cliente:** registrar el webhook (`POST /admin/tiendanube/registrar-webhooks`,
exige `APP_PUBLIC_URL` en HTTPS — no se probó en vivo para no mandar eventos de la demo a prod) y decidir si
un producto sin mapear debe seguir siendo recetable (hoy sí: la receta sale con un cupón que no se crea).
**Refs:** `integrations/tiendanube/{TiendaNubeClient,HttpTiendaNubeClient}.java`,
`modules/receta/service/CuponSyncService.java`, `modules/producto/repository/ProductoRepository.java`,
`backend/src/test/**/{HttpTiendaNubeClientTest,CuponSyncServiceTest}.java`.

## 2026-08-25 — Santi — integraciones (guard del cupón sin mapeo + registro de webhooks + mensaje honesto)
**Qué:** Segunda tanda del día, preparando el salto a la tienda **del cliente**. Tres cambios:
1. **`CuponSyncService.registrar` ya no emite un cupón sin restringir.** Si **algún** producto de la receta
   no tiene `tiendanube_variant_id`, **no llama a la API**: deja el cupón `PENDIENTE` con
   `cupon_sync_error = "Sin mapeo a TiendaNube: <productos>"`. Además `HttpTiendaNubeClient.createCoupon`
   rechaza de plano un `products[]` vacío (`IllegalArgumentException`), como segunda barrera.
2. **`listWebhooks` / `createWebhook`** en el port + `TiendaNubeWebhookRegistrar` +
   `POST /api/v1/admin/tiendanube/registrar-webhooks`. Idempotente (lista y sólo crea lo que falta) y
   exige `APP_PUBLIC_URL` en **HTTPS**.
3. **`CuponSyncEstado.mensajeDegradacion(error)`** ahora recibe el error y distingue los dos casos.
**Por qué:** (1) El mapeo por SKU de la entrada anterior **reduce** el problema pero no lo elimina: el filtro
de `registrar` descartaba en silencio los productos sin id, y si quedaban **cero** el body salía sin
`products[]` → TiendaNube aplica el cupón a **toda la tienda**. Con el catálogo real esto pasa seguro: hay
2267 productos locales y la tienda tiene un subconjunto. Peor variante: si mapeaban *algunos*, el cupón
quedaba restringido a un subconjunto silencioso y el descuento no aplicaba sobre lo recetado.
(2) Sin webhook registrado no llega ningún evento — no hay UI en TiendaNube, es por API o nada.
(3) El mensaje decía *"TiendaNube no está disponible. Se reintenta automáticamente"*, y para un producto sin
mapear **es falso**: la tienda anda y el `CuponSyncJob` va a fallar en loop hasta que un admin corra el mapeo.
Ahora ese caso dice que el producto no está publicado en la tienda y que avise al administrador.
**Problemas:** el guard rompía el fixture de `CuponSyncServiceTest` (las recetas del test no tenían items, y
sin items el guard ahora frena) — se les agregó un item con producto mapeado, que además es más fiel a la
realidad. Ojo con esto al escribir tests nuevos de cupones.
**Verificado en vivo** contra la demo: receta con producto **no** mapeado → `PENDIENTE` +
`"Sin mapeo a TiendaNube: 102 FOCUS X 30 COMP REC"` en la DB y warn en el log, **sin** pegarle a la API.
Suite **184/184**.
**Impacto para el otro:** `cuponSyncMensaje` puede traer ahora un texto nuevo — el que dice que el producto
no está publicado en la tienda. Se sigue mostrando igual, no cambia el contrato (mismo campo, mismo tipo).
**Refs:** `modules/receta/service/CuponSyncService.java`, `modules/receta/entity/CuponSyncEstado.java`,
`modules/receta/service/RecetaService.java`, `modules/webhook/service/TiendaNubeWebhookRegistrar.java`,
`modules/webhook/dto/RegistrarWebhooksResponse.java`, `integrations/tiendanube/*`,
`modules/admin/controller/AdminIntegracionesController.java`.

## 2026-08-25 — Santi — integraciones (TiendaNube: app + tienda demo conectada, mapeo por SKU, 401/403 degradan)
**Qué:** Arrancó Fase 2 sobre TiendaNube. App **40301** creada en el Partner Portal e instalada en la tienda
demo `thebcompanydemo.mitiendanube.com` (**store_id = 8145981**, que es el `user_id` que devuelve el token).
Token OAuth intercambiado y persistido en `.env` junto con `client_id`/`client_secret`; `TIENDANUBE_WEBHOOK_SECRET`
= `client_secret` (es con lo que TiendaNube firma el HMAC). Local quedó en `TIENDANUBE_MODE=live`.
Tres cambios de código:
1. **`listProducts(page, perPage)`** en el port `TiendaNubeClient` + impl HTTP + stub. Devuelve
   `ProductPage(items, hasNext)` con `hasNext` sacado del header `Link rel="next"`.
2. **`TiendaNubeMapeoService`** + `POST /api/v1/admin/tiendanube/mapear-productos` (admin): concilia el
   catálogo local con la tienda **por SKU** y escribe `tiendanube_product_id` / `tiendanube_variant_id`.
   Reporta `revisados/mapeados/yaMapeados/sinSku/sinMatch/pendientes` + muestra de SKUs que no matchearon.
3. **401/403 ahora degradan** a `IntegrationUnavailableException` en `HttpTiendaNubeClient.call()`.
**Por qué:** (1) y (2) cierran el agujero de la entrada del **2026-08-14**: `CuponSyncService` restringe el
cupón con esos ids y **nadie los escribía nunca** → el cupón salía sin `products[]`, o sea **30% sobre toda
la tienda**. (3) apareció al probar en vivo: un scope faltante o un token vencido son un problema de
**configuración**, no un request mal armado; propagarlos crudos le tira un **500** a la nutricionista en vez
de dejar el cupón `PENDIENTE` para que el `CuponSyncJob` lo drene.
**Problemas:**
- **La app quedó con un solo scope, `write_products`** — `GET /orders` y `GET /coupons` dan **403
  `Missing required scope`**. El scope viaja **dentro del token**: no alcanza con editar la app, hay que
  corregirla en el portal y **reinstalarla** en la demo para que salga un `code` nuevo. **Bloqueante** para
  cupones reales y detección de conversión. Falta: `read_orders`, `read_coupons`, `write_coupons`.
- **Trampa de paginación:** pedir una página más allá de la última devuelve **404** (`"Last page is 3"`),
  **no** un array vacío. Un `while` hasta página vacía revienta. Se corta por `Link rel="next"`, y el 404
  igual se trata como fin de catálogo por las dudas.
- La demo estaba vacía (1 producto sin SKU): se cargaron **4 productos con SKU real de TBC** (1024, 119,
  123, 127) vía API para poder conciliar de verdad.
- El host tiene Java 17 y el proyecto pide 21 → los tests corren en contenedor:
  `docker run --rm -v <backend>:/work -v ~/.m2:/root/.m2 -w /work maven:3.9-eclipse-temurin-21 mvn test`.
**Verificado contra la API real (no mocks):** mapeo 4/4 (`revisados=5, mapeados=4, sinSku=1`), segunda corrida
idempotente (`mapeados=0, yaMapeados=4`), ids persistidos en la DB, y emisión de receta con la app sin
`write_coupons` → cupón `PENDIENTE` + log `Missing required scope: write_coupons`, **sin 500**. Suite **173/173**
(12 tests nuevos). Quedan **695 publicados sin mapear**: no existen en la demo, se resuelve contra la tienda real.
**Impacto para el otro:** ninguno en el front. El endpoint nuevo es de admin; si en algún momento se le pone
pantalla, va al lado del sync de Contabilium y del import del maestro.
**Refs:** `integrations/tiendanube/{TiendaNubeClient,HttpTiendaNubeClient,StubTiendaNubeClient}.java`,
`modules/producto/service/TiendaNubeMapeoService.java`, `modules/producto/dto/MapeoTiendaNubeResponse.java`,
`modules/producto/repository/ProductoRepository.java`, `modules/admin/controller/AdminIntegracionesController.java`,
`backend/src/test/**/{HttpTiendaNubeClientTest,TiendaNubeMapeoServiceTest}.java`, `.env` (no versionado).

## 2026-08-14 — Santi — integraciones (⚠️ hallazgo Ola 4: hoy el cupón saldría SIN restricción de productos)
**Qué:** Revisando qué falta para encender TiendaNube apareció un agujero que **no se ve en stub y muerde el
primer día de live**: `CuponSyncService.registrar()` arma la lista de productos del cupón con
`Producto.tiendanubeVariantId`, y **ningún código escribe jamás esa columna** (`tiendanube_variant_id` sólo
aparece en la migración `V002` y en ese filtro). El catálogo se puebla desde Contabilium por SKU, y el cliente
de TiendaNube tiene 4 métodos —`createCoupon`, `deleteCoupon`, `getOrder`, `getPaidOrdersSince`— ninguno lista
productos. Resultado en live: `products` va vacío → **el cupón aplica a TODA la tienda**, no a los artículos
del bono. Es plata: el descuento se lo lleva cualquier cosa del carrito.
**Además, el campo no es el que creemos:** la API de cupones espera **product ids**, no variant ids
(`03-integraciones-apis.md §2`). Aunque se poblara la columna tal cual está, mandaríamos el id equivocado.
**Qué hace falta (Ola 4, cuando estén las credenciales):** listar productos/variantes de TiendaNube, conciliar
contra el catálogo local **por SKU** (mismo patrón que `ProductoSyncService` con Contabilium), persistir el id
correcto, y recién ahí el cupón sale restringido. Otros dos huecos del mismo módulo, más chicos:
`getPaidOrdersSince` **no pagina** (TiendaNube corta en 30 por página → el polling de respaldo se pierde
órdenes) y **no hay alta de webhook por API** (`POST /{store}/webhooks` hay que hacerlo a mano).
**Por qué no se rompió antes:** en stub `createCoupon` no valida nada y el simulador de dev matchea por código,
así que el flujo emisión→APLICADA da verde igual. Sólo se cae contra la tienda real.
**Refs:** `CuponSyncService:49-53`, `integrations/tiendanube/*`, `V002__core.sql:49`,
`instrucciones_claude/03-integraciones-apis.md §2`.

## 2026-08-14 — Santi — backend (el registro ya notifica: acuse, aviso al admin, aprobación y rechazo)
**Qué:** Cerrado **el bloqueante funcional para difundir el link**: `RegistroService` no encolaba nada, así
que quien se registraba no recibía nada y **al admin no le llegaba aviso** (había que mirar la tabla a mano).
Ahora la cola de notificaciones deja de ser exclusiva de recetas:
- **Migración `V013`**: `notificaciones` suma `tipo` (`EMISION_RECETA`, `REGISTRO_RECIBIDO`, `REGISTRO_APROBADO`,
  `REGISTRO_RECHAZADO`, `ADMIN_NUEVA_SOLICITUD`, con CHECK; las 7 filas viejas quedaron en `EMISION_RECETA`) y
  `nutricionista_id` **con `ON DELETE CASCADE`** — borrar una nutricionista es para altas equivocadas o de
  prueba y su acuse es cola operativa, no historial; sin el cascade el `DELETE` moría por FK.
- **4 avisos nuevos** (`NotificacionTemplates`): acuse a quien se registra, aviso al admin con los datos y el
  link a `/nutricionistas`, aprobación con el link a `/ingresar`, y rechazo con el motivo si lo hay.
- **Dos properties**: `ADMIN_NOTIFICATION_EMAIL` (casilla que recibe las solicitudes; **vacía = nadie se
  entera**, queda un warn) y `APP_PUBLIC_URL` (base de los links de los mails).
- Los avisos se encolan **en la misma tx** que el alta/aprobación: el envío es asíncrono, así que el proveedor
  caído no frena el registro (en stub quedan QUEUED con `intentos=0` y drenan solos al pasar a live).
**Por qué:** era el ítem que quedaba abierto en ESTADO y en el DIARIO del 14/08 para poder difundir el link.
**Problemas:** `mvn test` venía **rojo desde `ccf69b0`** — `AdminNutricionistaServiceTest` seguía esperando
"3 recetas emitidas" contra el mensaje ya renombrado a "3 bonos profesionales emitidos". Arreglado de paso.
**Verificado e2e** (stack local, back `:8088`): `V013` aplicada (13 migraciones), `POST /registro` 201 → 2 filas
QUEUED (`REGISTRO_RECIBIDO` + `ADMIN_NUEVA_SOLICITUD`) con `last_error` "integración mail no conectada" y
`intentos=0` (degradación transitoria correcta), aprobar → `REGISTRO_APROBADO` con el link bien armado,
rechazar → `REGISTRO_RECHAZADO` con el motivo, y `DELETE` de las dos de prueba → cascade limpio, 0 huérfanas.
`mvn test` **159 unit, BUILD SUCCESS**.
**⚠️ Lo que falta para que el mail SALGA en prod (ops, no código):** verificar `nutriappok.com.ar` en Resend +
sus 3 DNS en Hostinger, API key de prod, `MAIL_MODE=live`, `MAIL_FROM_ADDRESS=info@nutriappok.com.ar` (crear la
casilla) y **setear `ADMIN_NOTIFICATION_EMAIL` y `APP_PUBLIC_URL=https://nutriappok.com.ar` en el `.env` del
VPS**. Sin `APP_PUBLIC_URL` los links salen relativos (no rotos, pero inútiles en un mail).
**Impacto para el otro (Fran):** ningún cambio de contrato REST — no hay que espejar nada. Lo único visible es
que la cola ahora tiene filas sin receta, así que `pendientes` del panel `/integraciones` cuenta también estas.
**Refs:** `V013__notificaciones_de_registro.sql`, `modules/notificacion/**`, `RegistroService`,
`AdminNutricionistaService`, `application.yml`, `docker-compose.yml`, `.env.example`.

## 2026-08-14 — Santi — frontend (la píldora "Próximamente" se chocaba con el isotipo en la landing)
**Qué:** En `/` (landing pre-lanzamiento) el bloque de marca (isotipo + "NutriApp") y la píldora
"Próximamente" caían **en la misma línea**: `.soon__brand` y `.soon__eyebrow` eran los dos `inline-flex`
→ dos cajas inline-level que comparten line box mientras entren en los 612px útiles del `.soon__inner`
(~283px entre las dos), y el isotipo de 36px se le venía encima a la píldora. `.soon__brand` pasa a
`display: flex` + `justify-content: center`: block-level, fila propia, y el `margin-bottom: 30px` que ya
tenía queda de separación real.
**Por qué:** lo pidió el usuario al ver la landing desplegada.
**Problemas:** ninguno. Es un bug viejo de la landing, no lo introdujo el isotipo — con el badge `leaf`
anterior pasaba lo mismo, sólo que un ícono chato dentro de una píldora disimulaba el choque.
**Impacto para el otro (Fran):** toqué `frontend/src/index.css` (tu área) por pedido explícito, 1 regla.
Ojo si tenés algo en vuelo sobre la landing. `.auth__brand-top` (login/registro) ya era `flex`, no se tocó.
**Verificación:** lint verde, build verde, redeploy hecho (bundle `index-Cp6tiKN0.js`, CSS `index-CSJsWVJJ.css`,
regla confirmada en el CSS servido por HTTPS). **Sigue faltando la mirada humana en el browser.**
**Refs:** `frontend/src/index.css` (`.soon__brand`), `frontend/src/pages/Proximamente.tsx`.

## 2026-08-14 — Santi — infra (segundo redeploy del día: `ff47af2` en vivo, solo SPA)
**Qué:** `git pull` de `ff47af2` (isotipo de Gon + 3 huecos del rename) y publicación en `nutriappok.com.ar`.
- **Solo SPA**: el commit no toca `backend/ db/ nginx/ keycloak/ docker-compose*` → **no se reconstruyó ni
  reinició el backend**, no hubo migraciones y **no se tocó la DB** (por eso tampoco backup nuevo: el de las
  18:25 sigue siendo el último estado y nada lo invalidó).
- Bundle recompilado en `node:22-alpine` descartable con los `VITE_*` de prod y **`VITE_COMING_SOON=true`**
  (verificado horneado): `index-DTq62RVv.js` → **`index-D4rP5iq7.js`**. Vite vacía `dist/`, así que el
  `favicon.svg` borrado en el commit desapareció del servido. `nginx -s reload` (el `dist` es bind-mount ro).
- **Merge de docs a mano**: las entradas del redeploy de las 18:25 estaban sin commitear y chocaban con las
  que traía `ff47af2` en DIARIO/ESTADO. Se resolvió conservando ambas (stash → pull → pop → resolución).
**Por qué:** el pedido fue publicar el contenido nuevo; el pre-lanzamiento se mantiene igual.
**Problemas:** ninguno.
**Verificación (HTTPS público):** `/` `/registro` `/ingresar` → 200 sirviendo el bundle nuevo; assets de marca
`/favicon.png` `/apple-touch-icon.png` `/og-image.png` `/assets/logo-DRT11zeO.png` → 200 `image/png`;
`<title>`/`og:*` ya dicen "Bonos profesionales"; `/actuator/health` UP; issuer OIDC
`https://nutriappok.com.ar/auth/realms/nutriapp`; `/api/v1/recetas` → 401; `/auth/admin` y `/auth/realms/master`
→ 404; haltcatch y jeianell en 200. **Falta igual la verificación visual en el browser** que pedía Fran en su
entrada — se comprobó que los assets se sirven, no cómo se ven las pantallas.
**Impacto:** sigue en pie lo del mail — `MAIL_MODE=stub` en prod, el registro no notifica a nadie (ver entrada
de las 18:25 y ESTADO).
**Refs:** `DEPLOY.md` §2, `docker-compose.prod.yml`.

## 2026-08-14 — Santi — frontend (isotipo de Gon puesto en la marca + 3 huecos del rename "receta → bono")
**Qué:** Llegó el archivo del isotipo (el ramo multicolor de TBC) que quedó pendiente el 13/08, y de paso
audité el rename contra lo que había quedado sin tocar.
- **Logo**: componente nuevo `components/ui/Logo.tsx` (`<img>` sobre `src/assets/logo.png`) reemplazando al
  ícono genérico `leaf` en los **5 lugares de marca**: navbar, footer, login, registro y landing. El isotipo es
  multicolor → se sacaron las píldoras teñidas `.navbar__brand-badge` / `.auth__brand-badge` (quedaban de fondo
  contra un logo de 7 colores); ahora va suelto, con `.logo { object-fit: contain }`.
- **Assets generados del PNG original** (8488×11240 RGBA, recortado por bbox de alpha): `src/assets/logo.png`
  (320px, 57 KB, entra al build con hash), `public/favicon.png` (64px), `public/apple-touch-icon.png` (180px
  sobre blanco: iOS compone los transparentes contra negro) y `public/og-image.png` (1200×630 sobre `#16302c`).
  **Se borró `public/favicon.svg`**: era el favicon violeta del template de Vite, sin referencias.
- **3 huecos del rename** que quedaron con "receta" a la vista: `index.html` (`<title>`, `description`, `og:title`,
  `og:description` — es lo que ve cualquiera a quien Gon le pase el link por WhatsApp), el saludo del Dashboard
  ("Tenés N recetas pendientes") y el paginador de `/recetas` ("N recetas"). También `lang="en"` → `es-AR`.
**Por qué:** el isotipo era el único ítem del feedback de Gon marcado como pendiente, y el rename estaba
declarado como "en cualquier lugar de la webapp".
**Problemas:** a 17px (footer) el isotipo se empasta — las nervaduras claras desaparecen. Se subió a 20px;
de 34px para arriba lee bien tanto sobre blanco como sobre el `--ink`.
**Impacto para el otro (Fran):** toqué `frontend/` (tu área) por pedido explícito del usuario — 9 archivos.
Si tenés algo en vuelo sobre navbar/footer/login/registro/landing o `index.css`, ojo con el merge. El `og:image`
apunta a `https://nutriappok.com.ar/og-image.png` (absoluta a propósito: los scrapers no resuelven relativas),
así que **recién se ve cuando se despliegue**. Falta **verificación visual en el browser**: build y lint verdes,
pero nadie miró las pantallas.
**Refs:** `frontend/src/components/ui/Logo.tsx` (nuevo), `components/layout/{AppLayout,Footer}.tsx`,
`pages/{Login,Registro,Proximamente,Dashboard,Recetas}.tsx`, `src/index.css`, `index.html`, `src/assets/logo.png`,
`public/{favicon,apple-touch-icon,og-image}.png`.

## 2026-08-14 — Santi — infra (redeploy de prod con los cambios de Fran, sigue en pre-lanzamiento)
**Qué:** Se subió a `nutriappok.com.ar` el estado de `ccf69b0` (rename "receta → bono profesional",
feedback de Gon en landing/registro, emisor pulido). Lo que corría era el build del 2026-08-11.
- **Backup cifrado previo** (`backups/*-20260814-182505.dump.gpg`, nutriapp + keycloak).
- **SPA** recompilada en `node:22-alpine` descartable con los `VITE_*` de prod y **`VITE_COMING_SOON=true`**
  (verificado horneado en el bundle). El pre-lanzamiento **se mantiene**: `/` = landing "Próximamente"
  con CTA a `/registro`, login en `/ingresar` sin link desde ningún lado.
- **Backend** reconstruido (`nutriapp/backend:prod`) y recreado. **Sin migraciones nuevas**: Flyway validó
  12 y el schema ya estaba en 012 — el commit sólo tocaba texto de usuario en Java.
**Por qué:** el pedido era publicar los cambios manteniendo la restricción de que sólo se vea el registro,
para poder difundir el link y sumar nutricionistas.
**Problemas:** ninguno. Nada que reportar del build ni del arranque (48s, `Started NutriappApplication`).
**Verificación (HTTPS público):** `/` `/registro` `/ingresar` → 200; `/actuator/health` UP; issuer OIDC
`https://nutriappok.com.ar/auth/realms/nutriapp`; `/api/v1/recetas` → 401; `/auth/admin` y
`/auth/realms/master` → 404; bundle servido = el recién compilado (`index-DTq62RVv.js`);
`POST /api/v1/registro` con payload inválido → 400 `ApiError` con los errores por campo (probado sin
crear registro basura). haltcatch y jeianell siguen en 200 (no se tocó Caddy).
**⚠️ Impacto para Fran — el link se puede difundir, pero el mail sigue sin salir en prod:** `MAIL_MODE=stub`
en el `.env` del VPS y `RegistroService` no encola notificación, así que quien se registre no recibe el
"solicitud recibida" y **al admin tampoco le llega aviso**. Tu setup de Resend (2026-08-13) resuelve el
proveedor pero falta lo de prod: verificar el dominio en Resend + los 3 DNS en Hostinger, API key de prod
aparte, `MAIL_FROM_ADDRESS=info@nutriappok.com.ar` y crear esa casilla. Mientras tanto las solicitudes se
miran en la tabla `nutricionistas` y se aprueban por API/SQL.
**Refs:** `DEPLOY.md` §2 y §Modo pre-lanzamiento, `docker-compose.prod.yml`, `.env` del VPS (gitignored).

## 2026-08-13 — Fran — integraciones/email (Resend elegido como proveedor de mail; anda en local, falta setup de PROD)
**Qué:** Elegí **Resend** como proveedor de email y lo dejé andando **en local** contra el `SmtpMailSender` que ya
existía. **CERO cambios de código** — Resend expone SMTP nativo, así que se prende con puras envs. En mi `.env`
(gitignored): `MAIL_MODE=live`, `MAIL_SMTP_HOST=smtp.resend.com`, `MAIL_SMTP_PORT=587`, `MAIL_SMTP_USERNAME=resend`
(literal, igual para todos), `MAIL_SMTP_PASSWORD=<API key re_...>`, `MAIL_FROM_ADDRESS=onboarding@resend.dev`,
`MAIL_FROM_NAME=NutriApp`. Probado emitiendo un bono a un paciente de prueba → mail entregado.
**Por qué:** desbloquear el canal automático de notificación (hoy era el único que faltaba conectar).
**⚠️ Impacto para Santi — qué falta para que el mail sea PROD (tarea de infra/DevOps):**
  1. **Verificar el dominio `nutriappok.com.ar` en Resend** (panel → Domains → Add). Resend devuelve 3 registros DNS
     (un **MX** de bounce sobre un subdominio `send.`, un **TXT/SPF**, y el **DKIM** `resend._domainkey`) → cargarlos
     en **Hostinger**. Van donde antes estaban los de Hostinger (el `.zone` del repo los documenta comentados).
     **Hasta verificar el dominio, Resend solo permite remitente `onboarding@resend.dev` y solo entrega a la casilla
     de la cuenta** — por eso en local mando a `franallende2000@gmail.com`. Sin esto NO se puede mandar a pacientes reales.
  2. **Env de prod:** `MAIL_MODE=live` + los `MAIL_SMTP_*` de arriba, pero con una **API key de PROD separada** (crear
     otra en Resend, no reusar la de test) y `MAIL_FROM_ADDRESS=info@nutriappok.com.ar` (o `no-reply@…`). La key va
     **solo en el secret manager del hosting**, jamás al repo (regla de secretos).
  3. **Recepción ≠ envío:** Resend solo **envía**. La casilla **`INFO@nutriappok.com.ar`** donde caen los registros/
     respuestas hay que crearla aparte en Hostinger (buzón o forwarding). Son dos cosas distintas del mismo dominio.
  4. (Opcional, tarea 2.3) el cuerpo del mail hoy es **texto plano**; el HTML con logo queda para después.
**Refs:** `.env` (local, gitignored), `spring.mail.*` + `SmtpMailSender.java` (sin cambios), `nutriappok.com.ar.zone`.

## 2026-08-13 — Fran — frontend (emisor de bonos: buscador sin flicker + CTA contextual; productos de prueba en DB local)
**Qué:** Pulido del emisor de bonos y datos de prueba para poder testear el flujo mail end-to-end.
- **Flicker del buscador** (`ProductoBuscador.tsx`): "Buscando…" ahora sale **solo en la primera carga** (`loading && !data`)
  y "Sin productos" **solo con `!loading`**. Antes, en cada tecla parpadeaba entre lista y estado vacío (el `useFetch`
  marca `loading=true` en cada refetch); ahora se mantiene la lista anterior visible durante el refetch.
- **CTA "Nuevo bono"** (`AppLayout.tsx`): se **esconde** cuando ya estás en `/recetas/nueva` (`useLocation`).
- **Productos de prueba:** la DB estaba en **0 productos** (el `down -v` de esta sesión los borró y Contabilium está
  `stub` en local → no hay sync que los traiga). Cargué **12 suplementos** directo por SQL (`publicado=true`, `origen=SEED`)
  para poder probar. **⚠️ Son data LOCAL, NO commiteada; se pierden con `docker compose down -v`.** Si conviene un seed
  reproducible, es una **migración Flyway de seed** en `backend/` (área de Santi) — a coordinar.
**Refs:** `frontend/.../ProductoBuscador.tsx`, `frontend/.../layout/AppLayout.tsx`.

## 2026-08-13 — Santi (nota de Fran) — infra/build (flag: falta `*.properties text eol=lf` en `.gitattributes`)
**Qué:** En Windows, `backend/.mvn/wrapper/maven-wrapper.properties` se checkoutea con CRLF (cae bajo `* text=auto`,
no está cubierto por las reglas `eol=lf` de `mvnw`/`*.sh`/`*.sql`). El `\r` corrompe la `wrapperUrl` → el build baja
el wrapper con HTTP 400. Workaround local: `sed -i 's/\r$//'`. **Fix definitivo (Santi):** agregar
`*.properties text eol=lf` al `.gitattributes`. Ídem conviene revisar `package-lock.json` (mismo síntoma CRLF).

## 2026-08-13 — Fran — backend + dns (rename "receta → bono profesional" en texto de usuario; DNS ya limpio)
**Qué:** Con **autorización explícita de Santi** (Fran se hace cargo de todo el rebranding), completé el rename
"receta → bono profesional" en el **texto de cara al usuario que genera el backend** — solo strings, NO toqué
entidades/tablas/enums/DTOs/servicios (`Receta`, `receta_items`, `EstadoReceta`, etc. quedan igual):
- **Email al paciente** (`NotificacionTemplates`): asunto "Tu bono profesional {código}…" + cuerpo.
- **Texto de WhatsApp** (`WaMeLinkBuilder`): "Tu bono profesional con X% de descuento ya está listo…".
- **7 mensajes de error** (`ConflictException`/`NotFoundException`) que el front surfacea como toast:
  `RecetaService` (max-items, anular/reenviar sólo pendientes, paciente/bono no encontrado, código único),
  `PacienteService` (409 baja con bonos pendientes), `AdminNutricionistaService` (409 borrar con bonos emitidos),
  `WebhookSimulacionController` (dev). Los `log.info(...)` con "receta" quedaron (son logs, no los ve nadie).
- Backend rebuildeado (`up -d --build backend`).
**DNS — nada que borrar:** Fran pidió borrar el MX/SPF que autopobló Hostinger porque va a buscar **otro proveedor de
email**. Verifiqué el DNS en vivo de `nutriappok.com.ar` (nslookup vía 8.8.8.8): **NO hay MX ni TXT/SPF** (solo SOA);
los registros web (A/AAAA/www → VPS) están OK. Ya se limpiaron (o estaban en el dominio equivocado). El `.zone` del
repo los tiene comentados. **Cuando se elija el proveedor** (Resend/SES/Brevo/…), van los MX/SPF/DKIM **de ese
proveedor** (el `.zone` documenta dónde) — no los de Hostinger.
**Refs:** `backend/.../NotificacionTemplates.java`, `WaMeLinkBuilder.java`, `RecetaService.java`, `PacienteService.java`, `AdminNutricionistaService.java`, `WebhookSimulacionController.java`; `nutriappok.com.ar.zone` (sin cambios).

## 2026-08-13 — Fran — frontend (feedback de Gon: landing + registro + rename "Receta → Bono Profesional")
**Qué:** Tanda de cambios de texto/UX pedidos por Gon. `build`+`lint` verdes.
- **Landing `/` (Proximamente.tsx):** título → "Recomendaciones Profesionales, con beneficios exclusivos"; nuevo
  párrafo lead; los 3 recuadros reescritos (Emitís bono profesional / Tu paciente adquiere / Seguís todo acá);
  nota de validación → "Cada cuenta se valida individualmente… Te avisaremos por mail cuando la misma esté habilitada".
- **Registro:** campo Teléfono → "Whatsapp / Teléfono"; **"Matrícula nacional" se partió en dos**: "Jurisdicción de
  matrícula" + "N° de matrícula"; paso 3 → "…ya podés emitir bonos profesionales".
- **Rename global "Receta → Bono Profesional"** en TODO el texto visible del front (nav, dashboard, cierre mensual/
  consolidado, emisión, detalle, éxito, pacientes, perfil, catálogo, estadísticas). No toqué rutas (`/recetas`),
  tipos ni identificadores. En el catálogo, "recetable/no recetable" → "disponible/no disponible".
**⚠️ PARA SANTI (backend, tu área — el rename es "en cualquier lugar de la webapp"):**
1. **Falta el rename "receta → bono profesional" en lo que genera el backend:** plantillas de **email** (lo recibe
   la paciente), el mensaje 409 "tiene recetas PENDIENTES", y cualquier otro texto de `ApiError`/mensajes de usuario.
2. **Jurisdicción de matrícula:** hoy la combino en el string `matricula` que ya existe (`"<jurisdicción> · N° <número>"`)
   para no romper el contrato. Si querés guardarla estructurada, hace falta una columna/campo `jurisdiccion` en el back.
**Consulta abierta (ops, no código):** Gon pide crear el mailbox **INFO@nutriappok.com.ar** (donde caen los registros).
Es tarea de Hostinger + hay que sacar el MX/SPF autogenerado (ya flageado por Santi el 2026-08-11) antes de conectar
el proveedor de mail real; además el envío de la app sigue en `MAIL_MODE=stub`. Coordinar con Santi/Gon.
**Pendiente:** el **isotipo** (logo TBC multicolor) — Gon dijo que adjunta el archivo pero no llegó; queda para cuando lo pase.
**Refs:** `frontend/src/pages/{Proximamente,Registro,Login,Dashboard,Recetas,EmitirReceta,CierreMensual,CierreConsolidado,CatalogoAdmin,Pacientes,Perfil}.tsx`, `components/{layout/AppLayout,receta/*,dashboard/EstadisticasCharts,nutricionista/ParametrosModal}`.

## 2026-08-13 — Fran — frontend/infra local (vuelta de vacaciones: sync + puertos + limpieza CRLF)
**Qué:** Vuelvo de vacaciones y me pongo al día con el pull (deploy en prod + Fase 1/2 + olas post-demo, todo de Santi).
Puesta a punto de mi entorno local:
- **Puertos alineados con Santi** para esquivar imedba/GIA: `.env` local nuevo con `BACKEND_PORT=8088`,
  `frontend/.env.local` → `VITE_API_BASE_URL=http://localhost:8088`, y front en `:5174`. `vite.config.ts` ahora
  lee el puerto de **`VITE_DEV_PORT`** (default 5173) en vez de hardcodear — así cada máquina elige sin tocar el repo.
- **Dropeé mis 3 fixes CRLF locales** (`git checkout`) ya que el `.gitattributes` de Santi cubre `mvnw` y `db/init/*.sh`.
- **Levanté el stack local** (back `:8088`, keycloak `:8081`, db `:5432`) + front `:5174` para revisar las pantallas nuevas.
**⚠️ PARA SANTI — hueco en `.gitattributes`:** NO cubre `backend/.mvn/wrapper/maven-wrapper.properties` → cae en
`* text=auto` → en Windows (autocrlf=true) se checkoutea **CRLF**, el `\r` se cuela en `wrapperUrl` y el build del
backend muere con `HTTP 400` al bajar el maven-wrapper. Lo volví a arreglar local (LF), pero se re-rompe en cada
clone/checkout. **Falta agregar `*.properties text eol=lf`** (o `.mvn/wrapper/** text eol=lf`) al `.gitattributes` y
`git add --renormalize`.
**Refs:** `.env` (local), `frontend/.env.local`, `frontend/vite.config.ts`, `backend/.mvn/wrapper/maven-wrapper.properties`.

## 2026-08-11 — Santi — infra (🟢 **EN VIVO**: https://nutriappok.com.ar con TLS — deploy cerrado)
**Qué:** El sitio responde por HTTPS público con certificado de Let's Encrypt, en modo pre-lanzamiento.
Se destrabó el DNS y Caddy emitió los certs de `nutriappok.com.ar` y `www` **solo**, sin tocar nada del
stack: sólo hizo falta un `docker restart edge-caddy-1` para sacarlo del backoff de ACME.

**Cómo terminó la novela del DNS** (arrancó con el dominio equivocado, ver entrada de abajo):
1. hPanel asignó a `nutriappok` el par **`nova/cosmos.dns-parking.com`** — distinto del `orbit/horizon`
   que se había cargado en el registro copiándolo del dominio errado. **El par se asigna por dominio**,
   no por cuenta (haltcatch → lunar/solar, jeianell → ns1/ns2, nutriappok → nova/cosmos).
2. El import daba **409 "Domain is pending verification"**, y era circular: hPanel verifica resolviendo
   los `NS` por DNS, y el dominio daba `SERVFAIL` porque el padre delegaba a NS que lo rechazaban. La
   verificación pedía una respuesta que sólo existiría si la zona ya estuviera publicada. Se destrabó
   alineando la delegación a nova/cosmos.
3. Después el panel tiró **404 en `PATCH /api/dns/v1/direct/zone/resource-records`** al agregar un `A` a
   mano: la zona existía en sus nameservers pero su propio panel no la encontraba. Se resolvió solo.
4. Al importar, Hostinger **autopobló la zona apuntando a su hosting compartido** (`212.1.211.163` +
   `MX`/`SPF` propios) — el `A` de parking que veníamos vigilando. Corregido a mano al VPS.

**Problemas:**
- **Casi diagnostico mal el final.** `dig @nova.dns-parking.com` devolvía la IP vieja mientras
  `dig @172.64.52.46` (la misma máquina, por IP) devolvía la correcta: caché del resolver local
  resolviendo el **nombre** del nameserver. **Contra un autoritativo, preguntar por IP**, o se termina
  leyendo caché propia y creyendo que es el estado real. Estuve a punto de decir que el import no había
  entrado cuando sí.
- Caddy validó por **`tls-alpn-01`**, no por http-01. O sea que el webroot ACME de `nginx/acme/` no
  intervino para nada — es material del modo front único nomás.

**Verificado por HTTPS público** (sin `--resolve`, resolviendo por DNS real): `/`, `/registro`,
`/ingresar` 200; `/actuator/health` UP; discovery OIDC con issuer `https://nutriappok.com.ar/auth/realms/nutriapp`;
`/auth/realms/master` 404; `/api/v1/recetas` 401; HSTS/CSP/X-Frame-Options presentes; redirect 308 de
HTTP a HTTPS; bundle servido con `VITE_API_BASE_URL=https://nutriappok.com.ar` y `VITE_COMING_SOON=true`;
cert válido hasta el 2026-11-09. haltcatch y jeianell en 200.

**Backups cifrados, cerrado en la misma sesión.** Clave `ed25519/CEE22F19C64220E5`
(`backups@nutriappok.com.ar`) generada en el VPS + `BACKUP_GPG_RECIPIENT` en el `.env`. Verificado de
punta a punta: cifra → descifra → `pg_restore -l` lista 79 objetos con `nutricionistas`/`recetas`/
`productos`. **Y apareció un bug real de paso: `backup-db.sh` y `restore-db.sh` no leían el `.env`.**
Setear `BACKUP_GPG_RECIPIENT` ahí —que es donde el runbook dice que va— no tenía ningún efecto: los
dumps salían en **texto plano** con sólo un aviso por stderr, o sea invisible desde cron. Ahora los dos
scripts cargan `.env` (lo que ya venga del entorno le gana). **Queda un paso manual:** la privada está
en `/root/nutriapp-backup-gpg-PRIVATE.asc` → guardarla afuera y borrarla del host; cifrar sólo necesita
la pública. Sin la privada no hay restore.

**Pendiente (no bloquea):** borrar el `MX` y el `TXT` de SPF que autopobló Hostinger — el mail de la app
no sale por ahí, y ese SPF autenticaría al remitente equivocado cuando en Fase 2 se conecte el proveedor
real, mandando las recetas a spam.

**Impacto para el otro (Fran):** el sitio ya es público. Cualquier cambio de front necesita **rebuild con
los `VITE_*` de prod + copiar `dist` + `nginx -s reload`** — pushear no alcanza. Y la CSP de prod tiene
`script-src 'self'` sin `unsafe-inline`/`unsafe-eval`: si algo del bundle necesitara `eval`, rompe en prod
y no en dev.

## 2026-08-11 — Santi — infra (**CORRECCIÓN**: el dominio es `nutriappok.com.ar`, no `nutriapp.com.ar`)
**Qué:** Todo el deploy de la entrada de más abajo se había configurado con **el dominio equivocado**.
El dominio del proyecto es **`nutriappok.com.ar`**. Renombrado en todos lados: `.env`
(`KEYCLOAK_HOSTNAME`, `VITE_*`, `MAIL_FROM_ADDRESS`), `nginx/conf.d-proxied/nutriapp.conf` y
`nginx/conf.d/nutriapp.conf` (`server_name`), `docker-compose.prod.yml`, `/root/stack/Caddyfile`,
DEPLOY.md, y el `.zone` pasó a llamarse `nutriappok.com.ar.zone`. **Rebuild de la SPA obligatorio**:
el origen va horneado en el bundle. Aplicado: keycloak recreado, nginx recreado, Caddy reiniciado.

**Por qué:** `nutriapp.com.ar` **no es nuestro** — su delegación en el registro `.ar` apunta a
`ns1/ns2.donweb.com`. Es de otro.

**La buena noticia — no hubo que tocar DNS de nuevo.** Verificado contra el padre `.ar`:
- `nutriappok.com.ar` **ya está delegado a `orbit/horizon.dns-parking.com`** (d.dns.ar y f.dns.ar
  coinciden). La parte lenta y dolorosa ya estaba hecha, y sobre el dominio correcto.
- Lo que falta es al revés de lo que parecía: la **zona no existe** (orbit/horizon responden
  `REFUSED` para nutriappok), porque el alta en hPanel se hizo con el nombre equivocado. Esa zona
  huérfana de `nutriapp.com.ar` (SOA `2026081101`) existe pero nadie le delega → borrarla.

O sea el cruce exacto: **dominio bueno con delegación buena y sin zona; dominio equivocado con zona
y sin delegación.** Sólo falta dar de alta `nutriappok.com.ar` en hPanel + importar el `.zone`. Si
hPanel le asigna otro par de NS (se asigna por dominio, no por cuenta), ahí sí hay que alinear el
registro; si le toca orbit/horizon, no hay nada que hacer.

**Problemas:**
1. **Me rompí nginx en el medio y vale documentarlo.** El mount de `conf.d` es un **directorio**, así
   que editar los `.conf` adentro se ve bien — pero el `git rebase` del push pasó por commits donde
   `nginx/conf.d-proxied/` no existía, lo borró y lo recreó, y eso **cambia el inodo del directorio**:
   el contenedor quedó pegado al viejo, vacío. Es silencioso: nginx siguió sirviendo con la config en
   memoria y explotó recién en el `nginx -s reload` de este cambio, quedándose **sin ningún
   `server{}`** → *connection refused*, no un error de config (`nginx -t` pasa: una config vacía es
   válida). Se arregla con `up -d --force-recreate nginx`, no con reload. **Regla: después de
   cualquier git que toque `nginx/conf.d*/`, recrear nginx.** Documentado en DEPLOY.md.
2. La trampa del Caddyfile (bind-mount de archivo suelto) volvió a morder, como estaba previsto:
   `caddy validate` contestó "Valid configuration" **validando el archivo viejo**. Restart y listo.

**Verificado post-rename:** bundle con `https://nutriappok.com.ar` horneado y cero rastros del viejo;
issuer OIDC `https://nutriappok.com.ar/auth/realms/nutriapp`; `/` 200, `/actuator/health` 200,
`/api/v1/recetas` 401, `/auth/realms/master` 404; **`Host: nutriapp.com.ar` ahora cae en el catch-all
y cierra con 444**; real_ip sigue tomando el cliente real; haltcatch y jeianell en 200.

**Impacto para el otro (Fran):** `frontend/src/pages/Proximamente.tsx` menciona `nutriapp.com.ar` en un
**comentario** (línea 2). No tiene impacto funcional y es tu archivo, así que no lo toqué — corregilo
cuando pases por ahí.

**Refs:** `nutriappok.com.ar.zone` (era `nutriapp.com.ar.zone`), `.env` (fuera de git), DEPLOY.md,
`/root/stack/Caddyfile`.

## 2026-08-11 — Santi — infra (deploy de nutriapp.com.ar en modo pre-lanzamiento, detrás del Caddy del VPS)
**Qué:** Stack prod levantado y sirviendo en el VPS. NutriApp **no** es el front: los 80/443 los tiene
`edge-caddy-1` (stack `/root/stack`, sirve haltcatch.com.ar y jeianell.com.ar) y nutriapp se suma a ese
esquema. Cambios:
- `nginx/conf.d-proxied/nutriapp.conf` (nuevo): variante HTTP-only, sin TLS ni redirect (los hace Caddy),
  con `server_name` fijo + catch-all `return 444`, y `set_real_ip_from`/`real_ip_header`.
- `docker-compose.prod.yml`: nginx **sin `ports:`**, sumado a la red externa `web`, conf dir por
  `NGINX_CONF_DIR` (default la variante proxied).
- `docker-compose.edge.yml` (nuevo): override opcional para el modo front único (re-publica 80/443,
  `networks: !override` para sacar `web`). No se usa acá.
- `/root/stack/Caddyfile`: site block `nutriapp.com.ar, www.nutriapp.com.ar → reverse_proxy nutriapp-nginx:80`.
  Backup en `Caddyfile.bak-20260811`.
- `.env` de prod generado con secretos fuertes; SPA compilada con `VITE_COMING_SOON=true` en un contenedor
  `node:22-alpine` (el VPS no tiene node).

**Por qué:** El DEPLOY.md asumía o bien frontear directo (choca con Caddy) o bien "puerto alto en loopback".
Las dos estaban mal: **Caddy proxea por nombre de contenedor sobre la red docker `web`**, no por loopback —
`hac_frontend` y `jeianell_frontend` no publican ni un puerto. Copiando ese patrón, nutriapp no agrega
superficie de red al host y el TLS lo maneja Caddy (ACME automático), en vez de heredar la renovación manual
por certbot que documentaba el paso 1.

**Problemas:**
1. **Keycloak publicaba el issuer sin `/auth`.** Con `KC_HOSTNAME=https://nutriapp.com.ar` (origen pelado,
   como decía el comentario del override) el `.well-known` respondía OK bajo `/auth` pero adentro publicaba
   `"issuer":"https://nutriapp.com.ar/realms/nutriapp"`. En KC 25 (hostname v2), si el valor es una URL
   completa **el context path sale de ahí y `KC_HTTP_RELATIVE_PATH` no se le concatena**. Peor: ese path cae
   en el `try_files` de la SPA y devuelve `index.html` con **200**, así que el login habría fallado con un
   error de parseo en vez de un 404 honesto. **Fix:** `KEYCLOAK_HOSTNAME=https://nutriapp.com.ar/auth`.
   Corregidos los comentarios de `docker-compose.prod.yml`, `.env.example` y DEPLOY.md, que decían "SIN path".
2. **`caddy reload` fue un no-op silencioso.** El compose de Caddy monta el Caddyfile como **archivo suelto**,
   y docker lo ata al inodo: al editarlo (reemplazo de archivo, no escritura in-place) el contenedor siguió
   viendo la versión vieja. `caddy validate` decía "Valid configuration" y `reload` contestaba
   `"config is unchanged"` — las dos cosas ciertas y las dos inútiles. Y `curl -I` con `Host:` daba `308`
   igual, porque es el redirect genérico de Caddy, **no** prueba que la ruta exista. **Fix:**
   `docker restart edge-caddy-1`. **Verificación buena:** `wget -qO- http://127.0.0.1:2019/config/` dentro
   del contenedor y buscar el host (ojo: `localhost:2019` da connection refused, va la IP).
3. `.env` no era sourceable desde bash: `TIENDANUBE_USER_AGENT` tenía paréntesis sin comillas. Comillado en
   `.env` y `.env.example` (compose las stripea igual).
4. `KC_PROXY: edge` del compose base quedó deprecado en KC 25 — sólo WARN, funciona por `KC_PROXY_HEADERS`.
   **En KC 26 hay que sacarlo.**

**Verificado:** 12 migraciones Flyway aplicadas; `/actuator/health` UP; discovery OIDC con issuer correcto;
`client_credentials` del backend contra el secret nuevo devuelve token; `/auth/admin` y `/auth/realms/master`
→ 404; `/api/v1/recetas` → 401; `/actuator/env` cae al fallback del SPA (no expone actuator); catch-all cierra
Hosts desconocidos; **real_ip**: con `X-Forwarded-For: 1.2.3.4, 203.0.113.77` nginx loguea `203.0.113.77`
(el último, o sea el que appendea Caddy) y descarta el spoofeado. haltcatch y jeianell siguen en 200 después
del restart de Caddy.

**Lo que NO está:** **el sitio todavía no es alcanzable desde internet.** `nutriapp.com.ar` sigue en
**SERVFAIL** (`EDE 22 No Reachable Authority`; la delegación de nic.ar apunta a `ns1/ns2.donweb.com` —
`200.58.112.193` — que responden `Query refused` porque no tienen la zona) → Caddy no puede emitir el cert:
falla el http-01 con `"DNS problem: SERVFAIL"` y reintenta con backoff. **Todo lo demás está listo: en cuanto
la zona resuelva, Caddy emite solo y el sitio queda arriba sin tocar nada más.** El orden de los pasos de DNS
está en DEPLOY.md §DNS y en el `.zone` (commits `ec31f27`/`5a4f3c7`, del mismo día): **los NS primero**
(`orbit/horizon.dns-parking.com`, el par que hPanel asignó a *este* dominio) y recién después el import, porque
hPanel no lo habilita hasta que la delegación apunte a Hostinger.

**Impacto para el otro (Fran):** `frontend/` no se tocó — sólo se compiló. El build de prod se hornea con
`VITE_API_BASE_URL=https://nutriapp.com.ar` (origen pelado, el código le concatena `/api/v1`) y
`VITE_KEYCLOAK_URL=https://nutriapp.com.ar/auth`. **Cualquier cambio de front necesita rebuild + `nginx -s
reload`**, no alcanza con pushear. La CSP de prod tiene `script-src 'self'` sin `unsafe-inline`/`unsafe-eval`:
si algo del bundle necesitara eval, rompe en prod y no en dev.

**Refs:** `nginx/conf.d-proxied/nutriapp.conf`, `docker-compose.prod.yml`, `docker-compose.edge.yml`,
`DEPLOY.md` (sección nueva "Detrás del Caddy del VPS"), `.env.example`, `/root/stack/Caddyfile`.

## 2026-08-11 — Santi — infra (zona DNS de nutriapp.com.ar + **corrección** de dos cosas que escribí ayer)
**Qué:** Archivo `nutriapp.com.ar.zone` en la raíz del repo, formato BIND, listo para importar en hPanel
(mismo criterio que `haltcatch.com.ar.zone` del proyecto de la landing). Activos sólo tres registros:
`A @ → 187.127.36.153`, `AAAA @ → 2a02:4780:6e:84b8::1`, `CNAME www → nutriapp.com.ar.`. El bloque de
correo Hostinger y un bloque alternativo de anti-spoofing (SPF `-all` + DMARC reject + null MX) quedan
comentados, excluyentes entre sí, con la explicación de cuándo usar cada uno.
**Por qué:** el usuario ya había hecho lo mismo para haltcatch y jeianell; importar un `.zone` evita cargar
registros a mano en el panel.
**Corrección 1 — el `AAAA` SÍ va (ayer dije que no).** Escribí que el `A` (Telecom AR) y el `AAAA` (rango
Hostinger) de la landing eran dos servidores distintos. Es falso: los dos tienen el mismo PTR,
`srv1786758.hstgr.cloud` → un solo VPS de Hostinger dual-stack (el `/48` es HOSTINGER-HOSTING en RDAP, y el
IPv4 187.127.36.x es de Hostinger aunque parezca argentino). Corregido en DEPLOY.md y ESTADO.md.
**Corrección 2 — en el VPS los 80/443 los tiene Caddy, no un nginx.** Verificado contra el server:
`haltcatch.com.ar` da `Server: Caddy` en `:80` (308 → HTTPS) y en `:443` devuelve `Via: 1.1 Caddy` +
`Server: nginx/1.27.5`, o sea Caddy termina TLS y proxea a un nginx que sirve la landing. Consecuencias:
(a) el override de prod, que bindea `80:80`/`443:443`, **no levanta ahí** — nutriapp tiene que escuchar en un
puerto alto con un `reverse_proxy` de Caddy adelante; (b) **el certbot/webroot ACME que agregué ayer queda de
reserva**: Caddy emite y renueva el cert solo. Falta adaptar el override a HTTP plano en puerto alto y decidir
dónde quedan los security headers y el rate-limit, que hoy viven en el `server{}` de TLS del nginx nuestro.
**Hallazgo nuevo:** `nutriapp.com.ar` da **SERVFAIL** (no NXDOMAIN) → hay delegación en nic.ar pero los
nameservers no sirven la zona. Hay que crear el dominio en hPanel y poner en nic.ar el par de NS que hPanel
muestre **para ese dominio**: no es fijo por cuenta (haltcatch usa `lunar/solar.dns-parking.com`, jeianell
usa `ns1/ns2.dns-parking.com`). Hasta que eso esté, el `.zone` no tiene dónde importarse.
**Impacto para el otro:** ninguno (nada de esto toca `frontend/` ni el contrato).
**Refs:** `nutriapp.com.ar.zone` (nuevo), `DEPLOY.md` (secciones DNS + pre-requisitos + TLS), `ESTADO.md`.

## 2026-08-10 — Santi — frontend + infra (landing "Próximamente" detrás de un flag de build + DNS/TLS de nutriapp.com.ar)
**Qué:** Nueva pantalla `pages/Proximamente.tsx` y un flag de build `VITE_COMING_SOON`. Con el flag en
`true`: `/` es la landing de pre-lanzamiento (propuesta de valor + CTA "Solicitar acceso"), `/registro`
sigue igual, y el login se mueve a **`/ingresar`** sin link desde ningún lado. Con el flag apagado (default)
la app queda **exactamente** como estaba: `/` es el login. El área autenticada sigue montada en los dos
modos — el flag saca la puerta de entrada de la vista pública, no desarma la app. Estilos `.soon__*` al final
de `index.css` reusando los tokens y el `--ink` del split-screen de auth (nada de assets nuevos). Del lado
de infra: webroot ACME (`nginx/acme/` + `location ^~ /.well-known/acme-challenge/` en el `:80`, que antes
redirigía todo a HTTPS y hacía imposible el http-01), y DEPLOY.md con los registros DNS de `nutriapp.com.ar`,
el modo pre-lanzamiento y la emisión con certbot.
**Por qué:** el cliente presiona para tener algo publicado en `nutriapp.com.ar` ya. Un flag de build en vez
de una rama o un `index.html` aparte porque el registro tiene que pegarle al backend real (regla de oro: nada
mockeado) y porque apagarlo tiene que ser un rebuild, no un revert.
**Problemas:** ninguno en el build (`tsc` + `oxlint` + `vite build` verdes; verificado además que el flag se
hornea: `VITE_COMING_SOON:"true"` aparece en el bundle con el flag y desaparece sin él). Sí **encontré un
error preexistente en DEPLOY.md**: el snippet de build decía `VITE_API_BASE_URL=https://app.midominio.com/api`,
pero `config.ts` le concatena `/api/v1` → habría quedado `/api/api/v1` y **todos** los fetch en 404 en el
primer deploy. Corregido al origen pelado (así ya estaba, bien, en `.env.example`).
**Impacto para el otro:** Fran — **toqué `frontend/`, que es tuyo** (pedido explícito de Santi). Cinco
archivos: `pages/Proximamente.tsx` (nuevo), `config.ts` (+`comingSoon`), `App.tsx` (dos rutas: `/` condicional
y `/ingresar`), `Registro.tsx` (2 líneas: el pie "¿Ya tenés cuenta? Ingresar" se oculta con el flag) y
`index.css` (bloque nuevo al final) + `index.html` (title y meta OG). No cambié nada existente de tus
pantallas ni del contrato. Si te molesta la forma, el flag es un solo `if` y se mueve donde quieras.
**Pendiente / decisiones abiertas (Santi):** (1) los 80/443 del server los puede estar ocupando la landing
del cliente → hay que decidir quién es el front antes del `up`, y endurecer el `server_name _` (hoy catch-all,
le robaría el `Host` a la landing); (2) el registro público **no manda ningún mail** (no encola notificación y
el proveedor es stub) y la bandeja de aprobación del admin está diferida a Fase 3 → aprobar es por API o SQL,
y avisarle a la nutricionista lo hace una persona. Todo detallado en DEPLOY.md.
**Refs:** `frontend/src/pages/Proximamente.tsx`, `frontend/src/{config.ts,App.tsx,index.css}`,
`frontend/src/pages/Registro.tsx`, `frontend/index.html`, `nginx/conf.d/nutriapp.conf`, `nginx/acme/`,
`docker-compose.prod.yml`, `.env.example`, `DEPLOY.md`.

## 2026-08-04 — Santi — frontend (pantallas sin scroll: emisión, navbar/footer fijos, detalle en modal)
**Qué:** Cinco ajustes de layout salidos de usar la app. `tsc`/`oxlint`/`build` verdes.

- **Emitir receta rediseñada como panel de trabajo de alto fijo.** Era una página larga: al agregar
  el tercer producto había que scrollear para encontrar el botón de emitir, y el buscador quedaba
  arriba fuera de pantalla. Ahora la pantalla ocupa exactamente el viewport disponible y **no
  scrollea**: scrollean por dentro las dos listas que pueden crecer sin límite (resultados del
  buscador e items de la receta). El buscador, los totales y el botón quedan siempre a la vista.
  El paciente pasó de ocupar una tarjeta entera —para mostrar un solo dato— a una píldora en la
  barra de título, y ese alto se lo quedó el buscador. Abajo de 980px vuelve a ser una página que
  scrollea: no hay dos columnas que sostener.
- **Navbar fijo.** Estaba en `position: sticky` y no funcionaba: hay un `html, body { overflow-x:
  hidden }` (para que nada desborde a lo ancho) que crea un contexto de scroll y **anula el
  sticky**. Pasado a `fixed`, que se ancla al viewport y no se ve afectado. El alto de las dos
  barras vive ahora en `--navbar-h`/`--footer-h` y el shell las reserva con padding.
- **Footer reducido a la barra fija** (marca + copyright + Simple Apps). Los links que tenía arriba
  se eliminaron por dos razones: para la nutricionista duplicaban la navbar, y **para el admin
  apuntaban a rutas que no puede ver** (Panel/Recetas/Pacientes/Cierre son sólo de nutricionista
  por C-07) — o sea que lo mandaban a un 403 o lo rebotaban al home. Era un bug, no sólo ruido.
- **Detalle de producto: de fila expandible a modal.** La fila expandible empujaba todas las de
  abajo, la tabla saltaba y se perdía de vista lo que se venía leyendo.
- **Ficha de nutricionista sin scroll**: estado y email en una línea arriba, datos en grilla densa,
  los dos porcentajes y su aclaración en una sola fila, y las acciones de cuenta pasaron de tarjetas
  con título+descripción a una línea cada una (botón + aclaración al lado).

**Pregunta del usuario — de dónde salen las imágenes:** del **maestro**, columna
`LINK IMAGEN TIENDA NUBE`. Son URLs al CDN de TiendaNube (`dcdn-us.mitiendanube.com`): guardamos el
link, no el archivo, así que no cuestan storage pero dependen de que TBC no las mueva. Cobertura
real hoy: **173 de 699 recetables (25 %)** — por eso la fila de producto tiene que verse bien sin
imagen.
**Refs:** `pages/EmitirReceta.tsx`, `pages/CatalogoAdmin.tsx`, `components/layout/Footer.tsx`,
`components/nutricionista/ParametrosModal.tsx`, `components/receta/ProductoBuscador.tsx`, `index.css`.

## 2026-08-04 — Santi — frontend+backend (detalle expandible del catálogo del admin, con los tags)
**Qué:** Pedido: "traer los tags del maestro y que filtren en la búsqueda, y poder expandir el
producto viendo más detalles con los tags como burbujas".

**La mitad ya estaba hecha desde la Ola 3 y lo verifiqué antes de tocar nada:** el importador ya lee
la columna TAGS y la aplica (**8447 tags sobre 539 productos** en la DB actual), y el buscador ya
filtra por ellos — `q=veganos` devuelve 15 resultados, `q=fertilidad` 6, `q=magnesio` 54. No hacía
falta cambiar el script.

**Lo que sí faltaba, y es lo que se hizo:** la pantalla `/catalogo` del admin listaba productos pero
no mostraba tags ni dejaba ver el detalle. Ahora cada fila se expande (chevron + click en la fila) y
abajo aparece: imagen, los 9 datos de las dos fuentes (código de barras, taxonomía, laboratorio,
rubro y tipo del ERP, estado en el ERP, fechas de sync e import), la descripción web y **los tags
como burbujas**. Sólo lectura, como se pidió: el catálogo lo escriben el sync y el import, no esta
pantalla. En el buscador de recetas los tags siguen siendo clickeables (filtran) — son dos usos
distintos y por eso son dos estilos distintos (`.burbuja` vs `.tag-chip`).

Cuando un producto no tiene tags el detalle explica **por qué**, que es lo accionable: "el maestro lo
tocó pero no le cargó tags" si matcheó, o "no está en el maestro: sin tags no se lo encuentra por
palabra clave" si no. La columna Tags de la tabla muestra el conteo, así se ve de un vistazo dónde
falta carga.

**Backend:** `AdminProductoResponse` suma `tipoErp`, `activoErp` y `rubro` — no viajan al emisor
(a la nutricionista no le dicen nada) pero son el contexto de por qué un producto entró o quedó
afuera del catálogo recetable. `mvn test` 149 verde; front `tsc`/`oxlint`/`build` verdes.
**Refs:** `pages/CatalogoAdmin.tsx`, `components/ui/Icon.tsx` (ícono `chevron`), `index.css`,
`AdminProductoResponse`, `ProductoService.toAdminResponse`.

## 2026-08-04 — Santi — frontend (diálogos propios, ficha del admin y arreglos de la tanda anterior)
**Qué:** Cuatro cosas que salieron de mirar la app en vivo. `tsc`/`oxlint`/`build` verdes.

- **Fuera todos los `window.confirm` / `window.prompt`.** Eran 9 en 5 archivos. Nuevo
  `components/ui/Dialog.tsx`: `DialogProvider` + `useDialog()` con `confirmar()` y `pedirTexto()`,
  ambos promesa-based para que el call site siga leyéndose igual que antes
  (`if (!(await confirmar({...}))) return;`). Más allá de la estética, el diálogo del browser no
  deja explicar nada: en un borrado irreversible hace falta decir qué se pierde y qué alternativa
  hay, y eso no entra en una línea de texto plano. Ahora el botón que confirma **nombra la acción**
  ("Borrar definitivamente") en vez de decir "Aceptar", y va en rojo si es destructiva.
- **Ficha de nutricionista rediseñada.** Los botones estaban todos en una fila de tamaño parecido:
  guardar convivía con borrar-para-siempre. Quedó partida en dos zonas: arriba la ficha y los
  porcentajes con su botón primario abajo a la derecha, y separada una zona "Cuenta" donde cada
  acción (contraseña / desactivar / borrar) es una fila con su explicación al lado — sin eso,
  "Desactivar" y "Borrar" son dos botones parecidos y la diferencia entre ellos sólo se descubre
  apretando.
- **Datos de "Mi perfil" descomprimidos**: pasaron de grilla a una fila por dato con separadores.
  En grilla, la etiqueta de un campo y el valor de otro quedaban pegados y se leía como texto corrido.
- **`/catalogo` (Productos del admin) redirigía a `/nutricionistas`.** El código de la ruta y el
  guard estaban bien: era el **cache de Vite**. Edité `App.tsx` agregando el import de
  `CatalogoAdmin` *antes* de crear el archivo; Vite cachea el fallo de resolución y desde ahí la
  ruta no existía en el bundle, así que caía en el fallback `path="*"` → `/` → Login con sesión →
  home del rol → `/nutricionistas`, que es exactamente el síntoma. Se resolvió reiniciando el dev
  server con `node_modules/.vite` borrado. **Al crear un archivo nuevo, crearlo antes de importarlo.**

**De paso:** `CierreConsolidado` seguía usando la clase `filtros__date`, que había sido reemplazada
por `filtros__campo` al alinear los filtros de recetas — sus dos fechas estaban sin estilo.
**Refs:** `components/ui/Dialog.tsx` (nuevo), `components/nutricionista/ParametrosModal.tsx`,
`pages/{Perfil,Pacientes,CierreConsolidado}.tsx`, `components/receta/RecetaDetalle.tsx`, `index.css`.

## 2026-08-04 — Santi — backend+frontend+db (11 cambios pedidos: parámetros, cuentas, catálogo admin y UI)
**Qué:** Tanda grande de cambios pedidos por el usuario. `mvn verify` **149 unit + 1 IT**; front `tsc`/`oxlint`/
`build` verdes. Dos migraciones (`V011`, `V012`). Verificado e2e contra el stack (back `:8088`).

**🔎 El "bug" de la contraseña no era un bug.** Se reportó que un usuario dado de alta no podía loguear
"porque no le tomaba la contraseña". Reproducido de punta a punta: registro → aprobar → login **funciona**, y
el usuario en cuestión (`santiscally@gmail.com`) tiene su credencial `password` en Keycloak, `enabled=true` y
`APROBADA`. Lo que sí muestra Keycloak es **3 intentos fallidos** desde la IP del host — con `failureFactor=30`
no llegó a bloquearse. Quedan dos explicaciones: un typo, o haber probado **antes de aprobar** (ahí Keycloak
responde `Account disabled`, que ya está traducido pero no aclara que la contraseña estaba bien).
**Lo que sí era un agujero real: no existía ninguna forma de recuperar el acceso** — no hay "olvidé mi
contraseña" ni reset por admin. Quien se equivocaba quedaba afuera para siempre. Eso es lo que se construyó.

**Backend:**
- **`V011` — se elimina la configuración global de %.** Convivían un global (`configuracion_sistema`) y un
  override por nutricionista donde `NULL` significaba "usá el global": el mismo dato en dos lugares y dos
  formas de leerlo, y cualquier lectura que se salteara el resolutor devolvía un número distinto al de la
  emisión. Ahora cada nutricionista tiene los suyos, obligatorios. La migración **hereda el global vigente**
  (no un literal 15/10: si el admin lo había cambiado, el valor que estaba aplicando es el suyo) y verificado
  en la DB real: `ana.test` conservó su 30/8 y el resto quedó en 15/10. Las recetas ya emitidas no se tocan
  (los % están snapshoteados desde C-01). Se borró el módulo `configuracion` entero y la pantalla del front;
  `ParametrosNegocioService` se mudó a `modules/nutricionista/`. El descuento ahora viaja en `GET /me`.
- **`V012` + gestión de cuentas del admin.** Columna `activo` (espejo del `enabled` de Keycloak, para que la
  bandeja muestre 20 filas sin hacerle 20 requests a Keycloak) y cuatro acciones:
  `desactivar`/`reactivar` (reversibles, conservan todo), `DELETE` (borra usuario + fila + pacientes +
  archivos) y `POST /password` (reset). **Borrar se niega con 409 si emitió recetas**: esas recetas alimentan
  los cierres y borrar a su autora dejaría plata contabilizada sin nadie a quien atribuírsela; el mensaje
  manda a desactivar. El orden importa — primero Keycloak, después la fila local: al revés quedaría un
  usuario capaz de loguearse sin perfil (hay test).
- **`PUT /perfil/password`.** Exige la contraseña actual y la verifica contra Keycloak por ROPC, porque la
  Admin API pisa credenciales sin conocer la anterior: sin ese chequeo, una sesión abierta y olvidada
  alcanzaría para quedarse con la cuenta. El reset del admin además **limpia los intentos fallidos** — si
  alguien llegó a pedirlo es probable que haya reintentado hasta frenarse contra la protección de fuerza bruta,
  y la contraseña nueva no le serviría hasta que expirara el bloqueo.
- **La nutricionista deja de ver facturación** (extiende C-02, pedido del usuario a mitad de la tanda): fuera
  `ordenTotal` de `RecetaResponse.Conversion`, `ventasGeneradas*` del resumen, del cierre mensual y de las
  estadísticas. El detalle de conversión ahora dice sólo la comisión. **El admin lo conserva** en el cierre
  consolidado: es con lo que liquida. Se borró `sumVentasEntre`, que quedó sin uso.
- **`GET /admin/productos` + `/resumen`.** El catálogo con los despublicados incluidos y el motivo resuelto en
  castellano (`PublicacionPolicy.motivoNoPublicable`). Contra el catálogo real: **2267 productos, 699
  recetables, 104 sin match del maestro, 484 bloqueados**.
- **`/productos/filtros` ahora trae `precioMin`/`precioMax`** reales (4.011 – 1.421.999) para los extremos del
  slider: sin eso el front tendría que inventar un tope.

**Frontend:** pantalla `/catalogo` para el admin con tarjetas y filtro "sin match del maestro"; buscador con
los filtros **plegados** detrás de un botón (eran 8 controles siempre a la vista tapando la lista) + chips de
lo aplicado + **slider de precio de doble pulgar**; filtros de recetas alineados (todos con label y 40px de
alto — antes las fechas iban en un label de dos líneas y quedaban más bajas); `/perfil` en dos columnas con
form de contraseña; notas y fecha de nacimiento **editables** en pacientes y visibles en el listado; footer
reducido con la barra de copyright + Simple Apps **fija** al pie; modal con alto acotado al viewport y scroll
en el cuerpo (el "Más info" de un producto con descripción larga se cortaba y el botón de cerrar quedaba
fuera de pantalla).

**Problemas:**
1. **Cualquier ruta inexistente devolvía 500** ("Error interno") en vez de 404 — el catch-all del
   `GlobalExceptionHandler` se comía `NoResourceFoundException`. Preexistente, pero salta ahora que
   `/configuracion` dejó de existir y un front desactualizado lo va a seguir pegando. Arreglado.
2. **`Object[]` de un query con dos agregados viene anidado** (`Object[]{Object[]{min,max}}`) según el caso;
   hay que desanidar antes de leerlo o el rango de precios sale null.
3. **El `curl` de Git Bash manda los acentos en cp1252** y el backend responde 400 "JSON malformado". Me hizo
   creer que el PUT de pacientes ignoraba las notas. Con el body en un archivo UTF-8 anda: verificado el
   round-trip completo. **Para probar endpoints con texto en castellano: `--data-binary @archivo`, nunca `-d`
   inline.**

**Verificado e2e:** migraciones aplicadas y `configuracion_sistema` eliminada; desactivar → `Account disabled`
→ reactivar → login OK; reset de contraseña → login con la nueva; borrado de una nutricionista sin recetas
(y su login pasa a `Invalid user credentials`); **409 al intentar borrar una con 6 recetas**; cambio de
contraseña propio (rechaza la actual incorrecta con 409, acepta la correcta con 204); notas de paciente
round-trip; `/configuracion` → 404.

**Impacto para el otro (Fran):** contrato con **cambios que rompen** — `RecetaResponse.Conversion` pierde
`ordenTotal`; `DashboardResumen` pierde `ventasGeneradasMesActual`; `CierreMensual` pierde `ventasGeneradas` y
`Detalle.ordenTotal`; `EstadisticasMes` pierde `ventasGeneradas`; `NutricionistaAdmin` pierde
`descuentoPctEfectivo`/`comisionPctEfectiva` (ahora `descuentoPct`/`comisionPct` son obligatorios) y suma
`activo`; `GET /configuracion` **ya no existe** (el descuento sale de `/me`). Todo el front del repo ya quedó
actualizado.
**Refs:** `V011__parametros_solo_por_nutricionista.sql`, `V012__nutricionista_activo.sql`,
`AdminNutricionistaService` (+ test nuevo, 11 casos), `PerfilController`, `KeycloakAdminClient`,
`ProductoService.searchAdmin`, `PublicacionPolicy.motivoNoPublicable`, `frontend/src/pages/CatalogoAdmin.tsx`,
`components/receta/RangoPrecio.tsx`, `components/ui/Modal.tsx`, `index.css`.

## 2026-08-04 — Santi — backend+frontend+db (2.4: WhatsApp por link `wa.me`, se saca la Cloud API)
**Qué:** Ejecutada la tarea 2.4, que estaba decidida desde el 2026-07-28 y anotada sin tocar código. El envío
por WhatsApp pasa a ser **manual**: el backend devuelve `waMeUrl` en el `RecetaResponse` y la nutricionista
toca un botón que le abre el chat con la paciente con el mensaje ya escrito. `mvn verify` **147 unit + 1 IT**
(141 → +8 del builder, +1 de wiring, −3 del test de la Cloud API que se borró); el IT confirma además que las
10 migraciones aplican limpias sobre una Postgres nueva. Front `tsc`/`oxlint`/`build` verdes.

**Por qué:** el envío automático exigía WABA, número de empresa verificado y template aprobado por Meta —
trámites del cliente, no trabajo nuestro— más costo por conversación. El link no necesita nada de eso y el
mensaje sale del número que la paciente ya conoce.

**Lo que se sacó** (WhatsApp dejó de existir como integración, no sólo como adapter):
- `integrations/whatsapp/**` (port + `CloudApiWhatsAppSender` + stub) y su test.
- El valor `WHATSAPP` de `CanalNotificacion`, la rama del `NotificacionDispatcher` y el encolado en
  `NotificacionService`. **El único canal automático es el email.**
- El proveedor `whatsapp` de `GET /admin/integraciones/estado` (quedan 3), `IntegrationsProperties.WhatsApp`,
  el `Proveedor.WHATSAPP` del health registry y la config `WHATSAPP_*` de `application.yml`/compose/`.env.example`.
- **Migración `V010`**: borra las filas `canal='WHATSAPP'` y deja el CHECK en `('EMAIL')`.

**Decisiones que vale la pena registrar:**
1. **El borrado de las notificaciones WHATSAPP es físico**, contra la regla de soft-delete del proyecto. Una
   fila soft-deleted con un valor que el enum ya no tiene igual revienta cualquier lectura que no filtre por
   `deleted_at` (`findById`, `findAll`): la bomba queda armada esperando. Es una cola operativa, no un
   registro de negocio, y en stub nunca salió un mensaje. La receta, que es el dato real, no se toca.
2. **`waMeUrl` sólo viene si la receta está `PENDIENTE`** y el paciente tiene teléfono utilizable. Una
   ANULADA o VENCIDA daría un código muerto. El front decide mostrar el botón por la presencia del campo,
   sin repetir la regla de estados.
3. **Espacios como `%20`, no como `+`.** `URLEncoder` es form-encoding y manda `+`; algunos clientes de
   WhatsApp lo muestran literal en el mensaje. Hay un test que lo fija.
4. **El botón es la acción principal de la pantalla de éxito** (verde de marca), y "Emitir otra receta" bajó
   a secundaria: mandar el WhatsApp dejó de ser algo que hace el sistema y pasó a ser un paso que si la
   nutricionista se saltea, no ocurre. En el detalle de receta el botón convive con "Reenviar mail"
   (renombrado: reenviar ya no manda WhatsApp).

**Verificado e2e** contra el stack real (back `:8088`): `V010` aplicada (`success=t`), CHECK quedó en
`canal = 'EMAIL'`, 0 filas WHATSAPP; una receta PENDIENTE devuelve el link y las LIQUIDADAS no; el mensaje
decodificado sale correcto con acentos y emoji ("Hola Lucía! 🌱 … Código: *RX-HVUBRK* (válido hasta el
30/08/2026)"); emisión nueva → `waMeUrl` + una sola notificación EMAIL; el panel de integraciones devuelve
3 proveedores.

**Pendiente menor:** el mensaje no lleva link a la tienda (no hay URL del storefront configurada en ningún
lado; el mail tiene el mismo hueco). Se cierra en Fase 2 cuando esté la tienda real.

**Impacto para el otro (Fran):** `RecetaResponse` suma **`waMeUrl?: string | null`** (aditivo);
`RecetaNotificacion.canal` ya sólo puede ser `"EMAIL"`; `GET /admin/integraciones/estado` devuelve 3
proveedores. Los types espejo y las pantallas (`RecetaExito`, `RecetaDetalle`, `Integraciones`) ya quedaron
actualizados.
**Refs:** `modules/receta/service/WaMeLinkBuilder.java` (nuevo) + su test, `V010__notificaciones_solo_email.sql`,
`RecetaResponse`, `NotificacionService`/`Dispatcher`/`Templates`, `IntegrationsConfig`/`Properties`,
`IntegracionesEstadoService`, `frontend/src/components/receta/RecetaExito.tsx` + `RecetaDetalle.tsx`,
`03-integraciones-apis.md §4`.

## 2026-08-03 — Santi — frontend+infra (CORS del puerto 5174 + registro en una sola pantalla)
**Qué:** Dos cosas que salieron de probar la app en vivo. Front `tsc`/`oxlint`/`build` verdes.

- **CORS roto al levantar el front en `:5174`.** Login y registro fallaban con *"No 'Access-Control-Allow-Origin'
  header"*. Había que tocar **tres** lugares, no uno: (1) `app.cors.allowed-origins` del backend, (2) el
  **`.env` local**, que pisaba el default del compose —por eso el primer rebuild no cambió nada—, y (3) los
  **`redirectUris` del client `nutriapp-frontend` en Keycloak**, porque tiene `webOrigins: ["+"]`, o sea que
  los orígenes permitidos se derivan de los redirectUris: sin `http://localhost:5174/*` ahí, el `/token`
  responde sin header CORS por más que el backend esté bien.
  Quedaron los dos puertos permitidos (5173 y 5174) en `application.yml`, `docker-compose.yml`,
  `.env.example`, `.env` y el realm JSON, **y aplicado también al Keycloak vivo** por la Admin REST API
  (el JSON del realm solo se importa en un realm nuevo). Verificado con `curl -H "Origin: ..."`.
- **Registro en una sola pantalla.** Con C-08 el form pasó a 11 campos y en el card de 460px a dos columnas
  obligaba a scrollear. Ahora el card va a 720px y la grilla a **tres columnas**, con los campos reordenados
  para que las filas cierren completas (email ocupa dos celdas, el adjunto el ancho completo). Se agregó
  compactación por **alto de viewport** (`@media (max-height: 880px)`) en vez de dejar que aparezca scroll en
  notebooks, y breakpoints 3→2→1 columnas.
- **Alineación:** el `<select>` de condición fiscal usaba el padding base (`0.5rem`) y los inputs del auth
  `12px`, así que al lado quedaban de distinta altura; ahora inputs, selects y file inputs comparten caja.
  El `input[type=file]` tiene el botón nativo estilado como el resto.
- **Responsive del resto:** `overflow-x: hidden` global, la fila de producto (que ahora tiene 5 columnas por
  la miniatura y el "Más info") se apila en dos líneas abajo de 720px, los filtros del buscador pasan a ancho
  completo, y la grilla de integraciones a una columna.

**Pendiente:** verificación visual. Está todo verde por contrato pero no pude mirar las pantallas (no hay
herramientas de browser en esta sesión). Front en `:5174`, back en `:8088`.
**Impacto para el otro (Fran):** si levantás el front en un puerto distinto de 5173/5174, hay que agregarlo
en los tres lugares de arriba — está anotado en `.env.example`.
**Refs:** `frontend/src/pages/Registro.tsx`, `frontend/src/index.css`, `keycloak/realms/nutriapp-realm.json`,
`backend/src/main/resources/application.yml`, `docker-compose.yml`.

## 2026-08-03 — Santi — backend+db (maestro de artículos de TBC: C-10/C-11/C-12/C-13 + los 3 ajustes del mail de Gon)
**Qué:** Llegó el Excel maestro que faltaba desde la demo → **Ola 3 desbloqueada y hecha del lado backend**.
`mvn test` **141 unit** BUILD SUCCESS (+28). Migración `V009`. Análisis completo en
**`07-maestro-articulos-y-catalogo.md`** (documento nuevo, referenciado desde `CLAUDE.md` y desde el `06-`).

- **El archivo no se commitea.** De sus 126 columnas nutriapp usa 9; las otras traen costo, margen,
  comisión y precio por proveedor de 2225 artículos — la estructura de costos de TBC. Va al `.gitignore`
  igual que `presupuesto_nutriapp.pdf`. Y el importador **lee solo esas 9 y descarta el resto**: los costos
  nunca entran a la base.
- **C-12 importador** (`modules/producto/maestro/`): `POST /admin/productos/importar-maestro` multipart +
  `GET /admin/productos/maestro/estado`. Parser con `fastexcel-reader` (streaming, ~200 KB, contra los
  ~12 MB de deps de Apache POI para leer 9 columnas). **Busca las columnas por nombre, nunca por posición**:
  son 126 columnas de una planilla que el cliente edita a diario, y un parser posicional escribiría marcas
  en el campo de categoría sin que nadie se entere. Reporte con filas leídas/matcheadas/sin match/rechazadas.
- **Dos escritores, cero campos compartidos.** Contabilium es dueño de nombre/precio/stock/marca/rubro/
  tipo/código de barras; el maestro de departamento/categoría/subcategoría/laboratorio/descripción web/
  imagen/tags/bloqueo. Con esa partición **importar y sincronizar son conmutativos** — el admin no puede
  romper nada haciéndolo en el orden "equivocado". `publicado` dejó de ser un campo que alguien escribe y
  pasó a ser derivado (`PublicacionPolicy`, una sola definición para los dos procesos).
- **`categoria` cambió de dueño.** Guardaba el Rubro de Contabilium, que vale "Producto terminado" para el
  99,8 % del catálogo recetable: como filtro era decorativo. El rubro se mudó a su columna (`rubro`/`rubro_id`,
  ahora filtro de ingreso) y `categoria` pasó a ser la del maestro, con 23 valores reales.
- **C-10 buscador rankeado**: `q` matchea nombre + descripción + SKU + **código de barras** + **tags**, y
  ordena nombre → descripción → tag, que es literal lo que pidió Gon en la call (`29:15`). Los tags entran
  por `EXISTS` y no por `JOIN`: con JOIN, un producto con 20 tags que matchean salía 20 veces y rompía la
  paginación. **C-11 filtros** de departamento/categoría/subcategoría/laboratorio + `taxonomia` en cascada
  (142 subcategorías sueltas en un dropdown no las usa nadie).

**Problemas:**
1. **`Tipo` de Contabilium tiene TRES valores, no dos.** Gon pidió "solo quedarnos con Producto" pensando en
   sacar los servicios, pero el campo también vale `Combo` (209 artículos: los packs x2/x3 y los exhibidores
   de la línea ON-ROLL propia de TBC). Aplicado tal cual, **el catálogo recetable caía de 702 a 496 (−29 %)**.
   **Decidido con Santi: van los dos** (`CATALOGO_TIPOS_ERP=Producto,Combo`, que quedó como default en
   `application.yml`/compose/`.env.example`) → **699 recetables, 203 combos**. Igual hay que consultárselo a
   Gon; volver a su versión literal es cambiar el env var y re-sincronizar. Por eso `tipos-erp-permitidos`
   es una **lista** y no un valor único.
2. **Un `.xlsx` inválido salía como 500 "error interno"** en vez de un mensaje accionable: el `IOException`
   del zip se escapaba como `UncheckedIOException`. Lo encontró un test. Ahora 422 diciendo qué pasa.
   De paso quedó `UnprocessableException` + handler para archivos que el usuario puede arreglar.
3. **El default de multipart de Spring Boot es 1 MB** y el maestro pesa 1,5 MB: el import habría fallado
   siempre. Subido a 25 MB en el contenedor; el tope de negocio real lo pone `catalogo.import-max-bytes`.
4. **`mvn -q` me ocultó un BUILD FAILURE** al filtrar la salida con `Select-String`: di por compilado algo
   que no compilaba. Correr sin `-q` cuando se filtra el output.
5. **`RecetaFlowIT` estaba roto desde el 2026-07-28** (no por esto): tomaba `productoRepository.findAll().get(0)`,
   o sea el primer producto del seed de `V003`… que ese día se vació a propósito. Nadie corrió `mvn verify`
   desde entonces. Ahora el test **crea su propio producto** y no depende de ninguna migración de datos.
   `mvn verify` vuelve a estar verde: **141 unit + 1 IT**, y las 9 migraciones aplican limpias sobre una
   Postgres nueva (que es la validación real de `V009`, porque en la DB local corrió sobre datos existentes).

**Verificado e2e** contra el stack real y el archivo de verdad: import 2225 filas → **2163 aplicadas, 62 sin
match, 0 rechazadas** (coincide exacto con el análisis offline del Excel contra la DB), 26 despublicados por
`ESTADO=BLOQUEADO`, 8447 tags. Después corrí el sync de Contabilium y **no pisó un solo campo del maestro**.
Buscador: `q=magnesio` → 41 resultados con los de nombre arriba y los de solo-tag al final; `q=7798349830060`
→ ON-ROLL FLOW. Filtros: 4 departamentos / 14 categorías / 72 subcategorías / 35 laboratorios / 125 marcas.

**Hallazgo de alcance:** el presupuesto promete buscar "por principio activo, presentación, marca,
laboratorio". **Presentación no existe** en el maestro (lo más cercano tiene 12 % de cobertura) y **principio
activo tampoco**. Lo cubren los **tags**: el ejemplo que dio Gon en la call fue buscar "magnesio", y magnesio
está como tag en 39 artículos. Por eso C-10 se hizo con ranking y no como un OR suelto.

**Frontend (con autorización explícita del usuario, mismo criterio que C-06/C-07/C-09):** `tsc -b`, `oxlint`
y `vite build` verdes.
- **`MaestroImportCard`** en `/integraciones`, al lado del sync de Contabilium: examinar + importar + la
  última importación + el reporte. **Si hay filas sin match o rechazadas el toast sale como advertencia, no
  como éxito** — es el caso justo en el que un "importado correctamente" verde engaña. Los SKUs sin match se
  despliegan.
- **Buscador**: filtros nuevos de departamento/categoría/subcategoría/laboratorio **encadenados con el árbol
  de `/productos/filtros`** (sueltas, las subcategorías son 142 opciones y no las usa nadie), y cambiar un
  nivel limpia los de abajo. Placeholder del input ahora menciona el código de barras.
- **Miniatura + "Más info"**: la columna de la miniatura existe siempre, con placeholder cuando no hay imagen
  (solo el 24 % la tiene) para que las filas no queden desalineadas. El modal muestra imagen grande, SKU,
  código de barras, laboratorio, marca, categoría, el texto largo del maestro y los tags; **click en un tag
  filtra por ese tag**, que es la navegación "por propiedad" que el catálogo no tiene como campo.

**Verificación visual pendiente**: está todo verde por contrato (typecheck, lint, build, respuestas reales de
la API) pero nadie miró las pantallas. Front levantado en **:5174** (el 5173 lo ocupa imedba en esta máquina).

**Impacto para el otro (Fran):** **nada se rompe** — todo lo del contrato es aditivo. `ProductoResponse` suma
`codigoBarras`, `descripcionWeb`, `departamento`, `subcategoria`, `tags[]` (y `laboratorio` por fin tiene
datos); `ProductoFiltrosResponse` suma `departamentos`, `subcategorias`, `laboratorios` y `taxonomia`.
**Pero `categoria` cambió de significado**: antes traía "Producto terminado" para casi todo, ahora trae la
categoría real del maestro. `principioActivo` y `presentacion` siguen en el contrato pero son siempre `null`.
Toqué `frontend/`: `types/producto.ts`, `types/maestro.ts` (nuevo), `api/maestro.ts` (nuevo),
`components/admin/MaestroImportCard.tsx` (nuevo), `components/receta/ProductoBuscador.tsx`,
`pages/Integraciones.tsx`, `index.css`. Contrato actualizado en `05-api-endpoints.md`.
**Refs:** `V009__catalogo_maestro.sql`, `modules/producto/maestro/*`, `PublicacionPolicy`, `CatalogoProperties`,
`07-maestro-articulos-y-catalogo.md`.

## 2026-08-02 — Santi — backend+frontend (C-08 registro ampliado + C-17 foto de perfil) — CIERRA OLA 2
**Qué:** `mvn test` **113 unit** BUILD SUCCESS (+10); front `tsc`/`oxlint`/`build` verdes. Migración `V008`.

- **Subsistema de archivos** (lo comparten C-08 y C-17, por eso se hicieron juntos): tabla
  `nutricionista_archivos` + `ArchivoService`. **Los bytes van en la DB, no en un volumen**: son pocos y
  chicos (un PDF y una foto por persona) y así entran en el backup que ya existe (`scripts/backup-db.sh`)
  en vez de sumar una segunda cosa que acordarse de respaldar. Whitelist de content-type (no confiamos en
  la extensión), tope por tipo, y un archivo vigente por (nutricionista, tipo) — subir de nuevo reemplaza.
- **C-08 — registro ampliado.** `POST /registro` pasó a **multipart**: parte `datos` con el JSON y parte
  `matricula` con el PDF/imagen. Campos nuevos: **DNI** (con índice único parcial y guard de duplicados,
  que es justo para lo que Gon lo pidió), **CUIT** (normalizado sin guiones al guardar) y **condición
  fiscal**. El admin ve todo en la ficha y tiene **"Ver matrícula"**. El **CUIT ya sale en el exportable
  de C-06**, que era lo que le faltaba.
- **C-17 — foto de perfil.** `POST/DELETE /perfil/foto` + pantalla `/perfil`. La foto se **redimensiona en
  el server** a 256px de lado y se guarda en JPEG (una PNG de 400x400 y 220 KB quedó en 14 KB), y **viaja
  embebida como data URI en `/me`**: es un thumbnail de pocos KB y así el front pinta el avatar sin un
  segundo request autenticado ni manejar object URLs. Componente `Avatar` con fallback a iniciales.

**Problemas (dos, los dos reales):**
1. **`@Lob byte[]` no funciona contra `BYTEA` en Hibernate 6**: lo mapea a `oid` (large object) y tira
   *"column is of type bytea but expression is of type bigint"*. Va `@JdbcTypeCode(SqlTypes.VARBINARY)`.
2. **La compensación de Keycloak del registro no cubría fallas en el commit.** El error del punto 1 explotó
   al hacer flush **al cerrar la transacción**, o sea *fuera* del try/catch de `RegistroService` → el
   usuario de Keycloak quedó huérfano y bloqueando el email. Arreglado con `saveAndFlush` en
   `ArchivoService`: el INSERT revienta dentro del try y la compensación corre. **Vale como patrón**: en
   cualquier alta con compensación, forzar el flush antes de salir del bloque protegido.

**Verificado e2e** (script `verify_c08.sh`): registro multipart → PENDIENTE con DNI/CUIT/condición fiscal
guardados → el admin ve los datos y `tieneMatricula: true` → descarga el PDF **byte a byte idéntico** al
subido → una nutricionista pidiendo la matrícula de otra recibe **403** → foto 400x400 PNG de 220 KB queda
en JPEG de 14 KB → `/me` la trae como data URI → subir un PDF como foto da **409 con mensaje claro** →
CUIT presente en el consolidado. Validaciones del form: CUIT mal formado 400, DNI repetido 409.

**Nota de contrato para el front:** `api/client.ts` ahora detecta `FormData` y **no** le pisa el
`Content-Type` (si se lo seteás a mano, se rompe el boundary del multipart y el server no parsea nada).

**Impacto para el otro (Fran):** `Me` suma `foto` (data URI, opcional); `RegistroRequest` suma dni/cuit/
condicionFiscal y `registrar()` ahora pide el `File` de la matrícula; ruta `/perfil` nueva; componente
`Avatar` reemplaza al span de iniciales del navbar.

**Pendiente:** el campo `matricula` sigue llamándose así — Leo se llevó confirmar si va "matrícula
nacional" (call 42:34). Renombrarlo después es una migración de una línea.

**Refs:** `V008__nutricionista_datos_fiscales_y_archivos.sql`,
`modules/nutricionista/{entity/{NutricionistaArchivo,TipoArchivo},repository/NutricionistaArchivoRepository,service/ArchivoService,controller/PerfilController}`,
`modules/registro/**`, `common/web/MeController`, `frontend/src/{pages/{Perfil,Registro}.tsx,components/ui/Avatar.tsx,api/{perfil,registro,nutricionistas,client}.ts,types/{registro,session,nutricionista,cierre}.ts}`.

## 2026-08-02 — Santi — backend+frontend (C-06: cierre consolidado del admin con exportable)
**Qué:** Cierra el circuito de plata: conversión → comisión → liquidación → registro de que se pagó.
`mvn test` **103 unit** BUILD SUCCESS (+6); front `tsc`/`oxlint` verdes.

- **Backend:** `GET /api/v1/admin/liquidaciones/consolidado?desde&hasta` (`admin:manage`), una fila por
  nutricionista con actividad: recetas convertidas, facturado, comisión, y **cuánto queda impago** con los
  **ids de las recetas pendientes** para poder liquidar de un click contra el endpoint que ya existía.
  `CierreConsolidadoService` + `findConvertidasEntreTodas` (mismas reglas que el cierre individual: ventana por
  `ordenPaidAt` y cuenta APLICADA+LIQUIDADA).
  - **Rango configurable**, no mes calendario (Leo, 55:49). Fechas **inclusive de punta a punta** en hora
    argentina — "del 1 al 31" incluye todo el 31, que es donde este tipo de reportes suele perder un día.
  - **Sin paginar a propósito:** es una fila por nutricionista y el exportable tiene que salir completo, no la
    página que se esté mirando.
  - Guards: 409 si el rango está invertido, si falta una fecha o si supera 366 días (que nadie barra años de
    recetas de una); 403 para la nutricionista.
  - Ordena por **comisión pendiente descendente**: arriba a quien más hay que pagarle.
- **Frontend:** `/cierres` (solo admin) — rango con default al mes en curso, 4 tiles (convertidas, facturado,
  comisión, **a pagar**), tabla con botón "Liquidar N" por fila y confirmación que dice el monto, y
  **"Exportar CSV"**. Ícono `download` nuevo en el set.
- **El CSV está pensado para que Excel en español lo abra bien de una** (`lib/csv.ts`): separador `;` (con
  locale es-AR, la coma mete todo en una columna), **BOM UTF-8** (si no, se rompen acentos y ñ) y decimales con
  coma. Se genera en el front con los datos ya cargados: no vuelve a pegarle al backend y, como el endpoint no
  pagina, sale completo.

**Falta la columna CUIT** que Gon pidió explícitamente para el exportable (57:02): el campo no existe todavía
en `nutricionistas` — entra con **C-08**. Está anotado en el javadoc del DTO para que no se pierda.

**Verificado e2e:** receta nueva convertida a $8.000 → el consolidado muestra 3 recetas / $30.500 facturado /
$3.300 de comisión con **$2.050 pendientes en 2 recetas** → liquidar desde ahí devuelve "2 liquidadas por
$2.050" → el consolidado **mantiene el histórico** (3 recetas, $3.300) pero baja el pendiente a **0** →
reintento idempotente ("ya estaba liquidada"). Guards: 409 rango invertido, 403 como nutricionista.

**Impacto para el otro (Fran):** ruta `/cierres` + `types/cierre.ts` + `api/cierres.ts` + helper `lib/csv.ts`
reutilizable para cualquier otro exportable.

**Refs:** `modules/admin/{service/CierreConsolidadoService,dto/CierreConsolidadoResponse,controller/AdminLiquidacionController}`,
`modules/receta/repository/RecetaRepository`, `frontend/src/{pages/CierreConsolidado.tsx,api/cierres.ts,types/cierre.ts,lib/csv.ts,components/ui/Icon.tsx,App.tsx,components/layout/AppLayout.tsx}`.

## 2026-08-02 — Santi — frontend (C-09: bandeja de nutricionistas + mensajes de login traducidos)
**Qué:** Arranca la Ola 2. `tsc` + `oxlint` + `build` verdes. Backend sin cambios (los 4 endpoints ya existían).

- **C-09 — bandeja de nutricionistas** (`/nutricionistas`, sólo admin). Tres tabs: **Solicitudes pendientes /
  Aceptadas / Rechazadas** (la tercera no la pidieron, pero sin ella una rechazada desaparece de la vista y no
  hay forma de ver por qué se rechazó). La tabla muestra, por cada una, si el % es **override propio o el
  global** — el admin necesita ver cuál rige sin abrir la ficha. Desde la ficha (modal) se aprueba, se rechaza
  con motivo y se setean los % de C-01 en el mismo gesto: **vacío = usa el global**, así no hay que copiar el
  valor global en cada fila. Archivos nuevos: `types/nutricionista.ts`, `api/nutricionistas.ts`,
  `components/nutricionista/ParametrosModal.tsx`, `pages/Nutricionistas.tsx`, estilos `.tabs/.tab`.
- **La casa del admin pasó a ser `/nutricionistas`** (era `/configuracion`, interino mientras esto no existía):
  `lib/home.ts`, el brand de la navbar y el primer ítem del nav.
- **Mensajes de login traducidos** (era el ítem #1 de la auditoría de la pantalla de login, y lo destapó otra
  vez la verificación de este flujo): `lib/auth.ts` mapea `Account disabled` → *"Tu cuenta todavía no está
  habilitada: el administrador tiene que aprobar tu solicitud"*, más `Account temporarily disabled` (el realm
  tiene brute-force ON) e `Invalid client credentials`. Y **el error de red** ya no se ve como `Failed to
  fetch`: `fetch` sólo rechaza por red/CORS, así que se envuelve y sale *"No pudimos conectarnos con el
  servidor"*.

**Verificado e2e contra el stack real** (el flujo completo, no sólo las pantallas): registro público de
`ana.test@nutriapp.dev` → queda **PENDIENTE** → el login le da **"Account disabled"** (ahora traducido) →
aparece en la tab de pendientes con los % efectivos 15/10 heredados del global → el admin le setea **30% / 8%**
y la aprueba → queda **APROBADA con sus % propios** → **ahora sí puede loguearse**. Los contadores de las tabs
se mueven bien (pendientes 0, aprobadas 3).

**Dato de entorno:** quedó `ana.test@nutriapp.dev` / `test1234` en la DB y en Keycloak, creada por esta
verificación. Es una nutricionista APROBADA con 30%/8% — sirve para probar C-01 con dos perfiles distintos.
Si molesta para una demo, se borra de Keycloak + `nutricionistas`.

**Impacto para el otro (Fran):** ruta y pantalla nuevas + `lib/home.ts` decide el landing por rol. Si agregás
pantallas de admin, sumalas a `NAV_ADMIN` en `AppLayout` (no al array viejo, que ahora es `NAV_NUTRI`).

**Refs:** `frontend/src/{pages/Nutricionistas.tsx,components/nutricionista/ParametrosModal.tsx,api/nutricionistas.ts,types/nutricionista.ts,lib/{home,auth}.ts,App.tsx,components/layout/AppLayout.tsx,index.css}`.

## 2026-08-02 — Santi — backend+frontend (cierra Ola 1: C-07 admin sin recetas, C-02 sin precios)
**Qué:** Con Fran todavía de vacaciones, el usuario autorizó tocar `frontend/`. Cierra la Ola 1 del plan
`06-cambios-post-demo-2026-07-31.md`. Backend `mvn test` 97 unit BUILD SUCCESS; front `tsc` + `oxlint` + `build` verdes.

- **C-07 — el admin ya no emite recetas, de verdad.** No es esconder ítems del menú: el rol realm **ADMIN
  dejó de ser composite de `recetas:*`, `pacientes:*`, `productos:read` y `dashboard:read`** — queda sólo
  `admin:manage`. Aplicado en el `nutriapp-realm.json` **y** con `kcadm` sobre el Keycloak ya importado (si no,
  el cambio no entra hasta un re-import; misma lección que el service-account). Verificado: con token de admin,
  `/recetas`, `/pacientes`, `/dashboard/resumen` y `/productos` dan **403**, y `/admin/*` + `/me` siguen 200.
  La nutricionista quedó intacta.
  - Front: `NAV_ADMIN` (Configuración + Integraciones) vs `NAV_NUTRI`, CTA "Nueva receta" oculto para admin,
    guard `RequireRol` por ruta y `homeDe()` en `lib/home.ts` — cada rol arranca en su pantalla y el login
    redirige según rol (`login()` de `AuthContext` ahora devuelve el `Me` para poder decidir sin estado stale).
  - **Interino a mirar:** el admin queda con sólo dos pantallas y su "casa" es `/configuracion`, porque el
    cierre consolidado (C-06) y la bandeja de nutricionistas (C-09) todavía no existen en el front. Cuando
    esté C-09, la casa del admin debería pasar a ser la bandeja.
- **C-02 — precios sólo en la pantalla de emisión.** Backend: `RecetaResponse.Item` **ya no expone
  `precioLista`** (el snapshot se sigue guardando en `receta_items` para auditoría, pero no sale por la API).
  Front: sin importes en el detalle de receta, en la pantalla de éxito ni en la tabla del dashboard. En el
  dashboard la columna "Total" (que era una estimación con el precio de Contabilium) pasó a **"Venta"** con el
  monto real de TiendaNube, y "—" mientras no convierta. Se mantienen precios en el buscador y el carrito de
  emisión, con leyenda nueva: *"Valores aproximados. El precio final lo define la tienda…"* y el total pasó a
  llamarse **"Total estimado"**.
  - **Decisión discutible, marcada a propósito:** saqué los precios también de `RecetaExito` (la pantalla
    inmediatamente posterior a emitir). Se puede leer como parte de la emisión, pero Leo fue tajante con que no
    quede histórico con precios (53:35) y ahí ya la receta existe. Fácil de revertir si Gon lo pide.
  - **Lo que NO saqué:** el `producto` anidado del item sigue trayendo su `precio` **actual de catálogo**. No es
    el snapshot histórico y la nutricionista lo ve igual en el buscador, así que no contradice la regla.
- **C-05 en el front:** `EstadoReceta` suma `"LIQUIDADA"` (filtro del listado + badge propio, verde sólido), y
  el detalle muestra "Comisión liquidada el …" / "Comisión pendiente de liquidación".

**Pendiente de verificación:** todo lo anterior está verificado por contrato (API + typecheck + build), **no
visualmente**. Falta abrir las pantallas con los dos usuarios y mirar. Stack arriba: back `:8088`, front `:5173`.

**Impacto para el otro (Fran):** el contrato de `RecetaResponse.Item` **perdió** `precioLista` — cualquier
cálculo del front que dependiera de él ya no compila (revisé y ajusté los tres lugares que lo usaban).
`EstadoReceta` tiene un quinto valor. `AuthContext.login()` ahora devuelve `Promise<Me>` en vez de `Promise<void>`.

**Refs:** `keycloak/realms/nutriapp-realm.json`, `modules/receta/{dto/RecetaResponse,service/RecetaService}`,
`frontend/src/{App.tsx,components/layout/{AppLayout,RequireRol}.tsx,lib/home.ts,auth/AuthContext.tsx,pages/{Login,Dashboard,EmitirReceta,Recetas}.tsx,components/receta/{RecetaDetalle,RecetaExito}.tsx,types/receta.ts,index.css}`.

## 2026-08-01 — Santi — backend (Ola 1 post-demo: C-05 liquidación, C-04 cierre por fecha de pago, C-14 inactivos)
**Qué:** Primeros tres cambios del plan `06-cambios-post-demo-2026-07-31.md`. `mvn test` = **91 unit, BUILD SUCCESS**
(84 previos + 7 nuevos).
- **C-05 — estado terminal `LIQUIDADA`.** Migración `V006__receta_liquidada.sql` (nuevo valor en el CHECK de
  `estado` + columna `liquidada_at` + índice `(nutricionista_id, orden_paid_at)`). `EstadoReceta.LIQUIDADA` con
  helper `esConvertida()`. `LiquidacionService` (idempotente: lo ya liquidado se omite sin pisar la fecha) +
  `POST /api/v1/admin/liquidaciones` (`admin:manage`). La respuesta lleva `omitidas[]` con el **motivo** de cada
  receta que no se pudo liquidar — el admin tiene que ver qué quedó afuera, no un conteo mudo.
- **C-04 — el cierre agrupa por fecha de pago en TiendaNube.** Las agregaciones del dashboard y del cierre pasaron
  de ventanear por `aplicadaAt` a `ordenPaidAt`. En los datos actuales da igual (`aplicar()` ya seteaba
  `aplicadaAt = paidAt`), pero deja la regla explícita en vez de depender de esa coincidencia.
- **C-03 — verificado, ya estaba bien**: la comisión sale de `order.total()` (el total real de TiendaNube), nunca
  del precio de Contabilium. No hizo falta tocar nada.
- **C-14 — no se recetan inactivos de Contabilium.** `ProductoSyncService` ahora cruza el precio con el `Estado`
  del ERP. Defensivo a propósito: estado desconocido o nulo → se asume activo (preferimos publicar de más antes
  que vaciar el catálogo si Contabilium cambia el vocabulario).
- **Decisión de diseño (C-05):** las queries de cierre cuentan `APLICADA` **e** `LIQUIDADA`. Liquidar es haberle
  pagado a la nutricionista, no deshace la conversión: los cierres históricos tienen que seguir mostrando la
  receta, con `liquidadaAt` como marca de "ya cobraste esto". Lo que filtra sólo `APLICADA` es `findLiquidables`,
  que alimenta el cierre consolidado del admin (C-06, pendiente).

**Problemas (2 bugs preexistentes del working tree sin commitear, ninguno introducido por estos cambios):**
1. **NPE que tumbaba la sync entera de catálogo.** `ProductoSyncService.aplicar()` hacía
   `lk.rubros().get(c.idRubro())` sin chequear null, y el lookup puede ser un `Map` **inmutable** —
   `RubrosLookup.vacio()` es el fallback de `HttpContabiliumClient:106` cuando falla `/rubros`. `Map.of().get(null)`
   tira NPE, así que **un solo concepto sin rubro mataba el sync completo en live**. Arreglado chequeando los ids
   antes del lookup. Lo destaparon 5 tests que venían rotos en el working tree.
2. **`ProductoSyncServiceTest.sync_live_existenteSinCambios` desactualizado**: su fixture usaba precio $50, que
   bajo la regla de "precio irrisorio (<$100) = producto de baja" (trabajo del 28/07) despublica el producto y por
   lo tanto cuenta como cambio. Subido a $500, que es lo que el test quiso decir siempre.

**Impacto para el otro (Fran):** cambió el contrato en tres puntos, hay que espejar los types:
`RecetaResponse.Conversion` suma `liquidadaAt` (nullable), `CierreMensualResponse.Detalle` suma `liquidadaAt`
(nullable), y `estado` de receta ahora puede venir `"LIQUIDADA"` — el `EstadoReceta` del front tiene 4 valores y
necesita el quinto, y el filtro del listado debería ofrecerlo.

**Verificado e2e contra el stack real** (backend :8088, migración V006 aplicada según `flyway_schema_history`):
emitir → simular orden pagada ($12.500) → **APLICADA** con comisión 10% = $1.250 → liquidar como admin →
**LIQUIDADA** con `liquidadaAt` → reintento devuelve `liquidadas: 0, motivo: "ya estaba liquidada"` →
la receta **sigue apareciendo en el cierre mensual** de la nutricionista con su marca de pago →
`POST /admin/liquidaciones` con rol NUTRICIONISTA da **403**.

**C-01 también hecho (mismo día): % de descuento y comisión por nutricionista.** Migración `V007` (dos columnas
nullable con CHECK 0–100 en `nutricionistas`; **NULL = usá el global**, así no hay que backfillear ni duplicar la
config global en cada fila). `ParametrosNegocioService` es el **único** punto donde se resuelve override→global:
`RecetaService.emitir` (descuento) y el webhook (comisión) ya no leen `ConfiguracionService` directo — si alguien
vuelve a hacerlo se saltea el override y los cálculos quedan inconsistentes entre emisión y conversión.
`PUT /api/v1/admin/nutricionistas/{id}/parametros` (`admin:manage`), y la fila de la bandeja ahora expone el
override **y** el valor efectivo ya resuelto. **Ojo con el 0:** `0%` es un override válido (decisión del admin),
sólo `null` cae al global — hay test. Verificado e2e: sin override efectivo=15/10 → seteo 25/12,5 → receta nueva
snapshotea 25 → convertida a $10.000 comisiona 12,5% = $1.250 → reset a null vuelve a 15/10; 400 con 150%, 403 con
rol NUTRICIONISTA. **Nota de contrato:** el backend serializa sin nulls, así que los overrides sin setear llegan
como **campos ausentes**, no como `null`.

**Refs:** `V006__receta_liquidada.sql`, `V007__nutricionista_parametros.sql`,
`modules/configuracion/service/ParametrosNegocioService`, `modules/admin/{service/LiquidacionService,service/AdminNutricionistaService,controller/AdminLiquidacionController,controller/AdminNutricionistaController,dto/*}`,
`modules/receta/{entity/EstadoReceta,entity/Receta,repository/RecetaRepository,dto/RecetaResponse,service/RecetaService}`,
`modules/dashboard/{service/DashboardService,dto/CierreMensualResponse}`, `modules/producto/service/ProductoSyncService`.

## 2026-07-31 — Santi — docs (demo con el cliente: 16 cambios nuevos + segundo proyecto asomando)
**Qué:** Demo de la plataforma a Gon y Leo (call de 59 min, 2026-07-31 13:56). Tres entregables nuevos en
`instrucciones_claude/`:
1. **`transcripcion-2026-07-31-call-gon-leo.pdf`** (+ `.txt` para grepear) — transcripción cruda de Tactiq.
2. **`06-cambios-post-demo-2026-07-31.md`** — los 16 cambios (C-01…C-16) con timestamp de dónde se decidió cada uno,
   pendientes del cliente, preguntas abiertas y plan de ejecución en 4 olas.
3. **`nuevo-proyecto-tbc-insumos.pdf`** — insumos del *otro* proyecto que se habló en la misma call, para cotizarlo aparte.
   **Movido fuera de este repo** (2026-07-31): vive en `../../datawarehouse-contabilium/docs/`, como proyecto separado.
   **Alcance confirmado por el usuario el mismo día:** un **data warehouse de toda la data de Contabilium** (no solo
   productos — también ventas, comprobantes, clientes, stock, compras), con **sync automático una vez por día**, y el
   objetivo explícito de **dejar de pegarle a las APIs**. El PDF se rehízo con eso + el relevamiento completo de la API
   oficial de Contabilium (colección Postman): inventario de entidades extraíbles, restricciones duras, matemática de
   requests, arquitectura, fases, riesgos y las preguntas que faltan.

**Resultado de la demo:** les gustó ("espectacular"; la tipografía Comic Neue pasó el filtro de Leo, que la odiaba).
No hubo rechazos ni rehacer nada: la arquitectura y el modelo aguantan los 16 cambios.

**Los cambios que más pegan (detalle completo en el doc 06):**
- **C-03 — la comisión va sobre el total real de TiendaNube**, y los descuentos son **acumulativos**: el cupón de la
  receta (15%) se suma a la promo de la tienda (30%) → el paciente puede pagar 45% menos. Nada calculado con el precio
  de Contabilium sirve. Mientras la receta no convierta, la comisión es $0, **nunca** una estimación.
- **C-02 — los precios salen de casi toda la app.** Solo se ven en el buscador de la pantalla de emisión, con leyenda
  "valores aproximados, pueden cambiar sin previo aviso". Fuera del listado, del detalle y de lo que recibe el paciente.
  Racional de Leo: es una receta médica, y no quieren que la nutricionista se calcule la comisión con un número falso.
- **C-05/C-06 — aparece la liquidación:** estado terminal `LIQUIDADA` (una receta liquidada deja de salir en cierres
  siguientes; se liquida **por receta** aunque la pantalla sea mensual) + pantalla nueva de cierre consolidado del admin,
  con rango de fechas configurable, columnas CUIT/mail/facturado/comisionado/#recetas y **exportable a Excel**.
- **C-07 — el admin ya no emite recetas.** Se queda con Cierres, Nutricionistas, Integraciones y Configuración.
  Hay que separar permisos de verdad, no solo esconder ítems del menú (hoy el ADMIN tiene todas las authorities).
- **C-01 — % de descuento y de comisión por nutricionista** (nullable, fallback al global) + snapshot del % en la receta.
- **C-12 — ingesta del "maestro de artículos"** (Excel de OneDrive, cruza por SKU): es la fuente real de categoría,
  subcategoría, laboratorio, presentación y tags, que **Contabilium no tiene**. Carga **manual a demanda**, Gon fue
  explícito en que no quiere un job diario.

**Hallazgo de alcance:** los "buscadores por principio activo, presentación, marca, laboratorio" están **en el
presupuesto firmado**, y sin el Excel maestro no se pueden cumplir → C-10/C-11/C-12 entran sí o sí aunque no estuvieran
estimados. En cambio C-01, C-05, C-06 y C-08 **no están en el presupuesto**: son candidatos a negociar o a v1.1.

**Problemas:** la transcripción de Tactiq tiene un **hueco de ~9 minutos (04:05 → 12:57)**, justo el tramo donde Santi
habló con Gon a solas del proyecto nuevo, antes de que entrara Leo. Lo único que sobrevive de ahí es un link que Gon pegó
en el chat (`getStockBySKU`, stock por depósito). El tema se dedujo de las esquirlas del resto de la call y **el usuario
lo confirmó**; lo que sigue faltando es el **detalle** (cuánta historia hacia atrás, qué reportes, quién lo usa), que es
justo lo que más mueve el número.

**Datos de la API de Contabilium relevados hoy (sirven para nutriapp también):**
- **Rate limit real AR: 25 req/10s para toda la cuenta**, con **bloqueo por IP que afecta a TODOS los endpoints**
  (no solo al que se pasó) + cabecera `Retry-After`. `getStockByDeposito` tiene su propio límite de 30/10s.
- **`/api/stock/Novedades` (deltas) sigue siendo solo Chile y Uruguay** → confirmado que en AR no hay sync incremental
  para productos ni clientes: barrido completo. Comprobantes y órdenes sí filtran por rango de fechas.
- **`comprobantes/search` devuelve solo cabeceras**; las líneas requieren `GET /api/comprobantes/?id=` → **1 request por
  comprobante**. Es el costo dominante de cualquier carga histórica.
- **`getStockByDeposito`** (paginado por depósito) es mucho más barato que consultar SKU por SKU: ~322 req para los 7
  depósitos vs. 2266. Es la forma correcta de snapshotear stock a diario.
- **El detalle del comprobante trae `IDIntegracion` e `IDVentaIntegracion`** → la factura de Contabilium sabe de qué venta
  de TiendaNube vino. Sirve como **segunda vía para confirmar conversiones en nutriapp**, sin depender del webhook.
- `ordenesVenta/search` **no devuelve órdenes de integraciones** si no se pasa `IDIntegracion`.
- Proveedores solo se consultan **por ID**: no hay endpoint de listado.

**Impacto para el otro (Fran):** el frontend se lleva la mayor parte del trabajo de la Ola 1 y 2 — sacar precios de
listado/detalle/receta del paciente, menú separado por rol, pantalla nueva de cierre consolidado del admin con
exportable, tabs en la bandeja de nutricionistas y campos nuevos en el registro (DNI, celular, CUIT, condición fiscal,
matrícula + upload de archivo). Nada de eso lo toqué: ver el doc 06 antes de empezar.

**Refs:** `instrucciones_claude/06-cambios-post-demo-2026-07-31.md`, `../../datawarehouse-contabilium/`,
`transcripcion-2026-07-31-call-gon-leo.{pdf,txt}`, `presupuesto_nutriapp.pdf`.

## 2026-07-28 — Santi — backend+frontend (filtro de precio + regla "precio irrisorio (<$100) = producto de baja")
**Qué:** (1) **Filtro de precio** (precioMin/precioMax) en `GET /productos` + inputs "Precio desde/hasta" en el buscador
(con debounce). (2) **Regla de negocio:** un producto con `precioFinal < $100` se considera **dado de baja / inactivo**
→ el sync lo marca `publicado = false` (desaparece del catálogo; el buscador ya filtra `publicado = true`). Umbral
`UMBRAL_PRECIO_ACTIVO = 100` en `ProductoSyncService`.
- El sync ahora **sí** setea `publicado` (antes lo dejaba en true a propósito): `publicado = precioFinal >= 100`. Self-correcting:
  si el ERP corrige el precio, un re-sync lo republica.
- **Aplicado a los 2266 actuales por SQL** (`UPDATE productos SET publicado=false WHERE precio<100`) para que tenga efecto
  **ya** sin depender del DNS: **1538 marcados de baja** (placeholders de $1) → **728 productos activos** quedan visibles.
**Verificación:** filtro OK (sin filtro 728 · precioMin=20000→480 · 1000-5000→15). Backend compila (main+tests), front build verde.
**Impacto para Fran:** `GET /productos` gana params `precioMin`/`precioMax`; `ProductoQuery` type actualizado. Sin otros cambios de contrato.
**Nota:** el umbral $100 es constante; si Gon lo quiere configurable, se mueve al módulo `configuracion` (como descuento/comisión).
**Refs:** `modules/producto/{service/ProductoSyncService,repository/ProductoRepository,service/ProductoService,controller/ProductoController}.java`,
`frontend/src/components/receta/ProductoBuscador.tsx`, `types/producto.ts`, `index.css`.

## 2026-07-28 — Santi — auth (admin con acceso completo a la app como nutricionista)
**Qué:** El perfil ADMIN ahora tiene acceso completo a las funciones de nutricionista (emitir, pacientes, dashboard, recetas)
además de las de admin. **No hizo falta tocar el realm**: el rol `ADMIN` YA es composite e incluye todas las authorities de
nutri (`recetas:*`, `pacientes:*`, `productos:read`, `dashboard:read`) + `admin:manage`. **Ni el front**: el nav de `AppLayout`
ya muestra todo para admin (Panel/Recetas/Pacientes/Cierre + Configuración/Integraciones).
- **Único gap real:** `NutricionistaService.getCurrent()` resuelve el nutricionista por sub/email; `admin@nutriapp.dev` no tenía
  perfil de Nutricionista → emitir/pacientes/dashboard tiraban "no tiene perfil de nutricionista". **Fix:** el `DevDataSeeder`
  crea un perfil **APROBADO** para el admin (`ensureNutriAprobado`, idempotente), **antes** del guard del demo → se crea también
  en la DB ya seedeada al reiniciar el backend (no hace falta `down -v`, se preservan los 2266 productos).
- **Modelo:** el admin actúa como **su propio** nutricionista (pacientes/recetas propios, arranca vacío). NO ve la data de otros
  nutricionistas (eso sería otro feature). getCurrent linkea el sub por email en el primer acceso.
**Verificación (token admin):** `/me`, `/dashboard/resumen`, `/recetas`, `/pacientes` → 200; `/admin/integraciones/estado` → 200.
Perfil `admin@nutriapp.dev` APROBADA en la DB. Compila (main).
**Impacto para Fran:** ninguno en el contrato. El usuario admin ahora puede usar todas las pantallas de nutricionista con su propio espacio.
**Refs:** `config/DevDataSeeder.java` (ensureNutriAprobado + creación para admin). Realm y front sin cambios.

## 2026-07-28 — Santi — backend+frontend (mejoras del catálogo: filtros reales, paginador, sync async, multi-producto, footer Simple Apps)
**Qué:** Batch grande pedido por el usuario tras probar en vivo. Toca backend y `frontend/` (área de Fran, autorizado explícitamente).
**Análisis de la data de Contabilium (probe raw):** el concepto NO trae marca/laboratorio/presentación. **El Subrubro ES la marca**
(CENTRUM, ENA, GENTECH, NATIER, SUPRADYN… ~200 bajo el rubro "Producto terminado") y el **Rubro = categoría** (8: Producto terminado,
Insumos, Materias primas, Servicios, Gastos, Material PoP, General, Ficticios). ⚠️ El catálogo son los 2266 de **toda la farmacia**
(suplementos + golosinas + cosmética + higiene + pilas + insumos), no solo suplementos → los filtros importan.
- **Filtros (5):** `marca` ← Subrubro, `categoria` ← Rubro (mapeados en el sync vía `ContabiliumClient.rubrosLookup()`), toggle
  **"solo con stock"**, y texto (nombre/SKU/desc). Se sacaron laboratorio/presentación (no existen en Contabilium). Migración
  **`V005`** agrega `categoria` (se reusa `marca` para el subrubro). `ProductoRepository.search(q, marca, categoria, conStock)`,
  `ProductoFiltrosResponse{marcas, categorias}`, `ProductoResponse` gana `categoria`.
- **Paginador** en el `ProductoBuscador` (antes solo mostraba la página 1 → "solo 20"). Usa `PageResponse.{totalPages,first,last,totalElements}`.
- **Sync ASÍNCRONO:** `POST /admin/contabilium/sync-productos` ahora responde **202** y corre en background (`@Async` + `@EnableAsync`);
  `IntegracionesEstadoService`/DTO exponen `sincronizando` + `ultimoResultado`. El panel `/integraciones` togglea "iniciada" → pollinea el
  estado cada 3s → toast "Catálogo sincronizado. revisados=…". (Cierra el pedido de async + mensajes al usuario.)
- **Recetas multi-producto:** `RECETA_MAX_ITEMS` 1 → **10** (la UI del emisor YA soportaba N ítems; solo el backend lo capaba).
- **Footer "powered by `<s/a>`" (Simple Apps):** píldora al lado del copyright (logo navy `#092F70` en General Sans Bold vía Fontshare,
  resto con la paleta del sitio), link a simpleapps.com.ar. Se sumó Fontshare a la **CSP de prod** (`nginx/conf.d/nutriapp.conf`).
**⚠️ Pendiente para que los filtros tengan data:** hay que **re-sincronizar** (los 2266 actuales se cargaron sin categoria/marca;
el sync por SKU los actualiza y puebla). El POST del sync lo gatea el clasificador para mí → lo dispara el usuario desde el panel.
**Verificación:** backend compila (main + tests, image build) — arreglé `ProductoSyncServiceTest` (aridad de `Concepto` +1 idRubro/idSubrubro,
mock `rubrosLookup`) y `IntegracionesEstadoServiceTest` (dep nueva `ProductoSyncService`). Frontend `npm run build` (tsc+vite) verde.
Verificado en vivo: V005 aplicada, `/productos` pagina (454 págs), `/productos/filtros` = {marcas,categorias}. Falta el re-sync del usuario.
**Impacto para Fran:** contrato de productos cambió — `GET /productos` params `marca/categoria/conStock` (fuera laboratorio/principioActivo/presentacion),
`/productos/filtros` = {marcas,categorias}, `ProductoResponse.categoria` nuevo, `POST sync-productos` → 202 (no el resumen). `RecetaResponse`
sin cambios. Type espejo actualizado (`types/producto.ts`, `types/integraciones.ts`). Reescribí `ProductoBuscador` + `Integraciones` + `Footer`.
**Refs:** backend `modules/producto/**`, `integrations/contabilium/**`, `modules/admin/**`, `NutriappApplication`, `application.yml`,
`db/migration/V005__producto_categoria.sql`; frontend `components/receta/ProductoBuscador.tsx`, `pages/Integraciones.tsx`,
`components/layout/Footer.tsx`, `types/{producto,integraciones}.ts`, `api/{productos,integraciones}.ts`, `index.{html,css}`; `nginx/conf.d/nutriapp.conf`.

## 2026-07-28 — Santi — db/integraciones (catálogo SIN seed: ahora viene de la sync real de Contabilium; rebuild total)
**Qué:** Removí el seed de productos (`V003__seed_productos.sql` → no-op). El catálogo arranca **vacío** y se puebla con
la **sync real de Contabilium** (`POST /admin/contabilium/sync-productos` / botón "Sincronizar catálogo" del panel
`/integraciones`). Más fiel a la regla de oro: el catálogo es el del ERP real, no 12 suplementos ficticios.
- `DevDataSeeder` ya contemplaba el catálogo vacío (guard `productos.isEmpty()`): crea nutri demo + 4 pacientes y
  **saltea las 6 recetas demo** (dependían de productos). **Sin cambios en el seeder.**
- **`CONTABILIUM_MODE=live` persistido en `.env`** (antes era override transitorio) para que el botón del front funcione
  siempre. Machine-local (`.env` gitignored) — no afecta a Fran ni a prod (prod usa `application-prod.yml`, fail-closed).
  No hay job scheduled de Contabilium → live-by-default no golpea prod solo.
- **Rebuild total** (`docker compose down -v` + `up --build`): DB fresca con V003 vacío → **0 productos** (verificado por
  `select count(*)`), estado integraciones: contabilium `modo=live`, resto stub. (Hipo transitorio de DNS a Docker Hub en
  el 1er `up --build`; reintento OK.)
**Cómo probar (end-user):** login admin `admin@nutriapp.dev`/`test1234` → panel Integraciones → "Sincronizar catálogo"
→ ~2266 productos reales; después, como nutri, el emisor ya los ve. (El POST del sync lo gatea el clasificador de auto-mode
para mí; desde el navegador del usuario anda normal.)
**Impacto para Fran:** en entorno limpio el catálogo arranca **vacío** hasta sincronizar Contabilium; las recetas demo del
dashboard ya no se seedean (dependían de los productos ficticios).
**Refs:** `db/migration/V003__seed_productos.sql`, `.env` (local, gitignored), `config/DevDataSeeder.java` (sin cambios).

## 2026-07-28 — Santi — planificación/integraciones (decisión: WhatsApp por link wa.me, NO Cloud API — sacar la integración real)
**Qué:** Decisión del usuario/cliente: el envío por WhatsApp se hace con un **link `wa.me`** que el nutricionista toca
para mandar el mensaje él mismo desde su WhatsApp — **NO** se usa la WhatsApp Cloud API automática (patrón "WhatsApp
SIEMPRE manual" de imedba, opción 3 del §4 de `03-integraciones-apis.md`). Baja el costo y saca la dependencia del
WABA + aprobación de template de Meta.
**Qué hay que SACAR (cuando haya tiempo — anotado, NO urgente, NO se tocó código todavía):**
- `integrations/whatsapp/` completo (port `WhatsAppSender` + `CloudApiWhatsAppSender` + `StubWhatsAppSender`) y su
  config `WHATSAPP_*` (`application.yml`, `.env.example`, `docker-compose.yml`) + el test `CloudApiWhatsAppSenderTest`.
- El canal `CanalNotificacion.WHATSAPP` de la cola: `RecetaService.emitir` deja de encolar la notif WHATSAPP y el
  `NotificacionDispatcher` pierde su rama WhatsApp. **Email sigue igual** (canal automático real).
- El proveedor "whatsapp" del `GET /admin/integraciones/estado` (ya no es una integración).
**Qué hay que AGREGAR:**
- `waMeUrl` en `RecetaResponse` (o computarlo en el front desde `paciente.telefono` + `codigo`):
  `https://wa.me/<tel_e164_sin_+>?text=<mensaje url-encoded con código + link tienda + vencimiento>`. Front: botón
  "Enviar por WhatsApp" en la pantalla de receta emitida. Reusar el texto del template WhatsApp actual (`NotificacionTemplates`).
**Impacto para Fran:** al implementarse, `RecetaResponse` gana `waMeUrl` + botón en "Receta emitida"; el detalle de receta
ya no listará una notif WHATSAPP (solo EMAIL). Se coordina cuando se encare.
**Refs:** a tocar `integrations/whatsapp/**`, `modules/notificacion/**`, `RecetaService`, `RecetaResponse`, `application.yml`,
`03-integraciones-apis.md §4`, plan 2.4. Estado: **sólo anotado.**

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
