#!/usr/bin/env bash
# Genera un certificado TLS self-signed en nginx/certs/ para dev/staging.
# NO usar en producción: el navegador lo marca como no confiable. En prod se dropean
# los pem reales del dominio (ver nginx/certs/README.md).
#
# Uso:  bash scripts/gen-selfsigned-cert.sh [dominio]
#       (dominio por defecto: localhost)
set -euo pipefail

DOMAIN="${1:-localhost}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERT_DIR="${SCRIPT_DIR}/../nginx/certs"
mkdir -p "${CERT_DIR}"

if ! command -v openssl >/dev/null 2>&1; then
  echo "ERROR: openssl no está instalado." >&2
  exit 1
fi

echo "Generando cert self-signed para CN=${DOMAIN} en ${CERT_DIR} ..."
openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
  -keyout "${CERT_DIR}/privkey.pem" \
  -out    "${CERT_DIR}/fullchain.pem" \
  -subj   "/CN=${DOMAIN}" \
  -addext "subjectAltName=DNS:${DOMAIN},DNS:localhost,IP:127.0.0.1"

chmod 600 "${CERT_DIR}/privkey.pem" 2>/dev/null || true
echo "Listo: ${CERT_DIR}/fullchain.pem + privkey.pem (válido 365 días, self-signed)."
