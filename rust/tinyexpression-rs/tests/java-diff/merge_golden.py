#!/usr/bin/env python3
"""Joins corpus rows with the Java driver's outcomes into the committed golden files.

usage: merge_golden.py <out-dir> <corpus.jsonl> <java.jsonl> [<corpus.jsonl> <java.jsonl> ...]

Rows whose formula every Java calculator rejected at construction (and the Rust parser rejects
too, i.e. the row was only probed under the empty profile) are summarised in `rejected.txt`
instead of being kept: they are literals that are not formulas.
"""
import json
import sys
from pathlib import Path

out_dir = Path(sys.argv[1])
pairs = list(zip(sys.argv[2::2], sys.argv[3::2]))
formulas = []
index = {}
rows = []
rejected = set()
for corpus_path, java_path in pairs:
    java = {}
    for line in open(java_path, encoding='utf-8'):
        record = json.loads(line)
        java[record.pop('id')] = record
    corpus = [json.loads(line) for line in open(corpus_path, encoding='utf-8')]
    probe_only = 'probe' in Path(corpus_path).name or 'rejected' in Path(corpus_path).name
    accepted_somewhere = set()
    if probe_only:
        for row in corpus:
            outcome = java[row['id']]
            if not (outcome['kind'] == 'error' and outcome.get('stage') == 'create'):
                accepted_somewhere.add(row['formula'])
    for row in corpus:
        if probe_only and row['formula'] not in accepted_somewhere:
            rejected.add(row['formula'])
            continue
        formula = row['formula']
        outcome = java[row['id']]
        if 'random(' in formula and outcome['kind'] != 'error':
            # Math.random(): only the kind is comparable; drop the value so regeneration is stable.
            outcome = {'kind': outcome['kind'], 'random': True}
        if formula not in index:
            index[formula] = len(formulas)
            formulas.append({'text': formula, 'origin': row['origin']})
        rows.append({
            'id': row['id'] if not probe_only else 'r' + row['id'][1:],
            'f': index[formula],
            'rt': row['resultType'],
            'nt': row['numberType'] or 'float',
            'profile': row['profile'],
            'vars': row['vars'],
            'ext': row['externals'],
            'java': outcome,
        })

out_dir.mkdir(parents=True, exist_ok=True)
with open(out_dir / 'formulas.jsonl', 'w', encoding='utf-8') as f:
    for formula in formulas:
        f.write(json.dumps(formula, ensure_ascii=False) + '\n')
with open(out_dir / 'p4ast.jsonl', 'w', encoding='utf-8') as f:
    for row in rows:
        f.write(json.dumps(row, ensure_ascii=False, separators=(',', ':')) + '\n')
with open(out_dir / 'rejected-count.txt', 'w', encoding='utf-8') as f:
    f.write(f'{len(rejected)}\n')
print(f'{len(formulas)} formulas, {len(rows)} rows, {len(rejected)} rejected literals', file=sys.stderr)
