#!/usr/bin/env bash
# Aplica al realm YA EXISTENTE la config que el import del realm JSON no puede cambiar.
#
# `--import-realm` sólo corre la primera vez: en un entorno que ya arrancó, editar
# keycloak/realms/bonosapp-realm.json no tiene ningún efecto. Esto cierra esa brecha y es
# idempotente, así que se puede correr en cada deploy.
#
# Qué aplica:
#   S-08 — protección de fuerza bruta: bloqueo temporal tras 10 intentos fallidos.
#   S-09 — SMTP del realm, sin el cual el mail de "olvidé mi contraseña" no sale.
#
# Por qué la REST API y no kcadm: kcadm ignora en silencio `-s smtpServer={...}` (lo manda como
# string) y con `-f` falla con unknown_error. Un PUT parcial al endpoint del realm mergea bien y
# devuelve un código HTTP verificable, que es lo que queremos de un paso de deploy.
#
# Por qué el curl va en un contenedor: la imagen de Keycloak no trae curl ni python, y en prod
# Keycloak NO publica puerto (sólo nginx está expuesto). Engancharse a su namespace de red hace
# que `localhost:8080` sea Keycloak sin abrir nada ni depender del nombre de la red.
#
# Uso:  bash scripts/keycloak-config.sh            (aplica)
#       bash scripts/keycloak-config.sh --dry-run  (muestra lo que haría)
# Env:  KEYCLOAK_ADMIN, KEYCLOAK_ADMIN_PASSWORD y los MAIL_* del .env.
#       COMPOSE_FILES para apuntar a otro stack (dev: "-f docker-compose.yml").
set -euo pipefail

export MSYS_NO_PATHCONV=1

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

DRY_RUN=0
[ "${1:-}" = "--dry-run" ] && DRY_RUN=1

# shellcheck source=scripts/lib-env.sh
. "${REPO_ROOT}/scripts/lib-env.sh"
cargar_env "${REPO_ROOT}/.env"

REALM="${KEYCLOAK_REALM:-bonosapp}"
KC_ADMIN="${KEYCLOAK_ADMIN:-}"
KC_PASS="${KEYCLOAK_ADMIN_PASSWORD:-}"
COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
CURL_IMAGE="${CURL_IMAGE:-curlimages/curl:8.11.1}"

# Fail-closed: sin credenciales el script no puede verificar nada y terminaría diciendo "listo"
# sobre un realm que quedó igual.
if [ -z "${KC_ADMIN}" ] || [ -z "${KC_PASS}" ]; then
  echo "ERROR: faltan KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD (van en .env)." >&2
  exit 1
fi

KC_ID="$(docker compose ${COMPOSE_FILES} ps -q keycloak || true)"
if [ -z "${KC_ID}" ]; then
  echo "ERROR: el contenedor de keycloak no está corriendo (COMPOSE_FILES='${COMPOSE_FILES}')." >&2
  exit 1
fi

# `-i`: sin stdin conectado, el payload del PUT no llega y Keycloak responde 500 por body vacío.
kccurl() { docker run --rm -i --network "container:${KC_ID}" "${CURL_IMAGE}" -sS "$@"; }

# Escapa un valor para meterlo en un string JSON armado a mano.
json_esc() { printf '%s' "${1:-}" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }

# El path relativo lo fija el compose (prod: /auth; dev: ninguno), así que se detecta.
BASE=""
for candidato in "/auth" ""; do
  if kccurl -o /dev/null -w '%{http_code}' \
       "http://localhost:8080${candidato}/realms/master/.well-known/openid-configuration" \
       2>/dev/null | grep -q '^200$'; then
    BASE="${candidato}"
    break
  fi
done
if [ -z "${BASE}" ] && ! kccurl -o /dev/null -w '%{http_code}' \
     "http://localhost:8080/realms/master/.well-known/openid-configuration" 2>/dev/null | grep -q '^200$'; then
  echo "ERROR: Keycloak no responde en el contenedor. ¿Terminó de arrancar?" >&2
  exit 1
fi
KC_URL="http://localhost:8080${BASE}"
echo "==> Keycloak en ${KC_URL}"

TOKEN="$(kccurl -X POST "${KC_URL}/realms/master/protocol/openid-connect/token" \
  --data-urlencode "client_id=admin-cli" \
  --data-urlencode "username=${KC_ADMIN}" \
  --data-urlencode "password=${KC_PASS}" \
  --data-urlencode "grant_type=password" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')"
if [ -z "${TOKEN}" ]; then
  echo "ERROR: no se pudo obtener el token de admin. ¿Las credenciales son las de este entorno?" >&2
  exit 1
fi

# --- S-08: fuerza bruta ---------------------------------------------------------------
# failureFactor=10 es el pedido del cliente. permanentLockout=false a propósito: con bloqueo
# permanente, cualquiera que sepa el mail de una profesional le deja la cuenta muerta hasta que
# un admin la desbloquee a mano. La espera creciente frena el ataque sin regalar ese poder.
PAYLOAD="$(printf '{"realm":"%s","bruteForceProtected":true,"failureFactor":10,"permanentLockout":false,"waitIncrementSeconds":60,"maxFailureWaitSeconds":900,"maxDeltaTimeSeconds":43200,"quickLoginCheckMilliSeconds":1000,"minimumQuickLoginWaitSeconds":60,"resetPasswordAllowed":true' "$(json_esc "${REALM}")")"

# --- S-09: SMTP del realm -------------------------------------------------------------
# Mismas credenciales que usa la app (MAIL_*): una sola cuenta de envío y un solo dominio
# verificado. El `from` tiene que ser de ese dominio o el proveedor rechaza el envío.
if [ "${MAIL_MODE:-stub}" = "live" ] && [ -n "${MAIL_SMTP_HOST:-}" ]; then
  : "${MAIL_FROM_ADDRESS:?MAIL_FROM_ADDRESS es obligatorio con MAIL_MODE=live}"
  PAYLOAD="${PAYLOAD}$(printf ',"smtpServer":{"host":"%s","port":"%s","from":"%s","fromDisplayName":"%s","auth":"%s","starttls":"%s","ssl":"false","user":"%s","password":"%s"}' \
    "$(json_esc "${MAIL_SMTP_HOST}")" \
    "$(json_esc "${MAIL_SMTP_PORT:-587}")" \
    "$(json_esc "${MAIL_FROM_ADDRESS}")" \
    "$(json_esc "${MAIL_FROM_NAME:-BonosApp}")" \
    "$(json_esc "${MAIL_SMTP_AUTH:-true}")" \
    "$(json_esc "${MAIL_SMTP_STARTTLS:-true}")" \
    "$(json_esc "${MAIL_SMTP_USERNAME:-}")" \
    "$(json_esc "${MAIL_SMTP_PASSWORD:-}")")"
else
  echo "    (MAIL_MODE != live o sin MAIL_SMTP_HOST: NO se toca el SMTP del realm)"
  echo "    OJO: sin SMTP el mail de recupero no sale, y el endpoint responde 204 igual."
fi
PAYLOAD="${PAYLOAD}}"

oculta_pass() { sed 's/"password":"[^"]*"/"password":"***"/g'; }

if [ "${DRY_RUN}" = "1" ]; then
  echo "==> DRY RUN — PUT ${KC_URL}/admin/realms/${REALM} con:"
  printf '%s\n' "${PAYLOAD}" | oculta_pass
  exit 0
fi

echo "==> PUT /admin/realms/${REALM}"
CODE="$(printf '%s' "${PAYLOAD}" | kccurl -o /dev/null -w '%{http_code}' \
  -X PUT "${KC_URL}/admin/realms/${REALM}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  --data-binary @-)"
if [ "${CODE}" != "204" ] && [ "${CODE}" != "200" ]; then
  echo "ERROR: Keycloak respondió ${CODE} al actualizar el realm." >&2
  exit 1
fi

echo "==> Verificando lo que quedó guardado"
kccurl "${KC_URL}/admin/realms/${REALM}" -H "Authorization: Bearer ${TOKEN}" \
  | tr ',' '\n' \
  | grep -E '"(bruteForceProtected|failureFactor|permanentLockout|waitIncrementSeconds|maxFailureWaitSeconds|resetPasswordAllowed)"' \
  | sed 's/^{//'
kccurl "${KC_URL}/admin/realms/${REALM}" -H "Authorization: Bearer ${TOKEN}" \
  | sed -n 's/.*\("smtpServer":{[^}]*}\).*/\1/p' | oculta_pass

echo "==> Listo."
