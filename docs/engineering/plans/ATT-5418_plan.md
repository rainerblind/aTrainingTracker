# Stage 3: Implementation Plan - ATT-5418: Online User Guide Layout & Accuracy

**Ticket**: [[ATT-5418]](https://rainerblind.atlassian.net/browse/ATT-5418)  
**Sub-task**: [[ATT-5464]](https://rainerblind.atlassian.net/browse/ATT-5464) (`[Impl-Plan]`)  
**Parent Epic**: [[ATT-2273]](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5418`  
**Requirement Mapping**: `REQ-PRO-033` (`REQ-PRO-033.5`) | **Test Mapping**: `TST-PRO-033` (`TST-PRO-033.1..5`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-05  

---

## 1. Problem Description & Background
Online User Guide (`docs/guide/`, Ch. 1–9 `de` & `en`) has 3 core flaws: (1) oversized 9:20 portrait images breaking reading flow, (2) 12 invalid/misplaced/bloated screenshots in `docs/guide/img/`, and (3) formal German "Sie" tone alongside hallucinated features (TTS, OSM offline vectors, 1x/2x/3x widths, bike tare weight, TrainingPeaks).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-033.5` (*Responsive 2-Column Layout, Du-Anrede & Screenshot Authenticity*)
* **Test Mapping**: `TST-PRO-033.1..5` (Jekyll exclusion, chapter parity, asset resolution, anchor parity, contract tests)

---

## 3. System Invariants & Preserved Behavior
1. **Bilingual Parity**: Exact 1:1 anchor parity (`<a id="...">`) and 18 chapter files preserved for in-app deep linking.
2. **Build Hygiene**: Zero runtime app code modified (`app/src/main/...` untouched); Jekyll exclusion rules preserved.
3. **Asset Integrity**: All images $\le 1\text{ MB}$, authentic app UI, 0% developer boards/private data (Rule 71).
4. **Parent Gate**: Advances parent to `Final Review (Human)` only at Stage 5; no auto-resolve.

---

## 4. Proposed Architectural Changes
* **Layout Structure**: 2-column HTML table layout (`width="65%" valign="top" markdown="1"` left, `width="35%" valign="top" align="center"` right with `<a href="../img/..."><img src="../img/..." width="240" alt="..." /></a><br/><sub><em>...</em></sub>`).
* **Test Contract**: Extend `OnlineUserGuideContractTest.kt` with HTML `<img>` tag parsing, German "Du" audit, asset file size validation, and forbidden hallucinated terms check.
* **UI Consistency**: No Compose UI changes. Online documentation adheres to Design Guidelines §1–§6.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Replace & Verify 12 Screenshot Assets (`docs/guide/img/`)
* Capture clean screens on connected Pixel 10 (`com.atrainingtracker.debug` via ADB) / verified repository assets:
  - `02_sensor_pairing.png`: Meine Sensoren pairing screen (replaces crash dialog).
  - `02_dual_protocol.png`: Sensor detail / BLE & ANT+ status (replaces dev board).
  - `03_equipment_bikes.png` & `03_equipment_shoes.png`: Ausrüstung Räder & Laufschuhe (replace auto-pause).
  - `04_cockpit_tuning.png`: Display & Auto-Dimmer settings (replaces tips cards).
  - `05_gpx_routes.png`: Route selection bottom sheet / Meine Routen (replaces start tab).
  - `06_cluster_expanded.png`: Lieblingsstrecken overview list / map (replaces 4.2 MB car ride).
  - `07_training_zones.png` & `07_telemetry_charts.png`: Profil & Zonen + PMC chart (replace single workout detail).
  - `08_periods_screen.png`: Relocate to Ch 8.2; in 8.1 use workout detail overview / charts.
  - `08_periods_cards.png`: Clean Perioden card without status bar overlap.
  - `08_yearly_activity_grid.png`: Zeiträume -> Jahre with YearlyContributionGrid prominently visible.
  - `09_fit_export.png`: Export format settings dialog (.fit, .tcx, .gpx, .csv) (replaces private file manager).
* Visually inspect every image via `read_file` (Rule 71).

### Step 2: Modernize German & English Chapters (1–9 & index.md)
* Apply 2-column layout (`width="65%"` text, `width="35%"` screenshot `width="240"`).
* Rewrite German chapters to consistent "Du"-Anrede; correct all hallucinated features across DE & EN.

### Step 3: Extend `OnlineUserGuideContractTest.kt` & Run Targeted Unit Tests
* Update test to parse HTML `<img src="..." width="240">` tags, verify file existence, "Du"-Anrede, and forbidden term absence.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.OnlineUserGuideContractTest" && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: `OnlineUserGuideContractTest` passes 100%; Rule 71 visual inspection of all images.
* **Rollback**: `git checkout feature/ATT-5418 -- docs/` restores previous manual state cleanly.
