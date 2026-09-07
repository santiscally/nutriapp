#!/usr/bin/env bash
# Backup de las bases Postgres (bonosapp + keycloak) del contenedor `db`.
# Genera dumps en formato custom (-Fc), timestamped, en ./backups/ (git-ignored).
#
# Cifrado (recomendado): si BACKUP_GPG_RECIPIENT está seteado, el dump se cifra con GPG
# (→ .dump.gpg). Los dumps traen PII de pacientes/recetas + el store de credenciales de
# Keycloak, y están pensados para salir del host (DR) → cifrarlos evita multiplicar fugas.
#
# Uso:  bash scripts/backup-db.sh
# Env:  POSTGRES_USER (def bonosapp), POSTGRES_DB (def bonosapp),
#       BACKUP_GPG_RECIPIENT (opcional; si está, cifra),
#       COMPOSE_FILES (def "-f docker-compose.yml -f docker-compose.prod.yml")
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

# Config desde .env, que es donde el runbook dice que viven estas variables. Sin esto,
# setear BACKUP_GPG_RECIPIENT ahí no tenía ningún efecto y los dumps salían en TEXTO PLANO
# con sólo un aviso por stderr — invisible desde cron. Lo que ya venga del entorno gana.
if [ -f "${REPO_ROOT}/.env" ]; then
  _env_user="${POSTGRES_USER-}"; _env_db="${POSTGRES_DB-}"; _env_gpg="${BACKUP_GPG_RECIPIENT-}"
  set -a; . "${REPO_ROOT}/.env"; set +a
  [ -n "${_env_user}" ] && POSTGRES_USER="${_env_user}"
  [ -n "${_env_db}" ]   && POSTGRES_DB="${_env_db}"
  [ -n "${_env_gpg}" ]  && BACKUP_GPG_RECIPIENT="${_env_gpg}"
fi

PGUSER="${POSTGRES_USER:-bonosapp}"
APPDB="${POSTGRES_DB:-bonosapp}"
COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
GPG_RCPT="${BACKUP_GPG_RECIPIENT:-}"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${REPO_ROOT}/backups"
mkdir -p "${OUT_DIR}"

if [ -z "${GPG_RCPT}" ]; then
  echo "AVISO: BACKUP_GPG_RECIPIENT no seteado → dumps en TEXTO PLANO (PII + credenciales)." >&2
  echo "       Setealo para cifrar con GPG antes de mover los dumps fuera del host." >&2
fi

dump_db() {
  local db="$1"
  # -T: sin TTY. pg_dump -Fc = formato custom (comprimido, restore selectivo con pg_restore).
  if [ -n "${GPG_RCPT}" ]; then
    local out="${OUT_DIR}/${db}-${STAMP}.dump.gpg"
    echo "Dump cifrado de '${db}' -> ${out}"
    docker compose ${COMPOSE_FILES} exec -T db pg_dump -U "${PGUSER}" -Fc "${db}" \
      | gpg --batch --yes --recipient "${GPG_RCPT}" --encrypt --output "${out}"
    echo "  $(du -h "${out}" | cut -f1)"
  else
    local out="${OUT_DIR}/${db}-${STAMP}.dump"
    echo "Dump de '${db}' -> ${out}"
    docker compose ${COMPOSE_FILES} exec -T db pg_dump -U "${PGUSER}" -Fc "${db}" > "${out}"
    echo "  $(du -h "${out}" | cut -f1)"
  fi
}

dump_db "${APPDB}"
dump_db "keycloak"
echo "Backup OK. Guardar ${OUT_DIR}/*-${STAMP}.* fuera del host para DR."
