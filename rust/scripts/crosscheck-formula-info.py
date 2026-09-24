#!/usr/bin/env python3
"""Runs every FormulaInfo fixture through the Rust and the Java parser that ubnfc generates from
grammar/formula-info.ubnf, and compares (issue #180):

  1. the two generated backends with each other: full-input acceptance and the canonical AST
     (type / span / fields), byte for byte;
  2. both with the hand-written Java loader's syntax layer, as recorded in
     rust/tinyexpression-rs/tests/formula-info/golden/java.jsonl: acceptance (full consumption)
     and, per entry, the key and the raw value slice.

usage: crosscheck-formula-info.py <repo-root> <rust-driver> <java-classes-dir>
"""
import json
import pathlib
import subprocess
import sys


def run(cmd, requests):
    proc = subprocess.run(cmd, input=requests, capture_output=True, text=True, encoding="utf-8")
    if proc.returncode != 0:
        sys.stderr.write(proc.stderr[:4000])
        raise SystemExit(f"driver failed: {cmd[0]} ({proc.returncode})")
    return {json.loads(line)["id"]: json.loads(line) for line in proc.stdout.splitlines() if line.strip()}


def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False)


def entries(ast, source):
    chars = list(source)
    blocks = []
    for block in ast["fields"]["blocks"]:
        found = []
        for entry in block["fields"]["entries"]:
            start, end = entry["fields"]["value"]["span"]
            found.append((entry["fields"]["key"], "".join(chars[start:end])))
        if found:
            blocks.append(found)
    return blocks


def main():
    root, rust_driver, java_classes = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3]
    golden = root / "rust/tinyexpression-rs/tests/formula-info/golden/java.jsonl"
    rows = [json.loads(line) for line in golden.read_text(encoding="utf-8").splitlines() if line.strip()]
    sources = [(root / row["fixture"]).read_bytes().decode("utf-8") for row in rows]
    requests = "".join(
        json.dumps({"id": i, "grammar": "FormulaInfo", "entry": None, "input": text,
                    "options": {"lexical": False, "scope": False}}, ensure_ascii=False) + "\n"
        for i, text in enumerate(sources)
    )
    rust = run([rust_driver], requests)
    java = run(["java", "-cp", java_classes, "target.driver.Driver"], requests)
    failures = []
    accepted = 0
    for i, (row, source) in enumerate(zip(rows, sources)):
        name = row["fixture"]
        r, j = rust[i], java[i]
        if r["allConsumed"] != j["allConsumed"] or canonical(r["ast"]) != canonical(j["ast"]):
            failures.append(f"{name}: Rust and Java backends differ")
        java_loader_accepts = row["syntax"]["accepted"]
        if r["allConsumed"] != java_loader_accepts:
            failures.append(f"{name}: generated={r['allConsumed']} hand-written Java={java_loader_accepts}")
            continue
        if not java_loader_accepts:
            continue
        accepted += 1
        want = [
            [(e.get("key"), e["rawValue"] or "") for e in block["entries"]]
            for block in row["syntax"]["blocks"] if block["entries"]
        ]
        got = entries(r["ast"], source)
        same = len(want) == len(got) and all(
            len(a) == len(b) and all((ka is None or ka == kb) and va == vb for (ka, va), (kb, vb) in zip(a, b))
            for a, b in zip(want, got))
        if not same:
            failures.append(f"{name}: entries differ from the hand-written Java loader")
    print(f"{len(rows)} fixtures ({accepted} accepted, {len(rows) - accepted} rejected): "
          f"Rust/Java backends and the hand-written Java loader compared")
    if failures:
        print("\n".join(failures))
        raise SystemExit(1)
    print("canonical AST: Rust == Java on every fixture; acceptance and entries == hand-written loader")


if __name__ == "__main__":
    main()
