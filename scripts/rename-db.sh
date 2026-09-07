#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
rename-db.sh — renombra la base (y el rol) de Postgres del rename NutriApp -> BonosApp.

  bash scripts/rename-db.sh [--from nutriapp] [--to bonosapp] [--no-role] [--yes]

Hace, en orden y frenando ante el primer problema:
  1. Preflight: la base vieja existe, la nueva no, y el rol se puede renombrar sin romper la clave.
  2. Backup con scripts/backup-db.sh y VERIFICACION del dump (pg_restore -l), no solo que exista.
  3. Censo de filas por tabla, para comparar despues.
  4. Baja backend/keycloak/nginx (la base queda arriba) y corta conexiones residuales.
  5. ALTER DATABASE + ALTER ROLE.
  6. Reverifica el censo contra la base nueva. Si no coincide, aborta ruidosamente.
  7. Actualiza POSTGRES_DB/POSTGRES_USER en .env (deja copia .env.bak-<stamp>).

Es idempotente: si la base ya se llama como el destino, sale OK sin tocar nada.
Al terminar, levantar con:  docker compose $COMPOSE_FILES up -d
EOF
}

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

FROM_DB="nutriapp"; TO_DB="bonosapp"; RENAME_ROLE=1; ASSUME_YES=0
while [ $# -gt 0 ]; do
  case "$1" in
    --from) FROM_DB="$2"; shift 2 ;;
    --to) TO_DB="$2"; shift 2 ;;
    --no-role) RENAME_ROLE=0; shift ;;
    --yes|-y) ASSUME_YES=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opcion desconocida: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [ -f "${REPO_ROOT}/.env" ]; then set -a; . "${REPO_ROOT}/.env"; set +a; fi
COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
SUPERUSER="${POSTGRES_USER:-${FROM_DB}}"
STAMP="$(date +%Y%m%d-%H%M%S)"

dc() { docker compose ${COMPOSE_FILES} "$@"; }
# maintenance_db: no se puede renombrar una base estando conectado A ELLA.
psqlq() { dc exec -T db psql -U "${SUPERUSER}" -d postgres -tAc "$1"; }
die() { echo "ABORTA: $*" >&2; exit 1; }

echo "== 1/7 preflight =="
dc ps db >/dev/null 2>&1 || die "el contenedor 'db' no responde. Levantalo antes: docker compose ${COMPOSE_FILES} up -d db"

has_from="$(psqlq "SELECT 1 FROM pg_database WHERE datname='${FROM_DB}'" || true)"
has_to="$(psqlq "SELECT 1 FROM pg_database WHERE datname='${TO_DB}'" || true)"
if [ "${has_from}" != "1" ] && [ "${has_to}" = "1" ]; then
  echo "La base ya se llama '${TO_DB}' y '${FROM_DB}' no existe: nada que hacer."; exit 0
fi
[ "${has_from}" = "1" ] || die "no existe la base '${FROM_DB}'."
[ "${has_to}" != "1" ] || die "ya existe una base '${TO_DB}' ademas de '${FROM_DB}'. Resolvelo a mano: renombrar encima perderia datos."

# Un hash md5 incluye el nombre de usuario: renombrar el rol invalidaria la contrasena.
if [ "${RENAME_ROLE}" = "1" ]; then
  enc="$(psqlq "SELECT rolpassword LIKE 'md5%' FROM pg_authid WHERE rolname='${SUPERUSER}'" || echo "")"
  [ "${enc}" != "t" ] || die "el rol '${SUPERUSER}' tiene la clave hasheada en md5: renombrarlo la rompe. Volve a correr con --no-role, o migra a scram-sha-256 primero."
fi

echo "   base '${FROM_DB}' presente, '${TO_DB}' libre, rol '${SUPERUSER}' renombrable."
if [ "${ASSUME_YES}" != "1" ]; then
  read -r -p "Continuar? Se baja el stack (la app queda caida unos minutos). [s/N] " ok
  case "${ok}" in s|S|y|Y) ;; *) echo "Cancelado."; exit 1 ;; esac
fi

echo "== 2/7 backup verificado =="
before_backups="$(ls -1 "${REPO_ROOT}/backups" 2>/dev/null | wc -l || echo 0)"
POSTGRES_DB="${FROM_DB}" bash "${REPO_ROOT}/scripts/backup-db.sh"

DUMP="$(ls -1t "${REPO_ROOT}/backups/${FROM_DB}-"*.dump "${REPO_ROOT}/backups/${FROM_DB}-"*.dump.gpg 2>/dev/null | head -1 || true)"
[ -n "${DUMP}" ] || die "el backup no dejo ningun dump de '${FROM_DB}' en backups/."
[ -s "${DUMP}" ] || die "el dump ${DUMP} quedo vacio."
# Que exista y pese no alcanza: pg_restore -l prueba que el dump es integro y restaurable.
# Corre DENTRO del contenedor porque el VPS no tiene cliente de Postgres instalado.
# OJO: pg_restore NO lee el formato custom desde stdin — `pg_restore -l -` responde
# "could not open input file". Hay que darle un archivo real, asi que el dump se copia
# adentro del contenedor y se verifica por path (el VPS no tiene cliente de Postgres).
VERIFY_IN="/tmp/rename-db-verify-$$.dump"
if [ "${DUMP##*.}" = "gpg" ]; then
  gpg --batch --quiet --decrypt "${DUMP}" 2>/dev/null | dc exec -T db sh -c "cat > ${VERIFY_IN}" \
    || die "no se pudo descifrar ${DUMP} (falta la clave privada?). NO se renombro nada."
else
  dc exec -T db sh -c "cat > ${VERIFY_IN}" < "${DUMP}" \
    || die "no se pudo copiar ${DUMP} al contenedor. NO se renombro nada."
fi
verify_rc=0
dc exec -T db pg_restore -l "${VERIFY_IN}" >/dev/null 2>&1 || verify_rc=1
dc exec -T db rm -f "${VERIFY_IN}" >/dev/null 2>&1 || true
[ "${verify_rc}" = "0" ] \
  || die "el dump ${DUMP} no pasa pg_restore -l (truncado o corrupto). NO se renombro nada."
echo "   dump verificado: ${DUMP}"

echo "== 3/7 censo de filas =="
CENSUS_SQL="SELECT string_agg(t || '=' || c, ',' ORDER BY t) FROM (SELECT relname AS t, n_live_tup AS c FROM pg_stat_user_tables) s;"
CENSUS_BEFORE="$(dc exec -T db psql -U "${SUPERUSER}" -d "${FROM_DB}" -tAc "${CENSUS_SQL}")"
echo "   ${CENSUS_BEFORE:-(sin tablas)}"

echo "== 4/7 bajando servicios que usan la base =="
dc stop backend keycloak nginx 2>/dev/null || true
psqlq "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='${FROM_DB}' AND pid <> pg_backend_pid()" >/dev/null

echo "== 5/7 renombrando =="
psqlq "ALTER DATABASE \"${FROM_DB}\" RENAME TO \"${TO_DB}\"" >/dev/null
echo "   base ${FROM_DB} -> ${TO_DB}"
NEW_USER="${SUPERUSER}"
if [ "${RENAME_ROLE}" = "1" ] && [ "${SUPERUSER}" = "${FROM_DB}" ]; then
  psqlq "ALTER ROLE \"${SUPERUSER}\" RENAME TO \"${TO_DB}\"" >/dev/null
  NEW_USER="${TO_DB}"
  echo "   rol ${SUPERUSER} -> ${TO_DB}"
fi

echo "== 6/7 verificando =="
CENSUS_AFTER="$(dc exec -T db psql -U "${NEW_USER}" -d "${TO_DB}" -tAc "${CENSUS_SQL}")"
[ "${CENSUS_BEFORE}" = "${CENSUS_AFTER}" ] \
  || die "el censo de filas NO coincide. Antes: [${CENSUS_BEFORE}] Ahora: [${CENSUS_AFTER}]. El dump ${DUMP} tiene el estado previo."
echo "   censo identico, datos intactos."

echo "== 7/7 .env =="
if [ -f "${REPO_ROOT}/.env" ]; then
  cp "${REPO_ROOT}/.env" "${REPO_ROOT}/.env.bak-${STAMP}"
  sed -i "s/^POSTGRES_DB=.*/POSTGRES_DB=${TO_DB}/; s/^POSTGRES_USER=.*/POSTGRES_USER=${NEW_USER}/" "${REPO_ROOT}/.env"
  echo "   POSTGRES_DB=${TO_DB} / POSTGRES_USER=${NEW_USER}  (copia previa en .env.bak-${STAMP})"
else
  echo "   no hay .env: setear POSTGRES_DB=${TO_DB} y POSTGRES_USER=${NEW_USER} a mano."
fi

echo
echo "LISTO. Levantar con:  docker compose ${COMPOSE_FILES} up -d --build"
echo "Si algo sale mal, el estado previo esta en ${DUMP}."
