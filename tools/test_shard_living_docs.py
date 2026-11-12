#!/usr/bin/env python3
"""
test_shard_living_docs.py

Automated unit and regression test suite verifying living documentation sharding
tooling (tools/shard_living_docs.py) per REQ-PRO-065 and TST-PRO-065.

Scenarios tested:
  1. Tokenizer Code-Span Immunity & Escaped Pipe Handling (TST-PRO-065.1).
  2. Pre-Fix Regression Reproduction: Verifies naive row.split("|") corrupts
     Tests column on REQ-EXP-021 snippet while split_markdown_table_row preserves it (TST-PRO-065.1).
  3. 6-Column and 7-Column Requirement and Test Row Parsing (TST-PRO-065.2).
  4. Master Index Overwrite Safety Guard and --force Flag (TST-PRO-065.3).
  5. Shard Verification Mode (--verify-only) (TST-PRO-065.4).
"""

import os
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from tools.shard_living_docs import (
    clean_multiline_text,
    format_requirement_section,
    format_test_section,
    parse_requirements_file,
    parse_tests_file,
    split_markdown_table_row,
)


class TestShardLivingDocs(unittest.TestCase):

    def test_split_simple_row(self):
        """Standard row with 6 columns splits into 6 items."""
        row = "| REQ-001 | Description | Rationale | Impl.kt | TST-001 | Verified |"
        parts = split_markdown_table_row(row)
        self.assertEqual(len(parts), 6)
        self.assertEqual(parts[0], "REQ-001")
        self.assertEqual(parts[4], "TST-001")
        self.assertEqual(parts[5], "Verified")

    def test_split_row_with_code_span_pipes(self):
        """Pipes inside single and double backtick code spans are ignored as cell delimiters."""
        row = "| REQ-002 | Check `code == 200 || code == 201` logic | Why | ``val x = a | b`` | TST-002 | Verified |"
        parts = split_markdown_table_row(row)
        self.assertEqual(len(parts), 6)
        self.assertEqual(parts[1], "Check `code == 200 || code == 201` logic")
        self.assertEqual(parts[3], "``val x = a | b``")
        self.assertEqual(parts[4], "TST-002")

    def test_split_row_with_escaped_pipe(self):
        """Escaped pipes (\\|) are ignored as cell delimiters."""
        row = r"| REQ-003 | Option A \| Option B | Rationale | Impl.kt | TST-003 | Verified |"
        parts = split_markdown_table_row(row)
        self.assertEqual(len(parts), 6)
        self.assertIn(r"Option A \| Option B", parts[1])
        self.assertEqual(parts[4], "TST-003")

    def test_pre_fix_regression_reproduction_req_exp_021(self):
        """
        Reproduce the exact corruption from commit 06990c88 on REQ-EXP-021:
        Naive row.split("|") splits inside `code == 200 || code == 201` check,
        producing 9 parts and assigning a sentence fragment to the Tests column.
        split_markdown_table_row fixes this, preserving exact 7 columns and intact Tests field.
        """
        req_exp_021_row = (
            "| **REQ-EXP-021** | **Strava Upload Transient Error Resilient Retry, Polling Fault Tolerance & Pre-Upload Duplicate False-Start Protection.** "
            "| The system SHALL reliably classify HTTP response codes and transient network errors during Strava workout uploads, "
            "replacing a binary `code == 200 || code == 201` check that inadvertently classified rate limits and server outages as permanent fatal errors, "
            "and polling aborted on a single glitch. | Harden Strava upload retry classification on 429/5xx. "
            "| `StravaUploader.kt`, `StravaUploadDbHelper.java` | `TST-EXP-018` | Verified |"
        )

        # 1. Proves reproduction: naive split yields 9 parts and corrupts Tests field
        naive_parts = [p.strip() for p in req_exp_021_row.split("|")[1:-1]]
        self.assertEqual(len(naive_parts), 9, "Pre-fix naive split must yield 9 parts due to || splitting")
        # In pre-fix code: tests_raw was parts[4]
        corrupted_tests = naive_parts[4]
        self.assertTrue(
            corrupted_tests.startswith("code == 201` check"),
            f"Pre-fix naive split must reproduce corrupted Tests field starting with 'code == 201` check', got: {corrupted_tests}",
        )

        # 2. Proves fix: split_markdown_table_row yields exactly 7 parts with intact Tests field
        fixed_parts = split_markdown_table_row(req_exp_021_row)
        self.assertEqual(len(fixed_parts), 7, "Fixed parser must yield exactly 7 columns")
        self.assertEqual(fixed_parts[0], "**REQ-EXP-021**")
        self.assertEqual(
            fixed_parts[1],
            "**Strava Upload Transient Error Resilient Retry, Polling Fault Tolerance & Pre-Upload Duplicate False-Start Protection.**",
        )
        self.assertIn("binary `code == 200 || code == 201` check", fixed_parts[2])
        self.assertEqual(fixed_parts[3], "Harden Strava upload retry classification on 429/5xx.")
        self.assertEqual(fixed_parts[4], "`StravaUploader.kt`, `StravaUploadDbHelper.java`")
        self.assertEqual(fixed_parts[5], "`TST-EXP-018`")
        self.assertEqual(fixed_parts[6], "Verified")

    def test_parse_requirements_file_6_and_7_columns(self):
        """Verifies parsing of both 6-column and 7-column requirements rows."""
        markdown_content = """# Test Requirements
| ID | Description | Rationale | Implementation File(s) | Verification ID | Status |
|:---|:---|:---|:---|:---|:---|
| **REQ-PRO-001** | **Standard Six Column Format.** Normal description. | Rationale 1 | `File1.kt` | `TST-PRO-001` | Verified |
| **REQ-PRO-002** | **Explicit Seven Column Title.** | Explicit Description body with `a || b` code. | Rationale 2 | `File2.kt` | `TST-PRO-002` | Planned |
"""
        with tempfile.NamedTemporaryFile("w+", suffix=".md", delete=False) as tf:
            tf.write(markdown_content)
            temp_path = tf.name

        try:
            reqs = parse_requirements_file(temp_path)
            self.assertEqual(len(reqs), 2)

            # Check 6-column row
            r1 = reqs[0]
            self.assertEqual(r1["id"], "REQ-PRO-001")
            self.assertEqual(r1["title"], "Standard Six Column Format")
            self.assertEqual(r1["rationale"], "Rationale 1")
            self.assertEqual(r1["implementation"], "`File1.kt`")
            self.assertEqual(r1["tests"], "`TST-PRO-001`")
            self.assertEqual(r1["status"], "Verified")

            # Check 7-column row
            r2 = reqs[1]
            self.assertEqual(r2["id"], "REQ-PRO-002")
            self.assertEqual(r2["title"], "Explicit Seven Column Title")
            self.assertEqual(r2["description"], "Explicit Description body with `a || b` code.")
            self.assertEqual(r2["rationale"], "Rationale 2")
            self.assertEqual(r2["implementation"], "`File2.kt`")
            self.assertEqual(r2["tests"], "`TST-PRO-002`")
            self.assertEqual(r2["status"], "Planned")
        finally:
            if os.path.exists(temp_path):
                os.remove(temp_path)

    def test_parse_tests_file_with_code_span_pipes(self):
        """Verifies parsing of test cases containing pipes in code spans."""
        markdown_content = """# Test Specifications
| ID | Jira | Component | Requirement(s) | Test Description | Status |
|:---|:---|:---|:---|:---|:---|
| **TST-PRO-001** | `ATT-100` | Process | `REQ-PRO-001` | Verify `val flag = a | b` bitwise logic | Verified |
"""
        with tempfile.NamedTemporaryFile("w+", suffix=".md", delete=False) as tf:
            tf.write(markdown_content)
            temp_path = tf.name

        try:
            tests = parse_tests_file(temp_path)
            self.assertEqual(len(tests), 1)
            t1 = tests[0]
            self.assertEqual(t1["id"], "TST-PRO-001")
            self.assertEqual(t1["component"], "Process")
            self.assertEqual(t1["requirements"], "`REQ-PRO-001`")
            self.assertIn("`val flag = a | b`", t1["description"])
            self.assertEqual(t1["status"], "Verified")
        finally:
            if os.path.exists(temp_path):
                os.remove(temp_path)

    def test_master_index_overwrite_guard(self):
        """Verifies safety guard aborts when docs/requirements.md is a Master Index."""
        with tempfile.TemporaryDirectory() as temp_dir:
            docs_dir = os.path.join(temp_dir, "docs")
            os.makedirs(docs_dir, exist_ok=True)
            req_index = os.path.join(docs_dir, "requirements.md")
            tst_index = os.path.join(docs_dir, "tests.md")

            with open(req_index, "w") as f:
                f.write("# aTrainingTracker: Living Requirements Specification (Master Index)\n\n| Shard | Scope |\n")
            with open(tst_index, "w") as f:
                f.write("# aTrainingTracker: Living Test Specification (Master Index)\n\n| Shard | Scope |\n")

            script_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "shard_living_docs.py"))

            # 1. Run without --force: must fail with exit code 1
            proc = subprocess.run(
                [sys.executable, script_path, "--repo-root", temp_dir],
                capture_output=True,
                text=True,
            )
            self.assertEqual(proc.returncode, 1)
            self.assertIn("already a Master Index", proc.stderr)

            # 2. Run with --force: executes (will fail on empty reqs unless handled, but guard is bypassed)
            proc_force = subprocess.run(
                [sys.executable, script_path, "--repo-root", temp_dir, "--force"],
                capture_output=True,
                text=True,
            )
            self.assertNotIn("already a Master Index", proc_force.stderr)

    def test_verify_only_mode(self):
        """Verifies that --verify-only succeeds on the real repository."""
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
        script_path = os.path.join(repo_root, "tools", "shard_living_docs.py")
        proc = subprocess.run(
            [sys.executable, script_path, "--repo-root", repo_root, "--verify-only"],
            capture_output=True,
            text=True,
        )
        self.assertEqual(proc.returncode, 0, f"--verify-only failed: {proc.stderr}")
        self.assertIn("PASS: All 18 domain shards exist.", proc.stdout)


if __name__ == "__main__":
    unittest.main()
