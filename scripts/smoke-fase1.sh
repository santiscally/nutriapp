#!/usr/bin/env bash
#
# Smoke test e2e de la Fase 1 del backend de BonosApp.
# Verifica contra el stack real: notificaciones, ciclo de receta (anular/reenviar),
# 409 de borrado de paciente, cierre mensual, y el flujo registro -> aprobación (Keycloak).
#
# Uso:
#   bash scripts/smoke-fase1.sh
#   BACKEND_URL=http://localhost:8088 bash scripts/smoke-fase1.sh   # máquina de Santi (8080 ocupado por GIA)
#
# Requiere el stack arriba (docker compose up -d db keycloak backend) y `python` en el PATH.
# OJO: crea un registro de nutricionista (queda en Keycloak). Limpieza total: docker compose down -v && up.
set -u

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8081}"
REALM="${KEYCLOAK_REALM:-nutriapp}"
CLIENT="${KEYCLOAK_CLIENT_ID:-nutriapp-frontend}"

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

echo "== Tokens (backend=$BACKEND_URL keycloak=$KEYCLOAK_URL) =="
NT=$(token nutri@nutriapp.dev test1234)
AT=$(token admin@nutriapp.dev test1234)
HN="Authorization: Bearer $NT"; HA="Authorization: Bearer $AT"
[ -n "$NT" ] && echo "  nutri token OK" || { echo "  nutri token FAIL"; exit 1; }
[ -n "$AT" ] && echo "  admin token OK" || { echo "  admin token FAIL"; exit 1; }

echo "== Recetas: elegir PENDIENTE / APLICADA =="
REC=$(curl -s -H "$HN" "$API/recetas?size=50")
PEND_ID=$(echo "$REC" | py "next((r['id'] for r in d['content'] if r['estado']=='PENDIENTE'),'')")
APLIC_ID=$(echo "$REC" | py "next((r['id'] for r in d['content'] if r['estado']=='APLICADA'),'')")

echo "== GET /recetas/{id} incluye 'notificaciones' =="
check "detalle trae notificaciones" "True" "$(curl -s -H "$HN" "$API/recetas/$PEND_ID" | py "'notificaciones' in d")"

echo "== Emitir receta -> encola EMAIL+WHATSAPP QUEUED =="
PAC_ID=$(curl -s -H "$HN" "$API/pacientes?size=1" | py "d['content'][0]['id']")
PROD_ID=$(curl -s -H "$HN" "$API/productos?size=1" | py "d['content'][0]['id']")
NEW=$(curl -s -w $'\n%{http_code}' -H "$HN" -H "Content-Type: application/json" \
  -d "{\"pacienteId\":\"$PAC_ID\",\"items\":[{\"productoId\":\"$PROD_ID\",\"cantidad\":1,\"indicaciones\":\"smoke\"}],\"descuentoPct\":20}" \
  "$API/recetas")
check "POST /recetas 201" "201" "$(echo "$NEW" | tail -1)"
NEW_BODY=$(echo "$NEW" | sed '$d'); NEW_ID=$(echo "$NEW_BODY" | py "d['id']")
check "encola 2 canales" "['EMAIL', 'WHATSAPP']" "$(echo "$NEW_BODY" | py "sorted(n['canal'] for n in d['notificaciones'])")"
check "notificaciones QUEUED" "{'QUEUED'}" "$(echo "$NEW_BODY" | py "set(n['estado'] for n in d['notificaciones'])")"

echo "== Reenviar / Anular con guards de estado =="
check "reenviar PENDIENTE 200" "200" "$(curl -s -o /dev/null -w '%{http_code}' -H "$HN" -X POST "$API/recetas/$NEW_ID/reenviar")"
check "anular APLICADA -> 409" "409" "$(curl -s -o /dev/null -w '%{http_code}' -H "$HN" -X POST "$API/recetas/$APLIC_ID/anular")"
check "anular PENDIENTE -> ANULADA" "ANULADA" "$(curl -s -H "$HN" -X POST "$API/recetas/$NEW_ID/anular" | py "d['estado']")"

echo "== DELETE paciente con receta PENDIENTE -> 409 =="
PAC_PEND=$(curl -s -H "$HN" "$API/recetas/$PEND_ID" | py "d['paciente']['id']")
check "DELETE con pendientes -> 409" "409" "$(curl -s -o /dev/null -w '%{http_code}' -H "$HN" -X DELETE "$API/pacientes/$PAC_PEND")"

echo "== Dashboard cierre-mensual =="
CM=$(curl -s -w $'\n%{http_code}' -H "$HN" "$API/dashboard/cierre-mensual?year=2026&month=7")
check "cierre-mensual 200" "200" "$(echo "$CM" | tail -1)"

echo "== Registro público -> PENDIENTE + login bloqueado =="
NEWMAIL="smoke.$RANDOM$RANDOM@nutriapp.dev"
REG=$(curl -s -w $'\n%{http_code}' -H "Content-Type: application/json" \
  -d "{\"nombre\":\"Smoke\",\"apellido\":\"Test\",\"email\":\"$NEWMAIL\",\"telefono\":\"+5491155551234\",\"matricula\":\"MN 9999\",\"password\":\"test1234\"}" \
  "$API/registro")
check "POST /registro 201" "201" "$(echo "$REG" | tail -1)"
REG_BODY=$(echo "$REG" | sed '$d')
check "registro PENDIENTE" "PENDIENTE" "$(echo "$REG_BODY" | py "d['estadoValidacion']")"
REG_ID=$(echo "$REG_BODY" | py "d['id']")
check "login pre-aprobación falla" "" "$(token "$NEWMAIL" test1234)"

echo "== Admin aprueba -> login funciona =="
check "admin lista al registrado" "True" \
  "$(curl -s -H "$HA" "$API/admin/nutricionistas?estado=PENDIENTE&size=50" | py "any(n['email']=='$NEWMAIL' for n in d['content'])")"
APR=$(curl -s -w $'\n%{http_code}' -H "$HA" -X POST "$API/admin/nutricionistas/$REG_ID/aprobar")
check "aprobar 200" "200" "$(echo "$APR" | tail -1)"
check "estado APROBADA" "APROBADA" "$(echo "$APR" | sed '$d' | py "d['estadoValidacion']")"
NEWTOK2=$(token "$NEWMAIL" test1234)
[ -n "$NEWTOK2" ] && { echo "  OK   login post-aprobación funciona"; pass=$((pass+1)); } || { echo "  FAIL login post-aprobación"; fail=$((fail+1)); }
check "re-aprobar -> 409" "409" "$(curl -s -o /dev/null -w '%{http_code}' -H "$HA" -X POST "$API/admin/nutricionistas/$REG_ID/aprobar")"

echo ""
echo "==== RESULTADO: $pass OK, $fail FAIL ===="
[ "$fail" = "0" ]
