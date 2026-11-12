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
    LEGACY_DANGLING_TST_REFS,
    LEGACY_DANGLING_REQ_REFS,
    find_dangling_references,
    verify_dangling_references,
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
    calculate_traceability_coverage,
    extract_test_code_tags,
    format_coverage_markdown,
    format_coverage_json,
    verify_coverage_thresholds,
    verify_tests_field_format,
    CoverageCategoryResult,
    TraceabilityCoverageReport,
    LEGACY_UNGROUNDED_VERIFIED_REQS,
    extract_living_test_ids,
    find_ungrounded_verified_requirements,
    verify_requirement_evidence_status,
    verify_system_software_traceability,
    SWE_DERIVED_PREFIXES,
    SYSTEM_REFINED_PREFIXES,
    ALLOWED_PRIORITIES,
    ALLOWED_TYPES,
    ALLOWED_VERIFICATION_METHODS,
    find_missing_requirement_attributes,
    verify_requirement_attributes,
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
        """TST-PRO-066.4: Verify live repository living docs pass cleanly with zero duplicates (ATT-6123)."""
        # Standard mode: passes cleanly with zero duplicates
        ok, diag = verify_unique_ids()
        self.assertTrue(ok, f"Expected live repository to pass in standard mode, got: {diag}")
        self.assertTrue(any("All REQ and TST heading IDs are unique" in m for m in diag))

        # Strict mode (--enforce-unique): passes cleanly with zero duplicates
        ok_strict, diag_strict = verify_unique_ids(fail_on_duplicates=True)
        self.assertTrue(ok_strict, f"Expected live repository to pass strict mode, got: {diag_strict}")
        self.assertTrue(any("All REQ and TST heading IDs are unique" in m for m in diag_strict))

        dups = find_duplicate_heading_ids()
        self.assertEqual(len(dups), 0)
        self.assertEqual(set(dups.keys()), set())
        self.assertEqual(LEGACY_DUPLICATE_IDS, frozenset())

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

    def test_scenario_26_dangling_tst_detection_in_requirements(self):
        """TST-PRO-070.1: Assert detection of dangling TST citations in requirement entries."""
        with tempfile.TemporaryDirectory() as tmpdir:
            req_dir = os.path.join(tmpdir, "docs", "requirements")
            tst_dir = os.path.join(tmpdir, "docs", "tests")
            os.makedirs(req_dir, exist_ok=True)
            os.makedirs(tst_dir, exist_ok=True)

            # Valid test shard
            with open(os.path.join(tst_dir, "tst_sample.md"), "w", encoding="utf-8") as f:
                f.write("# Tests\n\n### TST-VALID-001: Valid Test [Verified]\n- **Requirement(s)**: `REQ-VALID-001`\n")

            # REQ shard citing both existing TST-VALID-001 and non-existent TST-GHOST-999
            with open(os.path.join(req_dir, "req_sample.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Requirements\n\n"
                    "### REQ-VALID-001: Sample Requirement [Verified]\n"
                    "- **Tests**: `TST-VALID-001`, `TST-GHOST-999`\n"
                    "- **Rationale**: Testing dangling ref detection.\n"
                )

            dangling = find_dangling_references(repo_root=tmpdir)
            self.assertEqual(len(dangling.dangling_tsts), 1)
            self.assertEqual(len(dangling.dangling_reqs), 0)

            ref = dangling.dangling_tsts[0]
            self.assertEqual(ref.source_id, "REQ-VALID-001")
            self.assertEqual(ref.target_id, "TST-GHOST-999")
            self.assertIn("req_sample.md", ref.source_file)
            self.assertEqual(ref.line_no, 4)

    def test_scenario_27_dangling_req_detection_in_tests(self):
        """TST-PRO-070.2: Assert detection of dangling REQ citations in test entries."""
        with tempfile.TemporaryDirectory() as tmpdir:
            req_dir = os.path.join(tmpdir, "docs", "requirements")
            tst_dir = os.path.join(tmpdir, "docs", "tests")
            os.makedirs(req_dir, exist_ok=True)
            os.makedirs(tst_dir, exist_ok=True)

            # Valid requirement shard
            with open(os.path.join(req_dir, "req_sample.md"), "w", encoding="utf-8") as f:
                f.write("# Requirements\n\n### REQ-VALID-001: Valid Requirement [Verified]\n- **Tests**: `TST-VALID-001`\n")

            # TST shard citing both existing REQ-VALID-001 and non-existent REQ-PHANTOM-888
            with open(os.path.join(tst_dir, "tst_sample.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Tests\n\n"
                    "### TST-VALID-001: Valid Test [Verified]\n"
                    "- **Requirement(s)**: `REQ-VALID-001`, `REQ-PHANTOM-888`\n"
                    "- **Component**: `TestComponent`\n"
                )

            dangling = find_dangling_references(repo_root=tmpdir)
            self.assertEqual(len(dangling.dangling_tsts), 0)
            self.assertEqual(len(dangling.dangling_reqs), 1)

            ref = dangling.dangling_reqs[0]
            self.assertEqual(ref.source_id, "TST-VALID-001")
            self.assertEqual(ref.target_id, "REQ-PHANTOM-888")
            self.assertIn("tst_sample.md", ref.source_file)
            self.assertEqual(ref.line_no, 4)

    def test_scenario_28_legacy_dangling_tolerance_and_new_reference_rejection(self):
        """TST-PRO-070.3: Assert legacy allowlist warnings vs new dangling reference rejection."""
        with tempfile.TemporaryDirectory() as tmpdir:
            req_dir = os.path.join(tmpdir, "docs", "requirements")
            tst_dir = os.path.join(tmpdir, "docs", "tests")
            os.makedirs(req_dir, exist_ok=True)
            os.makedirs(tst_dir, exist_ok=True)

            with open(os.path.join(req_dir, "req_sample.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Requirements\n\n"
                    "### REQ-A-001: Requirement A [Verified]\n"
                    "- **Tests**: `TST-LEGACY-001`\n"
                )
            with open(os.path.join(tst_dir, "tst_sample.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Tests\n\n"
                    "### TST-B-001: Test B [Verified]\n"
                    "- **Requirement(s)**: `REQ-LEGACY-002`\n"
                )

            # Tolerated via custom allowlist
            ok, diag = verify_dangling_references(
                repo_root=tmpdir,
                legacy_tst_allowlist=frozenset({"TST-LEGACY-001"}),
                legacy_req_allowlist=frozenset({"REQ-LEGACY-002"}),
            )
            self.assertTrue(ok)
            self.assertTrue(any("Zero new dangling" in m for m in diag))
            self.assertTrue(any("WARNING (Legacy dangling reference)" in m for m in diag))

            # Adding a novel dangling reference fails
            with open(os.path.join(req_dir, "req_sample.md"), "a", encoding="utf-8") as f:
                f.write(
                    "\n### REQ-A-002: Requirement Novel [Planned]\n"
                    "- **Tests**: `TST-NOVEL-999`\n"
                )

            ok_fail, diag_fail = verify_dangling_references(
                repo_root=tmpdir,
                legacy_tst_allowlist=frozenset({"TST-LEGACY-001"}),
                legacy_req_allowlist=frozenset({"REQ-LEGACY-002"}),
            )
            self.assertFalse(ok_fail)
            self.assertTrue(any("ERROR (Dangling reference)" in m and "TST-NOVEL-999" in m for m in diag_fail))

    def test_scenario_29_strict_enforce_links_flag(self):
        """TST-PRO-070.4: Assert strict failure under --enforce-links / fail_on_dangling=True."""
        with tempfile.TemporaryDirectory() as tmpdir:
            req_dir = os.path.join(tmpdir, "docs", "requirements")
            tst_dir = os.path.join(tmpdir, "docs", "tests")
            os.makedirs(req_dir, exist_ok=True)
            os.makedirs(tst_dir, exist_ok=True)

            with open(os.path.join(req_dir, "req_sample.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Requirements\n\n"
                    "### REQ-A-001: Requirement A [Verified]\n"
                    "- **Tests**: `TST-LEGACY-001`\n"
                )
            with open(os.path.join(tst_dir, "tst_sample.md"), "w", encoding="utf-8") as f:
                f.write("# Tests\n\n### TST-B-001: Test B [Verified]\n- **Requirement(s)**: `REQ-A-001`\n")

            # Strict mode fails even if in legacy allowlist
            ok, diag = verify_dangling_references(
                repo_root=tmpdir,
                fail_on_dangling=True,
                legacy_tst_allowlist=frozenset({"TST-LEGACY-001"}),
            )
            self.assertFalse(ok)
            self.assertTrue(any("strict enforcement active" in m for m in diag))
            self.assertTrue(any("ERROR (Dangling reference)" in m and "TST-LEGACY-001" in m for m in diag))

    def test_scenario_30_repository_dangling_references_baseline_pass(self):
        """TST-PRO-070.5: Verify live repository living docs pass with zero novel dangling refs."""
        # Standard mode: passes cleanly with 0 novel dangling references
        ok, diag = verify_dangling_references()
        self.assertTrue(ok, f"Expected live repository to pass in standard mode, got: {diag}")
        self.assertTrue(any("Zero new dangling REQ/TST references detected" in m for m in diag))

        # Check exact counts of legacy dangling references
        dangling = find_dangling_references()
        self.assertEqual(len(dangling.dangling_tsts), 7)
        self.assertEqual(len(dangling.dangling_reqs), 14)

        tst_targets = set(r.target_id for r in dangling.dangling_tsts)
        req_targets = set(r.target_id for r in dangling.dangling_reqs)
        self.assertEqual(tst_targets, LEGACY_DANGLING_TST_REFS)
        self.assertEqual(req_targets, LEGACY_DANGLING_REQ_REFS)

        # Strict mode: fails because 21 legacy dangling references exist pending A-08.x (ATT-6127 resolved 5, ATT-6131 resolved 1)
        ok_strict, diag_strict = verify_dangling_references(fail_on_dangling=True)
        self.assertFalse(ok_strict)
        self.assertTrue(any("strict enforcement active" in m for m in diag_strict))

    def test_scenario_31_coverage_category_1_reqs_without_tst(self):
        """TST-PRO-071.1: Verify Category 1 calculates REQs citing no TST in Tests field."""
        synthetic_reqs = {
            "req_shard.md": (
                "### REQ-A-001: Has TST [Verified]\n"
                "- **Tests**: `TST-A-001`\n\n"
                "### REQ-A-002: Has None [Verified]\n"
                "- **Tests**: None\n\n"
                "### REQ-A-003: Empty Tests [Verified]\n"
                "- **Tests**: \n\n"
                "### REQ-A-004: Missing Tests Field [Verified]\n"
                "- **Rationale**: No tests specified yet.\n"
            )
        }
        synthetic_tsts = {
            "tst_shard.md": "### TST-A-001: Valid test [Verified]\n- **Requirements**: `REQ-A-001`\n"
        }
        report = calculate_traceability_coverage(
            req_shards=synthetic_reqs,
            tst_shards=synthetic_tsts,
            test_sources={},
        )
        cat1 = report.cat1
        self.assertEqual(cat1.total_count, 4)
        self.assertEqual(cat1.covered_count, 1)
        self.assertEqual(cat1.gap_count, 3)
        self.assertEqual(cat1.gap_items, ["REQ-A-002", "REQ-A-003", "REQ-A-004"])
        self.assertAlmostEqual(cat1.percentage, 25.0, places=2)

    def test_scenario_32_coverage_category_2_tsts_without_code(self):
        """TST-PRO-071.2: Verify Category 2 identifies TST headings lacking test code tags and handles sub-tags."""
        synthetic_reqs = {
            "req_shard.md": (
                "### REQ-B-001: Req [Verified]\n"
                "- **Tests**: `TST-B-001`, `TST-B-002`, `TST-B-003`\n"
            )
        }
        synthetic_tsts = {
            "tst_shard.md": (
                "### TST-B-001: Has Direct Code [Verified]\n- **Requirements**: `REQ-B-001`\n\n"
                "### TST-B-002: Has Sub-Test Method Code [Verified]\n- **Requirements**: `REQ-B-001`\n\n"
                "### TST-B-003: Has No Test Code [Verified]\n- **Requirements**: `REQ-B-001`\n"
            )
        }
        synthetic_code = {
            "test_sample.kt": (
                "// Tests verifying TST-B-001 direct tag\n"
                "fun `some test for TST-B-002_1 method`() {}\n"
            )
        }
        report = calculate_traceability_coverage(
            req_shards=synthetic_reqs,
            tst_shards=synthetic_tsts,
            test_sources=synthetic_code,
        )
        cat2 = report.cat2
        self.assertEqual(cat2.total_count, 3)
        self.assertEqual(cat2.covered_count, 2)
        self.assertEqual(cat2.gap_count, 1)
        self.assertEqual(cat2.gap_items, ["TST-B-003"])
        self.assertAlmostEqual(cat2.percentage, 66.67, places=1)

    def test_scenario_33_coverage_category_3_code_tags_without_spec(self):
        """TST-PRO-071.3: Verify Category 3 flags test code tags whose base TST ID lacks a heading in living docs."""
        synthetic_reqs = {"req.md": "### REQ-C-001: Req [Verified]\n- **Tests**: `TST-C-001`\n"}
        synthetic_tsts = {"tst.md": "### TST-C-001: Valid TST [Verified]\n- **Requirements**: `REQ-C-001`\n"}
        synthetic_code = {
            "test_orphans.kt": (
                "// TST-C-001 has valid spec\n"
                "// TST-UNKNOWN-999 has no spec in living docs\n"
                "fun `test method with TST-ORPHAN-042_3`() {}\n"
            )
        }
        report = calculate_traceability_coverage(
            req_shards=synthetic_reqs,
            tst_shards=synthetic_tsts,
            test_sources=synthetic_code,
        )
        cat3 = report.cat3
        self.assertEqual(cat3.total_count, 3) # TST-C-001, TST-UNKNOWN-999, TST-ORPHAN-042
        self.assertEqual(cat3.covered_count, 1) # TST-C-001
        self.assertEqual(cat3.gap_count, 2)
        self.assertEqual(cat3.gap_items, ["TST-ORPHAN-042", "TST-UNKNOWN-999"])
        self.assertAlmostEqual(cat3.percentage, 33.33, places=1)

    def test_scenario_34_coverage_category_4_tsts_not_cited_back(self):
        """TST-PRO-071.4: Verify Category 4 identifies TSTs whose cited REQs do not cite the TST back."""
        synthetic_reqs = {
            "req.md": (
                "### REQ-D-001: Cites TST-D-001 [Verified]\n"
                "- **Tests**: `TST-D-001`\n\n"
                "### REQ-D-002: Does Not Cite TST-D-002 [Verified]\n"
                "- **Tests**: None\n"
            )
        }
        synthetic_tsts = {
            "tst.md": (
                "### TST-D-001: Bidirectional Match [Verified]\n"
                "- **Requirements**: `REQ-D-001`\n\n"
                "### TST-D-002: One-way Citation (REQ-D-002 does not cite back) [Verified]\n"
                "- **Requirements**: `REQ-D-002`\n\n"
                "### TST-D-003: Cites No REQ [Verified]\n"
                "- **Requirements**: None\n"
            )
        }
        report = calculate_traceability_coverage(
            req_shards=synthetic_reqs,
            tst_shards=synthetic_tsts,
            test_sources={},
        )
        cat4 = report.cat4
        self.assertEqual(cat4.total_count, 3)
        self.assertEqual(cat4.covered_count, 1) # TST-D-001
        self.assertEqual(cat4.gap_count, 2)
        self.assertEqual(cat4.gap_items, ["TST-D-002", "TST-D-003"])
        self.assertIn("TST-D-002", cat4.details)
        self.assertEqual(cat4.details["TST-D-002"], ["REQ-D-002"])
        self.assertAlmostEqual(cat4.percentage, 33.33, places=1)

    def test_scenario_35_coverage_report_formatting_markdown_and_json(self):
        """TST-PRO-071.5: Verify format_coverage_markdown and format_coverage_json outputs."""
        synthetic_reqs = {"req.md": "### REQ-E-001: Req [Verified]\n- **Tests**: `TST-E-001`\n"}
        synthetic_tsts = {"tst.md": "### TST-E-001: Tst [Verified]\n- **Requirements**: `REQ-E-001`\n"}
        synthetic_code = {"test.kt": "// TST-E-001\n"}

        report = calculate_traceability_coverage(
            req_shards=synthetic_reqs,
            tst_shards=synthetic_tsts,
            test_sources=synthetic_code,
        )

        md = format_coverage_markdown(report)
        self.assertIn("# ASPICE Traceability & Verification Coverage Report", md)
        self.assertIn("Category 1", md)
        self.assertIn("Category 2", md)
        self.assertIn("Category 3", md)
        self.assertIn("Category 4", md)
        self.assertIn("100.00%", md)

        js = format_coverage_json(report)
        import json
        data = json.loads(js)
        self.assertIn("category_1_reqs_without_tst", data)
        self.assertIn("category_2_tsts_without_code", data)
        self.assertIn("category_3_code_tags_without_spec", data)
        self.assertIn("category_4_tsts_not_cited_back", data)
        self.assertIn("summary", data)
        self.assertEqual(data["summary"]["total_reqs"], 1)
        self.assertEqual(data["summary"]["req_tst_coverage_pct"], 100.0)

    def test_scenario_36_coverage_threshold_ratchets_pass_and_fail(self):
        """TST-PRO-071.6: Verify configurable threshold ratchets pass and fail as expected."""
        synthetic_reqs = {
            "req.md": (
                "### REQ-F-001: Req 1 [Verified]\n- **Tests**: `TST-F-001`\n\n"
                "### REQ-F-002: Req 2 [Verified]\n- **Tests**: None\n"
            )
        }
        synthetic_tsts = {
            "tst.md": "### TST-F-001: Tst 1 [Verified]\n- **Requirements**: `REQ-F-001`\n"
        }
        synthetic_code = {"test.kt": "// TST-F-001\n// TST-ORPHAN-001\n"}

        report = calculate_traceability_coverage(
            req_shards=synthetic_reqs,
            tst_shards=synthetic_tsts,
            test_sources=synthetic_code,
        )
        # REQ-TST coverage is 1 / 2 = 50.0%
        # Code tags without spec is 1 (TST-ORPHAN-001)

        # Pass when min_req_tst_coverage is <= 50.0%
        ok_pass, diag_pass = verify_coverage_thresholds(report, min_req_tst_coverage=50.0, max_code_without_spec=1)
        self.assertTrue(ok_pass)
        self.assertTrue(any("meets threshold 50.00%" in m for m in diag_pass))

        # Fail when min_req_tst_coverage is 60.0%
        ok_fail_cov, diag_fail_cov = verify_coverage_thresholds(report, min_req_tst_coverage=60.0)
        self.assertFalse(ok_fail_cov)
        self.assertTrue(any("below threshold 60.00%" in m for m in diag_fail_cov))

        # Fail when max_code_without_spec is 0
        ok_fail_spec, diag_fail_spec = verify_coverage_thresholds(report, max_code_without_spec=0)
        self.assertFalse(ok_fail_spec)
        self.assertTrue(any("exceeds maximum allowed 0" in m for m in diag_fail_spec))

    def test_scenario_37_repository_coverage_integration(self):
        """TST-PRO-071: Verify coverage calculation on authentic live repository."""
        report = calculate_traceability_coverage()
        self.assertGreater(report.cat1.total_count, 1000)
        self.assertGreater(report.cat2.total_count, 1000)
        self.assertGreater(report.cat3.total_count, 500)
        self.assertGreater(report.cat4.total_count, 1000)

        # Verify ratcheted sanity thresholds on live repo
        ok, diag = verify_coverage_thresholds(
            report,
            min_req_tst_coverage=40.0,
            min_tst_code_coverage=55.0,
            max_code_without_spec=25,
            min_bidirectional_coverage=35.0,
        )
        self.assertTrue(ok, f"Expected repository coverage sanity thresholds to pass, got: {diag}")

    def test_scenario_38_master_table_parity_and_sync_4_columns(self):
        """TST-PRO-073.3: Verify 4-column master table parity and recalculation (Total | Verified | Implemented | Planned)."""
        with tempfile.TemporaryDirectory() as tmpdir:
            docs_req = os.path.join(tmpdir, "docs", "requirements")
            os.makedirs(docs_req, exist_ok=True)
            with open(os.path.join(docs_req, "req_test.md"), "w", encoding="utf-8") as f:
                f.write(
                    "### REQ-T-001: Verified Item [Verified]\n"
                    "- **Tests**: `TST-T-001`\n\n"
                    "### REQ-T-002: Implemented Item [Implemented]\n"
                    "- **Tests**: None\n\n"
                    "### REQ-T-003: Planned Item [Planned]\n"
                    "- **Tests**: None\n"
                )

            # Master table with 4 columns
            master_4col = (
                "# Living Requirements\n\n"
                "This master index links to the 1 domain-sharded requirement files under `docs/requirements/`.\n\n"
                "| Domain Shard | Scope | Prefixes | Total | Verified | Implemented | Planned / Open |\n"
                "| :--- | :--- | :--- | :---: | :---: | :---: | :---: |\n"
                "| [Test](requirements/req_test.md) | Scope | `T` | 3 | 1 | 1 | 1 |\n\n"
                "**Total Living Requirements**: 3 across 1 domain shards.\n"
            )
            with open(os.path.join(tmpdir, "docs", "requirements.md"), "w", encoding="utf-8") as f:
                f.write(master_4col)

            ok, errs = verify_master_table_parity(repo_root=tmpdir)
            self.assertTrue(ok, f"Expected 4-column parity to pass, got: {errs}")

            # Now modify master table to have mismatched Implemented count
            mismatched_master = master_4col.replace("| 3 | 1 | 1 | 1 |", "| 3 | 2 | 0 | 1 |")
            with open(os.path.join(tmpdir, "docs", "requirements.md"), "w", encoding="utf-8") as f:
                f.write(mismatched_master)

            ok_fail, errs_fail = verify_master_table_parity(repo_root=tmpdir)
            self.assertFalse(ok_fail)
            self.assertTrue(any("Count mismatch for req_test.md" in e for e in errs_fail))

            # Recalculate should restore 4-column parity
            recalculate_master_index_tables(repo_root=tmpdir)
            ok_recalc, errs_recalc = verify_master_table_parity(repo_root=tmpdir)
            self.assertTrue(ok_recalc, f"Expected parity after recalculate, got: {errs_recalc}")

    def test_scenario_39_evidence_status_rule(self):
        """TST-PRO-073.4: Verify requirement evidence status rule ([Verified] requires existing TST)."""
        req_shards = {
            "docs/requirements/req_test.md": (
                "### REQ-E-001: Valid Verified [Verified]\n"
                "- **Tests**: `TST-E-001`\n\n"
                "### REQ-E-002: Invalid Verified [Verified]\n"
                "- **Tests**: None\n\n"
                "### REQ-E-003: Valid Implemented [Implemented]\n"
                "- **Tests**: None\n\n"
                "### REQ-E-004: Valid Planned [Planned]\n"
                "- **Tests**: None\n\n"
                "### REQ-E-005: Verified With Nonexistent Test [Verified]\n"
                "- **Tests**: `TST-NONEXISTENT-999`\n"
            )
        }
        tst_shards = {
            "docs/tests/tst_test.md": (
                "### TST-E-001: Authentic Test [Verified]\n"
                "- **Requirement(s)**: `REQ-E-001`\n"
            )
        }

        # Full check without allowlist should flag REQ-E-002 and REQ-E-005
        ok_fail, diag_fail = verify_requirement_evidence_status(
            fail_on_unverified=True,
            req_shards=req_shards,
            tst_shards=tst_shards,
            legacy_allowlist=set(),
        )
        self.assertFalse(ok_fail)
        self.assertTrue(any("REQ-E-002" in d and "missing_or_empty_tests" in d for d in diag_fail))
        self.assertTrue(any("REQ-E-005" in d and "non_existent_tests" in d for d in diag_fail))

        # Check with REQ-E-002 and REQ-E-005 in legacy allowlist (when fail_on_unverified=False)
        ok_warn, diag_warn = verify_requirement_evidence_status(
            fail_on_unverified=False,
            req_shards=req_shards,
            tst_shards=tst_shards,
            legacy_allowlist={"REQ-E-002", "REQ-E-005"},
        )
        self.assertTrue(ok_warn)
        self.assertTrue(any("Legacy ungrounded requirement" in d for d in diag_warn))

        # Only clean REQs
        clean_reqs = {
            "docs/requirements/req_clean.md": (
                "### REQ-C-001: Verified [Verified]\n"
                "- **Tests**: `TST-E-001`\n\n"
                "### REQ-C-002: Implemented [Implemented]\n"
                "- **Tests**: None\n\n"
                "### REQ-C-003: Planned [Planned]\n"
                "- **Tests**: None\n"
            )
        }
        ok_clean, diag_clean = verify_requirement_evidence_status(
            fail_on_unverified=True,
            req_shards=clean_reqs,
            tst_shards=tst_shards,
            legacy_allowlist=set(),
        )
        self.assertTrue(ok_clean, f"Expected clean evidence verification to pass, got: {diag_clean}")

    def test_scenario_40_sysreq_citation_integrity_and_dangling_detection(self):
        """TST-PRO-076.1: Verify SYSREQ citation integrity and dangling SYSREQ reference detection."""
        with tempfile.TemporaryDirectory() as tmpdir:
            sys_dir = os.path.join(tmpdir, "docs", "system")
            req_dir = os.path.join(tmpdir, "docs", "requirements")
            os.makedirs(sys_dir)
            os.makedirs(req_dir)

            # Valid system requirements specification
            with open(os.path.join(sys_dir, "sysreq_test.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# System Requirements Test\n\n"
                    "### SYSREQ-TST-001: Telemetry Acquisition\n"
                    "- Statement: The system SHALL acquire telemetry.\n\n"
                    "## 4. Traceability Matrix\n"
                    "| `SYSREQ-TST-001` | Telemetry | `REQ-TST-001` | Test |\n"
                )

            # REQ citing dangling SYSREQ
            with open(os.path.join(req_dir, "req_test.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Living Requirements Test\n\n"
                    "### REQ-TST-001: Sensor Driver [Verified]\n"
                    "- **System Requirement**: `SYSREQ-TST-001`\n"
                    "- **Tests**: `TST-TST-001`\n\n"
                    "### REQ-TST-002: Invalid Reference [Verified]\n"
                    "- **System Requirement**: `SYSREQ-NONEXISTENT-999`\n"
                    "- **Tests**: `TST-TST-001`\n"
                )

            ok_fail, diag_fail, report_fail = verify_system_software_traceability(
                repo_root=tmpdir,
                fail_on_dangling_sysreq=True,
            )
            self.assertFalse(ok_fail)
            self.assertTrue(any("SYSREQ-NONEXISTENT-999" in d for d in diag_fail))

            # Fix dangling citation
            with open(os.path.join(req_dir, "req_test.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Living Requirements Test\n\n"
                    "### REQ-TST-001: Sensor Driver [Verified]\n"
                    "- **System Requirement**: `SYSREQ-TST-001`\n"
                    "- **Tests**: `TST-TST-001`\n\n"
                    "### REQ-TST-002: Clean Second Req [Verified]\n"
                    "- **System Requirement**: `SYSREQ-TST-001`\n"
                    "- **Tests**: `TST-TST-001`\n"
                )

            ok_pass, diag_pass, report_pass = verify_system_software_traceability(
                repo_root=tmpdir,
                fail_on_dangling_sysreq=True,
            )
            self.assertTrue(ok_pass, f"Expected clean pass, got: {diag_pass}")
            self.assertEqual(len(report_pass["dangling_sysreq_citations"]), 0)

    def test_scenario_41_sysreq_coverage_by_software_requirements(self):
        """TST-PRO-076.2: Verify coverage calculation of SYSREQs by SWE requirements."""
        with tempfile.TemporaryDirectory() as tmpdir:
            sys_dir = os.path.join(tmpdir, "docs", "system")
            req_dir = os.path.join(tmpdir, "docs", "requirements")
            os.makedirs(sys_dir)
            os.makedirs(req_dir)

            # Two declared system requirements
            with open(os.path.join(sys_dir, "sysreq_test.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# System Requirements Test\n\n"
                    "### SYSREQ-TST-001: Telemetry Acquisition\n"
                    "- Statement: Telemetry acquisition.\n\n"
                    "### SYSREQ-TST-002: Data Export\n"
                    "- Statement: Data export.\n\n"
                    "## 4. Traceability Matrix\n"
                    "| `SYSREQ-TST-001` | Telemetry | `REQ-TST-001` | Test |\n"
                )

            # Only SYSREQ-TST-001 is covered; SYSREQ-TST-002 is uncovered
            with open(os.path.join(req_dir, "req_test.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Living Requirements Test\n\n"
                    "### REQ-TST-001: Sensor Driver [Verified]\n"
                    "- **System Requirement**: `SYSREQ-TST-001`\n"
                    "- **Tests**: `TST-TST-001`\n"
                )

            ok_gap, diag_gap, report_gap = verify_system_software_traceability(
                repo_root=tmpdir,
                fail_on_missing_coverage=True,
            )
            self.assertFalse(ok_gap)
            self.assertIn("SYSREQ-TST-002", report_gap["uncovered_sysreqs"])
            self.assertEqual(report_gap["coverage_pct"], 50.0)

            # Now add coverage for SYSREQ-TST-002
            with open(os.path.join(req_dir, "req_test.md"), "a", encoding="utf-8") as f:
                f.write(
                    "\n### REQ-TST-002: Export Engine [Verified]\n"
                    "- **System Requirement**: `SYSREQ-TST-002`\n"
                    "- **Tests**: `TST-TST-001`\n"
                )

            ok_full, diag_full, report_full = verify_system_software_traceability(
                repo_root=tmpdir,
                fail_on_missing_coverage=True,
            )
            self.assertTrue(ok_full, f"Expected 100% coverage pass, got: {diag_full}")
            self.assertEqual(report_full["coverage_pct"], 100.0)
            self.assertEqual(len(report_full["uncovered_sysreqs"]), 0)

    def test_scenario_42_prefix_tailoring_rules_and_swe_derived_classification(self):
        """TST-PRO-076.3: Verify domain and prefix tailoring rules for software-derived requirements."""
        # Assert canonical UI and process prefixes belong to SWE_DERIVED_PREFIXES
        self.assertIn("UI", SWE_DERIVED_PREFIXES)
        self.assertIn("UNT", SWE_DERIVED_PREFIXES)
        self.assertIn("PRO", SWE_DERIVED_PREFIXES)
        self.assertIn("PER", SWE_DERIVED_PREFIXES)
        self.assertIn("SET", SWE_DERIVED_PREFIXES)
        self.assertIn("REV", SWE_DERIVED_PREFIXES)
        self.assertIn("STP", SWE_DERIVED_PREFIXES)

        # Assert telemetry and data exchange prefixes belong to SYSTEM_REFINED_PREFIXES
        self.assertIn("TRK", SYSTEM_REFINED_PREFIXES)
        self.assertIn("CON", SYSTEM_REFINED_PREFIXES)
        self.assertIn("FIL", SYSTEM_REFINED_PREFIXES)
        self.assertIn("DAT", SYSTEM_REFINED_PREFIXES)
        self.assertIn("EXP", SYSTEM_REFINED_PREFIXES)

    def test_scenario_43_live_repository_sys_traceability_integration(self):
        """TST-PRO-076.4: Verify system-to-software requirement traceability across live repository."""
        ok, diag, report = verify_system_software_traceability()
        self.assertTrue(ok, f"Expected live repository traceability to pass, got: {diag}")
        self.assertEqual(report["coverage_pct"], 100.0)
        self.assertEqual(report["covered_count"], 12)
        self.assertEqual(report["sysreq_count"], 12)
        self.assertEqual(len(report["dangling_sysreq_citations"]), 0)
        self.assertEqual(len(report["uncovered_sysreqs"]), 0)

    def test_scenario_44_requirement_attributes_validation(self):
        """TST-PRO-077.1: Verify attribute presence and domain validation for Priority, Type, and Verification Method."""
        valid_content = """### REQ-VAL-001: Valid Requirement [Verified]
- **Tests**: `TST-VAL-001`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit / Review
- **Implementation**: `ValidComponent.kt`
"""
        missing_pri = """### REQ-VAL-002: Missing Priority [Planned]
- **Tests**: `TST-VAL-002`
- **Type**: Non-Functional
- **Verification Method**: Integration
"""
        missing_type = """### REQ-VAL-003: Missing Type [Planned]
- **Tests**: `TST-VAL-003`
- **Priority**: Critical
- **Verification Method**: Formal Analysis
"""
        missing_vm = """### REQ-VAL-004: Missing Verification Method [Planned]
- **Tests**: `TST-VAL-004`
- **Priority**: Low
- **Type**: Functional
"""
        invalid_values = """### REQ-VAL-005: Invalid Attribute Values [Planned]
- **Tests**: `TST-VAL-005`
- **Priority**: SuperHigh
- **Type**: UnknownType
- **Verification Method**: HandWaving
"""

        # 1. Valid requirement passes
        shards_valid = {"docs/requirements/req_valid.md": valid_content}
        defective_valid = find_missing_requirement_attributes(req_shards=shards_valid)
        self.assertEqual(len(defective_valid), 0)

        # 2. Missing Priority detected
        shards_mis_pri = {"docs/requirements/req_test.md": missing_pri}
        defective_pri = find_missing_requirement_attributes(req_shards=shards_mis_pri)
        self.assertEqual(len(defective_pri), 1)
        self.assertEqual(defective_pri[0]["req_id"], "REQ-VAL-002")
        self.assertIn("Priority", defective_pri[0]["missing"])

        # 3. Missing Type detected
        shards_mis_type = {"docs/requirements/req_test.md": missing_type}
        defective_type = find_missing_requirement_attributes(req_shards=shards_mis_type)
        self.assertEqual(len(defective_type), 1)
        self.assertEqual(defective_type[0]["req_id"], "REQ-VAL-003")
        self.assertIn("Type", defective_type[0]["missing"])

        # 4. Missing Verification Method detected
        shards_mis_vm = {"docs/requirements/req_test.md": missing_vm}
        defective_vm = find_missing_requirement_attributes(req_shards=shards_mis_vm)
        self.assertEqual(len(defective_vm), 1)
        self.assertEqual(defective_vm[0]["req_id"], "REQ-VAL-004")
        self.assertIn("Verification Method", defective_vm[0]["missing"])

        # 5. Invalid values detected
        shards_invalid = {"docs/requirements/req_test.md": invalid_values}
        defective_inv = find_missing_requirement_attributes(req_shards=shards_invalid)
        self.assertEqual(len(defective_inv), 1)
        self.assertEqual(defective_inv[0]["req_id"], "REQ-VAL-005")
        self.assertEqual(len(defective_inv[0]["invalid"]), 3)

    def test_scenario_45_diff_attribute_enforcement_and_legacy_tolerance(self):
        """TST-PRO-077.2: Verify diff enforcement fails on new REQs without attributes, while legacy REQs pass with warning."""
        shard_content = """### REQ-LEG-001: Legacy Item [Verified]
- **Tests**: `TST-LEG-001`
- **Implementation**: `Legacy.kt`

### REQ-NEW-001: New Item [Planned]
- **Tests**: `TST-NEW-001`
- **Implementation**: `New.kt`
"""
        shards = {"docs/requirements/req_test.md": shard_content}

        # Diff adds REQ-NEW-001 without attributes
        diff_text = """diff --git a/docs/requirements/req_test.md b/docs/requirements/req_test.md
+### REQ-NEW-001: New Item [Planned]
+- **Tests**: `TST-NEW-001`
+- **Implementation**: `New.kt`
"""
        ok, diag = verify_requirement_attributes(
            req_shards=shards,
            diff_text=diff_text,
            enforce_attributes=False,
        )
        self.assertFalse(ok)
        self.assertTrue(any("REQ-NEW-001" in msg and "ERROR" in msg for msg in diag))

        # Now give REQ-NEW-001 valid attributes in shard
        shard_content_fixed = """### REQ-LEG-001: Legacy Item [Verified]
- **Tests**: `TST-LEG-001`
- **Implementation**: `Legacy.kt`

### REQ-NEW-001: New Item [Planned]
- **Tests**: `TST-NEW-001`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `New.kt`
"""
        shards_fixed = {"docs/requirements/req_test.md": shard_content_fixed}
        ok_fixed, diag_fixed = verify_requirement_attributes(
            req_shards=shards_fixed,
            diff_text=diff_text,
            enforce_attributes=False,
        )
        self.assertTrue(ok_fixed, f"Expected pass with legacy tolerance, got: {diag_fixed}")
        self.assertTrue(any("WARNING" in msg and "REQ-LEG-001" in msg for msg in diag_fixed))
        self.assertTrue(any("1 legacy requirements tolerated" in msg for msg in diag_fixed))

    def test_scenario_46_strict_attribute_enforcement_flag(self):
        """TST-PRO-077.3: Verify --enforce-attributes fails if ANY legacy requirement lacks attributes."""
        shard_content = """### REQ-LEG-001: Legacy Item [Verified]
- **Tests**: `TST-LEG-001`
- **Implementation**: `Legacy.kt`
"""
        shards = {"docs/requirements/req_test.md": shard_content}

        # Default mode (not enforced, no diff) -> passes with warning
        ok_def, diag_def = verify_requirement_attributes(
            req_shards=shards,
            diff_text="",
            enforce_attributes=False,
        )
        self.assertTrue(ok_def)
        self.assertTrue(any("WARNING" in msg for msg in diag_def))

        # Strict mode (--enforce-attributes) -> fails
        ok_strict, diag_strict = verify_requirement_attributes(
            req_shards=shards,
            diff_text="",
            enforce_attributes=True,
        )
        self.assertFalse(ok_strict)
        self.assertTrue(any("ERROR" in msg and "REQ-LEG-001" in msg for msg in diag_strict))

    def test_scenario_47_req_pro_077_living_doc_attribute_presence(self):
        """TST-PRO-077.4: Verify REQ-PRO-077 defines Priority, Type, and Verification Method in living documentation."""
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
        ps_path = os.path.join(repo_root, "docs", "requirements", "req_process_stability.md")
        self.assertTrue(os.path.exists(ps_path))

        with open(ps_path, "r", encoding="utf-8") as f:
            lines = f.readlines()

        shards = {"docs/requirements/req_process_stability.md": lines}
        defective = find_missing_requirement_attributes(req_shards=shards)

        # Filter defective items to check if REQ-PRO-077 is among them
        defective_pro_077 = [d for d in defective if d["req_id"] == "REQ-PRO-077"]
        self.assertEqual(
            len(defective_pro_077),
            0,
            f"REQ-PRO-077 must have valid attributes, but was flagged: {defective_pro_077}",
        )






    def test_scenario_44_verify_tests_field_format_valid_entries(self):
        """TST-PRO-075.1: Assert valid single/multiple TST IDs and recognized placeholders pass."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            req_dir = os.path.join(tmp_dir, "docs", "requirements")
            os.makedirs(req_dir)
            valid_content = """# Domain Shard
### REQ-TST-001: Test Valid Single [Verified]
- **Tests**: `TST-CON-001`
- **Implementation**: `ClassA.kt`

### REQ-TST-002: Test Valid Multiple [Verified]
- **Tests**: `TST-CON-001`, `TST-CON-002`, `TST-MAN-005`
- **Implementation**: `ClassB.kt`

### REQ-TST-003: Test Valid Placeholder None [Verified]
- **Tests**: None
- **Implementation**: `ClassC.kt`

### REQ-TST-004: Test Valid Placeholder Planned [Planned]
- **Tests**: `Planned`
- **Implementation**: `ClassD.kt`

### REQ-TST-005: Test Valid Placeholder Manual inspection [Verified]
- **Tests**: Manual inspection
- **Implementation**: `ClassE.kt`
"""
            with open(os.path.join(req_dir, "req_test.md"), "w", encoding="utf-8") as f:
                f.write(valid_content)

            ok, diag = verify_tests_field_format(repo_root=tmp_dir)
            self.assertTrue(ok, f"Expected valid Tests fields to pass, got: {diag}")
            self.assertTrue(any("PASS: All Tests fields" in d for d in diag))

    def test_scenario_45_verify_tests_field_format_rejects_corrupted_and_source_files(self):
        """TST-PRO-075.2 & TST-PRO-075.3: Assert rejection of unclosed backticks, dangling code, source files, and trailing tickets."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            req_dir = os.path.join(tmp_dir, "docs", "requirements")
            os.makedirs(req_dir)
            invalid_content = """# Domain Shard
### REQ-BAD-001: Unclosed backtick [Verified]
- **Tests**: `TST-CON-001
- **Implementation**: `ClassA.kt`

### REQ-BAD-002: Dangling code fragment [Verified]
- **Tests**: code == 201` check that inadvertently classified
- **Implementation**: `ClassB.kt`

### REQ-BAD-003: Source code files in Tests [Verified]
- **Tests**: `PeriodsViewModel.kt`, `PeriodMapScreen.kt`
- **Implementation**: `ClassC.kt`

### REQ-BAD-004: Trailing tickets annotation [Verified]
- **Tests**: `TST-CON-001` | **Tickets**: `ATT-1234`
- **Implementation**: `ClassD.kt`
"""
            with open(os.path.join(req_dir, "req_bad.md"), "w", encoding="utf-8") as f:
                f.write(invalid_content)

            ok, diag = verify_tests_field_format(repo_root=tmp_dir)
            self.assertFalse(ok, "Expected invalid Tests fields to fail")
            diag_text = "\n".join(diag)
            self.assertIn("REQ-BAD-001", diag_text)
            self.assertIn("unclosed backtick", diag_text)
            self.assertIn("REQ-BAD-002", diag_text)
            self.assertIn("invalid Tests field format", diag_text)
            self.assertIn("REQ-BAD-003", diag_text)
            self.assertIn("source code file in Tests field", diag_text)
            self.assertIn("REQ-BAD-004", diag_text)

    def test_scenario_46_verify_tests_field_format_live_repo(self):
        """TST-PRO-075.4: Assert all living requirement shards in repository pass Tests format verification."""
        ok, diag = verify_tests_field_format()
        self.assertTrue(ok, f"Expected live repository requirement shards to pass Tests format rule, got: {diag}")



if __name__ == "__main__":
    unittest.main()
