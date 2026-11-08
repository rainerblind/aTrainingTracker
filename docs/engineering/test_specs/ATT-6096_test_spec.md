# Stage 2: Requirement & Test Specification - ATT-6096: Root README.md: Replace Peaksware trademarks, add BLE Power Meters / Known Locations / Strava ZIP import & embed modern app screenshots

**Ticket**: [ATT-6096](https://rainerblind.atlassian.net/browse/ATT-6096)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-STP-005` (*Repository README Google Play Store Badge, Feature Accuracy & Screenshot Showcase*)  
**Test Spec ID**: `TST-STP-005`  
**Branch**: `feature/ATT-6096`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-STP-005)

### 1.1 Problem Statement & Rationale
`README.md` and `.reviews/README.md` contain registered Peaksware trademarks (`Normalized Power`, `NP`, `Intensity Factor`, `IF`, `Training Stress Score`, `TSS`, `PMC`, `CTL`, `ATL`, `TSB`), omit BLE Power Meters (`0x1818`), cite obsolete formats (`.gc`) while omitting Strava `.zip` and Full-App backup, omit Climb Detection and Known Locations (*Lieblingsorte*), and lack modern Jetpack Compose Material 3 screenshots.

### 1.2 Functional & Architectural Requirements
* **REQ-STP-005.1 (Hero Identity & Navigation)**: Root `README.md` SHALL feature application logo, title, subtitle, official Play Store badge linking to canonical store URL (`id=com.atrainingtracker`), and bilingual User Guide links.
* **REQ-STP-005.2 (Screenshot Showcase)**: SHALL embed a responsive 4-screenshot showcase row from `docs/guide/img/` (`01_cockpit_standard.png`, `08_workout_detail.png`, `06_cluster_expanded.png`, `07_telemetry_charts.png`).
* **REQ-STP-005.3 (Trademark-Free Physiology)**: SHALL strictly replace Peaksware trademarks with: Weighted Power (`WP`), Relative Intensity (`RI`), Training Load (`Load`), Variability Index (`VI`), Skiba $W'_{\text{bal}}$, Efficiency Factor (`EF`), Aerobic Decoupling (`Pw:Hr`), and Training Load & Form (`Fitness 42d`, `Fatigue 7d`, `Form`).
* **REQ-STP-005.4 (Sensors, Formats & Cloud)**: Bluetooth LE SHALL list Cycling Power Meters (`0x1818`, pedal balance). Import/export SHALL list native `.FIT`, `.TCX`, `.GPX`, `.CSV`, Strava Bulk Archive (`.zip`), and Full-App ZIP backup (`.attbackup`), omitting `.gc`. Cloud analysis SHALL reference Intervals.icu and GoldenCheetah.
* **REQ-STP-005.5 (Navigation & Geofences)**: Navigation SHALL describe on-device & imported Live Segments, Climb Detection, and Known Locations (*Lieblingsorte*) auto-naming.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Refines `REQ-STP-005` (*Repository README Google Play Store Badge & User Guide Navigation Integration*). Targets `README.md`, `.reviews/README.md`, `images/google-play-badge.svg`, and `ReadmePlayStoreBadgeContractTest.kt`.
* `Historical Origin & Commit Trace`: Commit `0bcdf317` (`ATT-2371`) established Play Store badge & user guide links; `ATT-5678` purged trademarks from app strings/guides but omitted root `README.md`.
* `Root Reason for Existing Formulation`: Initial V5.0 migration focused on Play Store badge and docs navigation without auditing legacy physiology trademarks or recent BLE power/ZIP additions.
* `Preservation of Core Invariants`: Centered hero logo (`images/logo_512.png`), vector Play Store badge (`images/google-play-badge.svg`) linking to canonical package `com.atrainingtracker`, and bilingual user guide links remain 100% intact.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Trademark-Free Terminology)**:
  * *Given* root `README.md` and `.reviews/README.md`,
  * *When* scanning for Peaksware trademarks (`Normalized Power`, `NP`, `Intensity Factor`, `IF`, `Training Stress Score`, `TSS`, `PMC`, `CTL`, `ATL`, `TSB`) or removed `.gc` formats,
  * *Then* zero occurrences are detected.
* **Criterion 2 (Modern Screenshot Showcase & Links)**:
  * *Given* `README.md`,
  * *When* resolving embedded screenshot and document links,
  * *Then* all 4 screenshots reference `docs/guide/img/` and exist on disk with valid PNG headers; user guide links resolve without broken paths.
* **Criterion 3 (Accurate Hardware & Features)**:
  * *Given* `README.md`,
  * *When* inspecting sensor, import, and navigation sections,
  * *Then* BLE Power Meters, Strava Bulk `.zip`, Full-App backup, Known Locations (*Lieblingsorte*), and Climb Detection are accurately described.

### 1.5 System Invariants
* Logo (`images/logo_512.png`), vector Play Store badge (`images/google-play-badge.svg`), and package ID `com.atrainingtracker` remain intact.
* All links resolve to local files or canonical external URLs.
* Contract test suite executes in CI with 100% pass rate.

---

## 2. Test Specification (TST-STP-005)

### Test Case 1: Contract Assertions on README.md (`TST-STP-005.1` - `TST-STP-005.4`)
* **Scope**: Unit / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReadmePlayStoreBadgeContractTest.kt`
* **Preconditions**: `README.md` and `docs/guide/img/*.png` exist in repository.
* **Action**: Execute enhanced tests asserting absence of trademarks, presence of BLE Power/ZIP/Lieblingsorte keywords, and existence of all 4 referenced screenshots.
* **Expected Result**: Assertions pass; 0 trademarks found, all image files resolve on disk.

### Test Case 2: Tier 1 Targeted Regression Suite (`TST-STP-005.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReadmePlayStoreBadgeContractTest"`
* **Expected Result**: 100% pass rate in CI.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-STP-005.1` | Contract | `testGooglePlayBadgeAsset_existsAndHasValidSvgStructure` | `REQ-STP-005.1` | Specified |
| `TST-STP-005.2` | Contract | `testReadme_containsValidUserGuideNavigationLinks` | `REQ-STP-005.1` | Specified |
| `TST-STP-005.3` | Contract | `testReadme_embedsScreenshotShowcaseResolvingOnDisk` | `REQ-STP-005.2` | Specified |
| `TST-STP-005.4` | Contract | `testReadme_containsZeroPeakswareTrademarksAndAccurateFeatures` | `REQ-STP-005.3`, `REQ-STP-005.4`, `REQ-STP-005.5` | Specified |
| `TST-STP-005.5` | Regression | `ReadmePlayStoreBadgeContractTest` (Targeted Suite) | `REQ-STP-005` | Specified |
