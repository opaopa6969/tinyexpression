import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
SPEC = importlib.util.spec_from_file_location("layout", ROOT / "scripts/prepare-ubnfc-layout.py")
LAYOUT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(LAYOUT)


class LayoutCompatibilityTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.base = Path(self.temporary.name)
        shutil.copytree(ROOT / "tools/tinyexpression-p4-lsp-vscode/grammar", self.base / "grammar")
        self.source = self.base / "grammar/tinyexpression-p4.ubnf"
        self.destination = self.base / "derived/tinyexpression-p4.ubnf"

    def tearDown(self):
        self.temporary.cleanup()

    def rejects(self):
        with self.assertRaises((ValueError, OSError)):
            LAYOUT.prepare(self.source, self.destination)
        self.assertFalse(self.destination.exists(), "reject before publishing the derived grammar")

    def test_exact_snapshot_restores_the_pinned_old_grammar(self):
        self.assertEqual("4a02d31de4e646a0664fd78a4b77ff2e145e7af7dde83a209e244e38298e962a",
                         LAYOUT.prepare(self.source, self.destination))
        self.assertNotIn("pkg:", self.destination.read_text())

    def test_custom_mixed_and_missing_policies_are_rejected(self):
        original = self.source.read_text()
        for changed in [
            original.replace("layout.SPACES_AND_COMMENTS", "layout.SPACES"),
            original.replace("@whitespace: layout.SPACES_AND_COMMENTS", "@whitespace: custom.GAP"),
            original + "\n@whitespace: none\n",
            original + "\n@whitespace(none)\n",
            original + "\n@interleave(profile=commentsAndSpaces)\n",
            original + "\n@comment: hidden\n",
            original + "\n@import extra from 'pkg:team/layout'\n",
            original.replace("@whitespace(layout.SPACES_AND_COMMENTS)", "", 1),
        ]:
            with self.subTest(changed=changed[-60:]):
                self.source.write_text(changed)
                self.rejects()

    def test_manifest_version_and_source_are_exact(self):
        path = self.source.parent / "ubnf.json"
        for dependency in [{"version": "1.0.1", "source": "builtin:std/layout@1.0.0"},
                           {"version": "1.0.0", "source": "https://example.test/layout.json"}]:
            path.write_text(json.dumps({"schemaVersion": 1, "dependencies": {"std/layout": dependency}}))
            self.rejects()

    def test_identity_hash_graph_and_artifact_tampering_are_rejected(self):
        lock = self.source.parent / "ubnf.lock.json"
        original = json.loads(lock.read_text())
        for key, value in [("id", "team/layout"), ("version", "1.0.1"), ("sha256", "0" * 64),
                           ("dependencies", {"team/layout": "1.0.0"})]:
            changed = json.loads(json.dumps(original))
            changed["packages"]["std/layout"][key] = value
            lock.write_text(json.dumps(changed))
            self.rejects()
        lock.write_text(json.dumps(original))
        cache = self.source.parent / ".ubnf-cache/packages" / (LAYOUT.STANDARD_HASH + ".json")
        cache.write_bytes(cache.read_bytes() + b" ")
        self.rejects()

    def test_non_integer_schema_is_rejected(self):
        (self.source.parent / "ubnf.json").write_text('{"schemaVersion":true}')
        self.rejects()

    def test_duplicate_json_keys_are_rejected(self):
        (self.source.parent / "ubnf.json").write_text('{"schemaVersion":1,"schemaVersion":1}')
        self.rejects()


if __name__ == "__main__":
    unittest.main()
