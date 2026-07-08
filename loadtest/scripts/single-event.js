// k6 load test: sustained single-event ingestion at a target arrival rate.
//
// Env:
//   BASE_URL   gateway base (e.g. http://localhost:18080)
//   API_KEY    tenant API key (default: qk_live_acme_primary)
//   RATE       target requests/sec (default 500)
//   DURATION   test duration (default 30s)
//   PREVU      preallocated VUs (default derived from RATE)
//
// Records the standard k6 metrics (http_req_duration p50/p90/p95/p99,
// throughput, error rate); the runner exports the summary to JSON.
import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:18080";
const API_KEY = __ENV.API_KEY || "qk_live_acme_primary";
const RATE = parseInt(__ENV.RATE || "500", 10);
const DURATION = __ENV.DURATION || "30s";
const PREALLOC = parseInt(__ENV.PREVU || String(Math.max(50, RATE)), 10);

export const options = {
  scenarios: {
    ingest: {
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: PREALLOC,
      maxVUs: PREALLOC * 4,
    },
  },
  thresholds: {
    // Fail the run if the error rate is high or p99 blows out badly.
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(99)<2000"],
  },
};

const METERS = ["llm.tokens.input", "llm.tokens.output", "llm.requests"];

export default function () {
  const meter = METERS[Math.floor(Math.random() * METERS.length)];
  const body = JSON.stringify({
    idempotencyKey: `k6-${__VU}-${__ITER}-${Date.now()}`,
    meterCode: meter,
    quantity: Math.floor(Math.random() * 50000) + 1,
    dimensions: { model: "gpt-4o" },
    occurredAt: new Date().toISOString(),
  });
  const res = http.post(`${BASE_URL}/v1/usage/events`, body, {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${API_KEY}`,
    },
  });
  check(res, { "status is 202": (r) => r.status === 202 });
}
