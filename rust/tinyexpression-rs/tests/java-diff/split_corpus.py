#!/usr/bin/env python3
"""Splits the corpus by what the Rust parser accepts (the release CLI's `parse`).

usage: split_corpus.py <corpus.jsonl> <tinyexpression-rs binary> <accepted.jsonl> <probe.jsonl>

Formulas the Rust parser accepts keep every row. The others keep only their empty-profile rows:
the Java side probes them, and any formula Java accepts stays in the golden (so a Rust-only
rejection is a visible difference); formulas both sides reject are counted, not kept.
"""
import json
import subprocess
import sys

corpus, binary, accepted_path, probe_path = sys.argv[1:5]
rows = [json.loads(line) for line in open(corpus, encoding='utf-8')]
accepted = {}
for formula in sorted({row['formula'] for row in rows}):
    result = subprocess.run([binary, 'parse', '-'], input=formula.encode(), capture_output=True)
    accepted[formula] = result.returncode == 0
with open(accepted_path, 'w', encoding='utf-8') as out:
    for row in rows:
        if accepted[row['formula']]:
            out.write(json.dumps(row, ensure_ascii=False) + '\n')
with open(probe_path, 'w', encoding='utf-8') as out:
    for row in rows:
        if not accepted[row['formula']] and row['profile'] == 'empty':
            out.write(json.dumps(row, ensure_ascii=False) + '\n')
print(f'{sum(accepted.values())} of {len(accepted)} formulas accepted by the Rust parser', file=sys.stderr)
