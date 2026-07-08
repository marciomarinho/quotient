#!/usr/bin/env python3
"""Quotient end-to-end demo.

Fires realistic usage traffic (with deliberate duplicates) at the ingestion
gateway across the three demo tenants, waits for the pipeline to aggregate →
rate → post to the ledger, then generates invoices and prints each tenant's
invoice and ledger balances. Finally it asserts the two things that matter:
duplicates were not billed, and the ledger is balanced (debits == credits).

Stdlib only — no third-party packages.
"""

import json
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

GATEWAY = "http://localhost:18080"
LEDGER = "http://localhost:8086"

TENANTS = [
    {"name": "Acme", "id": "11111111-1111-1111-1111-111111111111", "key": "qk_live_acme_primary"},
    {"name": "Globex", "id": "22222222-2222-2222-2222-222222222222", "key": "qk_live_globex_primary"},
    {"name": "Initech", "id": "33333333-3333-3333-3333-333333333333", "key": "qk_live_initech_primary"},
]

METERS = ["llm.tokens.input", "llm.tokens.output", "llm.requests"]
EVENTS_PER_TENANT = 3400  # ~10k across three tenants
DUP_RATE = 0.05
BATCH = 1000

random.seed(42)  # reproducible quantities / duplicate pattern
RUN_ID = str(time.time_ns())  # fresh idempotency keys each run (Redis remembers prior runs 24h)


def now_iso():
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def post(url, body, headers):
    data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json", **headers}, method="POST")
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.status, json.loads(resp.read() or "{}")


def get(url, headers):
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.status, json.loads(resp.read() or "{}")


def quantity_for(meter):
    if meter == "llm.requests":
        return random.randint(1, 20)
    return random.randint(500, 80_000)


def send_traffic(tenant):
    headers = {"Authorization": f"Bearer {tenant['key']}"}
    sent_keys = []
    deduped = 0
    accepted = 0
    made = 0
    while made < EVENTS_PER_TENANT:
        events = []
        for _ in range(min(BATCH, EVENTS_PER_TENANT - made)):
            if sent_keys and random.random() < DUP_RATE:
                key = random.choice(sent_keys)  # deliberate duplicate
            else:
                key = f"{tenant['name'].lower()}-{RUN_ID}-{made}-{random.randint(0, 1 << 30)}"
                sent_keys.append(key)
            meter = random.choice(METERS)
            events.append({
                "idempotencyKey": key,
                "meterCode": meter,
                "quantity": quantity_for(meter),
                "dimensions": {"model": random.choice(["gpt-4o", "claude"])},
                "occurredAt": now_iso(),
            })
            made += 1
        _, body = post(f"{GATEWAY}/v1/usage/events:batch", {"events": events}, headers)
        deduped += body.get("deduplicated", 0)
        accepted += body.get("accepted", 0)
    print(f"  {tenant['name']:8} sent={made}  accepted={accepted}  deduplicated={deduped}")
    return accepted, deduped


def heartbeat(seconds):
    """Keep event-time advancing so the aggregator's tumbling windows close.

    Kafka Streams suppress() emits a window only when a later-timestamped record
    pushes stream time past window-end + grace. After a burst the stream would
    otherwise stall, so we trickle one event per (tenant, meter, model) each
    second — enough distinct keys to advance every partition — for long enough to
    close the burst's windows.
    """
    models = ["gpt-4o", "claude"]
    deadline = time.time() + seconds
    while time.time() < deadline:
        for tenant in TENANTS:
            headers = {"Authorization": f"Bearer {tenant['key']}"}
            events = []
            for meter in METERS:
                for model in models:
                    events.append({
                        "idempotencyKey": f"hb-{tenant['name']}-{meter}-{model}-{time.time_ns()}",
                        "meterCode": meter,
                        "quantity": quantity_for(meter),
                        "dimensions": {"model": model},
                        "occurredAt": now_iso(),
                    })
            post(f"{GATEWAY}/v1/usage/events:batch", {"events": events}, headers)
        time.sleep(1)


def money(minor):
    return f"AUD {minor / 100:,.2f}"


def main():
    period = datetime.now(timezone.utc).strftime("%Y-%m")

    print("\n== 1. Firing usage traffic (5% deliberate duplicates) ==")
    total_deduped = 0
    for tenant in TENANTS:
        _, deduped = send_traffic(tenant)
        total_deduped += deduped

    print("\n== 2. Draining the pipeline (heartbeat closes windows; aggregate → rate → post) ==")
    heartbeat(25)
    time.sleep(12)

    print(f"\n== 3. Generating {period} invoices ==")
    invoices = {}
    for tenant in TENANTS:
        status, invoice = post(f"{LEDGER}/v1/tenants/{tenant['id']}/invoices?period={period}", {}, {})
        invoices[tenant["name"]] = invoice
        print(f"  {tenant['name']:8} net={money(invoice['netMinor'])}  tax={money(invoice['taxMinor'])}  total={money(invoice['totalMinor'])}  lines={len(invoice['lines'])}")

    print("\n== 4. Ledger balances ==")
    for tenant in TENANTS:
        _, balances = get(f"{LEDGER}/v1/ledger/balances", {"X-Tenant-Id": tenant["id"]})
        summary = "  ".join(f"{b['accountType']}={money(b['balanceMinor'])}" for b in balances)
        print(f"  {tenant['name']:8} {summary}")

    print("\n== 5. Assertions ==")
    _, verify = post(f"{LEDGER}/v1/admin/ledger/verify", {}, {})
    balanced = verify.get("allMatch", False)
    print(f"  duplicates rejected (not billed): {total_deduped} deduplicated  -> {'OK' if total_deduped > 0 else 'FAIL'}")
    print(f"  ledger balanced (debits == credits): {'OK' if balanced else 'FAIL'}")

    print_trace_link()

    ok = total_deduped > 0 and balanced and all(inv["totalMinor"] > 0 for inv in invoices.values())
    print(f"\nDEMO {'PASSED' if ok else 'FAILED'}\n")
    sys.exit(0 if ok else 1)


def print_trace_link():
    """Finale: surface one full pipeline trace in local Grafana/Tempo."""
    print("\n== 6. Distributed trace ==")
    try:
        status, body = get(
            "http://localhost:3200/api/search?limit=1&tags="
            + "service.name%3Dingest-gateway-vthreads",
            {},
        )
        traces = body.get("traces") or []
        if traces:
            trace_id = traces[0]["traceID"]
            print(f"  one pipeline trace: {trace_id}")
            print(f"  Tempo:   http://localhost:3200/api/traces/{trace_id}")
            print(f"  Grafana: http://localhost:3001/explore  (Tempo datasource, query {trace_id})")
        else:
            print("  no traces indexed yet — open http://localhost:3001/explore (Tempo)")
    except Exception:
        print("  Tempo not reachable — open http://localhost:3001/explore (Tempo)")


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code} from {e.url}: {e.read().decode()[:300]}", file=sys.stderr)
        sys.exit(1)
