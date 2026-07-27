#!/usr/bin/env bash
#
# Smoke test e2e del webhook de TiendaNube (Fase 1.4) contra el stack real.
# Cubre: verificación HMAC (válida/ inválida/ ausente), idempotencia del endpoint, y el flujo
# de conversión receta PENDIENTE -> APLICADA vía el simulador de dev (en stub no llega webhook real).
#
# Uso:
#   bash scripts/smoke-webhook.sh
#   BACKEND_URL=http://localhost:8088 bash scripts/smoke-webhook.sh   # máquina de Santi
#
# Requiere el stack arriba (docker compose up -d db keycloak backend), `python`, `openssl` y `curl`.
# El webhook-secret de dev es 'dev-webhook-secret' (application.yml). Perfil dev activo (sim endpoint).
set -u

BACKEND_URL="${BACKEND_URL:-http://localhost:8088}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8081}"
REALM="${KEYCLOAK_REALM:-nutriapp}"
CLIENT="${KEYCLOAK_CLIENT_ID:-nutriapp-frontend}"
SECRET="${TIENDANUBE_WEBHOOK_SECRET:-dev-webhook-secret}"

API="$BACKEND_URL/api/v1"
KC="$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token"
pass=0; fail=0

check() { if [ "$2" = "$3" ]; then echo "  OK   $1 (=$3)"; pass=$((pass+1));
  else echo "  FAIL $1 (esperado [$2], fue [$3])"; fail=$((fail+1)); fi; }
py() { python -c "import sys,json
try:
 d=json.load(sys.stdin); print($1)
except Exception:
 print('')"; }
token() { curl -s --max-time 10 -d "grant_type=password" -d "client_id=$CLIENT" \
  -d "username=$1" --data-urlencode "password=$2" "$KC" | py "d.get('access_token','')"; }
hmac() { printf '%s' "$1" | openssl dgst -sha256 -hmac "$SECRET" | awk '{print $NF}'; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

echo "== Token nutri (backend=$BACKEND_URL) =="
NT=$(token nutri@nutriapp.dev test1234)
HN="Authorization: Bearer $NT"
[ -n "$NT" ] && echo "  nutri token OK" || { echo "  nutri token FAIL"; exit 1; }

echo "== Emitir receta a convertir =="
PAC_ID=$(curl -s -H "$HN" "$API/pacientes?size=1" | py "d['content'][0]['id']")
PROD_ID=$(curl -s -H "$HN" "$API/productos?size=1" | py "d['content'][0]['id']")
NEW=$(curl -s -H "$HN" -H "Content-Type: application/json" \
  -d "{\"pacienteId\":\"$PAC_ID\",\"items\":[{\"productoId\":\"$PROD_ID\",\"cantidad\":1,\"indicaciones\":\"wh\"}],\"descuentoPct\":20}" \
  "$API/recetas")
REC_ID=$(echo "$NEW" | py "d['id']"); REC_COD=$(echo "$NEW" | py "d['codigo']")
check "receta emitida PENDIENTE" "PENDIENTE" "$(echo "$NEW" | py "d['estado']")"
echo "  receta=$REC_COD id=$REC_ID"

echo "== Webhook: verificación HMAC + idempotencia =="
BODY="{\"store_id\":1234,\"event\":\"order/paid\",\"id\":987654}"
SIG=$(hmac "$BODY")
check "firma válida -> 200" "200" \
  "$(code -X POST -H "Content-Type: application/json" -H "x-linkedstore-hmac-sha256: $SIG" --data-binary "$BODY" "$API/webhooks/tiendanube")"
check "mismo evento (repetido) -> 200 idempotente" "200" \
  "$(code -X POST -H "Content-Type: application/json" -H "x-linkedstore-hmac-sha256: $SIG" --data-binary "$BODY" "$API/webhooks/tiendanube")"
check "firma inválida -> 401" "401" \
  "$(code -X POST -H "Content-Type: application/json" -H "x-linkedstore-hmac-sha256: deadbeef" --data-binary "$BODY" "$API/webhooks/tiendanube")"
check "sin firma -> 401" "401" \
  "$(code -X POST -H "Content-Type: application/json" --data-binary "$BODY" "$API/webhooks/tiendanube")"
check "cuerpo alterado, misma firma -> 401" "401" \
  "$(code -X POST -H "Content-Type: application/json" -H "x-linkedstore-hmac-sha256: $SIG" --data-binary "{\"store_id\":1234,\"event\":\"order/paid\",\"id\":111}" "$API/webhooks/tiendanube")"

echo "== Conversión vía simulador de dev (stub no recibe webhook real) =="
ORDEN_ID=770077
SIM=$(curl -s -H "$HN" -H "Content-Type: application/json" \
  -d "{\"recetaCodigo\":\"$REC_COD\",\"ordenNumero\":306,\"ordenTiendanubeId\":$ORDEN_ID}" \
  "$API/dev/tiendanube/orden-pagada")
check "sim aplica 1 receta" "1" "$(echo "$SIM" | py "d['recetasAplicadas']")"
check "sim idempotente (misma orden) -> 1" "1" \
  "$(curl -s -H "$HN" -H "Content-Type: application/json" -d "{\"recetaCodigo\":\"$REC_COD\",\"ordenTiendanubeId\":$ORDEN_ID}" "$API/dev/tiendanube/orden-pagada" | py "d['recetasAplicadas']")"

echo "== Receta quedó APLICADA con conversión =="
DET=$(curl -s -H "$HN" "$API/recetas/$REC_ID")
check "estado APLICADA" "APLICADA" "$(echo "$DET" | py "d['estado']")"
check "conversion presente" "True" "$(echo "$DET" | py "d.get('conversion') is not None")"
check "ordenNumero=306" "306" "$(echo "$DET" | py "d['conversion']['ordenNumero']")"
check "comisionMonto > 0" "True" "$(echo "$DET" | py "float(d['conversion']['comisionMonto'])>0")"

echo "== Anular una APLICADA -> 409 (guard de estado) =="
check "anular APLICADA -> 409" "409" "$(code -H "$HN" -X POST "$API/recetas/$REC_ID/anular")"

echo ""
echo "==== RESULTADO webhook: $pass OK, $fail FAIL ===="
[ "$fail" = "0" ]
