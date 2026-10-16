#!/usr/bin/env bash
# Roteiro da demonstração gravada para o README.
set -euo pipefail

API=${API:-http://localhost:8080}
MP=${MP:-http://localhost:8089}
SECRET=${MP_WEBHOOK_SECRET:-demo-webhook-secret}
PRODUCT=749990f6-6ccd-40b3-ab75-e8061a3fee9f

bold=$'\e[1m'; cyan=$'\e[36m'; green=$'\e[32m'; yellow=$'\e[33m'; dim=$'\e[2m'; reset=$'\e[0m'

step() { printf "\n${bold}${cyan}▶ %s${reset}\n" "$1"; sleep 0.8; }
cmd()  { printf "${dim}\$ %s${reset}\n" "$1"; sleep 0.6; }
pause() { sleep "${1:-1.6}"; }

printf "${bold}Payflow API${reset} · Java 21 + Spring Boot + Mercado Pago\n"
pause 1.2

step "1. Cadastro e login (JWT)"
cmd "POST /auth/register  ·  POST /auth/login"
curl -s "$API/auth/register" -H 'Content-Type: application/json' \
  -d '{"name":"Maria Silva","email":"maria@exemplo.com","password":"senha-forte-1","document":"529.982.247-25"}' >/dev/null
TOKEN=$(curl -s "$API/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"maria@exemplo.com","password":"senha-forte-1"}' | jq -r .accessToken)
printf "${green}token:${reset} %s…\n" "${TOKEN:0:42}"
pause

step "2. Pedido criado; o total é calculado no servidor"
cmd "POST /orders  {\"productId\": \"…\", \"quantity\": 1}"
ORDER=$(curl -s "$API/orders" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"items\":[{\"productId\":\"$PRODUCT\",\"quantity\":1}]}")
echo "$ORDER" | jq -C '{id, status, total, item: .items[0].name}'
ORDER_ID=$(echo "$ORDER" | jq -r .id)
pause

step "3. Cobrança Pix com Idempotency-Key"
KEY=$(cat /proc/sys/kernel/random/uuid)
cmd "POST /payments/pix  Idempotency-Key: ${KEY:0:8}…"
PAY=$(curl -s "$API/payments/pix" -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $KEY" \
  -H 'Content-Type: application/json' -d "{\"orderId\":\"$ORDER_ID\"}")
echo "$PAY" | jq -C '{id, method, status, amount, pix: {qrCode: (.pix.qrCode[0:44] + "…")}}'
PAY_ID=$(echo "$PAY" | jq -r .id)
pause 2

step "4. Clique duplicado: mesma chave devolve o mesmo pagamento"
cmd "POST /payments/pix  (mesma Idempotency-Key)"
curl -s "$API/payments/pix" -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $KEY" \
  -H 'Content-Type: application/json' -d "{\"orderId\":\"$ORDER_ID\"}" | jq -C '{id, status}'
printf "${green}✔ nenhuma cobrança nova foi criada${reset}\n"
pause

# O cliente paga o Pix: o Mercado Pago (simulado) passa a responder "approved".
curl -s -o /dev/null "$MP/__admin/mappings" -H 'Content-Type: application/json' -d @- <<JSON
{"request":{"method":"GET","url":"/v1/payments/1325467890"},
 "response":{"status":200,"headers":{"Content-Type":"application/json"},
  "jsonBody":{"id":1325467890,"status":"approved","status_detail":"accredited",
   "transaction_amount":150.00,"currency_id":"BRL","external_reference":"$PAY_ID"}}}
JSON

step "5. Webhook forjado é recusado"
cmd "POST /webhooks/mercadopago  x-signature: ts=…,v1=falsa"
TS=$(date +%s)
code=$(curl -s -o /dev/null -w '%{http_code}' "$API/webhooks/mercadopago?data.id=1325467890&type=payment" \
  -H "x-request-id: fake-1" -H "x-signature: ts=$TS,v1=0000" -H 'Content-Type: application/json' -d '{}')
printf "${yellow}HTTP %s${reset} · assinatura HMAC não confere\n" "$code"
pause

step "6. Webhook assinado: status conferido na API do Mercado Pago"
REQ=$(cat /proc/sys/kernel/random/uuid)
SIG=$(printf 'id:1325467890;request-id:%s;ts:%s;' "$REQ" "$TS" | openssl dgst -sha256 -hmac "$SECRET" | awk '{print $NF}')
cmd "POST /webhooks/mercadopago  x-signature: ts=$TS,v1=${SIG:0:12}…"
curl -s "$API/webhooks/mercadopago?data.id=1325467890&type=payment" \
  -H "x-request-id: $REQ" -H "x-signature: ts=$TS,v1=$SIG" -H 'Content-Type: application/json' \
  -d '{"type":"payment","data":{"id":"1325467890"}}' | jq -C .
pause

step "7. Pagamento aprovado e pedido pago"
cmd "GET /payments/{id}  ·  GET /orders/{id}"
curl -s "$API/payments/$PAY_ID" -H "Authorization: Bearer $TOKEN" | jq -C '{status, statusDetail, amount}'
curl -s "$API/orders/$ORDER_ID" -H "Authorization: Bearer $TOKEN" | jq -C '{pedido: .status}'
printf "\n${bold}${green}✔ Pix confirmado com validação de assinatura, valor e referência${reset}\n"
pause 3
