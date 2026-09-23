#!/usr/bin/env bash
# Deploy de prod en un comando, fail-closed: si algo no cuadra, frena y dice dónde quedó el backup.
set -euo pipefail
export MSYS_NO_PATHCONV=1

usage() {
  cat <<'EOF'
Uso: bash scripts/deploy.sh [--dry-run] [--yes]

  1. Preflight: árbol limpio en main, stack arriba, variables obligatorias en .env.
  2. Ajusta el .env (con copia previa): TIENDANUBE_STORE_URL y CATALOGO_TIPOS_ERP.
  3. Backup de las dos bases, VERIFICADO con pg_restore -l.
  4. Censo de filas de las tablas que ya existen.
  5. Compila la SPA y levanta backend + nginx (Flyway corre las migraciones al arrancar).
  6. Espera el health y confirma la versión de Flyway.
  7. Recenso: si alguna tabla perdió filas, aborta y señala el dump (sumar filas es tráfico, no error).
  8. Config del realm (fuerza bruta, SMTP, verificación de mail con backfill previo).
  9. Smoke: /actuator/health y /terminos.

  --dry-run  hace 1, 3 y 4 y muestra el resto sin tocar nada.
  --yes      no pide confirmación.
EOF
}

DRY_RUN=0
ASSUME_YES=0
for arg in "$@"; do
  case "${arg}" in
    --dry-run) DRY_RUN=1 ;;
    --yes) ASSUME_YES=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Argumento desconocido: ${arg}" >&2; usage; exit 1 ;;
  esac
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"
# shellcheck source=scripts/lib-env.sh
. "${REPO_ROOT}/scripts/lib-env.sh"
cargar_env "${REPO_ROOT}/.env"

COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
PGUSER="${POSTGRES_USER:-bonosapp}"
APPDB="${POSTGRES_DB:-bonosapp}"
TIENDA_URL_ESPERADA="https://www.thebcompany.com.ar"
FLYWAY_ESPERADA="016"
TABLAS_CENSO="nutricionistas pacientes recetas receta_items productos notificaciones webhook_events"

dc() { docker compose ${COMPOSE_FILES} "$@"; }
die() { echo "ABORTA: $*" >&2; exit 1; }
paso() { echo; echo "== $* =="; }

paso "1/9 preflight"
[ -f "${REPO_ROOT}/.env" ] || die "no hay .env en ${REPO_ROOT}."
# Sin `git -C <ruta>`: con MSYS_NO_PATHCONV la ruta /c/... no llega convertida y git no la encuentra.
if command -v git >/dev/null 2>&1 && [ -d .git ]; then
  rama="$(git rev-parse --abbrev-ref HEAD)" || die "no se pudo leer la rama actual."
  [ "${rama}" = "main" ] || die "estás en la rama '${rama}', no en main."
  [ -z "$(git status --porcelain --untracked-files=no)" ] \
    || die "hay cambios sin commitear: se desplegaría algo que no está en el repo."
fi
dc ps db >/dev/null 2>&1 || die "el contenedor 'db' no responde. Levantalo: docker compose ${COMPOSE_FILES} up -d db"
for var in KEYCLOAK_HOSTNAME KEYCLOAK_ADMIN KEYCLOAK_ADMIN_PASSWORD POSTGRES_PASSWORD APP_PUBLIC_URL; do
  [ -n "${!var:-}" ] || die "falta ${var} en el .env."
done
case "${KEYCLOAK_HOSTNAME}" in
  */auth) ;;
  *) die "KEYCLOAK_HOSTNAME tiene que terminar en /auth (es ${KEYCLOAK_HOSTNAME}); sin eso el issuer sale sin prefijo y el login se rompe." ;;
esac
echo "   main limpio, db arriba, .env completo."

paso "2/9 ajustes del .env"
CAMBIOS=()
[ "${TIENDANUBE_STORE_URL:-}" = "${TIENDA_URL_ESPERADA}" ] || CAMBIOS+=("TIENDANUBE_STORE_URL=${TIENDA_URL_ESPERADA}")
[ "${CATALOGO_TIPOS_ERP:-}" = "Producto" ] || CAMBIOS+=("CATALOGO_TIPOS_ERP=Producto")
if [ "${#CAMBIOS[@]}" -eq 0 ]; then
  echo "   ya estaba todo."
else
  printf '   a cambiar: %s\n' "${CAMBIOS[@]}"
fi

if [ "${DRY_RUN}" != "1" ] && [ "${ASSUME_YES}" != "1" ]; then
  read -r -p "Continuar? Se reinician backend y nginx (unos minutos de corte). [s/N] " ok
  case "${ok}" in s|S|y|Y) ;; *) echo "Cancelado."; exit 1 ;; esac
fi

paso "3/9 backup verificado"
STAMP_ANTES="$(date +%s)"
bash "${REPO_ROOT}/scripts/backup-db.sh"
verificar_dump() {
  local db="$1" dump in rc=0
  dump="$(ls -1t "${REPO_ROOT}/backups/${db}-"*.dump "${REPO_ROOT}/backups/${db}-"*.dump.gpg 2>/dev/null | head -1 || true)"
  [ -n "${dump}" ] || die "el backup no dejó ningún dump de '${db}'."
  [ "$(stat -c %Y "${dump}" 2>/dev/null || date +%s)" -ge "${STAMP_ANTES}" ] || die "el dump más nuevo de '${db}' es viejo: el backup de ahora no se escribió."
  [ -s "${dump}" ] || die "el dump ${dump} quedó vacío."
  in="/tmp/deploy-verify-$$-${db}.dump"
  if [ "${dump##*.}" = "gpg" ]; then
    gpg --batch --quiet --decrypt "${dump}" 2>/dev/null | dc exec -T db sh -c "cat > ${in}" \
      || die "no se pudo descifrar ${dump}. No se tocó nada."
  else
    dc exec -T db sh -c "cat > ${in}" < "${dump}" || die "no se pudo copiar ${dump} al contenedor. No se tocó nada."
  fi
  dc exec -T db pg_restore -l "${in}" >/dev/null 2>&1 || rc=1
  dc exec -T db rm -f "${in}" >/dev/null 2>&1 || true
  [ "${rc}" = "0" ] || die "el dump ${dump} no pasa pg_restore -l (truncado o corrupto). No se tocó nada."
  echo "   ${db}: ${dump}"
  printf -v "DUMP_${db//-/_}" '%s' "${dump}"
}
verificar_dump "${APPDB}"
verificar_dump keycloak

paso "4/9 censo antes"
censo() {
  local sql="" t
  for t in ${TABLAS_CENSO}; do
    sql="${sql}${sql:+ UNION ALL }SELECT '${t}', count(*) FROM ${t}"
  done
  dc exec -T db psql -U "${PGUSER}" -d "${APPDB}" -tAc "SELECT string_agg(t || '=' || c, ',' ORDER BY t) FROM (${sql}) s(t, c);"
}
CENSO_ANTES="$(censo)"
echo "   ${CENSO_ANTES}"

if [ "${DRY_RUN}" = "1" ]; then
  paso "DRY RUN: no se sigue"
  echo "   Backup verificado y censo tomado. Lo siguiente sería:"
  echo "   - aplicar al .env: ${CAMBIOS[*]:-(nada)}"
  echo "   - compilar la SPA y levantar backend + nginx; Flyway tiene que quedar en v${FLYWAY_ESPERADA}"
  echo "   - recensar, correr scripts/keycloak-config.sh y el smoke"
  exit 0
fi

if [ "${#CAMBIOS[@]}" -gt 0 ]; then
  cp "${REPO_ROOT}/.env" "${REPO_ROOT}/.env.bak-deploy-$(date +%Y%m%d-%H%M%S)"
  for c in "${CAMBIOS[@]}"; do
    clave="${c%%=*}"
    if grep -q "^${clave}=" "${REPO_ROOT}/.env"; then
      sed -i "s|^${clave}=.*|${c}|" "${REPO_ROOT}/.env"
    else
      echo "${c}" >> "${REPO_ROOT}/.env"
    fi
    export "${c}"
  done
  echo "   .env actualizado (copia en .env.bak-deploy-*)."
fi

paso "5/9 SPA + backend + nginx"
docker run --rm -v "${REPO_ROOT}/frontend:/app" -w /app \
  -e VITE_API_BASE_URL="${APP_PUBLIC_URL%/}" \
  -e VITE_KEYCLOAK_URL="${KEYCLOAK_HOSTNAME}" \
  -e VITE_KEYCLOAK_REALM="${KEYCLOAK_REALM:-bonosapp}" \
  -e VITE_KEYCLOAK_CLIENT_ID="${KEYCLOAK_FRONTEND_CLIENT_ID:-bonosapp-frontend}" \
  -e VITE_COMING_SOON="${VITE_COMING_SOON:-false}" \
  -e VITE_CONTACTO_EMAIL="${VITE_CONTACTO_EMAIL:-info@bonosapp.com.ar}" \
  node:22-alpine sh -c 'npm ci --no-audit --no-fund && npm run build' \
  || die "falló el build de la SPA. El sitio sigue con la versión anterior; no se reinició nada."
dc up -d --build backend nginx

paso "6/9 health + Flyway"
for i in $(seq 1 90); do
  estado="$(dc exec -T backend wget -qO- http://localhost:8080/actuator/health 2>/dev/null || true)"
  case "${estado}" in *'"UP"'*) break ;; esac
  sleep 2
  [ "${i}" -lt 90 ] || die "el backend no levantó en 3 minutos: dc logs backend. Backup: ${DUMP_bonosapp:-backups/}"
done
version="$(dc exec -T db psql -U "${PGUSER}" -d "${APPDB}" -tAc "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1" | tr -d '[:space:]')"
[ "${version}" = "${FLYWAY_ESPERADA}" ] || die "Flyway quedó en v${version}, se esperaba v${FLYWAY_ESPERADA}. Backup: ${DUMP_bonosapp:-backups/}"
echo "   backend UP, Flyway en v${version}."

paso "7/9 recenso"
CENSO_DESPUES="$(censo)"
# No se compara por igualdad: la app sigue recibiendo tráfico y puede sumar filas en el medio.
declare -A ANTES=()
IFS=',' read -ra pares <<< "${CENSO_ANTES}"
for par in "${pares[@]}"; do ANTES["${par%%=*}"]="${par#*=}"; done
IFS=',' read -ra pares <<< "${CENSO_DESPUES}"
for par in "${pares[@]}"; do
  tabla="${par%%=*}"; filas="${par#*=}"
  [ "${filas}" -ge "${ANTES[${tabla}]:-0}" ] \
    || die "la tabla ${tabla} perdió filas (${ANTES[${tabla}]} -> ${filas}). El estado previo está en ${DUMP_bonosapp:-backups/}"
done
echo "   ninguna tabla perdió filas: ${CENSO_DESPUES}"

paso "8/9 realm de Keycloak"
COMPOSE_FILES="${COMPOSE_FILES}" bash "${REPO_ROOT}/scripts/keycloak-config.sh" \
  || die "falló la config del realm. La app ya está arriba con el código nuevo; correr scripts/keycloak-config.sh a mano."

paso "9/9 smoke"
codigo() { dc exec -T nginx wget -S -qO /dev/null "http://localhost$1" 2>&1 | awk '/HTTP\//{print $2}' | tail -1; }
[ "$(codigo /terminos)" = "200" ] || die "/terminos no responde 200: revisar el montaje de ./static en nginx."
echo "   /terminos 200."

cat <<EOF

Deploy OK. Backups: ${DUMP_bonosapp:-?} y ${DUMP_keycloak:-?}

Falta, desde el panel del admin (necesitan sesión, el script no se loguea por vos):
  1. Integraciones → "Sincronizar productos"   (aplica CATALOGO_TIPOS_ERP=Producto)
  2. Integraciones → "Mapear productos"        (trae handle y foto de cada producto)
Y a mano, una vez: pedir un recupero de contraseña y abrir el link del mail, para confirmar
que KEYCLOAK_HOSTNAME arma bien el enlace.
EOF
