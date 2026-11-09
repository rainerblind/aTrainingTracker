# Stage 1 Analysis: ATT-6096 - Root README.md: Replace Peaksware trademarks, add BLE Power Meters / Known Locations / Strava ZIP import & embed modern app screenshots

**Ticket**: [ATT-6096](https://rainerblind.atlassian.net/browse/ATT-6096)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Branch**: `feature/ATT-6096`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
The root `README.md` (and its `.reviews/README.md` mirror) serves as the primary gateway on GitHub. It has drifted out of sync with production capabilities in five areas:
1. **Trademark Infringement**: Retains Peaksware/TrainingPeaks trademarks (`Normalized Power`, `NP`, `Intensity Factor`, `IF`, `Training Stress Score`, `TSS`, `PMC`, `CTL`, `ATL`, `TSB`).
2. **Missing BLE Power Meters**: Line 46 omits Power Meters (`0x1818`, pedal power balance).
3. **Outdated Formats**: Mentions removed GoldenCheetah `.gc` and legacy *WKO+*; omits Strava Bulk Archive (`.zip`) and Full-App ZIP Backup & Restore.
4. **Outdated Navigation**: Cites obsolete `"Strava segment racing"`; omits on-device/imported Live Segments, Climb Detection, and Known Locations (*Lieblingsorte*) auto-naming.
5. **Missing Compose UI Showcase**: Displays only 2012 legacy ANT+ icons; lacks modern Jetpack Compose Material 3 screenshots.

---

## 2. Root Cause Analysis, Independent Verification & Code-Grounding (ATT-5431)
* **Hypothesis Verification**:
  * Verified root `README.md` and `.reviews/README.md` lines 26, 39, 46, 48, 50, 52: all 5 discrepancies confirmed.
  * Verified screenshot asset path: The ticket prompt references `docs/guide/en/images/`, but code-grounding forensics reveal the canonical directory is `docs/guide/img/` (containing verified screenshots `01_cockpit_standard.png`, `08_workout_detail.png`, `06_cluster_expanded.png`, `07_telemetry_charts.png`). `README.md` must link to `docs/guide/img/*.png`.
* **Documentation Code-Grounding Audit (ATT-5431)**:
  * **Physiology Trademarks (`ATT-5678`)**: Replaced by Weighted Power (`WP`), Relative Intensity (`RI`), Training Load (`Load`), Training Load & Form (`Fitness 42d`, `Fatigue 7d`, `Form`). Grounded in `app/src/main/res/values/strings.xml` and `PowerPhysiologyCalculator.kt`.
  * **BLE Power Meters**: Grounded in `BTLEBikePowerDevice.java` (`0x1818` Cycling Power Service, pedal balance, torque).
  * **Strava & Full-App ZIP**: Grounded in `StravaBulkExportImporter.kt` (`activities.csv`, `.fit.gz`) and `BackupManager.kt` (`.attbackup` ZIP).
  * **Lieblingsorte & Climbs**: Grounded in `WorkoutAutoNamingHelper.kt`, `KnownLocationsGeometryMath.kt`, `LiveClimbsRepository.kt`, and `ClimbDetector.kt`.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope Goals**:
  1. Update root `README.md` and `.reviews/README.md` to eliminate all Peaksware trademarks and align terminology with `ATT-5678`.
  2. Document BLE Power Meters, Strava ZIP archive import, Full-App ZIP Backup & Restore, Intervals.icu, and GoldenCheetah.
  3. Document Known Locations (*Lieblingsorte*) auto-naming, Climb Detection, and on-device Live Segments.
  4. Embed responsive 4-screenshot showcase row referencing `docs/guide/img/` (`01_cockpit_standard.png`, `08_workout_detail.png`, `06_cluster_expanded.png`, `07_telemetry_charts.png`) visually inspected via `read_file` (Rule 71).
  5. Update `REQ-STP-005` in `docs/requirements/req_project_governance.md` & `TST-STP-005` in `docs/tests/tst_project_governance.md`.
  6. Extend `ReadmePlayStoreBadgeContractTest.kt` to assert trademark absence, screenshot asset existence, and feature coverage.
* **Out-of-Scope**: Code modifications to tracking/export engines; creating new screenshot image files.
* **Complexity Rating**: **1 SP (Trivial / Documentation Refinement)**.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-STP-005` (*Repository README Google Play Store Badge & User Guide Navigation Integration*), originally created in `ATT-2371`.
* **Historical Origin & Commit Trace**: Commit `0bcdf317` (`ATT-2371`) integrated Play Store badge and user guide navigation. `ATT-5678` purged trademarks from app strings and user guides, but omitted root `README.md`.
* **Root Reason for Existing Formulation**: `README.md` was drafted early in project history and only partially updated during V5.0 migration.
* **Preservation of Core Invariants**: Logo (`images/logo_512.png`), canonical Play Store badge (`images/google-play-badge.svg`), and user guide navigation links (`docs/index.md`, `docs/guide/de/index.md`, `docs/guide/en/index.md`) MUST remain strictly intact per `TST-STP-005`.

---

## 5. Architectural Strategy & Test Verification
* **Markdown Updates**: Synchronize `README.md` and `.reviews/README.md` with responsive screenshot table and grounded feature list.
* **Contract Test**: Enhance `ReadmePlayStoreBadgeContractTest.kt` with assertions for trademark prohibition, screenshot links validation against disk, and BLE Power/ZIP/Lieblingsorte mentions.
* **Living Documentation**: Synchronize `req_project_governance.md` and `tst_project_governance.md` via `verify_requirement_governance.py`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `ReadmePlayStoreBadgeContractTest` passes 100% without breaking existing badge/logo/link assertions.
  2. All 4 embedded screenshot links resolve to existing, verified image files under `docs/guide/img/`.
  3. Zero Peaksware trademarks (`Normalized Power`, `NP`, `Intensity Factor`, `IF`, `Training Stress Score`, `TSS`, `PMC`, `CTL`, `ATL`, `TSB`).
* **Risk Rating**: **LOW**. Documentation and contract test updates only; zero production code or schema changes.
