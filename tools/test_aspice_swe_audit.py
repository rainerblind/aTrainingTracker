#!/usr/bin/env python3
"""
Python contract tests migrated from AspiceSweAuditContractTest.kt (ATT-8136).

Verifies ASPICE SWE.1/2/4/5/6 audit hardening:
1. docs/architecture.md completeness (layers, 9-domain shard allocation, 3 Mermaid models, invariants).
2. Authentic component nomenclature code-grounding & absence of drifted placeholders (REQ-PRO-043).
3. Software interface specifications (SWE.2.BP2) for IF-SWE-01 through IF-SWE-08 (REQ-PRO-044).
4. Status header standardization across all 9 requirement and test domain shards.
5. Master-to-shard table count parity in docs/requirements.md and docs/tests.md.
6. Skill & rule governance contracts (universal read_file image audit, doc code-grounding, SWE.2 maintenance,
   autonomous Retro write-allowlist REQ-PRO-117).
7. Software integration verification strategy (SWE.5.BP1) for INT-SWE-01 through INT-SWE-08 (REQ-PRO-045).

Linked test specs: TST-PRO-034, TST-PRO-043, TST-PRO-044, TST-PRO-045, TST-PRO-078, TST-PRO-093,
TST-PRO-110, TST-PRO-111, TST-PRO-117, TST-PRO-120, TST-PRO-130, TST-PRO-131, TST-PRO-132,
TST-STR-012, TST-STR-017.
"""

import os
import re
import subprocess
import unittest


REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def _read_repo_file(rel_path: str) -> str:
    abs_path = os.path.join(REPO_ROOT, rel_path)
    with open(abs_path, "r", encoding="utf-8") as fh:
        return fh.read()


def _repo_path(rel_path: str) -> str:
    return os.path.join(REPO_ROOT, rel_path)


class TestAspiceSweAudit(unittest.TestCase):
    """ASPICE SWE.1/2/4/5/6 audit hardening contract suite."""

    def test_architecture_document_fulfills_aspice_swe2(self):
        """TST-PRO-034.2: Architecture layers, 9 shards, Mermaid models, threading & DB invariants."""
        content = _read_repo_file("docs/architecture.md")

        for layer in (
            "Presentation Layer",
            "Domain & Scientific Calculators",
            "Background Services",
            "Persistence & Cloud Layer",
        ):
            self.assertIn(layer, content, f"Must specify {layer}")

        required_shards = (
            "Tracking & Live Sensor Cockpit",
            "Post-Workout Analytics & Periods",
            "Maps, Polylines & Navigation",
            "Persistence, Migration & Cloud Export",
            "Settings, Profiles & Localization",
            "UI Components & Design System",
            "Engineering Process & Stability",
            "Jubilee Milestones & App Reviews",
            "Store Presence",
        )
        for shard in required_shards:
            self.assertIn(shard, content, f"Allocation table must reference shard '{shard}'")

        self.assertIn("stateDiagram-v2", content, "Must include State Diagram for tracking")
        seq_count = len(re.findall(r"sequenceDiagram", content))
        self.assertGreaterEqual(
            seq_count, 2, "Must include at least 2 sequence diagrams (Sensor failover & Post-workout)"
        )

        self.assertIn("dbDispatcher", content)
        self.assertIn("Dispatchers.Main", content)
        self.assertIn("Dispatchers.IO", content)
        self.assertIn("location|health", content)
        self.assertIn("onUpgrade", content)

    def test_living_doc_shards_all_headings_standardized(self):
        """TST-PRO-034.1: All REQ-* and TST-* headings end with standard status tag."""
        for rel_dir, prefix in (("docs/requirements", "REQ"), ("docs/tests", "TST")):
            abs_dir = _repo_path(rel_dir)
            shard_files = [fn for fn in sorted(os.listdir(abs_dir)) if fn.endswith(".md")]
            self.assertTrue(shard_files, f"{rel_dir} must contain markdown files")
            heading_pat = re.compile(rf"^#{{2,4}}\s+{prefix}-[A-Z0-9_-]+:")
            for fn in shard_files:
                with open(os.path.join(abs_dir, fn), "r", encoding="utf-8") as fh:
                    for line in fh:
                        trimmed = line.strip()
                        if heading_pat.match(trimmed):
                            self.assertTrue(
                                trimmed.endswith(("[Verified]", "[Planned]", "[Implemented]", "[Open]")),
                                f"Heading in {fn} must end with standard status: '{trimmed}'",
                            )

    def test_master_table_parity_matches_domain_shards(self):
        """TST-PRO-034.1 / TST-PRO-078 / TST-PRO-093: Master-to-shard count parity."""
        try:
            proc = subprocess.run(
                ["git", "status", "--porcelain", "-b"],
                cwd=REPO_ROOT,
                capture_output=True,
                text=True,
                check=False,
            )
            header = (proc.stdout.splitlines() or [""])[0]
            is_feature_branch = (
                "sprint/" not in header and "develop" not in header and "master" not in header
            )
        except Exception:
            is_feature_branch = False

        line_pat = re.compile(
            r"\((?:requirements|tests)/((?:req|tst)_[^)]+\.md)\).*?\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|"
        )

        for master_rel, shard_rel_dir, prefix in (
            ("docs/requirements.md", "docs/requirements", "REQ"),
            ("docs/tests.md", "docs/tests", "TST"),
        ):
            shard_dir = _repo_path(shard_rel_dir)
            shard_counts = {}
            heading_pat = re.compile(rf"^#{{2,4}}\s+{prefix}-[A-Z0-9_-]+:")
            for fn in sorted(os.listdir(shard_dir)):
                if not fn.endswith(".md"):
                    continue
                tot = ver = impl = plan = 0
                with open(os.path.join(shard_dir, fn), "r", encoding="utf-8") as fh:
                    for line in fh:
                        if heading_pat.match(line.strip()):
                            tot += 1
                            if "[Verified]" in line:
                                ver += 1
                            elif "[Implemented]" in line:
                                impl += 1
                            else:
                                plan += 1
                shard_counts[fn] = (tot, ver, impl, plan)

            grand_total = 0
            checked_shards = 0
            master_content = _read_repo_file(master_rel)
            for line in master_content.splitlines():
                m = line_pat.search(line)
                if not m:
                    continue
                shard_name = m.group(1)
                total, verified, implemented, planned = (
                    int(m.group(2)),
                    int(m.group(3)),
                    int(m.group(4)),
                    int(m.group(5)),
                )
                self.assertIn(shard_name, shard_counts, f"Master table references missing shard {shard_name}")
                exp_tot, exp_ver, exp_impl, exp_plan = shard_counts[shard_name]
                if is_feature_branch:
                    self.assertGreaterEqual(exp_ver, verified)
                    self.assertGreaterEqual(exp_tot, total)
                else:
                    self.assertEqual(exp_tot, total, f"Total mismatch in {master_rel} for {shard_name}")
                    self.assertEqual(exp_ver, verified, f"Verified mismatch in {master_rel} for {shard_name}")
                    self.assertEqual(exp_impl, implemented, f"Implemented mismatch in {master_rel} for {shard_name}")
                    self.assertEqual(exp_plan, planned, f"Planned mismatch in {master_rel} for {shard_name}")
                grand_total += total
                checked_shards += 1

            self.assertEqual(len(shard_counts), checked_shards, "All shards must be present in master table")
            if not is_feature_branch:
                self.assertEqual(sum(c[0] for c in shard_counts.values()), grand_total)

    def test_governance_rules_and_skills_contain_swe_and_image_contracts(self):
        """TST-PRO-034.4 / TST-PRO-131.5 / TST-PRO-132.3: Rules & Stage skills invariants."""
        rules_text = _read_repo_file(".agents/rules/aspice_governance.md")
        self.assertIn("read_file", rules_text)
        self.assertTrue("Documentation Code-Grounding" in rules_text or "code-grounding" in rules_text)
        self.assertIn("Architecture Maintenance", rules_text)
        self.assertIn("SWE.2", rules_text)

        self.assertIn("Documentation Code-Grounding", _read_repo_file(".agents/skills/stage1-analysis/SKILL.md"))
        self.assertIn("Continuous SWE.2 Maintenance", _read_repo_file(".agents/skills/stage3-impl-plan/SKILL.md"))
        self.assertIn("Universal `read_file` Image Inspection", _read_repo_file(".agents/skills/stage4-implementation/SKILL.md"))
        self.assertIn("SWE.2 Architecture Parity", _read_repo_file(".agents/skills/stage5-verification/SKILL.md"))

    def test_autonomous_retro_write_allowlist_protected_rules_and_skills_contract(self):
        """REQ-PRO-117 / TST-PRO-117: Autonomous Retro write-allowlist and protected paths."""
        rules_text = _read_repo_file(".agents/rules/aspice_governance.md")
        planner_text = _read_repo_file(".agents/skills/sprint-planner/SKILL.md")

        expected_allowlist = (
            "Sprint_Review_and_Retro_",
            "docs/engineering/gates/",
            "docs/engineering/qa_audits/",
            "docs/engineering/reviews/",
            "docs/engineering/test_evidence/",
            "docs/engineering/metrics/process_metrics.jsonl",
            "docs/management/risk_register.md",
        )
        expected_protected = (
            ".agents/rules/",
            ".agents/skills/",
            "docs/coding_standards.md",
            "docs/design_guidelines.md",
            "docs/project_governance.md",
            "docs/project_protocol.md",
            "docs/engineering/rule_history_archive.md",
            "tools/",
        )
        for label, text in (("aspice_governance.md", rules_text), ("sprint-planner/SKILL.md", planner_text)):
            self.assertIn("REQ-PRO-117", text, f"{label} must reference REQ-PRO-117")
            self.assertIn("Wochen Review & Retro", text, f"{label} must route proposals to Wochen Review & Retro")
            for token in expected_allowlist:
                self.assertIn(token, text, f"{label} must list allowlisted path '{token}' (REQ-PRO-117.1)")
            for token in expected_protected:
                self.assertIn(token, text, f"{label} must protect path '{token}' (REQ-PRO-117.2)")

        self.assertIn("REQ-STP-010.2", planner_text)
        self.assertIn("sprint-metrics --record --post-to-retro", planner_text)
        self.assertIn("PA 4.2", planner_text)
        self.assertIn("PA 5.2", planner_text)

    def test_work_product_headers_and_governance_reconciliation_contract(self):
        """REQ-PRO-120 / TST-PRO-120: Work-product headers and cross-doc reconciliation."""
        exact_files = {
            "docs/architecture.md",
            "docs/requirements.md",
            "docs/tests.md",
            "docs/coding_standards.md",
            "docs/design_guidelines.md",
            "docs/project_governance.md",
            "docs/project_protocol.md",
            ".agents/rules/aspice_governance.md",
        }
        governed_dirs = {
            "docs/management",
            "docs/architecture",
            "docs/architecture/detailed_design",
            "docs/system",
            "docs/requirements",
            "docs/tests",
        }

        git_cmd = ["git", "-c", "core.quotePath=false", "ls-files", "--", *sorted(exact_files), *sorted(governed_dirs)]
        proc = subprocess.run(git_cmd, cwd=REPO_ROOT, capture_output=True, text=True, check=False)
        governed_files = []
        if proc.returncode == 0 and proc.stdout.strip():
            for rel in proc.stdout.splitlines():
                norm = rel.strip().replace("\\", "/")
                if not norm.endswith(".md"):
                    continue
                parent_dir = norm.rsplit("/", 1)[0] if "/" in norm else ""
                if (norm in exact_files or parent_dir in governed_dirs) and os.path.isfile(_repo_path(norm)):
                    governed_files.append(norm)
        else:
            governed_files.extend(sorted(exact_files))
            for dir_rel in sorted(governed_dirs):
                for fn in sorted(os.listdir(_repo_path(dir_rel))):
                    if fn.endswith(".md"):
                        governed_files.append(f"{dir_rel}/{fn}")

        self.assertGreaterEqual(len(governed_files), 45, "Must discover governed work-product files")

        doc_id_re = re.compile(r"\*\*Document ID\*\*:\s*`?DOC-[A-Z0-9-]+`?")
        version_re = re.compile(r"\*\*Version\*\*:\s*`?\d+\.\d+\.\d+`?")
        status_re = re.compile(r"\*\*Status\*\*:\s*([^\n|]+)")
        approver_re = re.compile(r"\*\*Approver\*\*:\s*([^\n|]+)")
        history_re = re.compile(r"^##\s+(?:\d+\.\s+)?(?:Change|Revision)\s+History\b", re.MULTILINE)

        for rel_path in sorted(set(governed_files)):
            text = _read_repo_file(rel_path)
            self.assertRegex(text, doc_id_re, f"{rel_path} must declare **Document ID**: DOC-* (REQ-PRO-120.1)")
            self.assertRegex(text, version_re, f"{rel_path} must declare semver **Version** (REQ-PRO-120.1)")
            st_m = status_re.search(text)
            self.assertIsNotNone(st_m, f"{rel_path} must declare **Status** (REQ-PRO-120.1)")
            ap_m = approver_re.search(text)
            self.assertIsNotNone(ap_m, f"{rel_path} must declare **Approver** (REQ-PRO-120.1)")
            self.assertRegex(text, history_re, f"{rel_path} must include Change/Revision History (REQ-PRO-120.1)")
            if st_m.group(1).strip() == "Approved":
                approver = ap_m.group(1).strip()
                self.assertNotIn("pending", approver.lower(), f"{rel_path} cannot claim Approved with Pending Approver")
                self.assertNotRegex(approver, re.compile(r"\bAI\b", re.IGNORECASE), f"{rel_path} cannot claim Approved with AI Approver")

        protocol_text = _read_repo_file("docs/project_protocol.md")
        self.assertIn("9 Core Rules", protocol_text)
        self.assertNotIn("8 Core Rules", protocol_text)
        self.assertIn("Rules 1–85+", protocol_text)
        self.assertNotIn("Rules 1–55", protocol_text)
        self.assertIn("6-Stage", protocol_text)

        jira_util_text = _read_repo_file("tools/jira_util.py")
        self.assertIn("9 CORE RULES", jira_util_text)
        self.assertNotIn("8 CORE RULES", jira_util_text)

        risk_reg_text = _read_repo_file("docs/management/risk_register.md")
        self.assertIn("Rule 3.4 / Rule 83", risk_reg_text)
        self.assertNotIn("mandatory **Rule 5**", risk_reg_text)

        hm_protocol_text = _read_repo_file("tools/hoechstmass/docs/project_protocol.md")
        self.assertIn("9 Core", hm_protocol_text)
        self.assertIn("6-Stage", hm_protocol_text)

        hm_readme_text = _read_repo_file("tools/hoechstmass/README.md")
        self.assertIn("9 Core", hm_readme_text)
        self.assertIn("6-Stage", hm_readme_text)

    def test_architecture_document_authentic_component_nomenclature(self):
        """REQ-PRO-043 / TST-PRO-043.1..043.3: Authentic nomenclature and absence of drifted placeholders."""
        content = _read_repo_file("docs/architecture.md")
        interfaces_content = _read_repo_file("docs/architecture/interfaces.md")

        for comp in (
            "WorkoutClusterEngine",
            "GPXFileWriter",
            "TCXFileWriter",
            "WorkoutSamplesDatabaseManager",
            "MilestoneAutoNamingManager",
            "FirstJubileeActionHelper",
            "SettingsDataStore",
            "EquipmentDbHelper",
            "EquipmentRepository",
            "ProFeatureManager",
            "SupporterTier",
            "GarageLeagueDetector",
            "ATrainingTrackerTheme",
            "TelemetryMetricGraph",
            "PathPoint",
            "PlayStoreListingContractTest",
        ):
            self.assertTrue(
                comp in content or comp in interfaces_content,
                f"Architecture docs must reference authentic component '{comp}' (REQ-PRO-043.1)",
            )

        for drift in (
            "RouteLearningEngine",
            "GpxExporter",
            "TcxExporter",
            "WorkoutSamplesDb",
            "MilestoneDetector",
            "InAppReviewManager",
            "UserPreferences",
            "EquipmentDatabaseManager",
            "PrivacyZoneManager",
            "BillingManager",
            "TTTheme",
            "CustomLineChart",
            "ElevationPoint",
            "StoreMetadataContractTest",
        ):
            self.assertNotIn(drift, content, f"docs/architecture.md must not contain drifted placeholder '{drift}'")
            self.assertNotIn(drift, interfaces_content, f"interfaces.md must not contain drifted placeholder '{drift}'")

        for token in ("IDLE", "READY", "TRACKING", "PAUSED", "AutoPauseState", "dbDispatcher", "Dispatchers.IO"):
            self.assertIn(token, content)

    def test_architecture_documents_all_allocated_and_specified_components_exist_in_codebase(self):
        """REQ-PRO-043.5 / TST-PRO-043.4: All allocated/specified components exist in codebase."""
        arch_text = _read_repo_file("docs/architecture.md")
        interfaces_text = _read_repo_file("docs/architecture/interfaces.md")

        table_m = re.search(
            r"## 2\. Requirement Allocation to Architecture.*?\| :--- \| :--- \| :--- \| :--- \|(.*?)\n---",
            arch_text,
            re.DOTALL,
        )
        self.assertIsNotNone(table_m, "Failed to locate Section 2 Requirement Allocation table in docs/architecture.md")
        allocated_components = set()
        for row in table_m.group(1).splitlines():
            row_s = row.strip()
            if not row_s.startswith("|"):
                continue
            cols = [c.strip() for c in row_s.split("|") if c.strip()]
            if len(cols) >= 3:
                for m in re.finditer(r"`([^`]+)`", cols[2]):
                    allocated_components.add(m.group(1))
        self.assertGreaterEqual(len(allocated_components), 50)

        interface_types = set()
        for line in interfaces_text.splitlines():
            t = line.strip()
            if t.startswith(("* **Provider**:", "* **Consumer**:", "* **Data Payloads / Schemas**:")):
                for m in re.finditer(r"`([A-Z][a-zA-Z0-9_]+)`", t):
                    interface_types.add(m.group(1))

        framework_allowlist = {
            "Context", "File", "Location", "LocationListener", "BluetoothGattCallback",
            "AntPluginPcc", "IPluginAccessResultReceiver", "OutputStream", "Writer",
            "HealthConnectClient", "ExerciseSessionRecord", "ContentValues", "StateFlow",
            "Flow", "Intent", "Result", "Double", "Long", "Int", "Boolean", "Float",
            "String", "List", "Map", "Set", "Pair", "Unit", "WorkManager", "NavHost",
            "LatLng", "ProGuard", "OkHttpClient",
        }

        all_basenames = set()
        all_names_no_ext = set()
        main_source_texts = []
        for base_rel in ("app/src/main", "app/src/test"):
            for root, _, files in os.walk(_repo_path(base_rel)):
                for fn in files:
                    if fn.endswith((".kt", ".java")):
                        all_basenames.add(fn)
                        all_names_no_ext.add(os.path.splitext(fn)[0])
                        if base_rel == "app/src/main":
                            with open(os.path.join(root, fn), "r", encoding="utf-8", errors="ignore") as fh:
                                main_source_texts.append(fh.read())
        for fn in os.listdir(_repo_path("tools")):
            if os.path.isfile(_repo_path(os.path.join("tools", fn))):
                all_basenames.add(fn)
                all_names_no_ext.add(os.path.splitext(fn)[0])

        to_validate = (allocated_components | interface_types) - framework_allowlist
        self.assertTrue(to_validate)
        ungrounded = []
        for comp in sorted(to_validate):
            if comp in all_basenames or comp in all_names_no_ext:
                continue
            decl_re = re.compile(rf"\b(?:class|interface|enum\s+class|enum|object|typealias|fun)\s+{re.escape(comp)}\b")
            if not any(decl_re.search(src) for src in main_source_texts):
                ungrounded.append(comp)
        self.assertEqual([], ungrounded, f"Ungrounded components in architecture docs: {ungrounded}")

    def test_architecture_document_software_interface_specifications(self):
        """REQ-PRO-044 / TST-PRO-044.1..044.3: IF-SWE-01..08 specifications in interfaces.md and architecture.md."""
        interfaces_content = _read_repo_file("docs/architecture/interfaces.md")
        arch_content = _read_repo_file("docs/architecture.md")

        interface_ids = [f"IF-SWE-{i:02d}" for i in range(1, 9)]
        for if_id in interface_ids:
            self.assertIn(if_id, interfaces_content)
            self.assertIn(if_id, arch_content)
        for attr in ("Provider", "Consumer", "Operations", "Threading", "Error Handling"):
            self.assertIn(attr, interfaces_content)
        self.assertIn("5. Software Interface Specifications", arch_content)
        self.assertIn("interfaces.md", arch_content)

    def test_architecture_document_software_interface_grounding(self):
        """REQ-PRO-044.3 / TST-PRO-044.4 / TST-PRO-130: Interface methods & types grounded in app/src/main."""
        interfaces_content = _read_repo_file("docs/architecture/interfaces.md")
        arch_content = _read_repo_file("docs/architecture.md")

        self.assertNotIn("Retrofit", interfaces_content)
        self.assertNotIn("Retrofit", arch_content)
        for token in (
            "ClustersDatabaseManager",
            "TrackingUiState",
            "AftermathUiState",
            "LocationUiState",
            "ExtremaValues",
            "WorkoutDetailScreen",
        ):
            self.assertNotIn(token, interfaces_content)
        self.assertNotRegex(interfaces_content, r"\bAftermathViewModel\b")
        self.assertNotRegex(arch_content, r"\bAftermathViewModel\b")

        all_sources = []
        for root, _, files in os.walk(_repo_path("app/src/main")):
            for fn in files:
                if fn.endswith((".kt", ".java")):
                    with open(os.path.join(root, fn), "r", encoding="utf-8", errors="ignore") as fh:
                        all_sources.append(fh.read())
        self.assertTrue(all_sources)

        extracted_methods = set(re.findall(r"\bfun\s+([a-zA-Z0-9_]+)\s*\(", interfaces_content))
        self.assertTrue(extracted_methods)
        ungrounded_methods = []
        for method_name in sorted(extracted_methods):
            m_re = re.compile(
                rf"\b(?:fun\s+{re.escape(method_name)}\s*[\(<]|(?:public|protected|private|static)\s+[\w<>,\s\[\]]+\s+{re.escape(method_name)}\s*\()"
            )
            if not any(m_re.search(src) for src in all_sources):
                ungrounded_methods.append(method_name)
        self.assertEqual([], ungrounded_methods, f"Ungrounded interface methods: {ungrounded_methods}")

        framework_allowlist = {
            "Context", "File", "Location", "LocationListener", "BluetoothGattCallback",
            "AntPluginPcc", "IPluginAccessResultReceiver", "OutputStream", "Writer",
            "HealthConnectClient", "ExerciseSessionRecord", "ContentValues", "StateFlow",
            "Flow", "Intent", "Result", "Double", "Long", "Int", "Boolean", "Float",
            "String", "List", "Map", "Set", "Pair", "Unit",
        }
        extracted_types = set()
        for line in interfaces_content.splitlines():
            t = line.strip()
            if t.startswith(("* **Provider**:", "* **Consumer**:", "* **Data Payloads / Schemas**:")):
                for m in re.finditer(r"`([A-Z][a-zA-Z0-9_]+)`", t):
                    tp = m.group(1)
                    if tp not in framework_allowlist:
                        extracted_types.add(tp)
        self.assertTrue(extracted_types)

        ungrounded_types = []
        for tp in sorted(extracted_types):
            t_re = re.compile(rf"\b(?:class|interface|enum\s+class|object|typealias|fun)\s+{re.escape(tp)}\b")
            if not any(t_re.search(src) for src in all_sources):
                ungrounded_types.append(tp)
        self.assertEqual([], ungrounded_types, f"Ungrounded interface types: {ungrounded_types}")

    def test_architecture_document_software_integration_strategy(self):
        """REQ-PRO-045 / TST-PRO-045: Software integration strategy in integration_strategy.md and architecture.md."""
        strategy_content = _read_repo_file("docs/architecture/integration_strategy.md")
        arch_content = _read_repo_file("docs/architecture.md")

        for sec in ("Integration Sequence", "Verification Levels", "Entry and Exit Criteria", "Regression Selection"):
            self.assertIn(sec, strategy_content)
        for i in range(1, 9):
            if_id = f"IF-SWE-{i:02d}"
            int_id = f"INT-SWE-{i:02d}"
            self.assertIn(if_id, strategy_content)
            self.assertIn(int_id, strategy_content)
            self.assertIn(if_id, arch_content)
            self.assertIn(int_id, arch_content)
        self.assertIn("6. Software Integration and Verification Strategy", arch_content)
        self.assertIn("integration_strategy.md", arch_content)

    def test_software_integration_verification_int_swe_01_to_04_test_classes_and_governance(self):
        """REQ-PRO-110 / TST-PRO-110: INT-SWE-01..04 test classes and test_spec traceability."""
        integration_files = (
            ("app/src/test/java/com/atrainingtracker/trainingtracker/integration/UiPresentationIntegrationTest.kt", "TST-INT-009"),
            ("app/src/test/java/com/atrainingtracker/trainingtracker/integration/ViewModelRepositoryIntegrationTest.kt", "TST-INT-010"),
            ("app/src/test/java/com/atrainingtracker/trainingtracker/integration/InterServiceIngestionIntegrationTest.kt", "TST-INT-011"),
            ("app/src/test/java/com/atrainingtracker/trainingtracker/integration/HardwareHalIngestionIntegrationTest.kt", "TST-INT-012"),
        )
        for rel_path, tst_id in integration_files:
            self.assertTrue(os.path.isfile(_repo_path(rel_path)), f"{rel_path} must exist")
            content = _read_repo_file(rel_path)
            self.assertIn(tst_id, content)
            self.assertIn("RobolectricTestRunner", content)
            self.assertIn("sdk = [34]", content)

        spec_content = _read_repo_file("docs/engineering/test_specs/ATT-8155_test_spec.md")
        self.assertIn("REQ-PRO-110", spec_content)
        self.assertIn("TST-PRO-110", spec_content)
        for i in range(9, 13):
            self.assertIn(f"TST-INT-{i:03d}", spec_content)

    def test_software_integration_verification_int_swe_05_to_08_test_classes_and_strategy_parity(self):
        """REQ-PRO-111 / TST-PRO-111: INT-SWE-05..08 test classes and strategy parity."""
        integration_files = (
            ("app/src/test/java/com/atrainingtracker/trainingtracker/integration/DomainCalculatorsIntegrationTest.kt", "TST-INT-013"),
            ("app/src/test/java/com/atrainingtracker/trainingtracker/integration/PersistenceSqliteIntegrationTest.kt", "TST-INT-014"),
            ("app/src/test/java/com/atrainingtracker/trainingtracker/integration/FileExportersIntegrationTest.kt", "TST-INT-015"),
            ("app/src/test/java/com/atrainingtracker/trainingtracker/integration/CloudHealthConnectIntegrationTest.kt", "TST-INT-016"),
        )
        for rel_path, tst_id in integration_files:
            self.assertTrue(os.path.isfile(_repo_path(rel_path)), f"{rel_path} must exist")
            content = _read_repo_file(rel_path)
            self.assertIn(tst_id, content)
            self.assertIn("RobolectricTestRunner", content)
            self.assertIn("sdk = [34]", content)

        spec_content = _read_repo_file("docs/engineering/test_specs/ATT-8156_test_spec.md")
        self.assertIn("REQ-PRO-111", spec_content)
        self.assertIn("TST-PRO-111", spec_content)
        for i in range(13, 17):
            self.assertIn(f"TST-INT-{i:03d}", spec_content)

        strategy_content = _read_repo_file("docs/architecture/integration_strategy.md")
        self.assertIn("6,540", strategy_content)
        self.assertIn("1,119", strategy_content)
        self.assertIn("emulator-5554", strategy_content)
        self.assertIn("`INT-SWE-01` through `INT-SWE-08`", strategy_content)

    def test_subtask_automation_contract_jira_util_and_req_link_tst_str_012(self):
        """TST-STR-012 / REQ-PRO-007: Jira stage subtask automation tooling and REQ link."""
        jira_util_content = _read_repo_file("tools/jira_util.py")
        self.assertIn("STAGE_SPECS", jira_util_content)
        self.assertIn("submit_stage", jira_util_content)
        self.assertIn("subtask", jira_util_content)

        req_content = _read_repo_file("docs/requirements/req_process_stability_part1.md")
        self.assertIn("REQ-PRO-007", req_content)
        self.assertIn("TST-STR-012", req_content)

    def test_protocol_integrity_positional_format_hardening_and_parity_test_tst_str_017(self):
        """TST-STR-017 / REQ-PRO-012: Positional format-string hardening guidelines and parity link."""
        guidelines_content = _read_repo_file("docs/design_guidelines.md")
        self.assertIn("%1$s", guidelines_content)
        self.assertTrue(
            os.path.isfile(
                _repo_path("app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt")
            )
        )
        req_content = _read_repo_file("docs/requirements/req_process_stability_part1.md")
        self.assertIn("REQ-PRO-012", req_content)
        self.assertIn("TST-STR-017", req_content)


if __name__ == "__main__":
    unittest.main()
