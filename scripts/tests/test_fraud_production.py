import copy
import importlib.util
from pathlib import Path
import unittest
import json
import tempfile

spec = importlib.util.spec_from_file_location("fraud", Path(__file__).parents[1] / "check-fraud-production.py")
fraud = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fraud)


def leaf(text):
    return {"type": "BinaryExpr", "span": [1, 2], "fields": {"left": None, "op": [text], "right": []}}


def ast():
    return {"type": "IfExpr", "span": [0, 5], "fields": {
        "condition": "true", "thenExpr": leaf("1"), "elseExpr": leaf("0")}}


class ProductionEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.case = {"id": "fraud#1", "cp": 5, "sha256": "input-hash", "if_count": 1}
        self.row = {"id": "fraud#1", "phase": "cold", "backend": "java-ubnfc",
                    "entry": "P4PreferredAstMapper.parseDetailed(_float)", "memo": "ubnfc-default-packrat",
                    "sha256": "input-hash", "elapsed_ns": 10, "cp": 5, "accepted": True, "diagnostic": None, "ast": ast()}

    def test_one_second_is_strict_and_no_negative_duration(self):
        for elapsed in [-1, 1_000_000_000, 2_000_000_000, None, True]:
            with self.subTest(elapsed=elapsed), self.assertRaises(ValueError):
                fraud.validate_row(dict(self.row, elapsed_ns=elapsed), self.case, "java-ubnfc")

    def test_failure_null_partial_wrong_span_and_wrong_branch_fail(self):
        for change in [{"accepted": False}, {"diagnostic": {}}, {"ast": None}, {"cp": 4}]:
            with self.subTest(change=change), self.assertRaises(ValueError):
                fraud.validate_row(dict(self.row, **change), self.case, "java-ubnfc")
        for change in ["root", "nested", "branch"]:
            row = copy.deepcopy(self.row)
            if change == "root": row["ast"]["span"] = [0, 4]
            if change == "nested": row["ast"]["fields"]["thenExpr"]["span"] = [1, 6]
            if change == "branch": row["ast"]["fields"]["thenExpr"]["fields"]["op"] = ["9"]
            with self.subTest(change=change), self.assertRaises(ValueError):
                fraud.validate_row(row, self.case, "java-ubnfc")

    def test_compat_projection_only_folds_exact_synthetic_operand(self):
        self.assertEqual("1", fraud.comparison_view(leaf("1")))
        wrong = leaf("1")
        wrong["fields"]["op"] = ["1", "+"]
        self.assertIsInstance(fraud.comparison_view(wrong), dict)

    def test_raw_projection_cannot_hide_declarations_or_a_different_ast(self):
        valid = {"type": "FormulaExpr", "span": [0, 5], "fields": {
            "imports": [], "declarations": [], "methods": [], "codeBlocks": [], "expression": ast()}}
        self.assertEqual(ast(), fraud.selected_conditional(valid, 5))
        valid["fields"]["declarations"] = ["unexpected"]
        with self.assertRaises(ValueError): fraud.selected_conditional(valid, 5)
        row = dict(self.row, backend="rust-ubnfc", entry="tinyexpression_rs::parse", raw_ast=None)
        with self.assertRaises(ValueError): fraud.validate_row(row, self.case, "rust-ubnfc")

    def test_ci_provenance_distinguishes_snapshot_pin_from_published_and_local(self):
        commit = "a" * 40
        self.assertEqual("reported-local-source", fraud.dependency_provenance("3.3.0", commit, None)["origin"])
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "dependency.json"
            for value in [
                {"version": "3.3.0-SNAPSHOT", "origin": "pinned-source", "source_commit": commit},
                {"version": "3.3.0", "origin": "maven-central", "source_commit": None},
            ]:
                path.write_text(json.dumps(value))
                self.assertEqual(value, fraud.dependency_provenance("ignored", None, path))
            for value in [
                [],
                {"version": "../3.3.0", "origin": "pinned-source", "source_commit": commit},
                {"version": "3.3.0", "origin": "maven-central", "source_commit": commit},
                {"version": "3.3.0", "origin": "pinned-source", "source_commit": "short"},
                {"version": "3.3.0", "origin": "unknown", "source_commit": None},
            ]:
                path.write_text(json.dumps(value))
                with self.subTest(value=value), self.assertRaises(ValueError):
                    fraud.dependency_provenance("ignored", None, path)

    def test_missing_duplicate_and_cross_language_difference_fail(self):
        java = [dict(self.row, phase=phase) for phase in sorted(fraud.PHASES)]
        rust = [dict(row, backend="rust-ubnfc", entry="tinyexpression_rs::parse", raw_ast=row["ast"]) for row in java]
        corpus = {"fraud#1": self.case}
        self.assertEqual("passed", fraud.compare(java, rust, corpus)["status"])
        for rows in [rust[:2], rust + rust[:1]]:
            with self.assertRaises(ValueError): fraud.compare(java, rows, corpus)
        changed = copy.deepcopy(rust)
        for row in changed:
            row["ast"]["fields"]["condition"] = "false"
            row["raw_ast"] = row["ast"]
        with self.assertRaises(ValueError): fraud.compare(java, changed, corpus)


if __name__ == "__main__":
    unittest.main()
