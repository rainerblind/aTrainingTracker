#!/usr/bin/env python3
"""
Python contract tests migrated from pure Markdown/YAML/JSON/Python doc tests under
app/src/test/java/com/atrainingtracker/trainingtracker/build/ (ATT-8136):
- OnlineUserGuideContractTest (REQ-PRO-033, TST-PRO-033)
- HoechstmassCoreFrameworkContractTest (REQ-PRO-035, TST-PRO-035, TST-PRO-038)
- HoechstmassToolingContractTest (REQ-PRO-036, TST-PRO-036)
- HoechstmassShowcaseAndCommunityContractTest (REQ-PRO-037, TST-PRO-037)
- ReleaseManagerSystemEvidenceContractTest (REQ-PRO-072, TST-PRO-072, REQ-PRO-112, TST-PRO-112)
- SystemRequirementsSpecificationContractTest (REQ-PRO-054, REQ-PRO-055, TST-PRO-054, TST-PRO-055, TST-PRO-131)
- SystemIntegrationTestContractTest (REQ-PRO-068, TST-PRO-068, REQ-PRO-112, TST-PRO-112)
"""

import json
import os
import re
import subprocess
import unittest


REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def _repo_path(rel_path: str) -> str:
    return os.path.join(REPO_ROOT, rel_path)


def _read_repo_file(rel_path: str) -> str:
    with open(_repo_path(rel_path), "r", encoding="utf-8") as fh:
        return fh.read()


class TestOnlineUserGuideContract(unittest.TestCase):
    """TST-PRO-033 / REQ-PRO-033: Online User Guide YAML, chapters, images, and anchors."""

    EXPECTED_CHAPTERS = (
        "01-quick-start.md",
        "02-sensors-dual-protocol.md",
        "03-equipment-sensor-matrix.md",
        "04-live-cockpit-tabs.md",
        "05-maps-routes-climbs-segments.md",
        "06-automatic-route-clustering.md",
        "07-physiology-training-zones.md",
        "08-workout-aftermath-periods.md",
        "09-export-cloud-sync.md",
    )

    def test_jekyll_config_excludes_internal_developer_docs(self):
        content = _read_repo_file("docs/_config.yml")
        for exclusion in (
            "ci-secrets.md",
            "project_protocol.md",
            "coding_standards.md",
            "design_guidelines.md",
            "engineering/",
            "requirements/",
            "tests/",
        ):
            self.assertIn(exclusion, content, f"docs/_config.yml must exclude '{exclusion}'")

    def test_landing_page_and_chapters_exist_for_both_locales(self):
        index_content = _read_repo_file("docs/index.md")
        for tok in ("guide/de/", "guide/en/", "01-quick-start.md", "09-export-cloud-sync.md"):
            self.assertIn(tok, index_content)
        self.assertTrue(os.path.isfile(_repo_path("docs/guide/de/index.md")))
        self.assertTrue(os.path.isfile(_repo_path("docs/guide/en/index.md")))
        for chapter in self.EXPECTED_CHAPTERS:
            for locale in ("de", "en"):
                fp = _repo_path(f"docs/guide/{locale}/{chapter}")
                self.assertTrue(os.path.isfile(fp) and os.path.getsize(fp) > 100, f"Missing/empty: {fp}")

    def test_image_references_and_sizes(self):
        guide_dir = _repo_path("docs/guide")
        md_re = re.compile(r"!\[.*?\]\((.*?)\)")
        html_re = re.compile(r'<img\s+[^>]*src=["\']([^"\']+)["\']', re.IGNORECASE)
        checked = 0
        for root, _, files in os.walk(guide_dir):
            for fn in sorted(files):
                if not fn.endswith(".md"):
                    continue
                fp = os.path.join(root, fn)
                with open(fp, "r", encoding="utf-8") as fh:
                    text = fh.read()
                for ref in md_re.findall(text) + html_re.findall(text):
                    resolved = os.path.realpath(os.path.join(root, ref))
                    self.assertTrue(
                        os.path.isfile(resolved) and os.path.getsize(resolved) > 0,
                        f"Missing image '{ref}' referenced in {fn}: {resolved}",
                    )
                    checked += 1
        self.assertGreaterEqual(checked, 20)

        img_dir = _repo_path("docs/guide/img")
        imgs = [f for f in os.listdir(img_dir) if f.endswith((".png", ".jpg")) and os.path.isfile(os.path.join(img_dir, f))]
        self.assertTrue(imgs)
        oversized = [f for f in imgs if os.path.getsize(os.path.join(img_dir, f)) > 1_000_000]
        self.assertEqual([], oversized, f"Oversized guide images (>1MB): {oversized}")

    def test_anchor_parity_du_pronouns_and_no_hallucinations(self):
        anchor_re = re.compile(r'<a id="(.*?)"></a>')
        for chapter in self.EXPECTED_CHAPTERS:
            de_anchors = set(anchor_re.findall(_read_repo_file(f"docs/guide/de/{chapter}")))
            en_anchors = set(anchor_re.findall(_read_repo_file(f"docs/guide/en/{chapter}")))
            self.assertGreaterEqual(len(de_anchors), 2)
            self.assertEqual(de_anchors, en_anchors, f"Anchor mismatch in {chapter}")

        formal_re = re.compile(r"\b(Sie|Ihnen|Ihre|Ihrem|Ihren|Ihres)\b")
        violations = []
        for root, _, files in os.walk(_repo_path("docs/guide/de")):
            for fn in sorted(files):
                if not fn.endswith(".md"):
                    continue
                with open(os.path.join(root, fn), "r", encoding="utf-8") as fh:
                    for idx, line in enumerate(fh, 1):
                        m = formal_re.search(line)
                        if m:
                            violations.append(f"{fn}:{idx}:{m.group(1)}")
        self.assertEqual([], violations, f"Formal pronouns in DE guide: {violations}")

        forbidden = (
            "TrainingPeaks",
            "ACTIVITY_RECOGNITION",
            "Sprachansagen",
            "Text-to-Speech",
            "1x, 2x, 3x",
            "1x/2x/3x",
            "Eigengewicht des Fahrrads",
            "bike tare weight",
        )
        for root, _, files in os.walk(_repo_path("docs/guide")):
            for fn in sorted(files):
                if not fn.endswith(".md"):
                    continue
                with open(os.path.join(root, fn), "r", encoding="utf-8") as fh:
                    text_lower = fh.read().lower()
                for term in forbidden:
                    self.assertNotIn(term.lower(), text_lower, f"Forbidden term '{term}' in {fn}")


class TestHoechstmassContracts(unittest.TestCase):
    """TST-PRO-035 / TST-PRO-036 / TST-PRO-037 / TST-PRO-038: Höchstmaß framework, tooling, and showcase."""

    def test_core_framework_documentation_and_assets(self):
        """TST-PRO-035 / TST-PRO-038: README, protocol, SVG, triad templates, skills, CL matrix, SPC."""
        readme = _read_repo_file("tools/hoechstmass/README.md")
        protocol = _read_repo_file("tools/hoechstmass/docs/project_protocol.md")
        epigraph = "Power without control can become destructive. Power guided by intelligence becomes excellence."
        for token in (
            "# Höchstmaß — The AAA Coding Framework",
            "Agile · Agentic · ASPICE — Precision Software Engineering with Autonomous AI Pairs",
            epigraph,
            "ChatGPT",
            "Höchst·maß",
            "/ˈhøːçstˌmaːs/",
            "Höchst-",
            "-maß",
            "Maßarbeit",
            "inside out",
            "Swiss Cheese",
            "assets/swiss_cheese_layers.svg",
            "Layer 1",
            "Layer 5",
            "Claude",
            "Stage 1",
            "Stage 5",
            "Entry Barrier",
            "Human Discipline",
        ):
            self.assertIn(token, readme)
        self.assertIn(epigraph, protocol)
        self.assertIn(".reviews/", protocol)
        self.assertIn("Emacs", protocol)

        self.assertTrue(os.path.getsize(_repo_path("tools/review_tool.py")) > 500)
        self.assertTrue(os.path.getsize(_repo_path("tools/hoechstmass/tools/review_tool.py")) > 500)
        svg = _read_repo_file("tools/hoechstmass/assets/swiss_cheese_layers.svg")
        for tok in ("Layer 1", "Layer 5", "Claude", "ChatGPT", "L1", "L2", "L3", "L4", "L5", "✕"):
            self.assertIn(tok, svg)

        rules = _read_repo_file("tools/hoechstmass/.agents/rules/aspice_governance.md")
        for i in range(1, 9):
            self.assertIn(f"Rule {i}", rules)
        self.assertNotIn("com.atrainingtracker", rules)

        for skill in (
            "stage1-analysis", "stage2-req-test-spec", "stage3-impl-plan",
            "stage4-implementation", "stage5-verification", "stage6-final-review",
            "sprint-planner", "jira-workflow", "regression-bisect", "brainstormer", "release-manager",
        ):
            sc = _read_repo_file(f"tools/hoechstmass/.agents/skills/{skill}/SKILL.md")
            self.assertIn(f"name: {skill}", sc)
            self.assertTrue(os.path.isfile(_repo_path(f".agents/skills/{skill}/SKILL.md")))

        for tmpl in (
            "analysis_template.md", "test_spec_template.md", "plan_template.md",
            "walkthrough_template.md", "backlog_ticket_template.md", "sprint_retro_template.md",
        ):
            self.assertGreater(os.path.getsize(_repo_path(f"tools/hoechstmass/templates/{tmpl}")), 100)

        for target in ("tools/hoechstmass/README.md", "tools/hoechstmass/docs/project_protocol.md", "docs/project_protocol.md"):
            txt = _read_repo_file(target)
            for pa in ("PA 1.1", "PA 2.1", "PA 2.2", "PA 3.1", "PA 3.2", "PA 4.1", "PA 4.2", "PA 5.1", "PA 5.2"):
                self.assertIn(pa, txt)
            self.assertIn("[Target / Not Yet Claimed]", txt)

        for st in (".agents/skills/sprint-planner/SKILL.md", "tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md"):
            txt = _read_repo_file(st)
            self.assertIn("sprint-metrics --record --post-to-retro", txt)
            self.assertIn("PA 4.2", txt)
            self.assertIn("PA 5.2", txt)

    def test_parameterized_tooling_and_python_tests(self):
        """TST-PRO-036 / REQ-PRO-036: Config JSON, parameterized scripts, and hoechstmass unittests."""
        cfg = json.loads(_read_repo_file("tools/hoechstmass/hoechstmass_config.json"))
        for key in ("project_key", "transitions", "roles", "custom_fields", "requirements", "paths"):
            self.assertIn(key, cfg)
        for tr in ("todo", "in_progress", "in_review", "done"):
            self.assertIn(tr, cfg["transitions"])

        env_ex = _read_repo_file("tools/hoechstmass/.env.jira.example")
        for var in ("JIRA_URL=", "JIRA_AGENT1_USER=", "JIRA_AGENT2_USER=", "JIRA_COORDINATOR_USER=", "JIRA_HUMAN_ACCOUNT_ID="):
            self.assertIn(var, env_ex)

        jira_hm = _read_repo_file("tools/hoechstmass/tools/jira_util.py")
        self.assertNotIn("project = ATT", jira_hm)
        self.assertNotIn("712020:63ce5f53-e2ba-43f5-9879-edf4a22ff748", jira_hm)
        self.assertIn("mask_secret", jira_hm)

        gov_hm = _read_repo_file("tools/hoechstmass/tools/verify_requirement_governance.py")
        self.assertNotIn("712020:63ce5f53", gov_hm)
        self.assertIn("validate_archaeology_section", gov_hm)
        self.assertIn("verify_master_table_parity", gov_hm)

        hm_tools_dir = _repo_path("tools/hoechstmass/tools")
        proc = subprocess.run(
            ["python3", "-m", "unittest", "discover", "-s", ".", "-p", "test_*.py"],
            cwd=hm_tools_dir,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(0, proc.returncode, f"hoechstmass tests failed:\n{proc.stdout}\n{proc.stderr}")

    def test_showcase_case_studies_and_community_articles(self):
        """TST-PRO-037 / REQ-PRO-037: Android showcase artifacts, case studies, and community articles."""
        showcase = "tools/hoechstmass/examples/android-jetpack-compose"
        self.assertGreater(os.path.getsize(_repo_path(f"{showcase}/README.md")), 200)
        self.assertGreater(os.path.getsize(_repo_path(f"{showcase}/coding_standards.md")), 500)
        self.assertGreater(os.path.getsize(_repo_path(f"{showcase}/design_guidelines.md")), 500)
        for art in (
            "ATT-5111_stage1_analysis.md", "ATT-5111_stage2_test_spec.md",
            "ATT-5111_stage3_impl_plan.md", "ATT-5111_stage4_impl_summary.md",
            "ATT-5111_stage5_walkthrough.md", "sprint_2026-44.1_retrospective.md",
        ):
            self.assertGreater(os.path.getsize(_repo_path(f"{showcase}/artifacts/{art}")), 100)

        cs = _read_repo_file(f"{showcase}/CASE_STUDIES.md")
        for tok in ("Mader-Heck", "ATT-3539", "Radar", "javap"):
            self.assertIn(tok, cs)

        epigraph = "Power without control can become destructive. Power guided by intelligence becomes excellence."
        for art in (
            "r_softwareengineering.md", "r_androiddev.md", "r_google_antigravity.md",
            "r_vibecoding.md", "hackernews_show_hn.md",
        ):
            txt = _read_repo_file(f"tools/hoechstmass/docs/community/{art}")
            self.assertIn(epigraph, txt)
            self.assertIn("ChatGPT", txt)
            self.assertIn("/ˈhøːçstˌmaːs/", txt)
            self.assertIn("Höchst-", txt)
            self.assertIn("-maß", txt)


class TestSystemAndReleaseDocContracts(unittest.TestCase):
    """TST-PRO-054, TST-PRO-055, TST-PRO-068, TST-PRO-072, TST-PRO-112, TST-PRO-131."""

    def test_release_manager_system_evidence_contracts(self):
        """TST-PRO-072 / TST-PRO-112 / REQ-PRO-072 / REQ-PRO-112."""
        skill = _read_repo_file(".agents/skills/release-manager/SKILL.md")
        for tok in ("SYS.5", "REQ-PRO-072", "REQ-PRO-112", "sys_verification_record.md", "verification_summary.md", "docs/releases/", "sys_verification_protocol.md", "INT-SWE-01..08", "BLOCKED"):
            self.assertIn(tok, skill)
        for bad in ("--output-json", "run_system_integration_tests.py", "system_integration_results.json"):
            self.assertNotIn(bad, skill)

        readme = _read_repo_file("docs/releases/README.md")
        for tok in ("SUP.8", "SYS.5", "sys_verification_record.md", "verification_summary.md", "BLOCKED", "retired as a blocking release gate"):
            self.assertIn(tok, readme)
        self.assertNotIn("--output-json", readme)

        tmpl = _read_repo_file("docs/releases/TEMPLATE.md")
        for tok in ("Release Qualification Dossier", "SYS.5", "sys_verification_record.md", "verification_summary.md", "Sign-Off"):
            self.assertIn(tok, tmpl)
        for bad in ("system_integration_results.json", "run_system_integration_tests.py"):
            self.assertNotIn(bad, tmpl)

        arch = _read_repo_file("docs/architecture.md")
        for tok in ("### 6.2", "### 6.4", "REQ-PRO-072", "TST-PRO-072", "REQ-PRO-112", "release-manager/SKILL.md", "docs/releases/"):
            self.assertIn(tok, arch)
        self.assertNotIn("run_system_integration_tests.py", arch)

    def test_system_requirements_specification_contracts(self):
        """TST-PRO-054 / TST-PRO-055 / TST-PRO-131 / REQ-PRO-054 / REQ-PRO-055."""
        content = _read_repo_file("docs/system_requirements.md")
        self.assertIn("## 1. System Requirements Overview & Architecture", content)
        self.assertIn("## 2. Live Tracking with Sensors System Requirements (Summary)", content)
        self.assertIn("## 3. Data Exchange and Cloud Integration System Requirements (SYSREQ-DEX-001..008)", content)
        self.assertIn("## 4. Complete System Requirements Traceability Matrix", content)
        for i in range(1, 14):
            self.assertIn(f"SYSREQ-TRK-{i:03d}", content)
        for i in range(1, 9):
            self.assertIn(f"SYSREQ-DEX-{i:03d}", content)

        refs = set(re.findall(r"`([A-Za-z0-9_]+\.(?:kt|java|xml))`", content)) - {"auth_credentials_prefs.xml"}
        disk_names = set()
        for root, _, files in os.walk(REPO_ROOT):
            if ".git" in root or os.sep + "build" + os.sep in root:
                continue
            disk_names.update(files)
        self.assertEqual([], sorted(refs - disk_names))

        req_part1 = _read_repo_file("docs/requirements/req_process_stability_part1.md")
        req_part2 = _read_repo_file("docs/requirements/req_process_stability_part2.md")
        req_all = req_part1 + "\n" + req_part2
        for tok in ("REQ-PRO-055", "sysreq_data_exchange.md", "SYSREQ-DEX-001", "SYSREQ-DEX-006"):
            self.assertIn(tok, req_all)

    def test_system_integration_test_contracts(self):
        """TST-PRO-068 / TST-PRO-112 / REQ-PRO-068 / REQ-PRO-112."""
        content = _read_repo_file("docs/system/sys_integration_tests.md")
        for tok in (
            "REQ-PRO-068", "TST-PRO-068", "REQ-PRO-112", "```mermaid", "graph TD",
            "Environment A", "Environment B", "Android APK", "INT-SWE-01..08",
            "SIM-HR-001", "SIM-PWR-001", "SIM-RDR-001", "SIM-GPS-001", "SIM-RPL-001",
            "SYS-IF-01", "SYS-IF-02", "SYS-IF-03", "SYS-IF-05",
            "0x180D", "0x1818", "6a4e3200", "com.dsi.ant.plugins.antplus",
            "LocationManager.GPS_PROVIDER", "Strava",
            "SYSREQ-TRK-001", "SYSREQ-DEX-001", "SYS-EL-01", "SYS-EL-05",
            "blocking release gate",
        ):
            self.assertIn(tok, content)
        for bad in ("run_system_integration_tests.py", "docs/releases/<tag>/system_integration_results.json"):
            self.assertNotIn(bad, content)

        for tool_rel in (
            "tools/run_simulator_self_tests.py",
            "tools/test_simulator_self_tests.py",
            "tools/ble_hr_simulator.py",
            "tools/ble_power_simulator.py",
            "tools/ble_radar_simulator.py",
            "tools/fake_gps.py",
            "tools/replay_workout.py",
        ):
            self.assertTrue(os.path.isfile(_repo_path(tool_rel)), f"Missing {tool_rel}")
        self.assertFalse(os.path.exists(_repo_path("tools/run_system_integration_tests.py")))
        self.assertFalse(os.path.exists(_repo_path("tools/test_system_integration_tests.py")))


if __name__ == "__main__":
    unittest.main()
