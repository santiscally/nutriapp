#!/bin/bash
# Corre una sola vez, en el primer boot de Postgres (volumen vacio).
# Crea la DB "keycloak" para que el contenedor de Keycloak la use.
# Keycloak reusa el mismo user/password que la app (simple para dev/prod chico).
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  CREATE DATABASE keycloak OWNER "$POSTGRES_USER";
EOSQL
