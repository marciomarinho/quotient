#!/usr/bin/env python3
"""Turn k6 summary-export JSON into the results tables in docs/BENCHMARK.md.

Reads loadtest/results/<gateway>-<rate>.json (+ optional *-stats.txt for
container CPU/RSS) and writes the RESULTS section of docs/BENCHMARK.md between
markers, preserving the hand-written methodology/analysis around them.
"""
import glob
import json
import os
import re

RESULTS_DIR = "loadtest/results"
DOC = "docs/BENCHMARK.md"
BEGIN = "<!-- RESULTS:BEGIN -->"
END = "<!-- RESULTS:END -->"


def load():
    rows = {}
    for path in sorted(glob.glob(f"{RESULTS_DIR}/*.json")):
        name = os.path.basename(path)[:-5]  # strip .json
        m = re.match(r"(vthreads|reactive)-(\d+)$", name)
        if not m:
            continue
        gw, rate = m.group(1), int(m.group(2))
        with open(path) as f:
            data = json.load(f)
        metrics = data.get("metrics", {})
        # k6 v2 summary-export: metrics are flat (percentiles directly on the metric).
        dur = metrics.get("http_req_duration", {})
        reqs = metrics.get("http_reqs", {})
        failed = metrics.get("http_req_failed", {})
        stats = read_stats(gw, rate)
        rows[(gw, rate)] = {
            "throughput": reqs.get("rate", 0.0),
            "p50": dur.get("p(50)", dur.get("med", 0.0)),
            "p95": dur.get("p(95)", 0.0),
            "p99": dur.get("p(99)", 0.0),
            "max": dur.get("max", 0.0),
            "error_rate": failed.get("rate", failed.get("value", 0.0)),
            "cpu": stats.get("cpu", "-"),
            "mem": stats.get("mem", "-"),
        }
    return rows


def read_stats(gw, rate):
    path = f"{RESULTS_DIR}/{gw}-{rate}-stats.txt"
    if not os.path.exists(path):
        return {}
    line = open(path).read().strip().splitlines()
    if not line:
        return {}
    parts = line[0].split()
    # "<name> <cpu%> <mem> / <limit>"
    cpu = parts[1] if len(parts) > 1 else "-"
    mem = parts[2] if len(parts) > 2 else "-"
    return {"cpu": cpu, "mem": mem}


def table(rows):
    rates = sorted({r for (_, r) in rows})
    out = []
    out.append("| Gateway | Target RPS | Throughput (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | max (ms) | Errors | CPU | RSS |")
    out.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for gw in ("vthreads", "reactive"):
        for rate in rates:
            r = rows.get((gw, rate))
            if not r:
                continue
            out.append(
                f"| {gw} | {rate} | {r['throughput']:.0f} | {r['p50']:.1f} | {r['p95']:.1f} | "
                f"{r['p99']:.1f} | {r['max']:.1f} | {r['error_rate']*100:.2f}% | {r['cpu']} | {r['mem']} |"
            )
    return "\n".join(out)


def main():
    rows = load()
    if not rows:
        print("no results found; run loadtest/run.sh first")
        return
    block = f"{BEGIN}\n\n{table(rows)}\n\n{END}"
    text = open(DOC).read() if os.path.exists(DOC) else ""
    if BEGIN in text and END in text:
        text = re.sub(re.escape(BEGIN) + r".*?" + re.escape(END), block, text, flags=re.S)
    else:
        text += "\n\n## Results\n\n" + block + "\n"
    with open(DOC, "w") as f:
        f.write(text)


if __name__ == "__main__":
    main()
