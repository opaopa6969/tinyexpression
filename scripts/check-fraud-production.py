#!/usr/bin/env python3
"""Compare actual production facade observations, never turn quick failure into success."""
import argparse
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
LENGTHS = [404, 209, 68, 707, 459]
PHASES = {"cold", "warmup", "warm"}
LIMIT_NS = 1_000_000_000


def cases():
    expected = json.loads((ROOT / "rust/tinyexpression-rs/tests/fixtures/fraud-acceptance.json").read_text())
    rows = []
    for line in (ROOT / "rust/tinyexpression-rs/tests/fixtures/fraud-alert.tsv").read_text().splitlines():
        if line and not line.startswith("#"):
            identifier, source = line.split("\t")
            rows.append({"id": identifier, "source": source, "sha256": hashlib.sha256(source.encode()).hexdigest(),
                         "cp": len(source), "if_count": 5 if identifier == "fraud#5" else 1})
    if len(expected) != 5 or len(rows) != 5 or [r["cp"] for r in rows] != LENGTHS:
        raise ValueError("original five fraud inputs changed")
    for row, pinned in zip(rows, expected):
        if any(row[key] != pinned[key] for key in ("id", "sha256", "cp", "if_count")):
            raise ValueError("pinned fraud identity changed")
    return {row["id"]: row for row in rows}


def nodes(value):
    if isinstance(value, dict):
        if isinstance(value.get("type"), str) and "fields" in value:
            yield value
        for child in value.values():
            yield from nodes(child)
    elif isinstance(value, list):
        for child in value:
            yield from nodes(child)


def comparison_view(value):
    """Only the pre-existing Rust compat.rs synthetic operand conversion; no source fallback."""
    if isinstance(value, list):
        return [comparison_view(child) for child in value]
    if not isinstance(value, dict):
        return value
    fields = value.get("fields")
    if value.get("type") == "BinaryExpr" and isinstance(fields, dict):
        if fields.get("left") is None and fields.get("right") == []:
            text = fields.get("op")
            if isinstance(text, list) and len(text) == 1 and isinstance(text[0], str):
                return text[0][1:-1] if text[0].startswith("'") and text[0].endswith("'") else text[0]
    return {key: comparison_view(child) for key, child in value.items()}


def branch_literal(node):
    while isinstance(node, dict):
        fields = node["fields"]
        if node["type"] == "BranchExpressionExpr":
            node = fields["value"]
        elif node["type"] == "BinaryExpr" and fields["op"] == [] and fields["right"] == []:
            node = fields["left"]
        else:
            break
    return node


def selected_conditional(raw, cp):
    node = raw
    while isinstance(node, dict) and node.get("span") == [0, cp]:
        kind, fields = node.get("type"), node.get("fields", {})
        if kind == "IfExpr":
            return node
        if kind == "FormulaExpr" and all(fields.get(key) == [] for key in ("imports", "declarations", "methods", "codeBlocks")):
            node = fields.get("expression")
        elif kind == "ExpressionExpr":
            node = fields.get("value")
        elif kind == "BinaryExpr" and fields.get("op") == [] and fields.get("right") == []:
            node = fields.get("left")
        else:
            break
    raise ValueError("invalid raw AST conditional projection")


def validate_row(row, case, backend):
    if row.get("backend") != backend or row.get("memo") != "ubnfc-default-packrat":
        raise ValueError("wrong production backend/memo policy")
    entry = "P4PreferredAstMapper.parseDetailed(_float)" if backend == "java-ubnfc" else "tinyexpression_rs::parse"
    if row.get("entry") != entry:
        raise ValueError("wrong production entry")
    if row.get("accepted") is not True or "diagnostic" not in row or row["diagnostic"] is not None:
        raise ValueError("not an explicit diagnostic-free success")
    actual_hash = row.get("sha256")
    if "input_utf8_hex" in row:
        actual_hash = hashlib.sha256(bytes.fromhex(row["input_utf8_hex"])).hexdigest()
    if row.get("cp") != case["cp"] or actual_hash != case["sha256"]:
        raise ValueError("input identity mismatch")
    elapsed = row.get("elapsed_ns")
    if type(elapsed) is not int or not 0 <= elapsed < LIMIT_NS:
        raise ValueError("parse+map did not complete in strictly less than one second")
    ast = row.get("ast")
    if not isinstance(ast, dict) or ast.get("type") != "IfExpr" or ast.get("span") != [0, case["cp"]]:
        raise ValueError("null, partial or wrong AST root")
    if backend == "rust-ubnfc" and selected_conditional(row.get("raw_ast"), case["cp"]) != ast:
        raise ValueError("selected conditional differs from raw production AST")
    for tree in [ast, row.get("raw_ast", ast)]:
        for node in nodes(tree):
            span = node.get("span")
            if (not isinstance(span, list) or len(span) != 2 or any(type(n) is not int for n in span)
                    or not 0 <= span[0] <= span[1] <= case["cp"]):
                raise ValueError("invalid code-point node span")
    view = comparison_view(ast)
    conditionals = [node for node in nodes(view) if node["type"] == "IfExpr"]
    if len(conditionals) != case["if_count"]:
        raise ValueError("conditional structure changed")
    expected_then = ["1", "1", "5", "5", "5"] if case["id"] == "fraud#5" else ["1"]
    if [branch_literal(node["fields"]["thenExpr"]) for node in conditionals] != expected_then:
        raise ValueError("numeric branch AST changed")
    if case["id"] != "fraud#5" and branch_literal(view["fields"]["elseExpr"]) != "0":
        raise ValueError("numeric else AST changed")
    return view


def compare(java, rust, corpus):
    observed = []
    for backend, rows in [("java-ubnfc", java), ("rust-ubnfc", rust)]:
        keyed = {}
        for row in rows:
            key = row.get("id"), row.get("phase")
            if key in keyed or key[0] not in corpus or key[1] not in PHASES:
                raise ValueError("duplicate or unknown observation")
            keyed[key] = validate_row(row, corpus[key[0]], backend)
        expected = {(identifier, phase) for identifier in corpus for phase in PHASES}
        if set(keyed) != expected:
            raise ValueError("missing production observation")
        for identifier in corpus:
            if not all(keyed[identifier, phase] == keyed[identifier, "cold"] for phase in PHASES):
                raise ValueError("AST/spans changed across phases")
        observed.append(keyed)
    if observed[0] != observed[1]:
        raise ValueError("Java/Rust production AST or source spans differ")
    return {"status": "passed", "observations": len(java) + len(rust), "measurement_eligible": False,
            "timing_kind": "shared-host-regression", "limit_ns": LIMIT_NS}


def command(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True, stderr=subprocess.STDOUT).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-report", type=Path, default=ROOT / "target/fraud-acceptance/java.jsonl")
    parser.add_argument("--rust-report", type=Path, default=ROOT / "target/fraud-acceptance/rust.jsonl")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--parser-commit", required=True, help="reported full source SHA installed into the isolated Maven repository")
    parser.add_argument("--parser-m2", type=Path, required=True, help="isolated Maven repo used for the Java build")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    summary = {"status": "failed", "measurement_eligible": False}
    try:
        if len(args.parser_commit) != 40 or any(c not in "0123456789abcdef" for c in args.parser_commit):
            raise ValueError("parser commit must be a full lowercase hexadecimal SHA")
        corpus = cases()
        reports = []
        for backend, path in [("java", args.java_report), ("rust", args.rust_report)]:
            raw = path.read_text()
            (args.output / f"{backend}.jsonl").write_text(raw)
            reports.append([json.loads(line) for line in raw.splitlines()])
        metadata = {"commit": command("git", "rev-parse", "HEAD"),
                    "reported_parser_commit": args.parser_commit,
                    "dirty": bool(command("git", "status", "--porcelain")),
                    "platform": platform.platform(), "hostname": platform.node(), "java": command("java", "-version"),
                    "rustc": command("rustc", "--version"), "maven": command("mvn", "-version"),
                    "concurrent_load": "uncontrolled; not exclusive-host attestation",
                    "cold_definition": "first observed call for this input; normal test-suite execution does not guarantee process-cold state",
                    "cases": list(corpus.values()), "artifacts": {}}
        for artifact in ("unlaxer-common", "unlaxer-dsl"):
            path = args.parser_m2 / "org/unlaxer" / artifact / "3.3.0" / f"{artifact}-3.3.0.jar"
            metadata["artifacts"][artifact] = hashlib.sha256(path.read_bytes()).hexdigest()
        for path in ["src/main/java/org/unlaxer/tinyexpression/p4/ubnfc/UBNFC_PIN", "rust/ubnfc-pin.txt",
                     "tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf"]:
            metadata.setdefault("source_identities", {})[path] = hashlib.sha256((ROOT / path).read_bytes()).hexdigest()
        (args.output / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
        summary = compare(*reports, corpus)
    except (ValueError, KeyError, TypeError, OSError, subprocess.CalledProcessError) as failure:
        summary["error"] = str(failure)
    (args.output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary))
    return 0 if summary["status"] == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
