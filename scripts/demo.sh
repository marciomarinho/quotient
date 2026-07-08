#!/usr/bin/env bash
# End-to-end demo: build, start the whole stack, fire traffic, invoice, verify.
# See scripts/demo.py for the traffic + assertions.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== Building application jars =="
./gradlew -q clean bootJar

echo "== Starting infrastructure =="
docker compose up -d

echo "== Building + starting application services =="
docker compose --profile apps up -d --build

echo "== Waiting for gateway + ledger to become healthy =="
wait_healthy() {
  local url="$1" name="$2"
  for _ in $(seq 1 60); do
    if curl -fsS "$url" >/dev/null 2>&1; then echo "  $name ready"; return 0; fi
    sleep 2
  done
  echo "  $name did not become healthy" >&2
  return 1
}
wait_healthy http://localhost:18080/actuator/health "ingest-gateway"
wait_healthy http://localhost:8086/actuator/health "ledger-service"

echo "== Running demo =="
python3 scripts/demo.py
