#!/usr/bin/env python3
"""Offline unit tests for tools/check_secrets_hygiene.py (REQ-PRO-124 / TST-PRO-124)."""

import os
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import check_secrets_hygiene as csh


class TestCheckSecretsHygiene(unittest.TestCase):
    """Covers secret-pattern detection, narrow allowlisting, and 0600 permission hygiene."""

    def test_detects_real_secret_patterns(self):
        fake_google = "AIza" + "SyRealTestKey9876543210abcdef12345"
        fake_aws = "AKIA" + "IOSFODNN7EXAMPLE"
        fake_gh = "ghp_" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6"
        fake_slack = "xoxb-" + "1234567890-1234567890-abcdef123456"
        fake_atatt = "ATATT3x" + "FfGF0AbCdEfGhIjKlMnOpQrStUvWxYz123456"
        fake_pem = "-----BEGIN " + "RSA PRIVATE KEY-----"
        fake_basic = "Basic " + "YWxpY2U6c3VwZXJzZWNyZXRwYXNzd29yZA=="
        fake_assign = "STRAVA_CLIENT_SECRET=" + "272b5ca4ba09a932e73ef2574162f04d7f41a643"

        for sample in (
            fake_google,
            fake_aws,
            fake_gh,
            fake_slack,
            fake_atatt,
            fake_pem,
            fake_basic,
            fake_assign,
        ):
            findings = csh.scan_text("app/src/main/SomeFile.kt", sample)
            self.assertGreaterEqual(len(findings), 1, f"Expected detection for sample: {sample[:10]}")

    def test_allowlists_scoped_to_authorized_paths(self):
        placeholder_maps = "MAPS_API_KEY=" + "AIza" + "SyPlaceholderMapsApiKeyForBuildTestingOnly00"
        self.assertEqual(csh.scan_text("secrets.defaults.properties", placeholder_maps), [])
        self.assertEqual(csh.scan_text("app/build.gradle", placeholder_maps), [])
        self.assertEqual(csh.scan_text("docs/engineering/plans/ATT-4004_plan.md", placeholder_maps), [])

        strava_fixture = 'val input = "client_secret=' + 'supersecret123&other=val"'
        self.assertEqual(
            csh.scan_text(
                "app/src/test/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaHelperTest.kt",
                strava_fixture,
            ),
            [],
        )

        # Copying a non-benign allowlisted fixture token to an unauthorized path must fail.
        unauthorized_basic = "Basic " + "dGVzdF91c2VyOnNlY3JldF90b2tlbg=="
        self.assertEqual(csh.scan_text("tools/test_jira_accounts.py", unauthorized_basic), [])
        self.assertEqual(len(csh.scan_text("app/src/main/Leaked.kt", unauthorized_basic)), 1)

    def test_benign_placeholders_and_redacted_lines_pass(self):
        benign_text = "\n".join(
            [
                "MAPS_API_KEY=ci-dummy-key",
                "STRAVA_CLIENT_SECRET=ci-dummy-secret",
                'public static final String MAPS_API_KEY = "[REDACTED — rotate key]";',
                "RELEASE_KEYSTORE_PASSWORD: ${{ secrets.RELEASE_KEYSTORE_PASSWORD }}",
            ]
        )
        self.assertEqual(csh.scan_text("docs/engineering/walkthroughs/ATT-4004_walkthrough.md", benign_text), [])

    def test_local_permissions_check_and_fix_lifecycle(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            ws = Path(tmpdir)
            (ws / "app").mkdir()
            targets = [
                ws / ".env.claude",
                ws / ".env.gemini",
                ws / ".env.jira",
                ws / ".env.jira.agent1",
                ws / "secrets.properties",
                ws / "release.properties",
                ws / "app" / "google-services.json",
            ]
            for f in targets:
                f.write_text("SECRET=value\n", encoding="utf-8")
                os.chmod(f, 0o664)

            example_file = ws / ".env.jira.example"
            example_file.write_text("JIRA_URL=https://example.atlassian.net\n", encoding="utf-8")
            os.chmod(example_file, 0o644)

            violations = csh.check_local_permissions(ws)
            self.assertEqual(len(violations), len(targets))
            self.assertEqual(csh.main(["--check-local-perms", "--workspace-dir", str(ws)]), 1)

            self.assertEqual(csh.main(["--fix-local-perms", "--workspace-dir", str(ws)]), 0)
            self.assertEqual(csh.check_local_permissions(ws), [])
            self.assertEqual(csh.main(["--check-local-perms", "--workspace-dir", str(ws)]), 0)

            for f in targets:
                self.assertEqual(stat.S_IMODE(os.stat(f).st_mode), 0o600)
            # Example template file must remain untouched.
            self.assertEqual(stat.S_IMODE(os.stat(example_file).st_mode), 0o644)

    def test_scan_repository_in_temp_git_repo(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            subprocess.run(["git", "-C", str(repo), "init"], check=True, capture_output=True)
            clean_file = repo / "README.md"
            clean_file.write_text("Clean repository\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(repo), "add", "README.md"], check=True, capture_output=True)
            self.assertEqual(csh.main(["--repo-root", str(repo)]), 0)

            leak_file = repo / "leak.txt"
            leak_file.write_text("token=" + "ghp_" + "1234567890abcdef1234567890abcdef1234\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(repo), "add", "leak.txt"], check=True, capture_output=True)
            self.assertEqual(csh.main(["--repo-root", str(repo)]), 1)

    def test_live_repository_scan_passes(self):
        findings = csh.scan_repository()
        self.assertEqual(findings, [], f"Unexpected secret findings in live repository: {findings}")


if __name__ == "__main__":
    unittest.main()
