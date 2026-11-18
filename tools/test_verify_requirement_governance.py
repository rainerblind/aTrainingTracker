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

import contextlib
import io
import json
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
    main as governance_cli_main,
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
    verify_retro_write_allowlist,
    verify_sprint_metrics,
    verify_independent_reviews,
    verify_sensor_type_javadocs,
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
    LEGACY_UNBACKED_VERIFIED_TSTS,
    triage_unbacked_verified_tsts,
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
    find_traceability_orphans,
    load_traceability_baseline,
    verify_traceability_orphans,
    TraceabilityOrphansResult,
    parse_junit_xml_results,
    correlate_test_results_to_tsts,
    verify_verified_test_evidence,
    has_verified_test_evidence,
    verify_test_evidence,
    LEGACY_UNBACKED_VERIFIED_TSTS,
    find_code_traceability_orphans,
    verify_code_traceability_orphans,
    verify_shard_header_counts,
    verify_shard_sizes,
    LEGACY_MISSING_DESCRIPTION_REQS,
    verify_req_descriptions,
    calculate_req_field_coverage,
    format_req_field_coverage,
    discover_governed_work_products,
    parse_work_product_header,
    verify_work_product_headers,
    verify_conflict_markers,
    verify_commit_hygiene,
)


# Linked test specs: TST-PRO-085, TST-PRO-086
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
        std_lib_modules = {"csv", "os", "sys", "re", "subprocess", "argparse", "unittest", "json", "math", "xml", "zipfile"}
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
            tools_dir = os.path.join(tmpdir, "tools")
            os.makedirs(req_dir, exist_ok=True)
            os.makedirs(tst_dir, exist_ok=True)
            os.makedirs(tools_dir, exist_ok=True)

            with open(os.path.join(tools_dir, "test_sharding.py"), "w", encoding="utf-8") as f:
                f.write("# TST-PRO-021\n")

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
        """Scenario 13: ALLOWED_ENGINEERING_ENTRIES registers 'gates', 'qa_audits', 'reviews', 'code_reviews', and 'aspice_audits' (TST-PRO-059.3, TST-PRO-061.4, TST-PRO-062.3, TST-PRO-095.1, TST-PRO-096.1)."""
        self.assertIn("gates", ALLOWED_ENGINEERING_ENTRIES)
        self.assertIn("qa_audits", ALLOWED_ENGINEERING_ENTRIES)
        self.assertIn("reviews", ALLOWED_ENGINEERING_ENTRIES)
        self.assertIn("code_reviews", ALLOWED_ENGINEERING_ENTRIES)
        self.assertIn("aspice_audits", ALLOWED_ENGINEERING_ENTRIES)
        with tempfile.TemporaryDirectory() as tmpdir:
            eng_dir = os.path.join(tmpdir, "docs", "engineering")
            os.makedirs(os.path.join(eng_dir, "gates"), exist_ok=True)
            os.makedirs(os.path.join(eng_dir, "analysis"), exist_ok=True)
            os.makedirs(os.path.join(eng_dir, "reviews"), exist_ok=True)
            os.makedirs(os.path.join(eng_dir, "code_reviews"), exist_ok=True)
            os.makedirs(os.path.join(eng_dir, "aspice_audits"), exist_ok=True)
            ok, errors = verify_engineering_directory_structure(eng_dir=eng_dir)
            self.assertTrue(ok, f"Expected gates, reviews, code_reviews, and aspice_audits to be allowed, got errors: {errors}")

            # Non-canonical entry should fail
            os.makedirs(os.path.join(eng_dir, "invalid_dir"), exist_ok=True)
            ok_bad, errors_bad = verify_engineering_directory_structure(eng_dir=eng_dir)
            self.assertFalse(ok_bad)
            self.assertTrue(any("invalid_dir" in e for e in errors_bad))

    def test_scenario_weekly_dual_vendor_code_reviews_registration(self):
        """TST-PRO-095.1: Assert 'code_reviews' is canonical in ALLOWED_ENGINEERING_ENTRIES."""
        self.assertIn("code_reviews", ALLOWED_ENGINEERING_ENTRIES)
        with tempfile.TemporaryDirectory() as tmpdir:
            eng_dir = os.path.join(tmpdir, "docs", "engineering")
            os.makedirs(os.path.join(eng_dir, "code_reviews"), exist_ok=True)
            ok, errors = verify_engineering_directory_structure(eng_dir=eng_dir)
            self.assertTrue(ok, f"Expected code_reviews to be allowed, got errors: {errors}")

    def test_scenario_weekly_dual_vendor_aspice_audits_registration(self):
        """TST-PRO-096.1: Assert 'aspice_audits' is canonical in ALLOWED_ENGINEERING_ENTRIES."""
        self.assertIn("aspice_audits", ALLOWED_ENGINEERING_ENTRIES)
        with tempfile.TemporaryDirectory() as tmpdir:
            eng_dir = os.path.join(tmpdir, "docs", "engineering")
            os.makedirs(os.path.join(eng_dir, "aspice_audits"), exist_ok=True)
            ok, errors = verify_engineering_directory_structure(eng_dir=eng_dir)
            self.assertTrue(ok, f"Expected aspice_audits to be allowed, got errors: {errors}")

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
                "**Auditor Role**: `qa`\n\n"
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

            # 6. Valid SKIPPED, NOT PERFORMED, and PARTIAL verdicts
            for alt_verdict in ("SKIPPED", "NOT PERFORMED", "PARTIAL"):
                alt_content = valid_content.replace("**Verdict**: **PASS**", f"**Verdict**: **{alt_verdict}**")
                with open(target_file, "w", encoding="utf-8") as f:
                    f.write(alt_content)
                ok_alt, msgs_alt = verify_independent_reviews(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
                self.assertTrue(ok_alt, f"Expected pass for verdict {alt_verdict}, got: {msgs_alt}")

            # 7. Wildcard verification without sprint name
            ok_any, msgs_any = verify_independent_reviews(sprint_name=None, repo_root=tmpdir)
            self.assertTrue(ok_any, f"Expected pass, got: {msgs_any}")
            self.assertTrue(any("Verified 1 independent review record(s)" in m for m in msgs_any))

            # 8. Layer 4 Human Sprint Review record with named human reviewer passes
            layer4_file = os.path.join(reviews_dir, "sprint_2026-46.4.md")
            with open(layer4_file, "w", encoding="utf-8") as f:
                f.write(
                    valid_content
                    .replace("Human Process Owner", "Rainer Blind")
                    .replace("Human Sprint Review (Ceremony 2)", "Human Sprint Review (Layer 4)")
                )
            ok_l4, msgs_l4 = verify_independent_reviews(sprint_name="2026-46.4", repo_root=tmpdir)
            self.assertTrue(ok_l4, f"Expected Layer 4 pass, got: {msgs_l4}")

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

    def test_scenario_24_verify_engineering_directory_structure_test_evidence(self):
        """TST-PRO-067.3 / TST-PRO-067.4 / TST-PRO-100.2: Verify verify_engineering_directory_structure enforces test_evidence and rejects retired test_results symlink."""
        self.assertIn("test_evidence", ALLOWED_ENGINEERING_ENTRIES)
        self.assertNotIn("test_results", ALLOWED_ENGINEERING_ENTRIES)
        with tempfile.TemporaryDirectory() as tmpdir:
            eng_dir = os.path.join(tmpdir, "docs", "engineering")
            os.makedirs(eng_dir, exist_ok=True)
            for entry in ["analysis", "test_specs", "plans", "walkthroughs", "retros", "test_evidence"]:
                os.makedirs(os.path.join(eng_dir, entry), exist_ok=True)

            # 1. Obsolete test_results symlink fails
            bad_results = os.path.join(eng_dir, "test_results")
            os.symlink("/some/target", bad_results)
            ok_bad, errs_bad = verify_engineering_directory_structure(eng_dir=eng_dir)
            self.assertFalse(ok_bad)
            self.assertTrue(any("obsolete and retired" in e for e in errs_bad))
            os.unlink(bad_results)

            # 2. Canonical test_evidence directory passes
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
        self.assertTrue(any("All cited REQ and TST references resolve to authentic headings" in m or "Zero new dangling" in m for m in diag))

        # Check exact counts of legacy dangling references
        dangling = find_dangling_references()
        self.assertEqual(len(dangling.dangling_tsts), 0)
        self.assertEqual(len(dangling.dangling_reqs), 0)

        tst_targets = set(r.target_id for r in dangling.dangling_tsts)
        req_targets = set(r.target_id for r in dangling.dangling_reqs)
        self.assertEqual(tst_targets, LEGACY_DANGLING_TST_REFS)
        self.assertEqual(req_targets, LEGACY_DANGLING_REQ_REFS)

        # Strict mode: passes cleanly because all legacy dangling references have been resolved across all domains!
        ok_strict, diag_strict = verify_dangling_references(fail_on_dangling=True)
        self.assertTrue(ok_strict, f"Expected strict mode to pass with 0 dangling refs, got: {diag_strict}")
        self.assertTrue(any("All cited REQ and TST references resolve to authentic headings" in m for m in diag_strict))

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
                    "### SYSREQ-TRK-001: Telemetry Acquisition\n"
                    "- Statement: The system SHALL acquire telemetry.\n\n"
                    "## 4. Traceability Matrix\n"
                    "| `SYSREQ-TRK-001` | Telemetry | `REQ-TRK-001` | Test |\n"
                )

            # REQ citing dangling SYSREQ
            with open(os.path.join(req_dir, "req_test.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Living Requirements Test\n\n"
                    "### REQ-TRK-001: Sensor Driver [Verified]\n"
                    "- **System Requirement**: `SYSREQ-TRK-001`\n"
                    "- **Tests**: `TST-TRK-001`\n\n"
                    "### REQ-TRK-002: Invalid Reference [Verified]\n"
                    "- **System Requirement**: `SYSREQ-NONEXISTENT-999`\n"
                    "- **Tests**: `TST-TRK-001`\n"
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
                    "### REQ-TRK-001: Sensor Driver [Verified]\n"
                    "- **System Requirement**: `SYSREQ-TRK-001`\n"
                    "- **Tests**: `TST-TRK-001`\n\n"
                    "### REQ-TRK-002: Clean Second Req [Verified]\n"
                    "- **System Requirement**: `SYSREQ-TRK-001`\n"
                    "- **Tests**: `TST-TRK-001`\n"
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
                    "### SYSREQ-TRK-001: Telemetry Acquisition\n"
                    "- Statement: Telemetry acquisition.\n\n"
                    "### SYSREQ-TRK-002: Data Export\n"
                    "- Statement: Data export.\n\n"
                    "## 4. Traceability Matrix\n"
                    "| `SYSREQ-TRK-001` | Telemetry | `REQ-TRK-001` | Test |\n"
                )

            # Only SYSREQ-TRK-001 is covered; SYSREQ-TRK-002 is uncovered
            with open(os.path.join(req_dir, "req_test.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Living Requirements Test\n\n"
                    "### REQ-TRK-001: Sensor Driver [Verified]\n"
                    "- **System Requirement**: `SYSREQ-TRK-001`\n"
                    "- **Tests**: `TST-TRK-001`\n"
                )

            ok_gap, diag_gap, report_gap = verify_system_software_traceability(
                repo_root=tmpdir,
                fail_on_missing_coverage=True,
            )
            self.assertFalse(ok_gap)
            self.assertIn("SYSREQ-TRK-002", report_gap["uncovered_sysreqs"])
            self.assertEqual(report_gap["coverage_pct"], 50.0)

            # Now add coverage for SYSREQ-TRK-002
            with open(os.path.join(req_dir, "req_test.md"), "a", encoding="utf-8") as f:
                f.write(
                    "\n### REQ-TRK-002: Export Engine [Verified]\n"
                    "- **System Requirement**: `SYSREQ-TRK-002`\n"
                    "- **Tests**: `TST-TRK-001`\n"
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
        req_dir = os.path.join(repo_root, "docs", "requirements")
        ps_files = sorted(
            fn for fn in os.listdir(req_dir)
            if fn == "req_process_stability.md" or (fn.startswith("req_process_stability_part") and fn.endswith(".md"))
        )
        self.assertTrue(len(ps_files) > 0, "Expected req_process_stability*.md shard(s)")

        shards = {}
        for fn in ps_files:
            with open(os.path.join(req_dir, fn), "r", encoding="utf-8") as f:
                shards[f"docs/requirements/{fn}"] = f.readlines()
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

    def test_scenario_47_bidirectional_traceability_clean_pass_with_baseline(self):
        """TST-PRO-087.1 (Criterion 1): Valid bidirectional traceability with baselined orphans passes cleanly."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            req_dir = os.path.join(tmp_dir, "docs", "requirements")
            tst_dir = os.path.join(tmp_dir, "docs", "tests")
            tools_dir = os.path.join(tmp_dir, "tools")
            os.makedirs(req_dir)
            os.makedirs(tst_dir)
            os.makedirs(tools_dir)

            with open(os.path.join(req_dir, "req_test.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Shard\n"
                    "### REQ-DOM-001: First Requirement [Verified]\n- **Tests**: `TST-DOM-001`\n\n"
                    "### REQ-DOM-002: Baselined Legacy Requirement [Verified]\n- **Tests**: `Planned`\n"
                )

            with open(os.path.join(tst_dir, "tst_test.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Test Shard\n"
                    "### TST-DOM-001: First Test [Verified]\n- **Verifies**: `REQ-DOM-001`\n"
                )

            baseline_data = {
                "metadata": {"total_baseline_orphans": 1},
                "orphan_requirements": ["REQ-DOM-002"],
            }
            baseline_path = os.path.join(tools_dir, "traceability_baseline.json")
            with open(baseline_path, "w", encoding="utf-8") as f:
                json.dump(baseline_data, f)

            ok, diag, report = verify_traceability_orphans(
                repo_root=tmp_dir,
                baseline_path=baseline_path,
                fail_on_orphans=True,
            )
            self.assertTrue(ok, f"Expected clean pass, got: {diag}")
            self.assertEqual(report["orphan_count"], 1)
            self.assertEqual(report["unbaselined_orphans"], [])
            self.assertEqual(report["stale_orphans"], [])

    def test_scenario_48_criterion_1_unbaselined_orphan_rejection(self):
        """TST-PRO-087.1 (Criterion 1): Requirements without verifying test outside baseline are rejected."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            req_dir = os.path.join(tmp_dir, "docs", "requirements")
            tst_dir = os.path.join(tmp_dir, "docs", "tests")
            tools_dir = os.path.join(tmp_dir, "tools")
            os.makedirs(req_dir)
            os.makedirs(tst_dir)
            os.makedirs(tools_dir)

            with open(os.path.join(req_dir, "req_test.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Shard\n"
                    "### REQ-DOM-001: Verified Requirement [Verified]\n- **Tests**: `TST-DOM-001`\n\n"
                    "### REQ-DOM-003: Unbaselined Orphan [Verified]\n- **Tests**: `Planned`\n"
                )

            with open(os.path.join(tst_dir, "tst_test.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Test Shard\n"
                    "### TST-DOM-001: First Test [Verified]\n- **Verifies**: `REQ-DOM-001`\n"
                )

            baseline_path = os.path.join(tools_dir, "traceability_baseline.json")
            with open(baseline_path, "w", encoding="utf-8") as f:
                json.dump({"orphan_requirements": []}, f)

            ok, diag, report = verify_traceability_orphans(
                repo_root=tmp_dir,
                baseline_path=baseline_path,
                fail_on_orphans=True,
            )
            self.assertFalse(ok, "Expected failure on unbaselined orphan")
            diag_text = "\n".join(diag)
            self.assertIn("REQ-DOM-003", diag_text)
            self.assertIn("Criterion 1", diag_text)
            self.assertEqual(report["unbaselined_orphans"], ["REQ-DOM-003"])

    def test_scenario_49_criterion_2_dangling_target_requirement_rejection(self):
        """TST-PRO-087.1 (Criterion 2): Tests referencing non-existent requirements are rejected."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            req_dir = os.path.join(tmp_dir, "docs", "requirements")
            tst_dir = os.path.join(tmp_dir, "docs", "tests")
            tools_dir = os.path.join(tmp_dir, "tools")
            os.makedirs(req_dir)
            os.makedirs(tst_dir)
            os.makedirs(tools_dir)

            with open(os.path.join(req_dir, "req_test.md"), "w", encoding="utf-8") as f:
                f.write("# Shard\n### REQ-DOM-001: Real Requirement [Verified]\n- **Tests**: `TST-DOM-001`\n")

            with open(os.path.join(tst_dir, "tst_test.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Test Shard\n"
                    "### TST-DOM-001: Valid Test [Verified]\n- **Verifies**: `REQ-DOM-001`\n\n"
                    "### TST-DOM-002: Broken Test [Verified]\n- **Verifies**: `REQ-NONEXISTENT-999`\n"
                )

            baseline_path = os.path.join(tools_dir, "traceability_baseline.json")
            with open(baseline_path, "w", encoding="utf-8") as f:
                json.dump({"orphan_requirements": []}, f)

            ok, diag, report = verify_traceability_orphans(
                repo_root=tmp_dir,
                baseline_path=baseline_path,
                fail_on_orphans=True,
            )
            self.assertFalse(ok, "Expected failure on dangling requirement target")
            diag_text = "\n".join(diag)
            self.assertIn("REQ-NONEXISTENT-999", diag_text)
            self.assertIn("Criterion 2", diag_text)

    def test_scenario_50_criterion_3_ratchet_monotonicity_and_inflation_rejection(self):
        """TST-PRO-087.1 (Criterion 3): Ratchet rejects baseline inflation when orphan count grows."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            req_dir = os.path.join(tmp_dir, "docs", "requirements")
            tst_dir = os.path.join(tmp_dir, "docs", "tests")
            tools_dir = os.path.join(tmp_dir, "tools")
            os.makedirs(req_dir)
            os.makedirs(tst_dir)
            os.makedirs(tools_dir)

            with open(os.path.join(req_dir, "req_test.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Shard\n"
                    "### REQ-DOM-001: Req 1 [Verified]\n- **Tests**: `Planned`\n\n"
                    "### REQ-DOM-002: Req 2 [Verified]\n- **Tests**: `Planned`\n"
                )

            with open(os.path.join(tst_dir, "tst_test.md"), "w", encoding="utf-8") as f:
                f.write("# Empty Test Shard\n")

            # Baseline has only 1 orphan, but 2 exist in docs -> inflation
            baseline_path = os.path.join(tools_dir, "traceability_baseline.json")
            with open(baseline_path, "w", encoding="utf-8") as f:
                json.dump({"orphan_requirements": ["REQ-DOM-001"]}, f)

            ok, diag, report = verify_traceability_orphans(
                repo_root=tmp_dir,
                baseline_path=baseline_path,
                fail_on_orphans=True,
            )
            self.assertFalse(ok, "Expected failure when orphan count exceeds baseline count")
            diag_text = "\n".join(diag)
            self.assertIn("Criterion 3 Ratchet", diag_text)

    def test_scenario_51_criterion_3_stale_baseline_rejection_forces_shrink(self):
        """TST-PRO-087.1 (Criterion 3): Resolving an orphan forces baseline shrinking (stale baseline rejected)."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            req_dir = os.path.join(tmp_dir, "docs", "requirements")
            tst_dir = os.path.join(tmp_dir, "docs", "tests")
            tools_dir = os.path.join(tmp_dir, "tools")
            os.makedirs(req_dir)
            os.makedirs(tst_dir)
            os.makedirs(tools_dir)

            with open(os.path.join(req_dir, "req_test.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Shard\n"
                    "### REQ-DOM-001: Resolved Requirement [Verified]\n- **Tests**: `TST-DOM-001`\n"
                )

            with open(os.path.join(tst_dir, "tst_test.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Test Shard\n"
                    "### TST-DOM-001: Verifying Test [Verified]\n- **Verifies**: `REQ-DOM-001`\n"
                )

            # Baseline still lists REQ-DOM-001 even though it was resolved
            baseline_path = os.path.join(tools_dir, "traceability_baseline.json")
            with open(baseline_path, "w", encoding="utf-8") as f:
                json.dump({"orphan_requirements": ["REQ-DOM-001"]}, f)

            ok, diag, report = verify_traceability_orphans(
                repo_root=tmp_dir,
                baseline_path=baseline_path,
                fail_on_orphans=True,
            )
            self.assertFalse(ok, "Expected failure when baseline contains resolved requirement")
            diag_text = "\n".join(diag)
            self.assertIn("resolved requirement(s): REQ-DOM-001", diag_text)
            self.assertEqual(report["stale_orphans"], ["REQ-DOM-001"])

    def test_scenario_52_verifies_field_syntax_variants_compatibility(self):
        """TST-PRO-087.1: Validates parser compatibility across Verifies, Requirement(s), and Target Requirement."""
        req_shards = {
            "req_demo.md": (
                "### REQ-DEMO-001: Item 1 [Verified]\n- **Tests**: `TST-DEMO-001`\n\n"
                "### REQ-DEMO-002: Item 2 [Verified]\n- **Tests**: `TST-DEMO-002`\n\n"
                "### REQ-DEMO-003: Item 3 [Verified]\n- **Tests**: `TST-DEMO-003`\n"
            )
        }
        tst_shards = {
            "tst_demo.md": (
                "### TST-DEMO-001: Test 1 [Verified]\n- **Verifies**: `REQ-DEMO-001`\n\n"
                "### TST-DEMO-002: Test 2 [Verified]\n- **Requirement(s)**: `REQ-DEMO-002`\n\n"
                "### TST-DEMO-003: Test 3 [Verified]\n- **Target Requirement**: `REQ-DEMO-003`\n"
            )
        }
        res = find_traceability_orphans(req_shards=req_shards, tst_shards=tst_shards)
        self.assertEqual(len(res.orphan_reqs), 0)
        self.assertEqual(len(res.dangling_req_cites), 0)
        self.assertEqual(len(res.tsts_without_reqs), 0)

    def test_scenario_53_live_repository_traceability_orphans_governance(self):
        """TST-PRO-087.1 / TST-PRO-087.3: Assert live repository passes bidirectional traceability with baseline."""
        ok, diag, report = verify_traceability_orphans()
        self.assertTrue(ok, f"Expected live repository to pass traceability governance, got: {diag}")
        self.assertEqual(report["unbaselined_orphans"], [])
        self.assertEqual(report["stale_orphans"], [])
        self.assertEqual(report["orphan_count"], 15)
        self.assertEqual(report["baseline_count"], 15)

    def test_scenario_54_verify_qa_audits_enforces_auditor_independence(self):
        """TST-PRO-091.4: verify_qa_audits enforces auditor role independence (qa/agent2 vs coordinator/agent1)."""
        with tempfile.TemporaryDirectory() as tmpdir:
            audits_dir = os.path.join(tmpdir, "docs", "engineering", "qa_audits")
            os.makedirs(audits_dir, exist_ok=True)
            target_file = os.path.join(audits_dir, "sprint_2026-46.3.md")

            content_template = (
                "# Sprint QA Process-Compliance Audit: Sprint 2026-46.3\n\n"
                "**Auditor Role**: `{role}`\n\n"
                "**Overall Process Verdict**: **COMPLIANT**\n\n"
                "| ATT-6149 | Gate Records Export | COMPLIANT |\n"
                "| ATT-6150 | Metrics Persistence | COMPLIANT |\n\n"
                "### Ticket: ATT-6149\n* **Process Verdict**: **COMPLIANT**\n\n"
                "### Ticket: ATT-6150\n* **Process Verdict**: **COMPLIANT**\n"
            )

            # coordinator role rejected
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(content_template.format(role="coordinator"))
            ok, msgs = verify_qa_audits(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("lacks ASPICE SUP.1 independence" in m for m in msgs))

            # agent1 role rejected
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(content_template.format(role="agent1"))
            ok, msgs = verify_qa_audits(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("lacks ASPICE SUP.1 independence" in m for m in msgs))

            # qa role accepted
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(content_template.format(role="qa"))
            ok, msgs = verify_qa_audits(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertTrue(ok, f"Expected pass for qa role, got: {msgs}")

            # agent2 role accepted
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(content_template.format(role="agent2"))
            ok, msgs = verify_qa_audits(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertTrue(ok, f"Expected pass for agent2 role, got: {msgs}")

    def test_scenario_55_verify_qa_audits_enforces_dynamic_sample_size(self):
        """TST-PRO-091.2: verify_qa_audits enforces dynamic sample size >= max(2, ceil(0.10 * N))."""
        with tempfile.TemporaryDirectory() as tmpdir:
            audits_dir = os.path.join(tmpdir, "docs", "engineering", "qa_audits")
            os.makedirs(audits_dir, exist_ok=True)
            target_file = os.path.join(audits_dir, "sprint_2026-46.3.md")

            # 25 total tickets requires ceil(25 * 0.10) = 3 samples. 2 sampled tickets should fail.
            content_insufficient = (
                "# Sprint QA Process-Compliance Audit: Sprint 2026-46.3\n\n"
                "**Auditor Role**: `qa`\n\n"
                "**Sample Size**: 2 / 25 tickets (8.0%)\n\n"
                "**Overall Process Verdict**: **COMPLIANT**\n\n"
                "| ATT-6149 | Gate Records Export | COMPLIANT |\n"
                "| ATT-6150 | Metrics Persistence | COMPLIANT |\n\n"
                "### Ticket: ATT-6149\n* **Process Verdict**: **COMPLIANT**\n\n"
                "### Ticket: ATT-6150\n* **Process Verdict**: **COMPLIANT**\n"
            )
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(content_insufficient)
            ok, msgs = verify_qa_audits(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("below required >= 10% dynamic threshold" in m for m in msgs))

            # 3 sampled tickets out of 25 tickets passes (>= 3)
            content_sufficient = (
                "# Sprint QA Process-Compliance Audit: Sprint 2026-46.3\n\n"
                "**Auditor Role**: `qa`\n\n"
                "**Sample Size**: 3 / 25 tickets (12.0%)\n\n"
                "**Overall Process Verdict**: **COMPLIANT**\n\n"
                "| ATT-6149 | Gate Records Export | COMPLIANT |\n"
                "| ATT-6150 | Metrics Persistence | COMPLIANT |\n"
                "| ATT-6151 | Notification Service | COMPLIANT |\n\n"
                "### Ticket: ATT-6149\n* **Process Verdict**: **COMPLIANT**\n\n"
                "### Ticket: ATT-6150\n* **Process Verdict**: **COMPLIANT**\n\n"
                "### Ticket: ATT-6151\n* **Process Verdict**: **COMPLIANT**\n"
            )
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(content_sufficient)
            ok, msgs = verify_qa_audits(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertTrue(ok, f"Expected pass for sufficient sample size, got: {msgs}")

    def test_scenario_56_verify_qa_audits_requires_corrective_action_links(self):
        """TST-PRO-091.3: verify_qa_audits requires Section 4 with Jira ticket links [ATT-XXXX] for NON-COMPLIANT audits."""
        with tempfile.TemporaryDirectory() as tmpdir:
            audits_dir = os.path.join(tmpdir, "docs", "engineering", "qa_audits")
            os.makedirs(audits_dir, exist_ok=True)
            target_file = os.path.join(audits_dir, "sprint_2026-46.3.md")

            # Missing Section 4 on NON-COMPLIANT verdict
            content_no_sec4 = (
                "# Sprint QA Process-Compliance Audit: Sprint 2026-46.3\n\n"
                "**Auditor Role**: `qa`\n\n"
                "**Overall Process Verdict**: **NON-COMPLIANT**\n\n"
                "| ATT-6149 | Gate Records Export | NON-COMPLIANT |\n"
                "| ATT-6150 | Metrics Persistence | COMPLIANT |\n\n"
                "### Ticket: ATT-6149\n* **Process Verdict**: **NON-COMPLIANT**\n\n"
                "### Ticket: ATT-6150\n* **Process Verdict**: **COMPLIANT**\n"
            )
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(content_no_sec4)
            ok, msgs = verify_qa_audits(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("missing Section 4" in m for m in msgs))

            # Section 4 present but lacking Jira ticket links
            content_sec4_no_links = content_no_sec4 + (
                "\n## 4. Non-Compliance Tracking & Remediation\n"
                "Some issues were found but no ticket was filed yet.\n"
            )
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(content_sec4_no_links)
            ok, msgs = verify_qa_audits(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("lacks required Jira corrective action ticket links" in m for m in msgs))

            # Section 4 with valid Jira ticket link passes
            content_sec4_with_links = content_no_sec4 + (
                "\n## 4. Non-Compliance Tracking & Remediation\n"
                "- Ticket: [ATT-7025](https://jira.example.com/browse/ATT-7025) - Fix gate verification\n"
            )
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(content_sec4_with_links)
            ok, msgs = verify_qa_audits(sprint_name="sprint/2026-46.3", repo_root=tmpdir)
            self.assertTrue(ok, f"Expected pass when corrective action ticket link is present, got: {msgs}")

    def test_scenario_57_req_pro_094_strict_attribute_enforcement_default(self):
        """TST-PRO-094: Verify strict attribute enforcement is enabled by default without flags."""
        shard_content = """### REQ-LEG-001: Legacy Item [Verified]
- **Tests**: `TST-LEG-001`
- **Implementation**: `Legacy.kt`
"""
        shards = {"docs/requirements/req_test.md": shard_content}

        # Default mode (no enforce_attributes argument specified) -> MUST fail strictly under REQ-PRO-094
        ok_def, diag_def = verify_requirement_attributes(
            req_shards=shards,
            diff_text="",
        )
        self.assertFalse(ok_def, "Default verification must fail when attributes are missing")
        self.assertTrue(any("ERROR" in msg and "REQ-LEG-001" in msg for msg in diag_def))
        self.assertTrue(any("lacks required attributes" in msg for msg in diag_def))

        # Explicitly opt-out via enforce_attributes=False -> passes with warning
        ok_lenient, diag_lenient = verify_requirement_attributes(
            req_shards=shards,
            diff_text="",
            enforce_attributes=False,
        )
        self.assertTrue(ok_lenient)
        self.assertTrue(any("WARNING" in msg and "REQ-LEG-001" in msg for msg in diag_lenient))

        # Fully compliant requirement -> passes strictly with PASS message
        compliant_shard = """### REQ-LEG-001: Legacy Item [Verified]
- **Tests**: `TST-LEG-001`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `Legacy.kt`
"""
        ok_comp, diag_comp = verify_requirement_attributes(
            req_shards={"docs/requirements/req_test.md": compliant_shard},
            diff_text="",
        )
        self.assertTrue(ok_comp)
        self.assertTrue(any("PASS: 100% of living requirements define valid Priority, Type, and Verification Method attributes" in msg for msg in diag_comp))

    def test_scenario_58_tst_pro_099_1_junit_xml_parsing_and_mapping(self):
        """TST-PRO-099.1: Verify parsing JUnit XML results and mapping testcases to TST-XXX IDs."""
        with tempfile.TemporaryDirectory() as tmpdir:
            xml_path = os.path.join(tmpdir, "TEST-com.atrainingtracker.ExampleTest.xml")
            with open(xml_path, "w", encoding="utf-8") as f:
                f.write(
                    '<?xml version="1.0" encoding="UTF-8"?>\n'
                    '<testsuite name="com.atrainingtracker.ExampleTest" tests="3" failures="0" errors="0" skipped="1" time="0.35">\n'
                    '  <testcase classname="com.atrainingtracker.ExampleTest" name="testFoo_TST_PRO_099" time="0.10"/>\n'
                    '  <testcase classname="com.atrainingtracker.ExampleTest" name="testBar_TST_PRO_005" time="0.15"/>\n'
                    '  <testcase classname="com.atrainingtracker.ExampleTest" name="testSkipped" time="0.10">\n'
                    '    <skipped message="temporarily skipped"/>\n'
                    '  </testcase>\n'
                    '</testsuite>\n'
                )

            res = parse_junit_xml_results(results_dir=tmpdir)
            self.assertEqual(res["summary"]["total"], 3)
            self.assertEqual(res["summary"]["passed"], 2)
            self.assertEqual(res["summary"]["skipped"], 1)
            self.assertEqual(res["summary"]["failures"], 0)
            self.assertEqual(res["summary"]["errors"], 0)
            self.assertIn("TST-PRO-099", res["by_tst"])
            self.assertEqual(res["by_tst"]["TST-PRO-099"]["status"], "PASS")
            self.assertEqual(len(res["by_tst"]["TST-PRO-099"]["passing_cases"]), 1)
            self.assertIn("TST-PRO-005", res["by_tst"])

    def test_scenario_59_tst_pro_099_2_failing_evidence_rejection(self):
        """TST-PRO-099.2: Verify verify_verified_test_evidence rejects [Verified] when testcase fails (Criterion 1)."""
        with tempfile.TemporaryDirectory() as tmpdir:
            xml_path = os.path.join(tmpdir, "TEST-com.atrainingtracker.FailureTest.xml")
            with open(xml_path, "w", encoding="utf-8") as f:
                f.write(
                    '<?xml version="1.0" encoding="UTF-8"?>\n'
                    '<testsuite name="com.atrainingtracker.FailureTest" tests="1" failures="1" errors="0" time="0.05">\n'
                    '  <testcase classname="com.atrainingtracker.FailureTest" name="testBroken_TST_PRO_099" time="0.05">\n'
                    '    <failure message="AssertionError: expected true but got false">Stack trace details</failure>\n'
                    '  </testcase>\n'
                    '</testsuite>\n'
                )

            synthetic_tsts = {
                "docs/tests/tst_test.md": (
                    "### TST-PRO-099: Test Result-Driven Verification [Verified]\n"
                    "- **Requirement(s)**: `REQ-PRO-099`\n"
                )
            }
            ok, diag = verify_verified_test_evidence(
                results_dir=tmpdir,
                tst_shards=synthetic_tsts,
            )
            self.assertFalse(ok, "Failing test result must cause verification to fail (Criterion 1)")
            self.assertTrue(any("ERROR" in msg and "TST-PRO-099" in msg and "AssertionError" in msg for msg in diag))

    def test_scenario_60_tst_pro_099_3_unbacked_tst_ratchet_baseline(self):
        """TST-PRO-099.3: Verify novel unbacked TST fails while legacy ratchet baseline items pass (Criterion 2 & 3)."""
        # 1. Legacy unbacked item in synthetic baseline passes with warning
        legacy_shard = {
            "docs/tests/tst_legacy.md": (
                "### TST-BUG-001: Legacy Bug Test [Verified]\n"
                "- **Requirement(s)**: `REQ-BUG-001`\n"
            )
        }
        empty_results = {"testcases": [], "summary": {}, "by_tst": {}}
        ok_leg, diag_leg = verify_verified_test_evidence(
            test_results=empty_results,
            code_tags={},
            baseline_allowlist=frozenset({"TST-BUG-001"}),
            tst_shards=legacy_shard,
        )
        self.assertTrue(ok_leg, "Legacy unbacked item in baseline must pass")
        self.assertTrue(any("WARNING (Legacy unbacked TST)" in msg and "TST-BUG-001" in msg for msg in diag_leg))

        # 2. Novel unbacked item outside baseline fails with ERROR
        novel_shard = {
            "docs/tests/tst_novel.md": (
                "### TST-NOVEL-999: Novel Test Without Code [Verified]\n"
                "- **Requirement(s)**: `REQ-NOVEL-999`\n"
            )
        }
        ok_nov, diag_nov = verify_verified_test_evidence(
            test_results=empty_results,
            code_tags={},
            baseline_allowlist=frozenset({"TST-BUG-001"}),
            tst_shards=novel_shard,
        )
        self.assertFalse(ok_nov, "Novel unbacked item outside baseline must fail (Criterion 2)")
        self.assertTrue(any("ERROR" in msg and "TST-NOVEL-999" in msg and "outside ratchet baseline" in msg for msg in diag_nov))

    def test_scenario_61_tst_pro_099_clean_worktree_safety(self):
        """TST-PRO-099: Verify clean worktree safety when test-results directory does not exist (REQ-PRO-099.5)."""
        clean_shard = {
            "docs/tests/tst_clean.md": (
                "### TST-PRO-099: Clean Worktree Spec [Verified]\n"
                "- **Requirement(s)**: `REQ-PRO-099`\n"
            )
        }
        ok, diag = verify_verified_test_evidence(
            results_dir="/path/that/does/not/exist/in/clean/worktree",
            tst_shards=clean_shard,
        )
        self.assertTrue(ok, "Clean worktree without build outputs must not crash or fail unexpectedly")

    def test_scenario_62_tst_pro_100_verify_test_evidence(self):
        """TST-PRO-100.2: Verify verify_test_evidence validates test evidence records and rejects failures (REQ-PRO-100.4)."""
        with tempfile.TemporaryDirectory() as tmpdir:
            ev_dir = os.path.join(tmpdir, "docs", "engineering", "test_evidence")
            os.makedirs(ev_dir, exist_ok=True)

            # 1. Clean initialized directory with README.md passes
            with open(os.path.join(ev_dir, "README.md"), "w", encoding="utf-8") as f:
                f.write("# Test Evidence\n")
            ok_clean, diag_clean = verify_test_evidence(repo_root=tmpdir)
            self.assertTrue(ok_clean, f"Clean initialized directory must pass: {diag_clean}")
            self.assertTrue(any("README.md verified" in m for m in diag_clean))

            # 2. Passing sprint evidence record passes
            passing_data = {
                "target": "sprint_2026-46.8",
                "type": "sprint",
                "commit": "abc1234",
                "timestamp": "2026-11-16T00:00:00Z",
                "unit_tests": {"total": 10, "passed": 10, "failures": 0, "errors": 0},
            }
            with open(os.path.join(ev_dir, "sprint_2026-46.8.json"), "w", encoding="utf-8") as f:
                json.dump(passing_data, f)

            ok_pass, diag_pass = verify_test_evidence(repo_root=tmpdir)
            self.assertTrue(ok_pass, f"Passing evidence record must pass: {diag_pass}")

            # Specific target query passes
            ok_spec, diag_spec = verify_test_evidence(target_id="sprint_2026-46.8", repo_root=tmpdir)
            self.assertTrue(ok_spec)

            # Missing specific target query fails
            ok_miss, diag_miss = verify_test_evidence(target_id="sprint_9999-99.9", repo_root=tmpdir)
            self.assertFalse(ok_miss)
            self.assertTrue(any("Missing test evidence record" in m for m in diag_miss))

            # 3. Failing evidence record triggers error
            failing_data = {
                "target": "sprint_2026-46.9",
                "type": "sprint",
                "commit": "def5678",
                "timestamp": "2026-11-16T01:00:00Z",
                "unit_tests": {"total": 10, "passed": 8, "failures": 2, "errors": 0},
            }
            with open(os.path.join(ev_dir, "sprint_2026-46.9.json"), "w", encoding="utf-8") as f:
                json.dump(failing_data, f)

            ok_fail, diag_fail = verify_test_evidence(repo_root=tmpdir)
            self.assertFalse(ok_fail)
            self.assertTrue(any("reports 2 failure(s)" in m for m in diag_fail))

    def test_scenario_63_tst_pro_101_1_triage_unbacked_classification_and_csv(self):
        """TST-PRO-101.1: Verify deterministic triage classification and CSV export compatibility."""
        with tempfile.TemporaryDirectory() as tmpdir:
            tst_shards = {
                "docs/tests/tst_sample.md": (
                    "### TST-DOM-001: Sample Unit [Verified]\n"
                    "- **Requirement(s)**: `REQ-DOM-001`\n"
                    "- **Component**: `SampleWidgetTest.kt`\n"
                    "### TST-DOM-002: Manual Tap [Verified]\n"
                    "- **Requirement(s)**: `REQ-DOM-002`\n"
                    "- **Test Description**: 1. Tap button on device.\n"
                    "### TST-DOM-003: [SUPERSEDED by TST-DOM-001] Old [Verified]\n"
                    "- **Requirement(s)**: `REQ-DOM-003`\n"
                    "### TST-DOM-004: Missing Unit [Verified]\n"
                    "- **Requirement(s)**: `REQ-DOM-004`\n"
                    "- **Test Description**: Assert calculation invariant.\n"
                )
            }
            req_shards = {
                "docs/requirements/req_sample.md": (
                    "### REQ-DOM-001: Unit Req [Verified]\n"
                    "- **Verification Method**: Unit\n"
                    "### REQ-DOM-002: Manual Req [Verified]\n"
                    "- **Verification Method**: On-Device Manual\n"
                    "### REQ-DOM-004: Missing Req [Verified]\n"
                    "- **Verification Method**: Unit\n"
                )
            }
            test_sources = {
                "app/src/test/SampleWidgetTest.kt": "class SampleWidgetTest {}",
            }
            csv_path = os.path.join(tmpdir, "docs", "engineering", "test_evidence", "triage.csv")
            report, diag = triage_unbacked_verified_tsts(
                repo_root=tmpdir,
                out_csv=csv_path,
                tst_shards=tst_shards,
                req_shards=req_shards,
                test_sources=test_sources,
                code_tags={},
                baseline_allowlist=frozenset({"TST-DOM-001", "TST-DOM-999"}),
            )
            self.assertEqual(report["total_unbacked_verified_tsts"], 4)
            self.assertEqual(report["by_category"]["unit-test-backed"], 1)
            self.assertEqual(report["by_category"]["UI/manual-only"], 1)
            self.assertEqual(report["by_category"]["obsolete/duplicate"], 1)
            self.assertEqual(report["by_category"]["missing test code"], 1)
            self.assertEqual(report["stale_baseline_ids"], ["TST-DOM-999"])
            self.assertTrue(os.path.isfile(csv_path))

    def test_scenario_64_tst_pro_101_2_3_4_live_repo_ratchet_zero(self):
        """TST-PRO-101.2, TST-PRO-101.3, TST-PRO-101.4: Verify live repo has 0 unbacked [Verified] TSTs and ratchet=0."""
        self.assertEqual(len(LEGACY_UNBACKED_VERIFIED_TSTS), 0, "Ratchet baseline must be reconciled to 0")
        ok, diag = verify_verified_test_evidence(results_dir="/nonexistent/clean_worktree")
        self.assertTrue(ok, f"Live repository --check-verified-evidence must pass cleanly: {diag}")
        report, _ = triage_unbacked_verified_tsts()
        self.assertEqual(report["total_unbacked_verified_tsts"], 0)
        self.assertEqual(report["stale_baseline_count"], 0)

    def test_scenario_65_tst_pro_101_5_hoechstmass_mirror_parity(self):
        """TST-PRO-101.5: Verify Hoechstmass mirror exposes parameterized triage_unbacked_verified_tsts."""
        from tools.hoechstmass.tools.verify_requirement_governance import triage_unbacked_verified_tsts as hm_triage
        self.assertTrue(callable(hm_triage))

    def test_scenario_66_tst_pro_102_1_sysreq_boundary_no_false_positive(self):
        """TST-PRO-102.1: SYSREQ-DEX-001 must NOT false-match as REQ-DEX-001 in traceability or test index."""
        from tools.verify_requirement_governance import _collect_test_file_index

        with tempfile.TemporaryDirectory() as tmpdir:
            sys_dir = os.path.join(tmpdir, "docs", "system")
            req_dir = os.path.join(tmpdir, "docs", "requirements")
            os.makedirs(sys_dir)
            os.makedirs(req_dir)

            with open(os.path.join(sys_dir, "sysreq_data.md"), "w", encoding="utf-8") as f:
                f.write(
                    "### SYSREQ-DEX-001: Export\n"
                    "| `SYSREQ-DEX-001` | Export | No standalone SWE REQ here |\n"
                )
            with open(os.path.join(req_dir, "req_data.md"), "w", encoding="utf-8") as f:
                f.write("### REQ-DAT-001: Data Storage [Verified]\n- **Tests**: `TST-DAT-001`\n")

            ok, _, report = verify_system_software_traceability(
                repo_root=tmpdir,
                fail_on_missing_coverage=False,
            )
            self.assertTrue(ok)
            self.assertEqual(report["sys_to_swe_map"]["SYSREQ-DEX-001"], [])
            self.assertEqual(report["covered_count"], 0)
            self.assertEqual(report["swe_refinement_covered_count"], 0)

            _, req_in_tests = _collect_test_file_index(
                tmpdir,
                test_sources={"app/src/test/SysOnlyTest.kt": "// Verifies SYSREQ-DEX-001 and REQ-DAT-001"},
            )
            self.assertNotIn("REQ-DEX-001", req_in_tests)
            self.assertIn("REQ-DAT-001", req_in_tests)

    def test_scenario_67_tst_pro_102_2_derived_prefix_exclusion(self):
        """TST-PRO-102.2: Process/derived REQs (REQ-PRO-*) must be excluded from sys_to_swe refinements."""
        with tempfile.TemporaryDirectory() as tmpdir:
            sys_dir = os.path.join(tmpdir, "docs", "system")
            req_dir = os.path.join(tmpdir, "docs", "requirements")
            os.makedirs(sys_dir)
            os.makedirs(req_dir)

            with open(os.path.join(sys_dir, "sysreq_trk.md"), "w", encoding="utf-8") as f:
                f.write(
                    "### SYSREQ-TRK-001: Tracking\n"
                    "| `SYSREQ-TRK-001` | Tracking | `REQ-PRO-054`, `REQ-TRK-001` |\n"
                )
            with open(os.path.join(req_dir, "req_pro.md"), "w", encoding="utf-8") as f:
                f.write("### REQ-PRO-054: Governance [Verified]\n- References `SYSREQ-TRK-001`\n")
            with open(os.path.join(req_dir, "req_trk.md"), "w", encoding="utf-8") as f:
                f.write(
                    "### REQ-TRK-001: Live Tracking [Verified]\n- References `SYSREQ-TRK-001`\n"
                    "### REQ-TRK-002: Unlinked Tracking [Verified]\n"
                )

            ok, _, report = verify_system_software_traceability(repo_root=tmpdir)
            self.assertTrue(ok)
            self.assertEqual(report["sys_to_swe_map"]["SYSREQ-TRK-001"], ["REQ-TRK-001"])
            self.assertNotIn("REQ-PRO-054", report["swe_to_sys_map"])
            self.assertEqual(report["swe_refinement_total_count"], 2)
            self.assertEqual(report["swe_refinement_covered_count"], 1)
            self.assertEqual(report["swe_refinement_coverage_pct"], 50.0)

    def test_scenario_68_tst_pro_102_3_bidirectional_sys_coverage_live_repo(self):
        """TST-PRO-102.3: Live repository reports authentic bidirectional SYS/SWE coverage without bogus IDs (REQ-PRO-102.3)."""
        ok, diag, report = verify_system_software_traceability()
        self.assertTrue(ok, f"Expected live --verify-sys-trace pass, got: {diag}")
        self.assertEqual(report["covered_count"], report["sysreq_count"])
        self.assertEqual(report["sysreq_count"], 12)
        all_mapped_reqs = {r for reqs in report["sys_to_swe_map"].values() for r in reqs}
        self.assertFalse(any(r.startswith("REQ-DEX-") for r in all_mapped_reqs))
        self.assertFalse(any(r.startswith("REQ-PRO-") for r in all_mapped_reqs))
        self.assertGreaterEqual(report["swe_refinement_total_count"], 362)
        self.assertGreaterEqual(report["swe_refinement_covered_count"], 346)
        self.assertGreaterEqual(report["swe_refinement_coverage_pct"], 95.0)

    def test_scenario_69_tst_pro_103_1_system_requirement_attribute_and_derived_parser(self):
        """TST-PRO-103.1: Parse System Requirement(s) (SYSREQ-* & Derived (SW-only)) and enforce unclassified rejection."""
        with tempfile.TemporaryDirectory() as tmpdir:
            sys_dir = os.path.join(tmpdir, "docs", "system")
            req_dir = os.path.join(tmpdir, "docs", "requirements")
            os.makedirs(sys_dir)
            os.makedirs(req_dir)

            with open(os.path.join(sys_dir, "sysreq_trk.md"), "w", encoding="utf-8") as f:
                f.write("### SYSREQ-TRK-001: Sensors\n")
            with open(os.path.join(req_dir, "req_trk.md"), "w", encoding="utf-8") as f:
                f.write(
                    "### REQ-TRK-001: Linked [Verified]\n"
                    "- **System Requirement(s)**: `SYSREQ-TRK-001`\n\n"
                    "### REQ-WEA-001: Pebble Derived [Implemented]\n"
                    "- **System Requirement(s)**: Derived (SW-only) — Companion watch software\n\n"
                    "### REQ-TRK-002: Missing Classification [Verified]\n"
                )

            ok_fail, diag_fail, rep_fail = verify_system_software_traceability(
                repo_root=tmpdir,
                fail_on_unclassified_swe=True,
            )
            self.assertFalse(ok_fail)
            self.assertEqual(rep_fail["unclassified_swe_refinement_reqs"], ["REQ-TRK-002"])
            self.assertIn("REQ-WEA-001", rep_fail["swe_derived_sw_only_map"])
            self.assertTrue(any("REQ-TRK-002" in d for d in diag_fail))

            with open(os.path.join(req_dir, "req_trk.md"), "a", encoding="utf-8") as f:
                f.write("- **System Requirement(s)**: `SYSREQ-TRK-001`\n")

            ok_pass, _, rep_pass = verify_system_software_traceability(
                repo_root=tmpdir,
                fail_on_unclassified_swe=True,
            )
            self.assertTrue(ok_pass)
            self.assertEqual(rep_pass["reverse_classified_count"], 3)
            self.assertEqual(rep_pass["reverse_classified_pct"], 100.0)
            self.assertEqual(rep_pass["unclassified_swe_refinement_reqs"], [])

    def test_scenario_70_tst_pro_103_2_live_reverse_sys_trace_100pct(self):
        """TST-PRO-103.2: Live repository achieves 100% System-Refinement classification with 0 unclassified REQs (REQ-PRO-103.2)."""
        ok, diag, report = verify_system_software_traceability(fail_on_unclassified_swe=True)
        self.assertTrue(ok, f"Expected live --check-reverse-sys-trace pass, got: {diag}")
        self.assertEqual(report["unclassified_swe_refinement_reqs"], [])
        self.assertEqual(report["reverse_classified_count"], report["swe_refinement_total_count"])
        self.assertEqual(
            report["swe_refinement_covered_count"] + report["swe_derived_sw_only_count"],
            report["swe_refinement_total_count"],
        )
        self.assertGreaterEqual(report["swe_refinement_total_count"], 362)
        self.assertEqual(report["reverse_classified_pct"], 100.0)

    def test_scenario_71_tst_pro_103_3_rad_clm_prefix_classification(self):
        """TST-PRO-103.3: Assert RAD and CLM belong to SYSTEM_REFINED_PREFIXES and REQ-PRO-* stays excluded."""
        self.assertIn("RAD", SYSTEM_REFINED_PREFIXES)
        self.assertIn("CLM", SYSTEM_REFINED_PREFIXES)
        self.assertIn("PRO", SWE_DERIVED_PREFIXES)
        self.assertNotIn("PRO", SYSTEM_REFINED_PREFIXES)

    def test_scenario_72_tst_pro_104_1_code_to_shard_orphan_detection(self):
        # TST-PRO-104.1: Code-to-Shard Orphan Detection & Exclusion Test
        with tempfile.TemporaryDirectory() as tmpdir:
            req_dir = os.path.join(tmpdir, "docs", "requirements")
            tst_dir = os.path.join(tmpdir, "docs", "tests")
            src_dir = os.path.join(tmpdir, "app", "src", "main", "java")
            os.makedirs(req_dir)
            os.makedirs(tst_dir)
            os.makedirs(src_dir)

            with open(os.path.join(req_dir, "req_core.md"), "w", encoding="utf-8") as f:
                f.write("### REQ-COR-001: Authentic Core [Implemented]\n- **Tests**: `TST-COR-001`\n- **Description**: Core.\n")
            with open(os.path.join(tst_dir, "tst_core.md"), "w", encoding="utf-8") as f:
                f.write("### TST-COR-001: Authentic Core Test [Implemented]\n- **Requirement(s)**: `REQ-COR-001`\n")

            with open(os.path.join(src_dir, "Component.kt"), "w", encoding="utf-8") as f:
                f.write("// Traceability: REQ-COR-001.1, TST-COR-001.2, REQ-SIM-001\nclass Component\n")

            orph_reqs, orph_tsts, count = find_code_traceability_orphans(repo_root=tmpdir, search_dirs=["app/src/main"])
            self.assertEqual(len(orph_reqs), 0)
            self.assertEqual(len(orph_tsts), 0)
            self.assertEqual(count, 1)

            ok, diag, rep = verify_code_traceability_orphans(repo_root=tmpdir, search_dirs=["app/src/main"])
            self.assertTrue(ok)
            self.assertEqual(rep["orphan_req_count"], 0)

            with open(os.path.join(src_dir, "Orphan.kt"), "w", encoding="utf-8") as f:
                f.write("// REQ-ORP-999 and TST-ORP-999\nclass Orphan\n")

            ok_fail, diag_fail, rep_fail = verify_code_traceability_orphans(repo_root=tmpdir, search_dirs=["app/src/main"])
            self.assertFalse(ok_fail)
            self.assertIn("REQ-ORP-999", rep_fail["orphan_reqs"])
            self.assertIn("TST-ORP-999", rep_fail["orphan_tsts"])

    def test_scenario_73_tst_pro_104_2_shard_header_count_sync_and_validation(self):
        # TST-PRO-104.2: Living Doc Shard Header Synchronization & Audit Test
        with tempfile.TemporaryDirectory() as tmpdir:
            req_dir = os.path.join(tmpdir, "docs", "requirements")
            tst_dir = os.path.join(tmpdir, "docs", "tests")
            os.makedirs(req_dir)
            os.makedirs(tst_dir)

            with open(os.path.join(req_dir, "req_sample.md"), "w", encoding="utf-8") as f:
                f.write("# Living Requirements\n**Total Requirements**: 1\n\n### REQ-SMP-001: S1\n### REQ-SMP-002: S2\n")
            with open(os.path.join(tst_dir, "tst_sample.md"), "w", encoding="utf-8") as f:
                f.write("# Living Tests\n**Total Tests**: 5\n\n### TST-SMP-001: T1\n")

            with open(os.path.join(tmpdir, "docs", "requirements.md"), "w", encoding="utf-8") as f:
                f.write("# Index\n| [Sample](requirements/req_sample.md) | D | `SMP` | 1 | 0 | 0 | 1 |\n**Total Living Requirements**: 1 across 1 domain shards.\n")
            with open(os.path.join(tmpdir, "docs", "tests.md"), "w", encoding="utf-8") as f:
                f.write("# Index\n| [Sample](tests/tst_sample.md) | D | `SMP` | 5 | 0 | 0 | 5 |\n**Total Living Tests**: 5 across 1 domain shards.\n")

            ok_fail, diag_fail = verify_shard_header_counts(repo_root=tmpdir)
            self.assertFalse(ok_fail)
            self.assertTrue(any("header count" in d for d in diag_fail))

            recalculate_master_index_tables(repo_root=tmpdir)

            ok_pass, diag_pass = verify_shard_header_counts(repo_root=tmpdir)
            self.assertTrue(ok_pass, f"Expected header pass after recalc, got: {diag_pass}")

            with open(os.path.join(req_dir, "req_sample.md"), "r", encoding="utf-8") as f:
                self.assertIn("**Total Requirements**: 2", f.read())
            with open(os.path.join(tst_dir, "tst_sample.md"), "r", encoding="utf-8") as f:
                self.assertIn("**Total Tests**: 1", f.read())

    def test_scenario_74_tst_pro_104_3_req_description_ratchet_and_coverage(self):
        # TST-PRO-104.3: Description Field Ratchet & Field Coverage Reporting Test
        with tempfile.TemporaryDirectory() as tmpdir:
            req_dir = os.path.join(tmpdir, "docs", "requirements")
            os.makedirs(req_dir)

            with open(os.path.join(req_dir, "req_dom.md"), "w", encoding="utf-8") as f:
                f.write(
                    "### REQ-LEG-001: Legacy REQ [Verified]\n"
                    "- **Rationale**: Legacy.\n\n"
                    "### REQ-NEW-001: New REQ without Description [Planned]\n"
                    "- **Rationale**: New.\n\n"
                    "### REQ-NEW-002: New REQ with Description [Planned]\n"
                    "- **Rationale**: New.\n"
                    "- **Description**: Non-empty contract description.\n"
                    "- **Implementation**: `Target.kt`\n"
                    "- **Acceptance Criteria**: Given X When Y Then Z.\n"
                )

            baseline = frozenset({"REQ-LEG-001"})

            ok_fail, diag_fail, rep_fail = verify_req_descriptions(
                repo_root=tmpdir,
                baseline=baseline,
                fail_on_missing=True,
            )
            self.assertFalse(ok_fail)
            self.assertEqual(rep_fail["unbaselined_missing"], ["REQ-NEW-001"])

            with open(os.path.join(req_dir, "req_dom.md"), "r", encoding="utf-8") as f:
                fixed_content = f.read().replace("### REQ-NEW-001: New REQ without Description [Planned]\n- **Rationale**: New.", "### REQ-NEW-001: New REQ with Description [Planned]\n- **Description**: Fixed desc.\n- **Rationale**: New.")
            with open(os.path.join(req_dir, "req_dom.md"), "w", encoding="utf-8") as f:
                f.write(fixed_content)

            ok_pass, _, rep_pass = verify_req_descriptions(
                repo_root=tmpdir,
                baseline=baseline,
                fail_on_missing=True,
            )
            self.assertTrue(ok_pass)
            self.assertEqual(rep_pass["unbaselined_missing"], [])

            cov = calculate_req_field_coverage(repo_root=tmpdir)
            self.assertEqual(cov["total_reqs"], 3)
            self.assertEqual(cov["description_count"], 2)
            formatted = format_req_field_coverage(cov)
            self.assertIn("Description Coverage:", formatted)

    def test_scenario_75_tst_pro_104_4_live_repo_governance_parity(self):
        # TST-PRO-104.4: Restored Requirement Governance & Evidence Cross-Check Test
        h_ok, h_diag = verify_shard_header_counts()
        self.assertTrue(h_ok, f"Shard headers failed on live repo: {h_diag}")

        d_ok, d_diag, _ = verify_req_descriptions(fail_on_missing=True)
        self.assertTrue(d_ok, f"Req descriptions failed on live repo: {d_diag}")

        c_ok, c_diag, _ = verify_code_traceability_orphans(fail_on_orphans=True)
        self.assertTrue(c_ok, f"Code orphans failed on live repo: {c_diag}")

    def test_scenario_76_tst_pro_091_12_verifier_rejects_self_referential_corrective_actions(self):
        # TST-PRO-091.12: Self-Reference Rejection in Verifier Test (REQ-PRO-091.3 / ATT-8153)
        with tempfile.TemporaryDirectory() as tmpdir:
            audits_dir = os.path.join(tmpdir, "docs", "engineering", "qa_audits")
            os.makedirs(audits_dir, exist_ok=True)

            target_file = os.path.join(audits_dir, "sprint_2026-11-16_03_nacht.md")
            content_self_referential = (
                "# Sprint QA Process-Compliance Audit: Sprint 2026-11-16_03_Nacht\n\n"
                "**Auditor Role**: `qa`\n\n"
                "**Overall Process Verdict**: **NON-COMPLIANT**\n\n"
                "| Metric | Value |\n| :--- | :--- |\n| **Sample Size** | 2 / 10 |\n\n"
                "| Ticket | Summary | Verdict |\n| :--- | :--- | :---: |\n"
                "| ATT-9901 | Test 1 | FAIL |\n"
                "| ATT-9902 | Test 2 | PASS |\n\n"
                "## 4. Non-Compliance Tracking & Remediation\n\n"
                "* **ATT-9901**: Process deviation in Role Separation; Corrective Action Ticket: [ATT-9901](https://example.com/ATT-9901)\n"
            )
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(content_self_referential)

            ok, msgs = verify_qa_audits(sprint_name="sprint/2026-11-16_03_Nacht", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("self-referential corrective action ticket" in m for m in msgs))

            content_distinct = (
                "# Sprint QA Process-Compliance Audit: Sprint 2026-11-16_03_Nacht\n\n"
                "**Auditor Role**: `qa`\n\n"
                "**Overall Process Verdict**: **NON-COMPLIANT**\n\n"
                "| Metric | Value |\n| :--- | :--- |\n| **Sample Size** | 2 / 10 |\n\n"
                "| Ticket | Summary | Verdict |\n| :--- | :--- | :---: |\n"
                "| ATT-9901 | Test 1 | FAIL |\n"
                "| ATT-9902 | Test 2 | PASS |\n\n"
                "## 4. Non-Compliance Tracking & Remediation\n\n"
                "* **ATT-9901**: Process deviation in Role Separation; Corrective Action Ticket: [ATT-9999](https://example.com/ATT-9999)\n"
            )
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(content_distinct)

            ok2, msgs2 = verify_qa_audits(sprint_name="sprint/2026-11-16_03_Nacht", repo_root=tmpdir)
            self.assertTrue(ok2, f"Expected pass with distinct corrective action ticket, got: {msgs2}")

            gf_file = os.path.join(audits_dir, "sprint_2026-46.6.md")
            with open(gf_file, "w", encoding="utf-8") as f:
                f.write(content_self_referential.replace("2026-11-16_03_Nacht", "2026-46.6"))
            ok3, msgs3 = verify_qa_audits(sprint_name="sprint/2026-46.6", repo_root=tmpdir)
            self.assertTrue(ok3, f"Expected grandfathered pass for legacy 2026-46.6 audit, got: {msgs3}")

    def test_scenario_77_tst_pro_117_1_retro_write_allowlist_and_exemptions(self):
        # TST-PRO-117.1: Autonomous Retro Write-Allowlist & Protected Path Guard (REQ-PRO-117 / ATT-8584)
        allowed_files = [
            "docs/engineering/retros/Sprint_Review_and_Retro_ATT-8655.md",
            "docs/engineering/gates/sprint_2026-11-17_02_spaet.md",
            "docs/engineering/qa_audits/sprint_2026-11-17_02_spaet.md",
            "docs/engineering/reviews/sprint_2026-11-17_02_spaet.md",
            "docs/engineering/test_evidence/sprint_2026-11-17_02_Spät.json",
            "docs/engineering/test_evidence/sprint_2026-11-17_02_Spät.md",
            "docs/engineering/metrics/process_metrics.jsonl",
            "docs/management/risk_register.md",
        ]
        ok, msgs = verify_retro_write_allowlist(
            changed_files=allowed_files,
            commit_message="chore(ceremony2): sprint 2026-11-17_02_Spät closure (ATT-8655)",
        )
        self.assertTrue(ok, f"Allowlisted ceremony2 files should pass: {msgs}")

        protected_cases = [
            ".agents/rules/aspice_governance.md",
            ".agents/skills/sprint-planner/SKILL.md",
            "docs/coding_standards.md",
            "docs/design_guidelines.md",
            "docs/project_governance.md",
            "docs/project_protocol.md",
            "docs/engineering/rule_history_archive.md",
            "tools/verify_requirement_governance.py",
        ]
        for protected_path in protected_cases:
            bad_ok, bad_msgs = verify_retro_write_allowlist(
                changed_files=allowed_files + [protected_path],
                commit_message="chore(ceremony2): sprint 2026-11-17_02_Spät closure (ATT-8655)",
            )
            self.assertFalse(bad_ok, f"Protected path {protected_path} must be rejected in ceremony2")
            self.assertTrue(any("PROHIBITED protected" in m and protected_path in m for m in bad_msgs), bad_msgs)

        non_allow_ok, non_allow_msgs = verify_retro_write_allowlist(
            changed_files=["app/src/main/java/Foo.kt"],
            commit_message="chore(ceremony2): sprint 2026-11-17_02_Spät closure (ATT-8655)",
        )
        self.assertFalse(non_allow_ok)
        self.assertTrue(any("non-allowlisted" in m for m in non_allow_msgs), non_allow_msgs)

        # Dedicated ticket commit (non-ceremony2) and Interactive-PO trailer are exempted
        ticket_ok, ticket_msgs = verify_retro_write_allowlist(
            changed_files=[".agents/rules/aspice_governance.md", "tools/verify_requirement_governance.py"],
            commit_message="ATT-8584: Restrict files editable during autonomous Schicht Retro",
        )
        self.assertTrue(ticket_ok, f"Dedicated ticket commit must be exempted: {ticket_msgs}")

        po_ok, po_msgs = verify_retro_write_allowlist(
            changed_files=[".agents/rules/aspice_governance.md", "docs/coding_standards.md"],
            commit_message="chore(ceremony2): interactive PO hardening\n\nInteractive-PO: Rainer Blind",
        )
        self.assertTrue(po_ok, f"Interactive-PO commit must be exempted: {po_msgs}")

        # Pre-cutoff historical commit (8aa881df) on live repo passes without false positives
        hist_ok, hist_msgs = verify_retro_write_allowlist(commit_ref="8aa881df")
        self.assertTrue(hist_ok, f"Historical pre-cutoff commit 8aa881df must pass: {hist_msgs}")

        # Real git repo test with post-cutoff (> 1794925800) UTF-8 umlaut filenames & protected-path violation
        with tempfile.TemporaryDirectory() as tmpdir:
            git_env = dict(os.environ)
            git_env["GIT_AUTHOR_DATE"] = "2026-11-17T22:00:00+01:00"
            git_env["GIT_COMMITTER_DATE"] = "2026-11-17T22:00:00+01:00"
            subprocess.check_call(["git", "init"], cwd=tmpdir, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            subprocess.check_call(["git", "config", "user.name", "Test Agent"], cwd=tmpdir)
            subprocess.check_call(["git", "config", "user.email", "test@atrainingtracker.local"], cwd=tmpdir)

            umlaut_allowed = [
                "docs/engineering/gates/sprint_2026-11-17_02_spät.md",
                "docs/engineering/qa_audits/sprint_2026-11-17_02_spät.md",
                "docs/engineering/reviews/sprint_2026-11-17_02_spät.md",
                "docs/engineering/test_evidence/sprint_2026-11-17_02_Spät.json",
                "docs/engineering/retros/Sprint_Review_and_Retro_ATT-8655.md",
                "docs/engineering/metrics/process_metrics.jsonl",
                "docs/management/risk_register.md",
            ]
            for rel in umlaut_allowed:
                full_p = os.path.join(tmpdir, rel)
                os.makedirs(os.path.dirname(full_p), exist_ok=True)
                with open(full_p, "w", encoding="utf-8") as f:
                    f.write("test\n")

            subprocess.check_call(["git", "add", "--all"], cwd=tmpdir)
            subprocess.check_call(
                ["git", "commit", "-m", "chore(ceremony2): sprint 2026-11-17_02_Spät closure (ATT-8655)"],
                cwd=tmpdir,
                env=git_env,
                stdout=subprocess.DEVNULL,
            )
            umlaut_ok, umlaut_msgs = verify_retro_write_allowlist(commit_ref="HEAD", repo_root=tmpdir)
            self.assertTrue(umlaut_ok, f"Post-cutoff umlaut ceremony2 commit must pass with core.quotePath=false: {umlaut_msgs}")

            prot_file = os.path.join(tmpdir, ".agents", "rules", "aspice_governance.md")
            os.makedirs(os.path.dirname(prot_file), exist_ok=True)
            with open(prot_file, "w", encoding="utf-8") as f:
                f.write("unauthorized\n")
            subprocess.check_call(["git", "add", "--all"], cwd=tmpdir)
            subprocess.check_call(
                ["git", "commit", "-m", "chore(ceremony2): unauthorized rule edit (ATT-8655)"],
                cwd=tmpdir,
                env=git_env,
                stdout=subprocess.DEVNULL,
            )
            prot_ok, prot_msgs = verify_retro_write_allowlist(commit_ref="HEAD", repo_root=tmpdir)
            self.assertFalse(prot_ok, "Post-cutoff ceremony2 protected-path edit must fail")
            self.assertTrue(any("PROHIBITED protected" in m and ".agents/rules/aspice_governance.md" in m for m in prot_msgs), prot_msgs)

    def test_scenario_78_tst_pro_118_5_verifier_rejects_synthetic_att_ca_links(self):
        """TST-PRO-118.5: verify_qa_audits rejects synthetic ATT-CA-* placeholders on post-cutoff audits (REQ-PRO-118.5)."""
        with tempfile.TemporaryDirectory() as tmpdir:
            audits_dir = os.path.join(tmpdir, "docs", "engineering", "qa_audits")
            os.makedirs(audits_dir, exist_ok=True)

            post_file = os.path.join(audits_dir, "sprint_2026-11-17_02_spät.md")
            synthetic_content = (
                "# Sprint QA Process-Compliance Audit: Sprint 2026-11-17_02_Spät\n\n"
                "**Auditor Role**: `agent2`\n\n"
                "**Overall Process Verdict**: **NON-COMPLIANT**\n\n"
                "| Metric | Value |\n| :--- | :--- |\n| **Sample Size** | 2 / 10 |\n\n"
                "| Ticket | Summary | Verdict |\n| :--- | :--- | :---: |\n"
                "| ATT-9901 | Test 1 | FAIL |\n"
                "| ATT-9902 | Test 2 | PASS |\n\n"
                "## 4. Non-Compliance Tracking & Remediation\n\n"
                "* **ATT-9901**: Process deviation in 4-Augen Role Separation; Corrective Action Ticket: [ATT-CA-4-Augen-Role-Separation](https://example.com/ATT-CA-4-Augen-Role-Separation)\n"
            )
            with open(post_file, "w", encoding="utf-8") as f:
                f.write(synthetic_content)

            ok, msgs = verify_qa_audits(sprint_name="sprint/2026-11-17_02_Spät", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("synthetic placeholder corrective action link(s)" in m for m in msgs), msgs)

            real_content = synthetic_content.replace(
                "[ATT-CA-4-Augen-Role-Separation](https://example.com/ATT-CA-4-Augen-Role-Separation)",
                "[ATT-9999](https://atrainingtracker.atlassian.net/browse/ATT-9999)",
            )
            with open(post_file, "w", encoding="utf-8") as f:
                f.write(real_content)

            ok_real, msgs_real = verify_qa_audits(sprint_name="sprint/2026-11-17_02_Spät", repo_root=tmpdir)
            self.assertTrue(ok_real, f"Expected pass with real ATT-9999 link, got: {msgs_real}")

            gf_file = os.path.join(audits_dir, "sprint_2026-11-17_01_früh.md")
            with open(gf_file, "w", encoding="utf-8") as f:
                f.write(synthetic_content.replace("2026-11-17_02_Spät", "2026-11-17_01_Früh"))
            ok_gf, msgs_gf = verify_qa_audits(sprint_name="sprint/2026-11-17_01_Früh", repo_root=tmpdir)
            self.assertTrue(ok_gf, f"Expected pre-fix closed audit 2026-11-17_01_Früh to remain grandfathered, got: {msgs_gf}")

    def test_scenario_79_tst_pro_119_4_ceremony2_allowlist_permits_baseline_manifest(self):
        """TST-PRO-119.4: verify_retro_write_allowlist accepts docs/releases/<baseline>/manifest.md in chore(ceremony2) (REQ-PRO-119.3)."""
        ok, msgs = verify_retro_write_allowlist(
            changed_files=[
                "docs/releases/sprint_2026-11-17_02_Spät/manifest.md",
                "docs/engineering/retros/Sprint_Review_and_Retro_ATT-8655.md",
            ],
            commit_message="chore(ceremony2): close sprint and record baseline manifest",
        )
        self.assertTrue(ok, f"Expected baseline manifest to be allowlisted in ceremony2, got: {msgs}")

        bad_ok, bad_msgs = verify_retro_write_allowlist(
            changed_files=[
                "docs/releases/sprint_2026-11-17_02_Spät/manifest.md",
                "tools/baseline_manager.py",
            ],
            commit_message="chore(ceremony2): unauthorized tool edit",
        )
        self.assertFalse(bad_ok, f"Protected tool path must still fail in ceremony2, got: {bad_msgs}")

    def test_scenario_80_tst_pro_120_1_work_product_header_validation(self):
        """TST-PRO-120.1: verify_work_product_headers validates DOC-* header fields and rejects unapproved Approved claims."""
        with tempfile.TemporaryDirectory() as tmpdir:
            mgt_dir = os.path.join(tmpdir, "docs", "management")
            os.makedirs(mgt_dir, exist_ok=True)
            cm_path = os.path.join(mgt_dir, "cm_plan.md")

            valid_doc = (
                "# Configuration Management Plan\n\n"
                "**Document ID**: `DOC-MGT-CM`  \n"
                "**Version**: 1.0.0  \n"
                "**Status**: Reviewed  \n"
                "**Approver**: Pending PO Sign-Off  \n\n"
                "## 1. Purpose\nBody content.\n\n"
                "## Change History\n\n"
                "| Version | Date | Author | Ticket | Summary of Changes |\n"
                "| :--- | :--- | :--- | :--- | :--- |\n"
                "| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8164`) | `ATT-8164` | Initial baseline. |\n"
            )
            with open(cm_path, "w", encoding="utf-8") as f:
                f.write(valid_doc)

            ok, msgs = verify_work_product_headers(repo_root=tmpdir, check_diff=False)
            self.assertTrue(ok, f"Valid Reviewed doc should pass: {msgs}")

            # Missing header fields fails
            with open(cm_path, "w", encoding="utf-8") as f:
                f.write("# CM Plan\n\nNo metadata header.\n")
            ok_miss, msgs_miss = verify_work_product_headers(repo_root=tmpdir, check_diff=False)
            self.assertFalse(ok_miss)
            self.assertTrue(any("missing required work-product header metadata" in m for m in msgs_miss), msgs_miss)

            # Status: Approved with non-human / Pending Approver fails
            self_approved = valid_doc.replace("**Status**: Reviewed", "**Status**: Approved")
            with open(cm_path, "w", encoding="utf-8") as f:
                f.write(self_approved)
            ok_app, msgs_app = verify_work_product_headers(repo_root=tmpdir, check_diff=False)
            self.assertFalse(ok_app)
            self.assertTrue(any("non-human/pending Approver" in m for m in msgs_app), msgs_app)

    def test_scenario_81_tst_pro_120_2_human_only_approval_and_version_bump(self):
        """TST-PRO-120.2: verify_work_product_headers enforces version bump and human-only Approved on git diff (Rule 5)."""
        with tempfile.TemporaryDirectory() as tmpdir:
            git_env = dict(os.environ)
            git_env.update({
                "GIT_AUTHOR_NAME": "Test Runner",
                "GIT_AUTHOR_EMAIL": "runner@example.com",
                "GIT_COMMITTER_NAME": "Test Runner",
                "GIT_COMMITTER_EMAIL": "runner@example.com",
            })
            subprocess.run(["git", "init", "-b", "develop"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            mgt_dir = os.path.join(tmpdir, "docs", "management")
            os.makedirs(mgt_dir, exist_ok=True)
            cm_path = os.path.join(mgt_dir, "cm_plan.md")

            def write_doc(version, status, approver, body="Initial body."):
                content = (
                    "# CM Plan\n\n"
                    "**Document ID**: `DOC-MGT-CM`  \n"
                    f"**Version**: {version}  \n"
                    f"**Status**: {status}  \n"
                    f"**Approver**: {approver}  \n\n"
                    f"## 1. Scope\n{body}\n\n"
                    "## Change History\n\n"
                    "| Version | Date | Author | Ticket | Summary of Changes |\n"
                    "| :--- | :--- | :--- | :--- | :--- |\n"
                    f"| `{version}` | 2026-11-17 | Test | `ATT-8164` | Update. |\n"
                )
                with open(cm_path, "w", encoding="utf-8") as f:
                    f.write(content)

            write_doc("1.0.0", "Approved", "Rainer Blind", "Baseline v1.")
            subprocess.run(["git", "add", "."], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            subprocess.run(
                ["git", "commit", "-m", "chore: initial baseline\n\nHuman-Approved: Rainer Blind"],
                cwd=tmpdir,
                env=git_env,
                check=True,
                stdout=subprocess.DEVNULL,
            )
            subprocess.run(["git", "checkout", "-b", "feature/ATT-9999"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

            # (a) Body changed without version bump -> FAIL
            write_doc("1.0.0", "Reviewed", "Pending PO Sign-Off", "Modified body without bump.")
            subprocess.run(["git", "add", "."], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            subprocess.run(["git", "commit", "-m", "docs: edit without bump"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            ok_nobump, msgs_nobump = verify_work_product_headers(repo_root=tmpdir, base_ref="develop", cutoff_ts=0)
            self.assertFalse(ok_nobump)
            self.assertTrue(any("without monotonic Version bump" in m for m in msgs_nobump), msgs_nobump)

            # (b) Version bumped, but kept Status: Approved without Human-Approved trailer -> FAIL
            write_doc("1.1.0", "Approved", "Rainer Blind", "Modified body retaining Approved without human trailer.")
            subprocess.run(["git", "add", "."], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            subprocess.run(["git", "commit", "-m", "docs: agent bump keeping Approved"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            ok_agent_app, msgs_agent_app = verify_work_product_headers(repo_root=tmpdir, base_ref="develop", cutoff_ts=0)
            self.assertFalse(ok_agent_app)
            self.assertTrue(any("without human sign-off" in m for m in msgs_agent_app), msgs_agent_app)

            # (c) Agent resets Approved -> Reviewed with version bump -> PASS
            write_doc("1.2.0", "Reviewed", "Pending PO Sign-Off", "Modified body cleanly reset to Reviewed.")
            subprocess.run(["git", "add", "."], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            subprocess.run(["git", "commit", "-m", "docs: bump and reset to Reviewed"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            ok_reset, msgs_reset = verify_work_product_headers(repo_root=tmpdir, base_ref="develop", cutoff_ts=0)
            self.assertTrue(ok_reset, f"Resetting Approved -> Reviewed with version bump should pass: {msgs_reset}")

            # (d) Human grants Approved with Human-Approved trailer + human Approver -> PASS
            write_doc("1.3.0", "Approved", "Rainer Blind", "Modified body cleanly reset to Reviewed.")
            subprocess.run(["git", "add", "."], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            subprocess.run(
                ["git", "commit", "-m", "docs: PO approval\n\nHuman-Approved: Rainer Blind"],
                cwd=tmpdir,
                env=git_env,
                check=True,
                stdout=subprocess.DEVNULL,
            )
            ok_human, msgs_human = verify_work_product_headers(repo_root=tmpdir, base_ref="develop", cutoff_ts=0)
            self.assertTrue(ok_human, f"Human-Approved commit should pass: {msgs_human}")

            # (e) Unapproved commit exempt if pre-cutoff
            subprocess.run(["git", "checkout", "-b", "feature/pre-cutoff", "develop"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            write_doc("1.0.0", "Approved", "Rainer Blind", "Pre-cutoff historical edit.")
            subprocess.run(["git", "add", "."], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            old_env = dict(git_env)
            old_env["GIT_COMMITTER_DATE"] = "2026-01-01T00:00:00Z"
            old_env["GIT_AUTHOR_DATE"] = "2026-01-01T00:00:00Z"
            subprocess.run(["git", "commit", "-m", "docs: historical edit"], cwd=tmpdir, env=old_env, check=True, stdout=subprocess.DEVNULL)
            ok_pre, msgs_pre = verify_work_product_headers(repo_root=tmpdir, base_ref="develop", cutoff_ts=1790000000)
            self.assertTrue(ok_pre, f"Pre-cutoff historical commit should be exempted: {msgs_pre}")

    def test_scenario_82_auto_verify_living_docs_and_rollover_sprint_base_ref(self):
        """TST-PRO-120.2: auto_verify_living_docs_on_stage5 bumps governed shard/index versions and _resolve_git_base_ref picks newest sprint ancestor."""
        from tools.jira_util import auto_verify_living_docs_on_stage5
        from tools.verify_requirement_governance import _resolve_git_base_ref

        with tempfile.TemporaryDirectory() as tmpdir:
            git_env = dict(os.environ)
            git_env.update({
                "GIT_AUTHOR_NAME": "Test Runner",
                "GIT_AUTHOR_EMAIL": "runner@example.com",
                "GIT_COMMITTER_NAME": "Test Runner",
                "GIT_COMMITTER_EMAIL": "runner@example.com",
            })
            subprocess.run(["git", "init", "-b", "develop"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)

            os.makedirs(os.path.join(tmpdir, "docs", "requirements"), exist_ok=True)
            os.makedirs(os.path.join(tmpdir, "docs", "tests"), exist_ok=True)
            os.makedirs(os.path.join(tmpdir, "app", "src", "test", "java"), exist_ok=True)
            os.makedirs(os.path.join(tmpdir, "app", "build", "test-results", "testDebugUnitTest"), exist_ok=True)

            req_shard = (
                "# Living Requirements: Process\n\n"
                "**Document ID**: `DOC-REQ-PRO`  \n"
                "**Version**: 1.0.0  \n"
                "**Status**: Reviewed  \n"
                "**Approver**: Pending PO Sign-Off  \n"
                "**Total Requirements**: 1\n\n"
                "### REQ-PRO-999: Sample (ATT-9999) [Implemented]\n"
                "- **Tests**: `TST-PRO-999`\n\n"
                "## Change History\n\n"
                "| Version | Date | Author | Ticket | Summary of Changes |\n"
                "| :--- | :--- | :--- | :--- | :--- |\n"
                "| `1.0.0` | 2026-11-17 | Test | `ATT-8164` | Init. |\n"
            )
            tst_shard = (
                "# Living Test Specification: Process\n\n"
                "**Document ID**: `DOC-TST-PRO`  \n"
                "**Version**: 1.0.0  \n"
                "**Status**: Reviewed  \n"
                "**Approver**: Pending PO Sign-Off  \n"
                "**Total Tests**: 1\n\n"
                "### TST-PRO-999: Sample (ATT-9999) [Implemented]\n"
                "- **Requirement(s)**: `REQ-PRO-999`\n\n"
                "## Change History\n\n"
                "| Version | Date | Author | Ticket | Summary of Changes |\n"
                "| :--- | :--- | :--- | :--- | :--- |\n"
                "| `1.0.0` | 2026-11-17 | Test | `ATT-8164` | Init. |\n"
            )
            req_master = (
                "# Living Requirements Index\n\n"
                "**Document ID**: `DOC-REQ-INDEX`  \n"
                "**Version**: 1.0.0  \n"
                "**Status**: Reviewed  \n"
                "**Approver**: Pending PO Sign-Off  \n\n"
                "This master index links to the 1 domain-sharded requirement files under `docs/requirements/`.\n\n"
                "| Domain Shard | Prefixes | Total | Verified | Implemented | Planned / Open |\n"
                "| :--- | :--- | :---: | :---: | :---: | :---: |\n"
                "| [Process](requirements/req_process_stability.md) | `PRO` | 1 | 0 | 1 | 0 |\n\n"
                "**Total Living Requirements**: 1 across 1 domain shards.\n\n"
                "## Change History\n\n"
                "| Version | Date | Author | Ticket | Summary of Changes |\n"
                "| :--- | :--- | :--- | :--- | :--- |\n"
                "| `1.0.0` | 2026-11-17 | Test | `ATT-8164` | Init. |\n"
            )
            tst_master = (
                "# Living Tests Index\n\n"
                "**Document ID**: `DOC-TST-INDEX`  \n"
                "**Version**: 1.0.0  \n"
                "**Status**: Reviewed  \n"
                "**Approver**: Pending PO Sign-Off  \n\n"
                "This master index links to the 1 domain-sharded test specification files under `docs/tests/`.\n\n"
                "| Domain Shard | Prefixes | Total | Verified | Implemented | Planned / Open |\n"
                "| :--- | :--- | :---: | :---: | :---: | :---: |\n"
                "| [Process](tests/tst_process_stability.md) | `PRO` | 1 | 0 | 1 | 0 |\n\n"
                "**Total Living Tests**: 1 across 1 domain shards.\n\n"
                "## Change History\n\n"
                "| Version | Date | Author | Ticket | Summary of Changes |\n"
                "| :--- | :--- | :--- | :--- | :--- |\n"
                "| `1.0.0` | 2026-11-17 | Test | `ATT-8164` | Init. |\n"
            )
            with open(os.path.join(tmpdir, "docs", "requirements", "req_process_stability.md"), "w", encoding="utf-8") as f:
                f.write(req_shard)
            with open(os.path.join(tmpdir, "docs", "tests", "tst_process_stability.md"), "w", encoding="utf-8") as f:
                f.write(tst_shard)
            with open(os.path.join(tmpdir, "docs", "requirements.md"), "w", encoding="utf-8") as f:
                f.write(req_master)
            with open(os.path.join(tmpdir, "docs", "tests.md"), "w", encoding="utf-8") as f:
                f.write(tst_master)
            with open(os.path.join(tmpdir, "app", "src", "test", "java", "SampleContractTest.kt"), "w", encoding="utf-8") as f:
                f.write("// Traces to: TST-PRO-999\nclass SampleContractTest { fun testSample_TST_PRO_999() {} }\n")
            with open(os.path.join(tmpdir, "app", "build", "test-results", "testDebugUnitTest", "TEST-SampleContractTest.xml"), "w", encoding="utf-8") as f:
                f.write('<testsuite name="SampleContractTest" tests="1" failures="0" errors="0" skipped="0"><testcase name="testSample_TST_PRO_999" classname="SampleContractTest"/></testsuite>\n')

            subprocess.run(["git", "add", "."], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            old_env = dict(git_env)
            old_env["GIT_COMMITTER_DATE"] = "2026-11-17T10:00:00Z"
            old_env["GIT_AUTHOR_DATE"] = "2026-11-17T10:00:00Z"
            subprocess.run(["git", "commit", "-m", "chore: baseline"], cwd=tmpdir, env=old_env, check=True, stdout=subprocess.DEVNULL)

            # Create older sprint branch (alphabetically smaller and larger tests) and newer sprint branch
            subprocess.run(["git", "checkout", "-b", "sprint/2026-11-16_03_Nacht"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            subprocess.run(["git", "commit", "--allow-empty", "-m", "chore: old sprint"], cwd=tmpdir, env=old_env, check=True, stdout=subprocess.DEVNULL)
            old_sprint_sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=tmpdir).decode().strip()

            subprocess.run(["git", "checkout", "-b", "sprint/2026-11-17_02_Spät"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            new_env = dict(git_env)
            new_env["GIT_COMMITTER_DATE"] = "2026-11-17T15:00:00Z"
            new_env["GIT_AUTHOR_DATE"] = "2026-11-17T15:00:00Z"
            subprocess.run(["git", "commit", "--allow-empty", "-m", "chore: newest sprint"], cwd=tmpdir, env=new_env, check=True, stdout=subprocess.DEVNULL)
            newest_sprint_sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=tmpdir).decode().strip()

            subprocess.run(["git", "checkout", "-b", "feature/ATT-9999"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            resolved = _resolve_git_base_ref("develop", cwd=tmpdir)
            self.assertEqual(resolved, newest_sprint_sha)
            self.assertNotEqual(resolved, old_sprint_sha)

            updated = auto_verify_living_docs_on_stage5("ATT-9999", repo_root=tmpdir)
            self.assertGreaterEqual(updated, 2)

            ok_hdr, hdr_msgs = verify_work_product_headers(repo_root=tmpdir, base_ref="develop", cutoff_ts=0)
            self.assertTrue(ok_hdr, f"auto_verify_living_docs_on_stage5 must bump shard/index versions cleanly: {hdr_msgs}")
            for rel_check in (
                "docs/requirements/req_process_stability.md",
                "docs/tests/tst_process_stability.md",
                "docs/requirements.md",
                "docs/tests.md",
            ):
                with open(os.path.join(tmpdir, rel_check), "r", encoding="utf-8") as f:
                    hdr = parse_work_product_header(f.read())
                self.assertEqual(hdr["version"], "1.0.1", f"Expected patched version 1.0.1 on {rel_check}, got {hdr}")

    def test_scenario_83_tst_pro_120_ignores_untracked_and_gitignored_drafts(self):
        """TST-PRO-120.2: discover_governed_work_products and verify_work_product_headers ignore untracked/gitignored drafts in Git repos."""
        with tempfile.TemporaryDirectory() as tmpdir:
            git_env = dict(os.environ)
            git_env.update({
                "GIT_AUTHOR_NAME": "Test Runner",
                "GIT_AUTHOR_EMAIL": "runner@example.com",
                "GIT_COMMITTER_NAME": "Test Runner",
                "GIT_COMMITTER_EMAIL": "runner@example.com",
            })
            subprocess.run(["git", "init", "-b", "develop"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            mgt_dir = os.path.join(tmpdir, "docs", "management")
            os.makedirs(mgt_dir, exist_ok=True)

            with open(os.path.join(tmpdir, ".gitignore"), "w", encoding="utf-8") as f:
                f.write("docs/management/schichtbetrieb.md\n")

            valid_cm = (
                "# CM Plan\n\n"
                "**Document ID**: `DOC-MGT-CM`  \n"
                "**Version**: 1.0.0  \n"
                "**Status**: Reviewed  \n"
                "**Approver**: Pending PO Sign-Off  \n\n"
                "## 1. Scope\nBody.\n\n"
                "## Change History\n\n"
                "| Version | Date | Author | Ticket | Summary of Changes |\n"
                "| :--- | :--- | :--- | :--- | :--- |\n"
                "| `1.0.0` | 2026-11-17 | Test | `ATT-8164` | Baseline. |\n"
            )
            with open(os.path.join(mgt_dir, "cm_plan.md"), "w", encoding="utf-8") as f:
                f.write(valid_cm)
            subprocess.run(["git", "add", ".gitignore", "docs/management/cm_plan.md"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            subprocess.run(["git", "commit", "-m", "chore: baseline"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)

            # Create gitignored draft and untracked draft without headers
            with open(os.path.join(mgt_dir, "schichtbetrieb.md"), "w", encoding="utf-8") as f:
                f.write("# PO Draft\n\nUnformatted personal draft.\n")
            with open(os.path.join(mgt_dir, "local_notes.md"), "w", encoding="utf-8") as f:
                f.write("# Untracked Notes\n\nNot tracked in Git.\n")

            discovered = discover_governed_work_products(repo_root=tmpdir)
            self.assertEqual(discovered, ["docs/management/cm_plan.md"])
            ok, msgs = verify_work_product_headers(repo_root=tmpdir, base_ref="develop", cutoff_ts=0)
            self.assertTrue(ok, f"Untracked/gitignored drafts must not fail header verification: {msgs}")


class TestConflictMarkersAndCommitHygiene(unittest.TestCase):
    """TST-PRO-121.3: Offline conflict-marker, commit-key hygiene, and .githooks enforcement."""

    def test_tst_pro_121_3_conflict_markers_detected_and_clean_repo_passes(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            git_env = dict(os.environ, GIT_AUTHOR_NAME="T", GIT_AUTHOR_EMAIL="t@e.com", GIT_COMMITTER_NAME="T", GIT_COMMITTER_EMAIL="t@e.com")
            subprocess.run(["git", "init", "-b", "develop"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            clean_fp = os.path.join(tmpdir, "clean.md")
            with open(clean_fp, "w", encoding="utf-8") as f:
                f.write("# Clean\nNormal content.\n")
            subprocess.run(["git", "add", "clean.md"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            subprocess.run(["git", "commit", "-m", "docs(ATT-8165): clean file"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)

            ok, msgs = verify_conflict_markers(repo_root=tmpdir)
            self.assertTrue(ok, f"Clean repo must pass: {msgs}")

            # Introduce conflict markers via programmatic concatenation (no literal conflict marker lines in test source)
            bad_fp = os.path.join(tmpdir, "broken.txt")
            marker_blob = ("<" * 7) + " HEAD\nfoo\n" + ("=" * 7) + "\nbar\n" + (">" * 7) + " feature\n"
            with open(bad_fp, "w", encoding="utf-8") as f:
                f.write(marker_blob)
            subprocess.run(["git", "add", "broken.txt"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)

            ok_bad, errs_bad = verify_conflict_markers(repo_root=tmpdir)
            self.assertFalse(ok_bad)
            self.assertTrue(any("broken.txt" in e for e in errs_bad))

    def test_tst_pro_121_3_commit_hygiene_enforces_att_key_post_cutoff_and_exempts_historical(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            git_env = dict(os.environ, GIT_AUTHOR_NAME="T", GIT_AUTHOR_EMAIL="t@e.com", GIT_COMMITTER_NAME="T", GIT_COMMITTER_EMAIL="t@e.com")
            subprocess.run(["git", "init", "-b", "develop"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)

            # 1. Pre-cutoff historical commit without ATT key -> exempted cleanly
            with open(os.path.join(tmpdir, "a.txt"), "w", encoding="utf-8") as f:
                f.write("v1\n")
            subprocess.run(["git", "add", "a.txt"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            old_env = dict(git_env, GIT_AUTHOR_DATE="1794685976 +0000", GIT_COMMITTER_DATE="1794685976 +0000")
            subprocess.run(["git", "commit", "-m", "fix(tier2): historical fix"], cwd=tmpdir, env=old_env, check=True, stdout=subprocess.DEVNULL)

            ok_hist, msgs_hist = verify_commit_hygiene(repo_root=tmpdir, base_ref="develop")
            self.assertTrue(ok_hist, f"Pre-cutoff commit should be exempted: {msgs_hist}")

            # 2. Post-cutoff commit without ATT key -> fails
            with open(os.path.join(tmpdir, "a.txt"), "w", encoding="utf-8") as f:
                f.write("v2\n")
            subprocess.run(["git", "add", "a.txt"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            new_env = dict(git_env, GIT_AUTHOR_DATE="1800000000 +0000", GIT_COMMITTER_DATE="1800000000 +0000")
            subprocess.run(["git", "commit", "-m", "hotfix(settings): missing ticket key"], cwd=tmpdir, env=new_env, check=True, stdout=subprocess.DEVNULL)

            ok_bad, errs_bad = verify_commit_hygiene(repo_root=tmpdir, base_ref="develop")
            self.assertFalse(ok_bad)
            self.assertTrue(any("ATT-" in e for e in errs_bad))

            # 3. Reset back and commit WITH ATT-8165 -> passes
            subprocess.run(["git", "reset", "--hard", "HEAD~1"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            with open(os.path.join(tmpdir, "a.txt"), "w", encoding="utf-8") as f:
                f.write("v3\n")
            subprocess.run(["git", "add", "a.txt"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            subprocess.run(["git", "commit", "-m", "fix(ATT-8165): tracked fix"], cwd=tmpdir, env=new_env, check=True, stdout=subprocess.DEVNULL)

            ok_good, msgs_good = verify_commit_hygiene(repo_root=tmpdir, base_ref="develop")
            self.assertTrue(ok_good, f"Post-cutoff commit with ATT-8165 should pass: {msgs_good}")

    def test_tst_pro_121_3_githooks_scripts_enforce_att_key_and_conflict_markers(self):
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
        commit_msg_hook = os.path.join(repo_root, ".githooks", "commit-msg")
        pre_commit_hook = os.path.join(repo_root, ".githooks", "pre-commit")
        self.assertTrue(os.path.isfile(commit_msg_hook))
        self.assertTrue(os.path.isfile(pre_commit_hook))

        with tempfile.TemporaryDirectory() as tmpdir:
            git_env = dict(os.environ, GIT_AUTHOR_NAME="T", GIT_AUTHOR_EMAIL="t@e.com", GIT_COMMITTER_NAME="T", GIT_COMMITTER_EMAIL="t@e.com")
            subprocess.run(["git", "init", "-b", "develop"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)

            msg_bad = os.path.join(tmpdir, "MSG_BAD")
            with open(msg_bad, "w", encoding="utf-8") as f:
                f.write("hotfix: missing key\n")
            res_bad = subprocess.run([commit_msg_hook, msg_bad], cwd=tmpdir, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            self.assertNotEqual(res_bad.returncode, 0)

            msg_good = os.path.join(tmpdir, "MSG_GOOD")
            with open(msg_good, "w", encoding="utf-8") as f:
                f.write("fix(ATT-8165): tracked fix\n")
            res_good = subprocess.run([commit_msg_hook, msg_good], cwd=tmpdir, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            self.assertEqual(res_good.returncode, 0)

            # pre-commit rejects staged conflict markers and accepts clean files
            staged_bad = os.path.join(tmpdir, "staged.txt")
            with open(staged_bad, "w", encoding="utf-8") as f:
                f.write(("<" * 7) + " HEAD\nconflict\n" + ("=" * 7) + "\nother\n" + (">" * 7) + " b\n")
            subprocess.run(["git", "add", "staged.txt"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            res_pre_bad = subprocess.run([pre_commit_hook], cwd=tmpdir, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            self.assertNotEqual(res_pre_bad.returncode, 0)

            with open(staged_bad, "w", encoding="utf-8") as f:
                f.write("clean resolved content\n")
            subprocess.run(["git", "add", "staged.txt"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            res_pre_good = subprocess.run([pre_commit_hook], cwd=tmpdir, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            self.assertEqual(res_pre_good.returncode, 0)

    def test_tst_pro_121_3_combined_check_flags_execute_all_checks_without_short_circuit(self):
        """TST-PRO-121.3: Combined --check-* CLI flags run all requested checks and exit 0 iff all pass."""
        def _run_cli(args):
            out_buf = io.StringIO()
            err_buf = io.StringIO()
            with contextlib.redirect_stdout(out_buf), contextlib.redirect_stderr(err_buf):
                with self.assertRaises(SystemExit) as cm:
                    governance_cli_main(args)
            return cm.exception.code, out_buf.getvalue(), err_buf.getvalue()

        with tempfile.TemporaryDirectory() as tmpdir:
            git_env = dict(
                os.environ,
                GIT_AUTHOR_NAME="T",
                GIT_AUTHOR_EMAIL="t@e.com",
                GIT_COMMITTER_NAME="T",
                GIT_COMMITTER_EMAIL="t@e.com",
            )
            subprocess.run(["git", "init", "-b", "develop"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)

            # Valid governed doc header + clean commit with ATT-8165 + clean file
            mgt_dir = os.path.join(tmpdir, "docs", "management")
            os.makedirs(mgt_dir, exist_ok=True)
            with open(os.path.join(mgt_dir, "cm_plan.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Configuration Management Plan\n\n"
                    "**Document ID**: `DOC-MGT-001`\n"
                    "**Version**: `1.0.0`\n"
                    "**Status**: `Draft`\n"
                    "**Owner**: Rainer Blind\n"
                    "**Approver**: Pending Human Review\n\n"
                    "## Change History\n"
                    "| Version | Date | Author | Ticket | Summary |\n"
                    "| :--- | :--- | :--- | :--- | :--- |\n"
                    "| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8165`) | `ATT-8165` | Initial |\n"
                )
            subprocess.run(["git", "add", "."], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            post_env = dict(git_env, GIT_AUTHOR_DATE="1800000000 +0000", GIT_COMMITTER_DATE="1800000000 +0000")
            subprocess.run(["git", "commit", "-m", "docs(ATT-8165): baseline"], cwd=tmpdir, env=post_env, check=True, stdout=subprocess.DEVNULL)

            # 1. All 3 combined checks pass -> exit 0 and all 3 PASS lines printed
            code, out, err = _run_cli([
                "--repo-root", tmpdir,
                "--base-ref", "develop",
                "--check-doc-headers",
                "--check-commit-hygiene",
                "--check-conflict-markers",
            ])
            self.assertEqual(code, 0, f"Expected exit 0, got stderr={err}")
            self.assertIn("Work-product headers", out)
            self.assertIn("Commit hygiene verified", out)
            self.assertIn("Zero Git conflict markers detected", out)

            # 2. First two pass, later --check-conflict-markers fails -> must NOT skip 3rd check
            broken_fp = os.path.join(tmpdir, "broken.txt")
            with open(broken_fp, "w", encoding="utf-8") as f:
                f.write(("<" * 7) + " HEAD\nleft\n" + ("=" * 7) + "\nright\n" + (">" * 7) + " branch\n")
            subprocess.run(["git", "add", "broken.txt"], cwd=tmpdir, env=git_env, check=True, stdout=subprocess.DEVNULL)
            subprocess.run(["git", "commit", "-m", "fix(ATT-8165): add file"], cwd=tmpdir, env=post_env, check=True, stdout=subprocess.DEVNULL)

            code_fail3, out_fail3, err_fail3 = _run_cli([
                "--repo-root", tmpdir,
                "--base-ref", "develop",
                "--check-doc-headers",
                "--check-commit-hygiene",
                "--check-conflict-markers",
            ])
            self.assertEqual(code_fail3, 1)
            self.assertIn("Work-product headers", out_fail3)
            self.assertIn("Commit hygiene verified", out_fail3)
            self.assertIn("Unresolved Git conflict marker", err_fail3)

            # 3. Both middle (--check-commit-hygiene) AND last (--check-conflict-markers) fail -> both errors reported
            subprocess.run(["git", "commit", "--amend", "-m", "missing ticket key"], cwd=tmpdir, env=post_env, check=True, stdout=subprocess.DEVNULL)
            code_both, out_both, err_both = _run_cli([
                "--repo-root", tmpdir,
                "--base-ref", "develop",
                "--check-doc-headers",
                "--check-commit-hygiene",
                "--check-conflict-markers",
            ])
            self.assertEqual(code_both, 1)
            self.assertIn("Work-product headers", out_both)
            self.assertIn("lacks a mandatory Jira ticket key", err_both)
            self.assertIn("Unresolved Git conflict marker", err_both)

            # 4. Combined --check-doc-headers --check-qa-audits --check-impact also aggregates later failures
            analysis_dir = os.path.join(tmpdir, "docs", "engineering", "analysis")
            os.makedirs(analysis_dir, exist_ok=True)
            with open(os.path.join(analysis_dir, "ATT-8165_analysis.md"), "w", encoding="utf-8") as f:
                f.write("# Incomplete analysis\n")
            code_other, out_other, err_other = _run_cli([
                "--repo-root", tmpdir,
                "--base-ref", "develop",
                "--check-doc-headers",
                "--check-qa-audits",
                "--check-impact",
            ])
            self.assertEqual(code_other, 1)
            self.assertIn("Work-product headers", out_other)
            self.assertIn("failed validation", err_other)


class TestSensorTypeJavadocVerification(unittest.TestCase):
    """TST-PRO-126.3: Offline unit tests for verify_sensor_type_javadocs() (REQ-PRO-126.2, REQ-PRO-126.4)."""

    def _write_fixture(self, tmpdir, java_source, sensor_units_md=None):
        java_fp = os.path.join(
            tmpdir,
            "app", "src", "main", "java", "com", "atrainingtracker", "banalservice", "sensor", "SensorType.java",
        )
        os.makedirs(os.path.dirname(java_fp), exist_ok=True)
        with open(java_fp, "w", encoding="utf-8") as f:
            f.write(java_source)
        if sensor_units_md is not None:
            doc_fp = os.path.join(tmpdir, "docs", "architecture", "sensor_units.md")
            os.makedirs(os.path.dirname(doc_fp), exist_ok=True)
            with open(doc_fp, "w", encoding="utf-8") as f:
                f.write(sensor_units_md)

    def test_valid_constants_and_doc_parity_pass(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            self._write_fixture(
                tmpdir,
                """package com.atrainingtracker.banalservice.sensor;
public enum SensorType {
    /**
     * Horizontal speed.
     * Internal unit: {@code m/s} ({@code DOUBLE}).
     */
    SPEED_mps(SensorCategory.SPEED_PACE_AND_DISTANCE, 1, 2, 3, SensorValueType.DOUBLE, null, true, "m/s"),
    // SPEED_mps_gps(1, 2, 3, SensorValueType.DOUBLE, null),
    /**
     * Instantaneous heart rate.
     * Internal unit: {@code bpm} ({@code INTEGER}).
     */
    HR(SensorCategory.HEART_RATE_AND_ENERGY, 1, 2, 3, SensorValueType.INTEGER, null, true, "bpm");
}
""",
                """| SensorType | Category | SubCategory | Internal Unit | ValueType | Range & Sentinels | UI Formatter & Display Unit | Export Units |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `SPEED_mps` | `SPEED_PACE_AND_DISTANCE` | `null` | `m/s` | `DOUBLE` | `>= 0` | `SpeedFormatter` | `m/s` |
| `HR` | `HEART_RATE_AND_ENERGY` | `null` | `bpm` | `INTEGER` | `[1, 299]` | `IntegerFormatter` | `bpm` |
""",
            )
            ok, diag = verify_sensor_type_javadocs(repo_root=tmpdir, require_file=True)
            self.assertTrue(ok, f"Expected pass, got: {diag}")
            self.assertIn("2 SensorType constant(s)", "\n".join(diag))

    def test_missing_javadoc_fails(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            self._write_fixture(
                tmpdir,
                """public enum SensorType {
    // Only a line comment, no Javadoc block
    HR(SensorCategory.HEART_RATE_AND_ENERGY, 1, 2, 3, SensorValueType.INTEGER, null, true, "bpm");
}
""",
            )
            ok, diag = verify_sensor_type_javadocs(repo_root=tmpdir, require_file=True)
            self.assertFalse(ok)
            self.assertIn("SensorType.HR must have a preceding /** ... */ Javadoc block", "\n".join(diag))

    def test_missing_internal_unit_tag_or_mismatch_fails(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            self._write_fixture(
                tmpdir,
                """public enum SensorType {
    /**
     * Heart rate without internal unit tag.
     */
    HR(SensorCategory.HEART_RATE_AND_ENERGY, 1, 2, 3, SensorValueType.INTEGER, null, true, "bpm"),
    /**
     * Speed with wrong unit in Javadoc.
     * Internal unit: {@code km/h}.
     */
    SPEED_mps(SensorCategory.SPEED_PACE_AND_DISTANCE, 1, 2, 3, SensorValueType.DOUBLE, null, true, "m/s");
}
""",
            )
            ok, diag = verify_sensor_type_javadocs(repo_root=tmpdir, require_file=True)
            self.assertFalse(ok)
            joined = "\n".join(diag)
            self.assertIn("SensorType.HR Javadoc must document 'Internal unit:'", joined)
            self.assertIn("SensorType.SPEED_mps Javadoc must document 'Internal unit:'", joined)

    def test_live_cli_check_sensor_javadocs_passes(self):
        buf_out = io.StringIO()
        buf_err = io.StringIO()
        with contextlib.redirect_stdout(buf_out), contextlib.redirect_stderr(buf_err):
            with self.assertRaises(SystemExit) as cm:
                governance_cli_main(["--check-sensor-javadocs"])
        self.assertEqual(cm.exception.code, 0, f"stderr={buf_err.getvalue()}")
        self.assertIn("67 SensorType constant(s)", buf_out.getvalue())


class TestSymmetricTraceabilityAndScannerAudit(unittest.TestCase):
    """TST-PRO-127.1, TST-PRO-127.2, TST-PRO-127.4: Symmetric REQ<->TST parity, tag scanner & --check-size."""

    def test_symmetric_edge_parity_rejects_asymmetric_req_to_tst_and_tst_to_req(self):
        """TST-PRO-127.1: Asymmetric REQ->TST and TST->REQ links outside baseline fail deterministically."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            req_dir = os.path.join(tmp_dir, "docs", "requirements")
            tst_dir = os.path.join(tmp_dir, "docs", "tests")
            tools_dir = os.path.join(tmp_dir, "tools")
            os.makedirs(req_dir)
            os.makedirs(tst_dir)
            os.makedirs(tools_dir)

            with open(os.path.join(req_dir, "req_dom.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Shard\n"
                    "### REQ-DOM-001: Req One [Verified]\n- **Tests**: `TST-DOM-001`, `TST-DOM-002`\n\n"
                    "### REQ-DOM-002: Req Two [Verified]\n- **Tests**: `TST-DOM-002`\n\n"
                    "### REQ-DOM-003: Baselined Legacy [Verified]\n- **Tests**: `TST-DOM-001`\n"
                )
            with open(os.path.join(tst_dir, "tst_dom.md"), "w", encoding="utf-8") as f:
                f.write(
                    "# Test Shard\n"
                    "### TST-DOM-001: Test One [Verified]\n- **Verifies**: `REQ-DOM-001`\n\n"
                    "### TST-DOM-002: Test Two [Verified]\n- **Verifies**: `REQ-DOM-002`\n\n"
                    "### TST-DOM-003: Test Three [Verified]\n- **Verifies**: `REQ-DOM-001`\n"
                )
            baseline_path = os.path.join(tools_dir, "traceability_baseline.json")
            with open(baseline_path, "w", encoding="utf-8") as f:
                json.dump({"orphan_requirements": ["REQ-DOM-003"]}, f)

            ok, diag, report = verify_traceability_orphans(
                repo_root=tmp_dir,
                baseline_path=baseline_path,
                fail_on_orphans=True,
            )
            self.assertFalse(ok)
            self.assertEqual(report["asymmetric_req_to_tst"], [("REQ-DOM-001", "TST-DOM-002")])
            self.assertEqual(report["asymmetric_tst_to_req"], [("TST-DOM-003", "REQ-DOM-001")])
            joined = "\n".join(diag)
            self.assertIn("Criterion 4 Symmetric REQ->TST", joined)
            self.assertIn("Criterion 4 Symmetric TST->REQ", joined)

    def test_extract_test_code_tags_scans_android_test_and_tools_test_py(self):
        """TST-PRO-127.2: extract_test_code_tags discovers tags across app/src/{test,androidTest} and tools/test_*.py."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            unit_dir = os.path.join(tmp_dir, "app", "src", "test", "java")
            inst_dir = os.path.join(tmp_dir, "app", "src", "androidTest", "java")
            tools_dir = os.path.join(tmp_dir, "tools")
            os.makedirs(unit_dir)
            os.makedirs(inst_dir)
            os.makedirs(tools_dir)

            with open(os.path.join(unit_dir, "UnitSampleTest.kt"), "w", encoding="utf-8") as f:
                f.write("// Traces to TST-TRK-001\n")
            with open(os.path.join(inst_dir, "SmokeAndroidTest.kt"), "w", encoding="utf-8") as f:
                f.write("// Verifies TST-UI-101.1\n")
            with open(os.path.join(tools_dir, "test_sample_tool.py"), "w", encoding="utf-8") as f:
                f.write("# TST-TOOL-001 and ignored TST-UI-999\n")

            tags = extract_test_code_tags(repo_root=tmp_dir)
            self.assertIn("TST-TRK-001", tags)
            self.assertIn("TST-UI-101", tags)
            self.assertIn("TST-TOOL-001", tags)
            self.assertNotIn("TST-UI-999", tags)

    def test_cli_check_size_and_live_symmetric_coverage(self):
        """TST-PRO-127.1 / TST-PRO-127.4: Live repo passes --check-size and achieves 0 Cat-4 gaps."""
        ok_size, diag_size = verify_shard_sizes(max_lines=4000)
        self.assertTrue(ok_size, f"Expected all live shards <= 4000 lines: {diag_size}")

        buf_out = io.StringIO()
        buf_err = io.StringIO()
        with contextlib.redirect_stdout(buf_out), contextlib.redirect_stderr(buf_err):
            with self.assertRaises(SystemExit) as cm:
                governance_cli_main(["--check-size"])
        self.assertEqual(cm.exception.code, 0, f"stderr={buf_err.getvalue()}")
        self.assertIn("within line limits", buf_out.getvalue())

        cov = calculate_traceability_coverage()
        self.assertEqual(cov.cat4.gap_count, 0, f"Expected 0 Cat-4 gaps, got: {cov.cat4.details}")


if __name__ == "__main__":
    unittest.main()
