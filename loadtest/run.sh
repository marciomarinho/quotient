#!/usr/bin/env bash
# Benchmark harness: run identical k6 scenarios against both gateways (equal
# 2 CPU / 1 GB envelopes) and generate docs/BENCHMARK.md from the results.
#
# Assumes the stack is up with both gateways:
#   make up && docker compose --profile apps up -d --build
set -euo pipefail
cd "$(dirname "$0")/.."

RESULTS=loadtest/results
mkdir -p "$RESULTS"
DURATION="${DURATION:-20s}"
RATES="${RATES:-500 1000 2000}"
TREND="avg,med,p(50),p(90),p(95),p(99),max"

# macOS ships bash 3.2 (no associative arrays), so iterate "name|url" pairs.
run_gateway() {
  gw="$1"; base="$2"
  echo "== waiting for $gw ($base) =="
  for _ in $(seq 1 30); do curl -fsS "$base/actuator/health" >/dev/null 2>&1 && break; sleep 2; done
  for rate in $RATES; do
    echo "== $gw @ ${rate} RPS for $DURATION =="
    k6 run \
      --summary-trend-stats="$TREND" \
      --summary-export="$RESULTS/${gw}-${rate}.json" \
      -e BASE_URL="$base" -e RATE="$rate" -e DURATION="$DURATION" \
      loadtest/scripts/single-event.js || echo "  (thresholds breached at ${rate} RPS — recorded)"
    docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' 2>/dev/null \
      | grep -i "$gw" > "$RESULTS/${gw}-${rate}-stats.txt" || true
  done
}

run_gateway vthreads http://localhost:18080
run_gateway reactive http://localhost:18082

echo "== generating report =="
python3 loadtest/report/generate.py
echo "wrote docs/BENCHMARK.md"
