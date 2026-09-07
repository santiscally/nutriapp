#!/usr/bin/env bash
# Simula que una paciente compró con el cupón de un bono, sin comprar de verdad en la tienda.
# Uso: ./scripts/simular-compra.sh RX-XXXXXX [total]
set -euo pipefail

CODIGO="${1:-}"
TOTAL="${2:-}"
if [ -z "$CODIGO" ]; then
  echo "Uso: $0 RX-XXXXXX [total]"; exit 1
fi

BACKEND_URL="${BACKEND_URL:-http://localhost:8088}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8081}"
REALM="${KEYCLOAK_REALM:-nutriapp}"
CLIENT="${KEYCLOAK_CLIENT_ID:-nutriapp-frontend}"
USER="${NUTRIAPP_USER:-admin@nutriapp.dev}"
PASS="${NUTRIAPP_PASS:-test1234}"

py() { python -c "import sys,json
try:
 d=json.load(sys.stdin); print($1)
except Exception:
 print('')"; }

TOKEN=$(curl -s --max-time 10 -d "grant_type=password" -d "client_id=$CLIENT" \
  -d "username=$USER" --data-urlencode "password=$PASS" \
  "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" | py "d.get('access_token','')")

if [ -z "$TOKEN" ]; then
  echo "No se pudo obtener el token (¿está Keycloak arriba en $KEYCLOAK_URL?)"; exit 1
fi

BODY="{\"recetaCodigo\":\"$CODIGO\""
[ -n "$TOTAL" ] && BODY="$BODY,\"ordenTotal\":$TOTAL"
BODY="$BODY}"

echo "== Simulando compra pagada del bono $CODIGO =="
RESP=$(curl -s -w '\n%{http_code}' -X POST "$BACKEND_URL/api/v1/dev/tiendanube/orden-pagada" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$BODY")
CODE=$(echo "$RESP" | tail -n1)
echo "$RESP" | sed '$d' | python -m json.tool 2>/dev/null || echo "$RESP" | sed '$d'

if [ "$CODE" = "200" ]; then
  echo "OK — mirá el bono en BonosApp: debería estar APLICADA con la comisión calculada."
else
  echo "Falló (HTTP $CODE)."; exit 1
fi
