# Stage 5: Walkthrough & Verification - ATT-5418: Online User Guide Layout & Accuracy

**Ticket**: [[ATT-5418]](https://rainerblind.atlassian.net/browse/ATT-5418)  
**Sub-task**: [[ATT-5466]](https://rainerblind.atlassian.net/browse/ATT-5466) (`[Test]`)  
**Parent Epic**: [[ATT-2273]](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5418`  
**Requirement Mapping**: `REQ-PRO-033` (`REQ-PRO-033.5`) | **Test Mapping**: `TST-PRO-033` (`TST-PRO-033.1..5`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-05  

---

## 1. Executive Summary & Verification Overview
Resolved all 3 defects in the Online User Guide (`docs/guide/`):
1. **2-Column Layout**: Transformed all 18 chapters (`de/01..09.md`, `en/01..09.md`) into HTML `<table>` side-by-side layouts (`width="65%"` text, `width="35%"` screenshot with `width="240"` clickable thumbnail).
2. **Du-Anrede**: Converted all German files to informal personal "Du" (verified 0 formal pronouns via automated regex test).
3. **Screenshot Authenticity (Rule 71)**: Replaced 13 defective/misplaced screenshots with authentic Pixel 10 app captures (`read_file` visual audit confirmed; 4.2 MB cluster asset reduced to 576 KB).
4. **Factual Parity**: Eliminated hallucinated features (OSM offline vectors, 1x/2x/3x widths, TTS, `ACTIVITY_RECOGNITION`, bike tare weight, TrainingPeaks).
5. **Contract Suite**: `OnlineUserGuideContractTest.kt` extended and verified 100% PASS.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope & Method | Result | Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-033.1..2` | `TST-PRO-033.1..2` | Jekyll exclusions & 18-chapter file completeness | **PASSED** | `Verified` |
| `REQ-PRO-033.3..4` | `TST-PRO-033.3..4` | 50 image assets resolution & 1:1 anchor parity | **PASSED** | `Verified` |
| `REQ-PRO-033.5` | `TST-PRO-033.5` | HTML `<img>` parsing, Du-Anrede, size $\le 1$ MB | **PASSED** | `Verified` |
| `REQ-PRO-022` | `TST-PRO-015` | `python3 tools/verify_requirement_governance.py` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence (Tier 1 Targeted Verification)

* **Explicit Test Scope**: **Tier 1 targeted verification**: `com.atrainingtracker.trainingtracker.build.*` (`OnlineUserGuideContractTest`) and requirement governance script.
* **Execution Results**:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`: **BUILD SUCCESSFUL** (32 actionable tasks, 0 failures).
  - `python3 tools/verify_requirement_governance.py`: **PASS** (Zero governance violations).

---

## 4. Visual Asset Audit & Rule 71 Inspection

* **Rule 71 Mandatory Visual Audit**: All 13 replacement screenshots in `docs/guide/img/` (`02_sensor_pairing.png`, `02_dual_protocol.png`, `03_equipment_bikes.png`, `03_equipment_shoes.png`, `04_cockpit_tuning.png`, `05_gpx_routes.png`, `06_cluster_expanded.png`, `07_training_zones.png`, `07_telemetry_charts.png`, `08_periods_screen.png`, `08_periods_cards.png`, `08_yearly_activity_grid.png`, `09_fit_export.png`) were inspected via `read_file`.
* **Findings**: 0% developer boards, 0% private accounts/NAS icons, 0% status bar overlaps, 0% scrolled-off grids, and authentic UI widgets throughout.

---

## 5. Invariants & Release Governance

1. **Zero Runtime APK Regression**: `app/src/main/...` contains 0 modifications.
2. **Living Docs Updated**: `REQ-PRO-033` and `TST-PRO-033` updated to `Verified`.
3. **FixVersion Governance**: Target release `V5.0.0` confirmed from Jira active version.
