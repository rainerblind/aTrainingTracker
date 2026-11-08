# Stage 5: Walkthrough & Verification - ATT-6096: Root README.md Modernization & Trademark Purge

**Ticket**: [ATT-6096](https://rainerblind.atlassian.net/browse/ATT-6096)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-STP-005` (`REQ-STP-005.1`–`REQ-STP-005.5`)  
**Test Mapping**: `TST-STP-005` (`TST-STP-005.1`–`TST-STP-005.5`)  
**Branch**: `feature/ATT-6096`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-08  

---

## 1. Executive Summary & Verification Overview
Root `README.md` and `.reviews/README.md` have been updated in 1:1 synchronization:
* **Trademark Purge**: Zero Peaksware/TrainingPeaks trademarks remaining; replaced with Weighted Power (`WP`), Relative Intensity (`RI`), Training Load (`Load`), `VI`, Skiba $W'_{\text{bal}}$, `EF`, `Pw:Hr`, and Training Load & Form (`Fitness 42d`, `Fatigue 7d`, `Form`).
* **Hardware & Capabilities**: BLE Power Meters (`0x1818`, including Pedal Power Balance), Strava Bulk Archive (`.zip`), Full-App ZIP Backup & Restore (`.attbackup`), Intervals.icu, GoldenCheetah, Known Locations (*Lieblingsorte*), Climb Detection, and Live Segments documented.
* **Modern Screenshots Showcase**: Responsive 4-screenshot row embedded referencing `docs/guide/img/`. All 4 images visually verified via `read_file` (`Rule 71`).
* **Contract Tests**: `ReadmePlayStoreBadgeContractTest.kt` extended with screenshot existence/header checks and trademark/feature assertions.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-STP-005.1` | `TST-STP-005.1`, `TST-STP-005.2` | `testGooglePlayBadgeAsset_existsAndHasValidSvgStructure`, `testReadme_containsValidUserGuideNavigationLinks` | **PASSED** | `Verified` |
| `REQ-STP-005.2` | `TST-STP-005.3` | `testReadme_embedsScreenshotShowcaseResolvingOnDisk` | **PASSED** | `Verified` |
| `REQ-STP-005.3` | `TST-STP-005.4` | `testReadme_containsZeroPeakswareTrademarksAndAccurateFeatures` (Trademarks) | **PASSED** | `Verified` |
| `REQ-STP-005.4` | `TST-STP-005.4` | `testReadme_containsZeroPeakswareTrademarksAndAccurateFeatures` (Sensors/Formats) | **PASSED** | `Verified` |
| `REQ-STP-005.5` | `TST-STP-005.4` | `testReadme_containsZeroPeakswareTrademarksAndAccurateFeatures` (Nav/Locations) | **PASSED** | `Verified` |
| `REQ-STP-005` | `TST-STP-005.5` | Tier 1 Targeted Suite (`com.atrainingtracker.trainingtracker.build.*`, `doc.*`) | **PASSED** | `Verified` |

---

## 3. Test Evidence & Scope

### Tier 1 Targeted Test Scope (Rule 60)
* **Scope**: `--tests "com.atrainingtracker.trainingtracker.build.*"` and `--tests "com.atrainingtracker.trainingtracker.doc.*"`
* **Command**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.doc.*"`
* **Result**: `BUILD SUCCESSFUL` across 32 actionable tasks; 100% pass rate in CI.
* **Governance Verification**: `python3 tools/verify_requirement_governance.py` reported 100% master-shard parity and valid Chesterton's Fence archaeology.

---

## 4. Visual Screenshot Inspection (Rule 71 Certification)
All 4 screenshots embedded in `README.md` were visually inspected via `read_file`:
1. `docs/guide/img/01_cockpit_standard.png`: Live Telemetry Cockpit (verified UI components, zone distribution, bike bell).
2. `docs/guide/img/08_workout_detail.png`: Workout Retrospective & Zones (verified HR and Power histogram distribution).
3. `docs/guide/img/06_cluster_expanded.png`: Favorite Routes / Lieblingsstrecken list (verified route clusters and stats).
4. `docs/guide/img/07_telemetry_charts.png`: Athlete Profile / Training Load & Form (verified ACWR, PMC curve, history).

Zero runtime bytecode changes to APK tracking or services; no physical device deployment required.

---

## 5. Invariant & Governance Verification
1. **Play Store Badge & Links**: Google Play Store badge vector asset, canonical package ID (`id=com.atrainingtracker`), and bilingual user guide links intact.
2. **Parity**: `README.md` and `.reviews/README.md` are 1:1 synchronized (zero diff).
3. **Living Docs**: `REQ-STP-005` and `TST-STP-005` are marked `[Verified]`.
