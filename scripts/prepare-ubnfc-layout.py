#!/usr/bin/env python3
"""Lower only pinned std/layout 1.0.0 to ubnfc's existing javaStyle policy.

This is a TinyExpression compatibility bridge, not a package resolver. It never
reads credentials or downloads anything, and refuses every other named policy.
"""
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys

STANDARD_HASH = "2701f6884ea6c712d28c76ec3d340c3b27fca4575846239bd0854933205f4b67"
STANDARD_TEXT = (
    "grammar StandardLayout {\n  @ubnf: v2\n"
    "  token SPACES ::= (' ' | CHAR_RANGE('\\t', '\\r'))+;\n"
    "  token LINE_COMMENT ::= '//' NEGATION('\\r\\n')*;\n"
    "  token BLOCK_COMMENT ::= '/*' (NEGATIVE_LOOKAHEAD('*/') ANY)* '*/';\n"
    "  token SPACES_AND_COMMENTS ::= SPACES | LINE_COMMENT | BLOCK_COMMENT;\n}\n"
)


def strict_object(pairs):
    result = {}
    for name, value in pairs:
        if name in result:
            raise ValueError("duplicate JSON key")
        if name == "schemaVersion" and type(value) is not int:
            raise ValueError("schemaVersion must be an integer")
        result[name] = value
    return result


def read_json(path):
    def invalid_constant(value):
        raise ValueError("invalid JSON numeric constant")
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=strict_object,
                      parse_constant=invalid_constant)


def prepare(source, destination):
    base = source.parent
    expected_dependency = {"version": "1.0.0", "source": "builtin:std/layout@1.0.0"}
    if read_json(base / "ubnf.json") != {
        "schemaVersion": 1, "dependencies": {"std/layout": expected_dependency}
    }:
        raise ValueError("only the exact builtin std/layout dependency is supported")
    if read_json(base / "ubnf.lock.json") != {
        "schemaVersion": 1, "roots": {"std/layout": "1.0.0"},
        "packages": {"std/layout": {"id": "std/layout", "version": "1.0.0",
                                  "sha256": STANDARD_HASH, "dependencies": {}}}
    }:
        raise ValueError("layout lock identity or dependency graph differs")
    artifact_path = base / ".ubnf-cache" / "packages" / (STANDARD_HASH + ".json")
    artifact = artifact_path.read_bytes()
    if hashlib.sha256(artifact).hexdigest() != STANDARD_HASH:
        raise ValueError("layout artifact SHA-256 mismatch")
    if read_json(artifact_path) != {
        "schemaVersion": 1, "id": "std/layout", "version": "1.0.0",
        "entry": "layout.ubnf", "files": {"layout.ubnf": STANDARD_TEXT}, "dependencies": {}
    }:
        raise ValueError("layout definitions differ from the javaStyle compatibility set")

    text = source.read_text(encoding="utf-8")
    imported = "  @import layout from 'pkg:std/layout'\n\n"
    global_policy = "@whitespace: layout.SPACES_AND_COMMENTS"
    local_policy = "@whitespace(layout.SPACES_AND_COMMENTS)"
    if text.count(imported) != 1 or text.count(global_policy) != 1 or text.count(local_policy) != 3:
        raise ValueError("P4 layout policy scopes differ from the verified migration")
    lowered = text.replace(imported, "").replace(global_policy, "@whitespace: javaStyle")
    lowered = lowered.replace(local_policy, "@interleave(profile=javaStyle)")
    # Reject mixed/custom policies and extra package imports before invoking ubnfc.
    if "pkg:" in lowered or "@comment" in lowered:
        raise ValueError("additional package/comment configuration is unsupported")
    if re.findall(r"@whitespace\s*:\s*(\S+)", lowered) != ["javaStyle"]:
        raise ValueError("mixed or unknown global whitespace policy")
    if re.search(r"@whitespace\s*\(", lowered):
        raise ValueError("additional local whitespace policy is unsupported")
    if re.findall(r"@interleave\s*\(([^)]*)\)", lowered) != ["profile=javaStyle"] * 3:
        raise ValueError("mixed or unknown interleave policy")
    # Keep imported module paths identical in the derived tree; no external files.
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(base, destination.parent, dirs_exist_ok=True)
    destination.write_text(lowered, encoding="utf-8")
    return hashlib.sha256(lowered.encode("utf-8")).hexdigest()


if __name__ == "__main__":
    try:
        if len(sys.argv) != 3:
            raise ValueError("usage: prepare-ubnfc-layout.py SOURCE.ubnf DESTINATION.ubnf")
        print(prepare(Path(sys.argv[1]), Path(sys.argv[2])))
    except (ValueError, OSError, KeyError) as error:
        print("E-UBNFC-LAYOUT: " + str(error), file=sys.stderr)
        sys.exit(1)
