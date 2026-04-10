#!/usr/bin/env bash

set -euo pipefail

LOG_ROOT="${1:-./logs/mock-service}"
INTERVAL_SECONDS="${2:-1}"

mkdir -p "$LOG_ROOT"

APP_LOG="$LOG_ROOT/app.log"
ACCESS_LOG="$LOG_ROOT/access.log"
WORKER_LOG="$LOG_ROOT/worker.log"

request_id=1000

services=("user-service" "order-service" "trade-service" "settlement-service")
apis=(
  "GET /api/users/profile"
  "POST /api/orders/create"
  "POST /api/trade/confirm"
  "GET /api/settlement/detail"
  "POST /api/refund/apply"
)
levels=("INFO" "INFO" "INFO" "WARN" "ERROR")
messages=(
  "interface invoke success"
  "rpc call timeout after 1820ms"
  "sql slow query cost 940ms"
  "remote dependency returned 502"
  "arthas trace hotspot detected"
)
slow_nodes=("Controller" "FeignClient" "Service" "Repository" "Redis")

echo "mock logs writing to: $LOG_ROOT"
echo "press Ctrl+C to stop"

while true; do
  timestamp="$(date '+%Y-%m-%d %H:%M:%S')"
  level="${levels[RANDOM % ${#levels[@]}]}"
  service="${services[RANDOM % ${#services[@]}]}"
  api="${apis[RANDOM % ${#apis[@]}]}"
  message="${messages[RANDOM % ${#messages[@]}]}"
  slow_node="${slow_nodes[RANDOM % ${#slow_nodes[@]}]}"
  trace_id="$(printf 'T%08d' "$((RANDOM * RANDOM % 100000000))")"
  duration_ms="$((50 + RANDOM % 2500))"
  status_code=$((RANDOM % 10 == 0 ? 500 : 200))

  printf '%s [%s] [%s] [traceId=%s] [requestId=%s] %s duration=%sms node=%s message="%s"\n' \
    "$timestamp" "$level" "$service" "$trace_id" "$request_id" "$api" "$duration_ms" "$slow_node" "$message" >> "$APP_LOG"

  printf '%s traceId=%s requestId=%s method=%s status=%s cost=%sms clientIp=127.0.0.1\n' \
    "$timestamp" "$trace_id" "$request_id" "$api" "$status_code" "$duration_ms" >> "$ACCESS_LOG"

  printf '%s [worker-%s] job=diagnose-refresh state=%s cost=%sms detail="%s"\n' \
    "$timestamp" "$((RANDOM % 4 + 1))" "$([ $((RANDOM % 7)) -eq 0 ] && echo failed || echo finished)" "$((20 + RANDOM % 800))" "$message" >> "$WORKER_LOG"

  if (( request_id % 7 == 0 )); then
    printf '%s [WARN] [%s] [traceId=%s] slow-path detected at %s threshold=500ms actual=%sms\n' \
      "$timestamp" "$service" "$trace_id" "$slow_node" "$duration_ms" >> "$APP_LOG"
  fi

  if (( request_id % 11 == 0 )); then
    printf '%s [ERROR] [%s] [traceId=%s] exception=java.lang.IllegalStateException message="mock business failure on %s"\n' \
      "$timestamp" "$service" "$trace_id" "$api" >> "$APP_LOG"
  fi

  request_id=$((request_id + 1))
  sleep "$INTERVAL_SECONDS"
done
