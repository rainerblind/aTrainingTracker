# Stage 3: Implementation Plan - ATT-6096: Root README.md Modernization & Trademark Purge

**Ticket**: [ATT-6096](https://rainerblind.atlassian.net/browse/ATT-6096)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-STP-005` (`REQ-STP-005.1`–`REQ-STP-005.5`)  
**Test Mapping**: `TST-STP-005` (`TST-STP-005.1`–`TST-STP-005.5`)  
**Branch**: `feature/ATT-6096`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Background
The root `README.md` and `.reviews/README.md` contain legacy Peaksware trademarks (`Normalized Power`, `NP`, `Intensity Factor`, `IF`, `Training Stress Score`, `TSS`, `PMC`, `CTL`, `ATL`, `TSB`), omit BLE Power Meters (`0x1818`), cite removed formats (`.gc`) while omitting Strava `.zip` and Full-App backup, omit Climb Detection and Known Locations (*Lieblingsorte*), and lack modern Jetpack Compose Material 3 screenshots. This plan outlines the atomic steps to synchronize both files and enforce the invariants via automated contract tests.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-STP-005` (`REQ-STP-005.1` Hero/Navigation, `REQ-STP-005.2` Showcase, `REQ-STP-005.3` Trademark-Free, `REQ-STP-005.4` Hardware/Formats, `REQ-STP-005.5` Navigation/Locations).
* **Test Mapping**: `TST-STP-005` (`TST-STP-005.1`–`TST-STP-005.5` in `ReadmePlayStoreBadgeContractTest.kt`).

---

## 3. System Invariants & Preserved Behavior
1. **Hero Identity & Navigation**: Logo (`images/logo_512.png`), vector Play Store badge (`images/google-play-badge.svg`) linking to canonical package `com.atrainingtracker`, and bilingual user guide links remain 100% intact.
2. **Asset Path Fidelity**: Embedded screenshot links must strictly resolve to verified files in `docs/guide/img/` (`01_cockpit_standard.png`, `08_workout_detail.png`, `06_cluster_expanded.png`, `07_telemetry_charts.png`) per Rule 71.
3. **Zero Trademark Leaks**: 0 Peaksware trademarks (`Normalized Power`, `NP`, `Intensity Factor`, `IF`, `Training Stress Score`, `TSS`, `PMC`, `CTL`, `ATL`, `TSB`) across `README.md` and `.reviews/README.md`.
4. **Mirror Parity**: Root `README.md` and `.reviews/README.md` remain 1:1 synchronized.

---

## 4. Proposed Architectural Changes
* **UI & Documentation Presentation**: Responsive 4-column screenshot table embedded beneath the user guide navigation links, providing clickable high-resolution image links with clear descriptive captions.
* **Feature List Realignment**: Update bullets for BLE Power Meters, Strava Bulk `.zip`, Full-App backup (`.attbackup`), Intervals.icu, GoldenCheetah, Known Locations, Climb Detection, and Live Segments.
* **Automated Contract Test**: Extend `ReadmePlayStoreBadgeContractTest.kt` with assertions for trademark prohibition, screenshot links validation against disk, and capability keyword presence.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Root README.md & .reviews/README.md Content Synchronization
* Files: `README.md`, `.reviews/README.md`
* Changes:
  1. Embed responsive 4-screenshot showcase row referencing `docs/guide/img/01_cockpit_standard.png`, `08_workout_detail.png`, `06_cluster_expanded.png`, `07_telemetry_charts.png`.
  2. Replace Peaksware trademarks with trademark-free terms (`WP`, `RI`, `Load`, `Fitness 42d`, `Fatigue 7d`, `Form`).
  3. Add BLE Power Meters (`0x1818`, pedal power balance) to Bluetooth LE list.
  4. Replace WKO+ with Intervals.icu & GoldenCheetah; add Strava Bulk Archive (`.zip`) & Full-App ZIP backup, removing `.gc`.
  5. Add Known Locations (*Lieblingsorte*) auto-naming, Climb Detection, and Live Segments.

### Step 2: ReadmePlayStoreBadgeContractTest Enhancement
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReadmePlayStoreBadgeContractTest.kt`
* Changes:
  1. Add `testReadme_embedsScreenshotShowcaseResolvingOnDisk()` verifying all 4 screenshots exist on disk and have valid PNG headers.
  2. Add `testReadme_containsZeroPeakswareTrademarksAndAccurateFeatures()` verifying trademark absence and required feature strings in both `README.md` and `.reviews/README.md`.

### Step 3: Targeted Unit Tests & Governance Verification
* Commands:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReadmePlayStoreBadgeContractTest"`
  - `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: `ReadmePlayStoreBadgeContractTest` passes 100%; `verify_requirement_governance.py` reports zero anomalies.
* **Rollback**: Clean branch isolation on `feature/ATT-6096`.
