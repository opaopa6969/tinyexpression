"""Exercise installer provenance branches without network/Maven or shared repository writes."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "ci/install-unlaxer-if-unpublished.sh"
PIN = "f" * 40


class InstallerProvenanceTest(unittest.TestCase):
    def run_installer(self, central, failed=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repo = root / "project"
            script = repo / "scripts/ci" / SCRIPT.name
            script.parent.mkdir(parents=True)
            shutil.copy2(SCRIPT, script)
            (repo / ".github").mkdir()
            (repo / ".github/unlaxer-source-pin").write_text(f"commit={PIN}\n")
            version = "3.3.0" if central else "3.3.0-SNAPSHOT"
            (repo / "pom.xml").write_text(f"<unlaxer.version>{version}</unlaxer.version>\n")
            stubs = root / "bin"
            stubs.mkdir()
            stub = f'''#!{sys.executable}
import os
from pathlib import Path
import sys
name=Path(sys.argv[0]).name
args=sys.argv[1:]
if name == 'curl': sys.exit(0 if os.environ['MOCK_CENTRAL'] == 'yes' else 22)
if name == 'mvn': sys.exit(int(os.environ['MOCK_MVN_EXIT']))
if name == 'git':
    if args[0] == 'init': Path(args[-1]).mkdir(parents=True,exist_ok=True)
    elif args[2] == 'fetch': (Path(args[1])/'pom.xml').write_text('<revision>3.3.0-SNAPSHOT</revision>')
    elif args[2] == 'rev-parse': print('{PIN}')
'''
            for name in ("curl", "git", "mvn"):
                path = stubs / name
                path.write_text(stub)
                path.chmod(0o755)
            runner_temp = root / "runner"
            runner_temp.mkdir()
            provenance = root / "evidence/dependency.json"
            provenance.parent.mkdir(parents=True)
            provenance.write_text('{"origin":"stale-success"}\n')
            environment = dict(os.environ, PATH=str(stubs) + os.pathsep + os.environ["PATH"],
                               MAVEN_REPO=str(root / "m2"), RUNNER_TEMP=str(runner_temp),
                               UNLAXER_PROVENANCE_FILE=str(provenance), MOCK_CENTRAL="yes" if central else "no",
                               MOCK_MVN_EXIT="1" if failed else "0", GITHUB_STEP_SUMMARY="")
            result = subprocess.run(["bash", str(script)], env=environment, capture_output=True, text=True)
            return result.returncode, json.loads(provenance.read_text()) if provenance.exists() else None

    def test_published_artifacts_do_not_claim_the_source_pin(self):
        code, provenance = self.run_installer(True)
        self.assertEqual(0, code)
        self.assertEqual({"version": "3.3.0", "origin": "maven-central", "source_commit": None}, provenance)

    def test_unpublished_snapshot_records_actual_checked_out_commit(self):
        code, provenance = self.run_installer(False)
        self.assertEqual(0, code)
        self.assertEqual({"version": "3.3.0-SNAPSHOT", "origin": "pinned-source", "source_commit": PIN}, provenance)

    def test_failed_install_cannot_publish_success_provenance(self):
        code, provenance = self.run_installer(False, failed=True)
        self.assertNotEqual(0, code)
        self.assertIsNone(provenance)


if __name__ == "__main__":
    unittest.main()
