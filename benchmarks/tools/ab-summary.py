#!/usr/bin/env python3
"""Summarise run-java-ab.sh output as Markdown tables (3-run medians and scaled runs).

Usage: benchmarks/tools/ab-summary.py <results-dir> <baseline-name> <candidate-name> [<candidate-name> ...]
"""
import glob, json, os, statistics, sys

def load_runs(d, name):
    out = {}
    for path in sorted(glob.glob(os.path.join(d, f"java-{name}-run*.json"))):
        for r in json.load(open(path)):
            key = (r["benchmark"].split(".")[-1], r["params"]["fixture"])
            out.setdefault(key, []).append(r["primaryMetric"]["score"] / 1000.0)  # us -> ms
    return out

def load_scaled(d, name):
    path = os.path.join(d, f"java-x64-{name}.json")
    if not os.path.exists(path):
        return {}
    return {(r["benchmark"].split(".")[-1], r["params"]["fixture"]): r["primaryMetric"]["score"] / 1000.0
            for r in json.load(open(path))}

def main():
    d, base, *cands = sys.argv[1:]
    runs = {n: load_runs(d, n) for n in [base, *cands]}
    header = "| bench | fixture | " + f"{base} runs | median | " + " | ".join(f"{c} runs | median | 変化" for c in cands) + " |"
    print(header)
    print("|---|---|---|---:|" + "---|---:|---:|" * len(cands))
    for key in sorted(runs[base]):
        bm = statistics.median(runs[base][key])
        row = f"| {key[0]} | {key[1]} | {' / '.join(f'{x:.3f}' for x in runs[base][key])} | **{bm:.3f}**"
        for c in cands:
            vals = runs[c].get(key)
            if not vals:
                row += " | - | - | -"
                continue
            m = statistics.median(vals)
            row += f" | {' / '.join(f'{x:.3f}' for x in vals)} | **{m:.3f}** | **{100 * (m - bm) / bm:+.2f}%**"
        print(row + " |")
    scaled = {n: load_scaled(d, n) for n in [base, *cands]}
    if scaled[base]:
        print()
        print("| bench | fixture | " + f"{base} ms | " + " | ".join(f"{c} ms | 変化" for c in cands) + " |")
        print("|---|---|---:|" + "---:|---:|" * len(cands))
        for key in sorted(scaled[base]):
            a = scaled[base][key]
            row = f"| {key[0]} | {key[1]} | {a:.1f}"
            for c in cands:
                v = scaled[c].get(key)
                row += f" | {v:.1f} | {100 * (v - a) / a:+.1f}%" if v is not None else " | - | -"
            print(row + " |")

if __name__ == "__main__":
    main()
