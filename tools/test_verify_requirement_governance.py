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
import tempfile
import unittest

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from tools.verify_requirement_governance import (
    ALLOWED_ENGINEERING_ENTRIES,
    LEGACY_DUPLICATE_IDS,
    find_duplicate_heading_ids,
    parse_diff_requirements,
    recalculate_master_index_tables,
    validate_archaeology_section,
    verify_architecture_and_system_specs,
    verify_composable_islands,
    verify_engineering_directory_structure,
    verify_gate_records,
    verify_governance,
    verify_master_table_parity,
    verify_qa_audits,
    verify_repository_encapsulation,
    verify_sprint_metrics,
    verify_independent_reviews,
    verify_unique_ids,
    validate_impact_analysis_section,
    verify_stage1_impact_analysis,
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
        std_lib_modules = {"os", "sys", "re", "subprocess", "argparse", "unittest", "json"}
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

    def test_scenario_8_composable_islands_detection(self):
        """Scenario 8: Deterministic Composable island detection (Rule 81 / REQ-PRO-041.5)."""
        import tempfile

        with tempfile.TemporaryDirectory() as tmpdir:
            main_dir = os.path.join(tmpdir, "app", "src", "main", "java", "com", "test")
            os.makedirs(main_dir, exist_ok=True)

            # File with caller
            caller_file = os.path.join(main_dir, "MainActivity.kt")
            with open(caller_file, "w", encoding="utf-8") as f:
                f.write(
                    "package com.test\n"
                    "fun setContent() {\n"
                    "    MountedComponent()\n"
                    "}\n"
                )

            # 1. Unmounted island composable fails
            diff_unmounted = """
diff --git a/app/src/main/java/com/test/UnmountedComponent.kt b/app/src/main/java/com/test/UnmountedComponent.kt
new file mode 100644
--- /dev/null
+++ b/app/src/main/java/com/test/UnmountedComponent.kt
@@ -0,0 +1,8 @@
+package com.test
+import androidx.compose.runtime.Composable
+@Composable
+fun UnmountedComponent() {
+}
+"""
            ok, diag = verify_composable_islands(diff_text=diff_unmounted, repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("UnmountedComponent" in d for d in diag))

            # 2. Mounted composable with production caller passes
            diff_mounted = """
diff --git a/app/src/main/java/com/test/MountedComponent.kt b/app/src/main/java/com/test/MountedComponent.kt
new file mode 100644
--- /dev/null
+++ b/app/src/main/java/com/test/MountedComponent.kt
@@ -0,0 +1,8 @@
+package com.test
+import androidx.compose.runtime.Composable
+@Composable
+fun MountedComponent() {
+}
+"""
            ok_m, diag_m = verify_composable_islands(diff_text=diff_mounted, repo_root=tmpdir)
            self.assertTrue(ok_m, f"Expected mounted composable to pass, diag: {diag_m}")

            # 3. Preview and private composables are ignored and pass cleanly
            diff_ignored = """
diff --git a/app/src/main/java/com/test/PreviewComponent.kt b/app/src/main/java/com/test/PreviewComponent.kt
new file mode 100644
--- /dev/null
+++ b/app/src/main/java/com/test/PreviewComponent.kt
@@ -0,0 +1,12 @@
+package com.test
+import androidx.compose.ui.tooling.preview.Preview
+@Preview
+@Composable
+fun PreviewComponent() {
+}
+@Composable
+private fun PrivateHelper() {
+}
+"""
            ok_i, diag_i = verify_composable_islands(diff_text=diff_ignored, repo_root=tmpdir)
            self.assertTrue(ok_i)

            # 4. Allowlisted theme composables pass
            diff_theme = """
diff --git a/app/src/main/java/com/test/Theme.kt b/app/src/main/java/com/test/Theme.kt
new file mode 100644
--- /dev/null
+++ b/app/src/main/java/com/test/Theme.kt
@@ -0,0 +1,6 @@
+package com.test
+@Composable
+fun ATrainingTrackerTheme() {
+}
+"""
            ok_t, diag_t = verify_composable_islands(diff_text=diff_theme, repo_root=tmpdir)
            self.assertTrue(ok_t)

    def test_scenario_9_repository_encapsulation_guard(self):
        """Scenario 9: Repository encapsulation guard rejects SQLite/DatabaseManager leaks (Rule 81 / REQ-PRO-041.5)."""
        # Leaky public DatabaseManager property
        diff_leaky_prop = """
diff --git a/app/src/main/java/com/test/WorkoutRepository.kt b/app/src/main/java/com/test/WorkoutRepository.kt
--- a/app/src/main/java/com/test/WorkoutRepository.kt
+++ b/app/src/main/java/com/test/WorkoutRepository.kt
@@ -10,3 +10,4 @@
+    val dbManager: WorkoutDatabaseManager = WorkoutDatabaseManager.getInstance(context)
"""
        ok_p, diag_p = verify_repository_encapsulation(diff_text=diff_leaky_prop)
        self.assertFalse(ok_p)
        self.assertTrue(any("WorkoutDatabaseManager" in d or "Repository encapsulation violation" in d for d in diag_p))

        # Leaky public getter method returning DatabaseManager or Cursor
        diff_leaky_getter = """
diff --git a/app/src/main/java/com/test/EquipmentRepository.kt b/app/src/main/java/com/test/EquipmentRepository.kt
--- a/app/src/main/java/com/test/EquipmentRepository.kt
+++ b/app/src/main/java/com/test/EquipmentRepository.kt
@@ -10,3 +10,4 @@
+    fun getDatabaseManager(): EquipmentDatabaseManager = dbManager
+    fun queryRawCursor(): Cursor? = null
"""
        ok_g, diag_g = verify_repository_encapsulation(diff_text=diff_leaky_getter)
        self.assertFalse(ok_g)
        self.assertTrue(len(diag_g) >= 2)

        # Private database helper and domain model returns pass cleanly
        diff_clean_repo = """
diff --git a/app/src/main/java/com/test/SafeRepository.kt b/app/src/main/java/com/test/SafeRepository.kt
--- a/app/src/main/java/com/test/SafeRepository.kt
+++ b/app/src/main/java/com/test/SafeRepository.kt
@@ -10,3 +10,4 @@
+    private val dbHelper: EquipmentDbHelper = EquipmentDbHelper.getInstance(context)
+    fun getEquipment(id: Long): String? = null
"""
        ok_c, diag_c = verify_repository_encapsulation(diff_text=diff_clean_repo)
        self.assertTrue(ok_c)

    def test_scenario_10_architecture_and_system_specs_grounding(self):
        """Scenario 10: Architecture & system spec code-grounding and SYSREQ parity (Rule 81 / REQ-PRO-041.5)."""
        import tempfile

        with tempfile.TemporaryDirectory() as tmpdir:
            arch_dir = os.path.join(tmpdir, "docs", "architecture")
            sys_dir = os.path.join(tmpdir, "docs", "system")
            app_dir = os.path.join(tmpdir, "app", "src", "main", "java")
            os.makedirs(arch_dir, exist_ok=True)
            os.makedirs(sys_dir, exist_ok=True)
            os.makedirs(app_dir, exist_ok=True)

            # Existing source file
            with open(os.path.join(app_dir, "RealClass.kt"), "w", encoding="utf-8") as f:
                f.write("class RealClass\n")

            # Ungrounded symbol in diff
            diff_ungrounded = """
diff --git a/docs/architecture/spec.md b/docs/architecture/spec.md
new file mode 100644
--- /dev/null
+++ b/docs/architecture/spec.md
@@ -0,0 +1,3 @@
+# Spec
+See `FakeNonExistentClass.kt` for details.
+"""
            ok_u, diag_u = verify_architecture_and_system_specs(diff_text=diff_ungrounded, repo_root=tmpdir)
            self.assertFalse(ok_u)
            self.assertTrue(any("FakeNonExistentClass.kt" in d for d in diag_u))

            # Grounded authentic symbol passes
            diff_grounded = """
diff --git a/docs/architecture/spec.md b/docs/architecture/spec.md
new file mode 100644
--- /dev/null
+++ b/docs/architecture/spec.md
@@ -0,0 +1,3 @@
+# Spec
+See `RealClass.kt` for details.
+"""
            ok_g, diag_g = verify_architecture_and_system_specs(diff_text=diff_grounded, repo_root=tmpdir)
            self.assertTrue(ok_g)

            # SYSREQ allocation mismatch: declared in sysreq_test.md but missing from system_architecture.md
            with open(os.path.join(sys_dir, "sysreq_test.md"), "w", encoding="utf-8") as f:
                f.write("### SYSREQ-TEST-001: Test Requirement\n")
            with open(os.path.join(sys_dir, "system_architecture.md"), "w", encoding="utf-8") as f:
                f.write("# Architecture\nAllocated: SYSREQ-OTHER-001\n")

            diff_sysreq = """
diff --git a/docs/system/sysreq_test.md b/docs/system/sysreq_test.md
new file mode 100644
--- /dev/null
+++ b/docs/system/sysreq_test.md
@@ -0,0 +1,2 @@
+### SYSREQ-TEST-001: Test
+"""
            ok_s, diag_s = verify_architecture_and_system_specs(diff_text=diff_sysreq, repo_root=tmpdir)
            self.assertFalse(ok_s)
            self.assertTrue(any("SYSREQ allocation mismatch" in d or "SYSREQ declaration mismatch" in d for d in diag_s))

    def test_scenario_11_verify_governance_full_integration(self):
        """Scenario 11: verify_governance integrates all 3 tripwires with deterministic exit codes."""
        diff_clean = """
diff --git a/docs/requirements/req_process_stability.md b/docs/requirements/req_process_stability.md
--- a/docs/requirements/req_process_stability.md
+++ b/docs/requirements/req_process_stability.md
@@ -10,3 +10,3 @@
-### REQ-PRO-041: Parallel Sprint [Verified]
+### REQ-PRO-041: Parallel Sprint Execution Protocol [Verified]
"""
        success, diag = verify_governance(
            deliverable_text=self.valid_archaeology_markdown,
            diff_text=diff_clean,
        )
        self.assertTrue(success)
        self.assertTrue(any("Composable islands detected" in d for d in diag))
        self.assertTrue(any("Repository encapsulation verified" in d for d in diag))
        self.assertTrue(any("Architecture & system specification" in d for d in diag))

    def test_scenario_12_master_table_preamble_and_shard_count_parity(self):
        """Scenario 12: verify_master_table_parity asserts preamble and summary shard counts (ATT-6140)."""
        with tempfile.TemporaryDirectory() as tmpdir:
            docs_req = os.path.join(tmpdir, "docs", "requirements")
            os.makedirs(docs_req, exist_ok=True)
            # Create a single shard
            with open(os.path.join(docs_req, "req_test.md"), "w", encoding="utf-8") as f:
                f.write("### REQ-TST-001: Sample [Verified]\n")

            # Mismatched preamble (claims 7 domain-sharded, but filesystem has 1 shard)
            master_content_mismatch = (
                "# Living Requirements\n\n"
                "This master index links to the 7 domain-sharded requirement files under `docs/requirements/`.\n\n"
                "| Domain Shard | Prefixes | Total | Verified | Planned / Open |\n"
                "| :--- | :--- | :---: | :---: | :---: |\n"
                "| [Test](requirements/req_test.md) | `TST` | 1 | 1 | 0 |\n\n"
                "**Total Living Requirements**: 1 across 7 domain shards.\n"
            )
            with open(os.path.join(tmpdir, "docs", "requirements.md"), "w", encoding="utf-8") as f:
                f.write(master_content_mismatch)

            ok, errs = verify_master_table_parity(repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("Preamble shard count mismatch" in e for e in errs))
            self.assertTrue(any("Summary shard count mismatch" in e for e in errs))

            # Recalculate should synchronize both preamble and summary counts to actual 1 shard
            recalculate_master_index_tables(repo_root=tmpdir)
            ok_after, errs_after = verify_master_table_parity(repo_root=tmpdir)
            self.assertTrue(ok_after, f"Expected parity after recalculate, got errors: {errs_after}")

            with open(os.path.join(tmpdir, "docs", "requirements.md"), "r", encoding="utf-8") as f:
                updated_text = f.read()
            self.assertIn("links to the 1 domain-sharded requirement files", updated_text)
            self.assertIn("across 1 domain shards.", updated_text)

    def test_scenario_13_canonical_engineering_directory_allows_gates(self):
        """Scenario 13: ALLOWED_ENGINEERING_ENTRIES registers 'gates', 'qa_audits', and 'reviews' (TST-PRO-059.3, TST-PRO-061.4, TST-PRO-062.3)."""
        self.assertIn("gates", ALLOWED_ENGINEERING_ENTRIES)
        self.assertIn("qa_audits", ALLOWED_ENGINEERING_ENTRIES)
        self.assertIn("reviews", ALLOWED_ENGINEERING_ENTRIES)
        with tempfile.TemporaryDirectory() as tmpdir:
            eng_dir = os.path.join(tmpdir, "docs", "engineering")
            os.makedirs(os.path.join(eng_dir, "gates"), exist_ok=True)
            os.makedirs(os.path.join(eng_dir, "analysis"), exist_ok=True)
            os.makedirs(os.path.join(eng_dir, "reviews"), exist_ok=True)
            ok, errors = verify_engineering_directory_structure(eng_dir=eng_dir)
            self.assertTrue(ok, f"Expected gates and reviews to be allowed, got errors: {errors}")

            # Non-canonical entry should fail
            os.makedirs(os.path.join(eng_dir, "invalid_dir"), exist_ok=True)
            ok_bad, errors_bad = verify_engineering_directory_structure(eng_dir=eng_dir)
            self.assertFalse(ok_bad)
            self.assertTrue(any("invalid_dir" in e for e in errors_bad))

    def test_scenario_14_verify_gate_records_validation(self):
        """Scenario 14: verify_gate_records validates export presence and failure modes (TST-PRO-059.2)."""
        with tempfile.TemporaryDirectory() as tmpdir:
            # 1. Missing gates directory
            ok, errs = verify_gate_records(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("does not exist" in e for e in errs))

            gates_dir = os.path.join(tmpdir, "docs", "engineering", "gates")
            os.makedirs(gates_dir, exist_ok=True)

            # 2. Missing sprint file
            ok, errs = verify_gate_records(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("Missing sprint quality gate export file" in e for e in errs))

            # 3. Empty sprint file
            target_file = os.path.join(gates_dir, "sprint_2026-46.3.md")
            with open(target_file, "w", encoding="utf-8") as f:
                f.write("   \n")
            ok, errs = verify_gate_records(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("empty" in e for e in errs))

            # 4. Valid sprint file with gate records
            valid_content = (
                "# Sprint Quality Gate Records: sprint/2026-46.3\n\n"
                "| Parent Ticket | Subtask Key | Gate | Stage | Verdict | Role | Actor | Timestamp |\n"
                "| ATT-6149 | ATT-7247 | Gate 1 | Analysis | PASS | agent2 | Agent 2 | 2026-11-12 |\n"
            )
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(valid_content)

            ok, msgs = verify_gate_records(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertTrue(ok, f"Expected valid gate records, got: {msgs}")
            self.assertTrue(any("PASS" in m for m in msgs))

            # 5. Wildcard validation without sprint name
            ok_any, msgs_any = verify_gate_records(sprint_name=None, repo_root=tmpdir)
            self.assertTrue(ok_any)
            self.assertTrue(any("Verified 1 sprint quality gate export record" in m for m in msgs_any))

    def test_scenario_15_verify_sprint_metrics_validation(self):
        """Scenario 15: verify_sprint_metrics validates JSONL schema and positive story points (TST-PRO-060.2)."""
        import json

        with tempfile.TemporaryDirectory() as tmpdir:
            metrics_dir = os.path.join(tmpdir, "docs", "engineering", "metrics")
            os.makedirs(metrics_dir, exist_ok=True)
            metrics_file = os.path.join(metrics_dir, "process_metrics.jsonl")

            # 1. Missing metrics file
            ok, errs = verify_sprint_metrics(sprint_name="2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("does not exist" in e for e in errs))

            # 2. Empty metrics file
            with open(metrics_file, "w", encoding="utf-8") as f:
                f.write("   \n")
            ok, errs = verify_sprint_metrics(sprint_name="2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("empty" in e for e in errs))

            # 3. Malformed JSON
            with open(metrics_file, "w", encoding="utf-8") as f:
                f.write("not valid json\n")
            ok, errs = verify_sprint_metrics(sprint_name="2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("Malformed JSON" in e for e in errs))

            # 4. Missing required schema fields
            with open(metrics_file, "w", encoding="utf-8") as f:
                f.write(json.dumps({"sprint_id": 1, "sprint_name": "test"}) + "\n")
            ok, errs = verify_sprint_metrics(sprint_name="test", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("missing required schema fields" in e for e in errs))

            # 5. Sprint with 0 story points
            zero_sp_record = {
                "sprint_id": 100,
                "sprint_name": "2026-40.1",
                "recorded_at": "2026-11-12T00:00:00Z",
                "throughput": {
                    "total_tickets": 5,
                    "completed_tickets": 5,
                    "total_story_points": 0.0,
                    "completed_story_points": 0.0,
                },
                "quality_gates": {"overall_ftpr_percent": 100.0},
            }
            with open(metrics_file, "w", encoding="utf-8") as f:
                f.write(json.dumps(zero_sp_record) + "\n")
            ok, errs = verify_sprint_metrics(sprint_name="2026-40.1", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("invalid story points" in e for e in errs))

            # 6. Missing requested sprint
            ok, errs = verify_sprint_metrics(sprint_name="nonexistent", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("Missing sprint metrics record" in e for e in errs))

            # 7. Valid sprint record with >0 story points
            valid_record = {
                "sprint_id": 440,
                "sprint_name": "2026-46.3",
                "recorded_at": "2026-11-12T04:00:00Z",
                "throughput": {
                    "total_tickets": 10,
                    "completed_tickets": 10,
                    "total_story_points": 25.0,
                    "completed_story_points": 25.0,
                },
                "quality_gates": {"overall_ftpr_percent": 95.0},
            }
            with open(metrics_file, "a", encoding="utf-8") as f:
                f.write(json.dumps(valid_record) + "\n")

            ok, msgs = verify_sprint_metrics(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertTrue(ok, f"Expected pass, got: {msgs}")
            self.assertTrue(any("PASS" in m for m in msgs))

            # 8. Wildcard verification (latest entry is valid_record)
            ok_any, msgs_any = verify_sprint_metrics(sprint_name=None, repo_root=tmpdir)
            self.assertTrue(ok_any, f"Expected pass, got: {msgs_any}")
            self.assertTrue(any("Verified 2 sprint metrics record(s)" in m for m in msgs_any))

            # 9. Record with valid problem_trends schema passes
            valid_pt_record = {
                "sprint_id": 441,
                "sprint_name": "2026-47.1",
                "throughput": {"total_story_points": 15.0, "completed_story_points": 15.0},
                "quality_gates": {},
                "problem_trends": {
                    "total_bugs": 2,
                    "open_bugs": 0,
                    "closed_bugs": 2,
                    "resolution_rate_percent": 100.0,
                    "by_severity": {"Critical": 1, "Major": 1, "Minor": 0, "Cosmetic": 0},
                },
            }
            with open(metrics_file, "a", encoding="utf-8") as f:
                f.write(json.dumps(valid_pt_record) + "\n")
            ok_valid_pt, msgs_valid_pt = verify_sprint_metrics(sprint_name="2026-47.1", repo_root=tmpdir)
            self.assertTrue(ok_valid_pt, f"Expected valid problem_trends to pass, got: {msgs_valid_pt}")

            # 10. Record with malformed problem_trends schema fails (REQ-PRO-063.6)
            bad_pt_record = {
                "sprint_id": 442,
                "sprint_name": "2026-47.2",
                "throughput": {"total_story_points": 10.0, "completed_story_points": 10.0},
                "quality_gates": {},
                "problem_trends": {"total_bugs": 1},  # missing open_bugs, closed_bugs, by_severity
            }
            with open(metrics_file, "a", encoding="utf-8") as f:
                f.write(json.dumps(bad_pt_record) + "\n")
            ok_bad_pt, errs_bad_pt = verify_sprint_metrics(sprint_name="2026-47.2", repo_root=tmpdir)
            self.assertFalse(ok_bad_pt)
            self.assertTrue(any("problem_trends fields" in e for e in errs_bad_pt))

    def test_scenario_16_verify_qa_audits_validation(self):
        """Scenario 16: verify_qa_audits validates report presence, verdicts, and >= 2 sampled tickets (TST-PRO-061.2)."""
        with tempfile.TemporaryDirectory() as tmpdir:
            # 1. Missing qa_audits directory
            ok, errs = verify_qa_audits(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("does not exist" in e for e in errs))

            qa_dir = os.path.join(tmpdir, "docs", "engineering", "qa_audits")
            os.makedirs(qa_dir, exist_ok=True)

            # 2. Missing sprint report
            ok, errs = verify_qa_audits(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("Missing sprint QA process-compliance audit file" in e for e in errs))

            # 3. Empty report
            target_file = os.path.join(qa_dir, "sprint_2026-46.3.md")
            with open(target_file, "w", encoding="utf-8") as f:
                f.write("   \n")
            ok, errs = verify_qa_audits(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("empty" in e for e in errs))

            # 4. Report without recognized compliance verdicts
            with open(target_file, "w", encoding="utf-8") as f:
                f.write("# QA Report\nRandom notes without verdict.\n")
            ok, errs = verify_qa_audits(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("no recognized compliance verdicts" in e for e in errs))

            # 5. Report with only 1 sampled ticket (< 2)
            single_sample_content = (
                "# Sprint QA Process-Compliance Audit: Sprint 2026-46.3\n\n"
                "**Overall Process Verdict**: **COMPLIANT**\n\n"
                "| Ticket | Summary | Compliance Verdict |\n"
                "| ATT-6150 | Metrics Persistence | COMPLIANT |\n\n"
                "### Ticket: ATT-6150\n"
                "* **Process Verdict**: **COMPLIANT**\n"
            )
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(single_sample_content)
            ok, errs = verify_qa_audits(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("minimum >= 2 required" in e for e in errs))

            # 6. Valid report with >= 2 sampled tickets
            valid_content = (
                "# Sprint QA Process-Compliance Audit: Sprint 2026-46.3\n\n"
                "**Overall Process Verdict**: **COMPLIANT**\n\n"
                "| Ticket | Summary | Compliance Verdict |\n"
                "| ATT-6149 | Gate Records Export | COMPLIANT |\n"
                "| ATT-6150 | Metrics Persistence | COMPLIANT |\n\n"
                "### Ticket: ATT-6149 - Gate Records Export\n"
                "* **Process Verdict**: **COMPLIANT**\n\n"
                "### Ticket: ATT-6150 - Metrics Persistence\n"
                "* **Process Verdict**: **COMPLIANT**\n"
            )
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(valid_content)

            ok, msgs = verify_qa_audits(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertTrue(ok, f"Expected pass, got: {msgs}")
            self.assertTrue(any("PASS" in m for m in msgs))

            # 7. Wildcard verification without sprint name
            ok_any, msgs_any = verify_qa_audits(sprint_name=None, repo_root=tmpdir)
            self.assertTrue(ok_any, f"Expected pass, got: {msgs_any}")
            self.assertTrue(any("Verified 1 sprint QA process-compliance audit record" in m for m in msgs_any))

    def test_scenario_17_verify_reviews_validation(self):
        """Scenario 17: verify_independent_reviews validates presence, schema, and metadata fields (TST-PRO-062.2)."""
        with tempfile.TemporaryDirectory() as tmpdir:
            # 1. Missing reviews directory
            ok, errs = verify_independent_reviews(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("does not exist" in e for e in errs))

            reviews_dir = os.path.join(tmpdir, "docs", "engineering", "reviews")
            os.makedirs(reviews_dir, exist_ok=True)

            # 2. Missing sprint review record
            ok, errs = verify_independent_reviews(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("Missing independent review record" in e for e in errs))

            # 3. Empty review file
            target_file = os.path.join(reviews_dir, "sprint_2026-46.3.md")
            with open(target_file, "w", encoding="utf-8") as f:
                f.write("   \n")
            ok, errs = verify_independent_reviews(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("empty" in e for e in errs))

            # 4. Review file missing required metadata fields (e.g., missing Verdict)
            incomplete_content = (
                "# Independent Review: sprint/2026-46.3\n\n"
                "* **Reviewer**: Human Process Owner\n"
                "* **Date**: 2026-11-12 04:00:00\n"
                "* **Scope**: Ceremony 2 Sprint Review\n"
            )
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(incomplete_content)
            ok, errs = verify_independent_reviews(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("missing required metadata field: Verdict" in e for e in errs))

            # 5. Valid sprint review record with all metadata fields
            valid_content = (
                "# Independent Review: sprint/2026-46.3\n\n"
                "## Metadata\n"
                "* **Reviewer**: Human Process Owner\n"
                "* **Review Type**: Human Sprint Review (Ceremony 2)\n"
                "* **Vendor**: Human\n"
                "* **Model**: Human Process Owner\n"
                "* **Version**: N/A\n"
                "* **Date**: 2026-11-12 04:00:00\n"
                "* **Scope**: Ceremony 2 Sprint Review\n"
                "* **Verdict**: **PASS**\n\n"
                "## Findings\n"
                "All sprint deliverables reviewed and accepted.\n"
            )
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(valid_content)

            ok, msgs = verify_independent_reviews(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertTrue(ok, f"Expected pass, got: {msgs}")
            self.assertTrue(any("PASS" in m for m in msgs))

            # 6. Wildcard verification without sprint name
            ok_any, msgs_any = verify_independent_reviews(sprint_name=None, repo_root=tmpdir)
            self.assertTrue(ok_any, f"Expected pass, got: {msgs_any}")
            self.assertTrue(any("Verified 1 independent review record(s)" in m for m in msgs_any))

    def test_scenario_18_impact_analysis_section_validation(self):
        """TST-PRO-064.1: Verify validate_impact_analysis_section validation across valid, missing, and placeholder cases."""
        # 1. Valid Impact Analysis section with all 4 dimensions
        valid_text = """
# Stage 1 Analysis: ATT-6157

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new REQ-PRO-064
* **Affected Architecture Elements**: tools/jira_util.py, tools/verify_requirement_governance.py
* **Verification Impact & Test Surface**: TST-PRO-064 and governance unit test suites
* **Risk Assessment & Invariants**: Low risk. Affects developer governance tooling only.
"""
        ok, errors = validate_impact_analysis_section(valid_text)
        self.assertTrue(ok, f"Expected valid impact analysis to pass, got: {errors}")
        self.assertEqual(len(errors), 0)

        # 2. Missing section header
        missing_header_text = """
# Stage 1 Analysis: ATT-6157
* **Affected Requirements**: Net-new REQ-PRO-064
* **Affected Architecture Elements**: tools/jira_util.py
"""
        ok_hdr, errs_hdr = validate_impact_analysis_section(missing_header_text)
        self.assertFalse(ok_hdr)
        self.assertTrue(any("Missing mandatory section header" in e for e in errs_hdr))

        # 3. Missing one mandatory dimension (e.g. Risk Assessment)
        missing_dim_text = """
## Change Impact Analysis
* **Affected Requirements**: Net-new REQ-PRO-064
* **Affected Architecture Elements**: tools/jira_util.py
* **Affected Tests**: TST-PRO-064
"""
        ok_dim, errs_dim = validate_impact_analysis_section(missing_dim_text)
        self.assertFalse(ok_dim)
        self.assertTrue(any("Risk Assessment" in e for e in errs_dim))

        # 4. Placeholder content in dimensions
        placeholder_text = """
## Change-Request Impact Analysis
* **Affected Requirements**: TODO
* **Affected Architecture Elements**: TBD
* **Verification Impact & Test Surface**: None
* **Risk Assessment & Invariants**: N/A
"""
        ok_ph, errs_ph = validate_impact_analysis_section(placeholder_text)
        self.assertFalse(ok_ph)
        self.assertTrue(len(errs_ph) >= 4)
        self.assertTrue(all("placeholder content" in e for e in errs_ph))

    def test_scenario_19_verify_stage1_impact_analysis_file_and_cli(self):
        """TST-PRO-064.2: Verify verify_stage1_impact_analysis and CLI --check-impact."""
        with tempfile.TemporaryDirectory() as tmpdir:
            analysis_dir = os.path.join(tmpdir, "docs", "engineering", "analysis")
            os.makedirs(analysis_dir, exist_ok=True)

            # 1. Non-existent file
            ok, errs = verify_stage1_impact_analysis(analysis_file="missing.md", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("not found" in e for e in errs))

            # 2. Invalid analysis file
            bad_file = os.path.join(analysis_dir, "ATT-9999_analysis.md")
            with open(bad_file, "w", encoding="utf-8") as f:
                f.write("# Incomplete Analysis\nNo impact section here.\n")
            ok_bad, errs_bad = verify_stage1_impact_analysis(analysis_file=bad_file, repo_root=tmpdir)
            self.assertFalse(ok_bad)
            self.assertTrue(any("failed validation" in e for e in errs_bad))

            # 3. Valid analysis file
            good_file = os.path.join(analysis_dir, "ATT-6157_analysis.md")
            with open(good_file, "w", encoding="utf-8") as f:
                f.write("""# Stage 1 Analysis: ATT-6157
## Change Impact Analysis
* **Affected Requirements**: REQ-PRO-064
* **Affected Architecture Elements**: tools/jira_util.py
* **Verification Impact & Test Surface**: TST-PRO-064
* **Risk Assessment & Invariants**: Low risk developer tooling
""")
            ok_good, msgs_good = verify_stage1_impact_analysis(analysis_file=good_file, repo_root=tmpdir)
            self.assertTrue(ok_good, f"Expected pass, got: {msgs_good}")
            self.assertTrue(any("PASS:" in m for m in msgs_good))

            # 4. Auto-discovery without explicit file
            ok_auto, msgs_auto = verify_stage1_impact_analysis(analysis_file=None, repo_root=tmpdir)
            self.assertTrue(ok_auto, f"Expected auto-discovery pass, got: {msgs_auto}")

    def test_scenario_20_duplicate_heading_detection_and_reporting(self):
        """TST-PRO-066.1: Verify duplicate heading detection and detailed file/line reporting."""
        with tempfile.TemporaryDirectory() as tmpdir:
            req_dir = os.path.join(tmpdir, "docs", "requirements")
            tst_dir = os.path.join(tmpdir, "docs", "tests")
            os.makedirs(req_dir, exist_ok=True)
            os.makedirs(tst_dir, exist_ok=True)

            # Create synthetic shard files with duplicates
            req_file_1 = os.path.join(req_dir, "req_shard1.md")
            with open(req_file_1, "w", encoding="utf-8") as f:
                f.write("# Requirements 1\n\n### REQ-SYN-001: First Heading [Verified]\nBody 1\n\n### REQ-SYN-002: Second Heading [Planned]\nBody 2\n")

            req_file_2 = os.path.join(req_dir, "req_shard2.md")
            with open(req_file_2, "w", encoding="utf-8") as f:
                f.write("# Requirements 2\n\n### REQ-SYN-001: Duplicate First Heading [Planned]\nBody 3\n")

            tst_file_1 = os.path.join(tst_dir, "tst_shard1.md")
            with open(tst_file_1, "w", encoding="utf-8") as f:
                f.write("# Tests 1\n\n### TST-SYN-001: Test One [Verified]\nLine 4\n\n### TST-SYN-001: Test Duplicate [Planned]\nLine 6\n")

            dups = find_duplicate_heading_ids(repo_root=tmpdir)
            self.assertIn("REQ-SYN-001", dups)
            self.assertIn("TST-SYN-001", dups)
            self.assertNotIn("REQ-SYN-002", dups)

            # Check occurrences
            req_locs = dups["REQ-SYN-001"]
            self.assertEqual(len(req_locs), 2)
            self.assertEqual(req_locs[0], ("docs/requirements/req_shard1.md", 3))
            self.assertEqual(req_locs[1], ("docs/requirements/req_shard2.md", 3))

            tst_locs = dups["TST-SYN-001"]
            self.assertEqual(len(tst_locs), 2)
            self.assertEqual(tst_locs[0], ("docs/tests/tst_shard1.md", 3))
            self.assertEqual(tst_locs[1], ("docs/tests/tst_shard1.md", 6))

            # verify_unique_ids without allowlist fails and reports exact locations
            ok, diag = verify_unique_ids(repo_root=tmpdir, legacy_allowlist=frozenset())
            self.assertFalse(ok)
            diag_str = "\n".join(diag)
            self.assertIn("REQ-SYN-001", diag_str)
            self.assertIn("docs/requirements/req_shard1.md:3", diag_str)
            self.assertIn("docs/requirements/req_shard2.md:3", diag_str)
            self.assertIn("TST-SYN-001", diag_str)
            self.assertIn("docs/tests/tst_shard1.md:3", diag_str)
            self.assertIn("docs/tests/tst_shard1.md:6", diag_str)

    def test_scenario_21_legacy_baseline_tolerance_and_new_duplicate_rejection(self):
        """TST-PRO-066.2: Verify legacy baseline tolerance and immediate rejection of novel duplicates."""
        with tempfile.TemporaryDirectory() as tmpdir:
            req_dir = os.path.join(tmpdir, "docs", "requirements")
            os.makedirs(req_dir, exist_ok=True)

            allowlist = frozenset({"REQ-LEGACY-001"})

            # Only legacy duplicate present
            shard_path = os.path.join(req_dir, "req_test.md")
            with open(shard_path, "w", encoding="utf-8") as f:
                f.write("### REQ-LEGACY-001: Item 1\nline 2\n### REQ-LEGACY-001: Item 2\nline 4\n")

            ok_leg, diag_leg = verify_unique_ids(repo_root=tmpdir, legacy_allowlist=allowlist)
            self.assertTrue(ok_leg, f"Expected legacy duplicate to be tolerated, got: {diag_leg}")
            self.assertTrue(any("WARNING (Legacy duplicate)" in m for m in diag_leg))
            self.assertTrue(any("tolerated pending ATT-6123" in m for m in diag_leg))

            # Add a novel duplicate outside allowlist
            with open(shard_path, "a", encoding="utf-8") as f:
                f.write("### REQ-NOVEL-001: Item 3\nline 6\n### REQ-NOVEL-001: Item 4\nline 8\n")

            ok_nov, diag_nov = verify_unique_ids(repo_root=tmpdir, legacy_allowlist=allowlist)
            self.assertFalse(ok_nov, f"Expected novel duplicate to fail, got: {diag_nov}")
            self.assertTrue(any("outside legacy allowlist: REQ-NOVEL-001" in m for m in diag_nov))
            self.assertTrue(any("ERROR (Duplicate ID): REQ-NOVEL-001" in m for m in diag_nov))

    def test_scenario_22_strict_enforcement_flag(self):
        """TST-PRO-066.3: Verify fail_on_duplicates=True rejects even legacy duplicates."""
        with tempfile.TemporaryDirectory() as tmpdir:
            req_dir = os.path.join(tmpdir, "docs", "requirements")
            os.makedirs(req_dir, exist_ok=True)

            allowlist = frozenset({"REQ-LEGACY-001"})
            shard_path = os.path.join(req_dir, "req_test.md")
            with open(shard_path, "w", encoding="utf-8") as f:
                f.write("### REQ-LEGACY-001: Item 1\nline 2\n### REQ-LEGACY-001: Item 2\nline 4\n")

            # With fail_on_duplicates=True, even allowlisted duplicate fails
            ok_strict, diag_strict = verify_unique_ids(
                repo_root=tmpdir,
                fail_on_duplicates=True,
                legacy_allowlist=allowlist,
            )
            self.assertFalse(ok_strict)
            self.assertTrue(any("ERROR: Duplicate REQ/TST IDs detected" in m for m in diag_strict))
            self.assertTrue(any("ERROR (Duplicate ID): REQ-LEGACY-001" in m for m in diag_strict))

    def test_scenario_23_live_repository_unique_ids_parity(self):
        """TST-PRO-066.4: Verify live repository living docs pass in standard mode and match known baseline."""
        # Standard mode: passes cleanly with zero novel duplicates
        ok, diag = verify_unique_ids()
        self.assertTrue(ok, f"Expected live repository to pass in standard mode, got: {diag}")
        self.assertTrue(any("Zero new duplicate REQ/TST IDs detected" in m for m in diag))

        # Strict mode: detects exactly the 11 known legacy duplicates
        ok_strict, diag_strict = verify_unique_ids(fail_on_duplicates=True)
        self.assertFalse(ok_strict)
        dups = find_duplicate_heading_ids()
        self.assertEqual(set(dups.keys()), set(LEGACY_DUPLICATE_IDS))
        self.assertEqual(len(dups), 11)

    def test_scenario_24_verify_engineering_directory_structure_test_results_symlink(self):
        """TST-PRO-067.4: Verify verify_engineering_directory_structure enforces test_results symlink."""
        with tempfile.TemporaryDirectory() as tmpdir:
            eng_dir = os.path.join(tmpdir, "docs", "engineering")
            os.makedirs(eng_dir, exist_ok=True)
            for entry in ["analysis", "test_specs", "plans", "walkthroughs", "retros"]:
                os.makedirs(os.path.join(eng_dir, entry), exist_ok=True)

            # 1. Non-symlink test_results directory fails
            bad_results = os.path.join(eng_dir, "test_results")
            os.makedirs(bad_results, exist_ok=True)
            ok_bad, errs_bad = verify_engineering_directory_structure(eng_dir=eng_dir)
            self.assertFalse(ok_bad)
            self.assertTrue(any("must be a symbolic link" in e for e in errs_bad))
            os.rmdir(bad_results)

            # 2. Symlink with wrong target fails
            os.symlink("/wrong/target", bad_results)
            ok_wrong, errs_wrong = verify_engineering_directory_structure(eng_dir=eng_dir)
            self.assertFalse(ok_wrong)
            self.assertTrue(any("does not match expected canonical target" in e for e in errs_wrong))
            os.unlink(bad_results)

            # 3. Canonical symlink passes
            os.symlink("../../app/build/reports/tests/testDebugUnitTest", bad_results)
            ok_good, errs_good = verify_engineering_directory_structure(eng_dir=eng_dir)
            self.assertTrue(ok_good, f"Expected pass, got: {errs_good}")

    def test_scenario_25_workflow_yaml_syntax_validity(self):
        """TST-PRO-067.5: Assert workflow YAML syntax validity using yaml.safe_load."""
        import yaml
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
        workflows = [
            os.path.join(repo_root, ".github", "workflows", "android-branch-build.yml"),
            os.path.join(repo_root, ".github", "workflows", "android-release.yml"),
        ]
        for wf in workflows:
            self.assertTrue(os.path.exists(wf), f"Workflow file must exist: {wf}")
            with open(wf, "r", encoding="utf-8") as f:
                data = yaml.safe_load(f)
            self.assertIsInstance(data, dict, f"Parsed YAML must be a dict: {wf}")
            self.assertIn("jobs", data, f"Workflow must contain jobs: {wf}")


if __name__ == "__main__":
    unittest.main()
