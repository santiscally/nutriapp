#!/usr/bin/env bash
# Restore de un dump (-Fc) generado por backup-db.sh, a una base del contenedor `db`.
# Autodetecta dumps cifrados (.gpg → los descifra con GPG antes de restaurar).
# DESTRUCTIVO: --clean dropea objetos existentes antes de recrearlos. Exige --yes.
#
# Uso:  bash scripts/restore-db.sh <archivo.dump[.gpg]> [nombre_db] --yes
#       (nombre_db por defecto: se infiere del prefijo del archivo; si no, POSTGRES_DB)
# Env:  POSTGRES_USER (def bonosapp), POSTGRES_DB (def bonosapp),
#       COMPOSE_FILES (def "-f docker-compose.yml -f docker-compose.prod.yml")
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

# Misma carga de .env que backup-db.sh: el usuario/DB de Postgres viven ahí.
# Lo que ya venga del entorno gana.
if [ -f "${REPO_ROOT}/.env" ]; then
  _env_user="${POSTGRES_USER-}"; _env_db="${POSTGRES_DB-}"
  set -a; . "${REPO_ROOT}/.env"; set +a
  [ -n "${_env_user}" ] && POSTGRES_USER="${_env_user}"
  [ -n "${_env_db}" ]   && POSTGRES_DB="${_env_db}"
fi

DUMP_FILE="${1:-}"
TARGET_DB="${2:-}"
CONFIRM=""
for arg in "$@"; do [ "${arg}" = "--yes" ] && CONFIRM="yes"; done

if [ -z "${DUMP_FILE}" ] || [ ! -f "${DUMP_FILE}" ]; then
  echo "ERROR: pasar un archivo .dump o .dump.gpg existente como primer argumento." >&2
  echo "Uso: bash scripts/restore-db.sh <archivo.dump[.gpg]> [nombre_db] --yes" >&2
  exit 1
fi

PGUSER="${POSTGRES_USER:-bonosapp}"
COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"

# Inferir DB objetivo del nombre del archivo (keycloak-*.dump[.gpg] -> keycloak) si no se pasó.
if [ -z "${TARGET_DB}" ] || [ "${TARGET_DB}" = "--yes" ]; then
  base="$(basename "${DUMP_FILE}")"
  case "${base}" in
    keycloak-*) TARGET_DB="keycloak" ;;
    *)          TARGET_DB="${POSTGRES_DB:-bonosapp}" ;;
  esac
fi

if [ "${CONFIRM}" != "yes" ]; then
  echo "DESTRUCTIVO: esto va a --clean sobre la base '${TARGET_DB}' (dropea y recrea objetos)." >&2
  echo "Re-ejecutar agregando --yes para confirmar." >&2
  exit 1
fi

echo "Restore de ${DUMP_FILE} -> base '${TARGET_DB}' ..."
# pg_restore lee de stdin. Si el dump está cifrado, lo descifra con GPG en el pipe (la clave
# nunca toca disco). Si no, lo cabecea tal cual.
case "${DUMP_FILE}" in
  *.gpg)
    gpg --batch --quiet --decrypt "${DUMP_FILE}" \
      | docker compose ${COMPOSE_FILES} exec -T db \
          pg_restore -U "${PGUSER}" -d "${TARGET_DB}" --clean --if-exists --no-owner
    ;;
  *)
    docker compose ${COMPOSE_FILES} exec -T db \
      pg_restore -U "${PGUSER}" -d "${TARGET_DB}" --clean --if-exists --no-owner < "${DUMP_FILE}"
    ;;
esac
echo "Restore OK sobre '${TARGET_DB}'."
