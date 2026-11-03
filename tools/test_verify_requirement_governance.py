#!/usr/bin/env python3
"""
test_verify_requirement_governance.py

Automated unit test suite verifying Chesterton's Fence requirement archaeology governance
tooling (tools/verify_requirement_governance.py) per REQ-PRO-022 and TST-PRO-015.

Scenarios tested:
  1. Valid Full Archaeology: Modifying an existing requirement with all 4 mandatory fields passes (exit code 0).
  2. Missing / Blank Field: Missing or empty fields fail with exit code 1 and schema diagnostics.
  3. Net-New Requirement Invariance: Novel requirements added without base presence bypass archaeology cleanly.
  4. Diff Hunk Parsing: Accurately distinguishes modified vs added vs deleted requirement rows.
  5. Standard Library Footprint Invariant: Zero external pip dependencies; imports only standard library.
"""

import os
import subprocess
import sys
import unittest

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from tools.verify_requirement_governance import (
    parse_diff_requirements,
    validate_archaeology_section,
    verify_governance,
)


class TestRequirementGovernance(unittest.TestCase):

    def setUp(self):
        self.valid_archaeology_markdown = """
## Stage 2: Test Specification & Requirements

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: REQ-TRK-002 (Location Foreground Service Resilience)
* `Historical Origin & Commit Trace`: Commit 8a2f1b0 (ATT-622), fix for Android 14 SecurityException.
* `Root Reason for Existing Formulation`: In Android 14 (API 34), promoting to location foreground service from background without while-in-use permission throws SecurityException. The requirement mandated fallback to stopSelf() with data preservation.
* `Preservation of Core Invariants`: Safe to modify now because new foreground service delegate guarantees while-in-use context before promotion; START_STICKY and SQLite data preservation remain 100% intact.

### 1. Synchronized Requirement Specification
| **REQ-TRK-002** | Refined Location FGS | ... |
"""

        self.valid_archaeology_jira = """
h2. Stage 2: Test Specification & Requirements

h3. Requirement Archaeology & Chesterton's Fence Audit
* *Original Requirement ID & Target*: REQ-TRK-002 (Location Foreground Service Resilience)
* *Historical Origin & Commit Trace*: Commit 8a2f1b0 (ATT-622), fix for Android 14 SecurityException.
* *Root Reason for Existing Formulation*: In Android 14 (API 34), promoting to location foreground service from background without while-in-use permission throws SecurityException.
* *Preservation of Core Invariants*: Safe to modify now because new delegate guarantees while-in-use context before promotion; START_STICKY and SQLite data preservation remain intact.

h3. 1. Synchronized Requirement Specification
"""

        self.missing_field_markdown = """
### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: REQ-TRK-002
* `Historical Origin & Commit Trace`: Commit 8a2f1b0
* `Root Reason for Existing Formulation`: SecurityException on Android 14.
"""

        self.empty_field_markdown = """
### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: REQ-TRK-002
* `Historical Origin & Commit Trace`: None
* `Root Reason for Existing Formulation`: TODO
* `Preservation of Core Invariants`: 
"""

    def test_scenario_1_valid_full_archaeology(self):
        """Scenario 1: Valid deliverable with all 4 mandatory fields passes validation."""
        valid_md, errors_md = validate_archaeology_section(self.valid_archaeology_markdown)
        self.assertTrue(valid_md, f"Expected markdown archaeology to pass, errors: {errors_md}")
        self.assertEqual(len(errors_md), 0)

        valid_jira, errors_jira = validate_archaeology_section(self.valid_archaeology_jira)
        self.assertTrue(valid_jira, f"Expected Jira archaeology to pass, errors: {errors_jira}")
        self.assertEqual(len(errors_jira), 0)

    def test_scenario_2_missing_or_empty_mandatory_field(self):
        """Scenario 2: Missing or placeholder fields fail validation with descriptive diagnostics."""
        # Missing Preservation of Core Invariants
        valid_missing, errors_missing = validate_archaeology_section(self.missing_field_markdown)
        self.assertFalse(valid_missing)
        self.assertTrue(any("Preservation of Core Invariants" in e for e in errors_missing))

        # Empty and placeholder values
        valid_empty, errors_empty = validate_archaeology_section(self.empty_field_markdown)
        self.assertFalse(valid_empty)
        self.assertTrue(len(errors_empty) >= 2)

    def test_scenario_3_net_new_requirement_invariance(self):
        """Scenario 3: Net-new requirements bypass archaeology checks cleanly."""
        diff_net_new = """
diff --git a/docs/requirements.md b/docs/requirements.md
index 1234567..89abcdef 100644
--- a/docs/requirements.md
+++ b/docs/requirements.md
@@ -400,6 +400,7 @@
+| **REQ-PRO-999** | **Novel Autonomous Pipeline.** | Brand new requirement that did not exist before. |
"""
        modified, added, deleted = parse_diff_requirements(diff_net_new)
        self.assertEqual(len(modified), 0)
        self.assertEqual(added, {"REQ-PRO-999"})
        self.assertEqual(len(deleted), 0)

        success, diagnostics = verify_governance(
            deliverable_text="Net new requirement without archaeology",
            diff_text=diff_net_new,
        )
        self.assertTrue(success)
        self.assertTrue(any("Net-new requirement" in d for d in diagnostics))

    def test_scenario_4_diff_hunk_detection_modified_vs_added(self):
        """Scenario 4: Accurately identifies modified requirement rows when lines are altered."""
        diff_modified = """
diff --git a/docs/requirements.md b/docs/requirements.md
index 1234567..89abcdef 100644
--- a/docs/requirements.md
+++ b/docs/requirements.md
@@ -210,3 +210,3 @@
-| **REQ-UI-061** | **Unified Deletion UI.** | Old phrasing. |
+| **REQ-UI-061** | **Unified Deletion UI.** | New refined phrasing with modified constraints. |
"""
        modified, added, deleted = parse_diff_requirements(diff_modified)
        self.assertEqual(modified, {"REQ-UI-061"})
        self.assertEqual(len(added), 0)
        self.assertEqual(len(deleted), 0)

        # Fails when deliverable lacks archaeology
        success_fail, diag_fail = verify_governance(
            deliverable_text="Some deliverable lacking archaeology",
            diff_text=diff_modified,
        )
        self.assertFalse(success_fail)
        self.assertTrue(any("Modified/Altered existing requirement" in d for d in diag_fail))

        # Passes when deliverable contains valid archaeology
        success_pass, diag_pass = verify_governance(
            deliverable_text=self.valid_archaeology_markdown,
            diff_text=diff_modified,
        )
        self.assertTrue(success_pass)

    def test_scenario_5_standard_library_footprint_invariant(self):
        """Scenario 5: Verify tools/verify_requirement_governance.py uses only standard library."""
        import ast

        script_path = os.path.join(
            os.path.dirname(__file__),
            "verify_requirement_governance.py",
        )
        with open(script_path, "r", encoding="utf-8") as f:
            tree = ast.parse(f.read(), filename=script_path)

        imported_modules = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    imported_modules.add(alias.name.split(".")[0])
            elif isinstance(node, ast.ImportFrom):
                if node.module:
                    imported_modules.add(node.module.split(".")[0])

        # Allowed standard library modules
        std_lib_modules = {"os", "sys", "re", "subprocess", "argparse", "unittest"}
        for mod in imported_modules:
            self.assertIn(
                mod,
                std_lib_modules,
                f"Prohibited third-party or non-standard module imported: {mod}",
            )

    def test_scenario_6_multiline_markdown_heading_diff(self):
        """Scenario 6: Accurately identifies net-new and modified requirements in multi-line heading format."""
        diff_net_new_multiline = """
diff --git a/docs/requirements/req_process_stability.md b/docs/requirements/req_process_stability.md
new file mode 100644
--- /dev/null
+++ b/docs/requirements/req_process_stability.md
@@ -0,0 +1,5 @@
+### REQ-PRO-028: Living Documentation Sharding [Planned]
+- **Tests**: `TST-PRO-021`
+- **REQ-PRO-028.1**: Shard living specs into 7 domain files...
+"""
        modified, added, deleted = parse_diff_requirements(diff_net_new_multiline)
        self.assertEqual(len(modified), 0)
        self.assertIn("REQ-PRO-028", added)

        diff_modified_multiline = """
diff --git a/docs/requirements/req_tracking_cockpit.md b/docs/requirements/req_tracking_cockpit.md
--- a/docs/requirements/req_tracking_cockpit.md
+++ b/docs/requirements/req_tracking_cockpit.md
@@ -40,4 +40,4 @@
 ### REQ-TRK-001: Sensor Processing Pipeline [Verified]
 - **Tests**: `TST-TRK-001`
-- **REQ-TRK-001.1**: Old filter configuration.
+- **REQ-TRK-001.1**: New refined filter configuration with adaptive weights.
 """
        modified, added, deleted = parse_diff_requirements(diff_modified_multiline)
        self.assertIn("REQ-TRK-001", modified)

    def test_scenario_7_auto_verify_living_docs_multiline_shards(self):
        """Scenario 7: Verify auto_verify_living_docs_on_stage5 flips Planned to Verified in sharded files."""
        import tempfile
        from tools.jira_util import auto_verify_living_docs_on_stage5

        with tempfile.TemporaryDirectory() as tmpdir:
            req_dir = os.path.join(tmpdir, "docs", "requirements")
            tst_dir = os.path.join(tmpdir, "docs", "tests")
            os.makedirs(req_dir, exist_ok=True)
            os.makedirs(tst_dir, exist_ok=True)

            shard_file = os.path.join(req_dir, "req_process_stability.md")
            with open(shard_file, "w", encoding="utf-8") as f:
                f.write(
                    "### REQ-PRO-028: Living Documentation Sharding [Planned (ATT-4594)]\n"
                    "- **Tests**: `TST-PRO-021`\n"
                    "- **Status**: Planned (ATT-4594)\n"
                )

            count = auto_verify_living_docs_on_stage5("ATT-4594", repo_root=tmpdir)
            self.assertGreaterEqual(count, 1)

            with open(shard_file, "r", encoding="utf-8") as f:
                content = f.read()
            self.assertIn("### REQ-PRO-028: Living Documentation Sharding [Verified]", content)
            self.assertIn("- **Status**: Verified", content)


if __name__ == "__main__":
    unittest.main()
