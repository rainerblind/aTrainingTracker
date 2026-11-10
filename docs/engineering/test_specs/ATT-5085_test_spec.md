# Stage 2: Requirement & Test Specification - ATT-5085: Live Rearview Radar Vertical Edge Overlay

**Ticket**: [ATT-5085](https://rainerblind.atlassian.net/browse/ATT-5085) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-5085`  
**Requirement Mapping**: `REQ-TRK-062` | **Test Spec ID**: `TST-TRK-062`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-TRK-062)

### 1.1 Problem Statement & Rationale
Cyclists operating in traffic require peripheral visual situational awareness across all tracking tabs (Cockpit grids, Live Map, Elevation/Climb charts, Zone screens). `RadarEdgeOverlay` observes `RadarStateRepository.radarState` to render a high-contrast vertical edge rail with animated vehicle dots (0–140m / 175m) and semantic threat colors. The overlay must preserve full gesture pass-through for `HorizontalPager` swipes and Map gestures, enforce fail-safe disconnect visualization (strictly preventing false "All Clear" signals during packet loss or disconnect), and integrate customizable edge placement and clear-state behavior in `TuningPreferencesDataStore`.

### 1.2 Functional & Architectural Requirements
* **REQ-TRK-062.1** (*Vertical Edge Rail & Dot Interpolation*): `RadarEdgeOverlay` SHALL render a vertical rail (24–28 dp width) on the configured screen edge, displaying approaching vehicle dots with smooth 60 fps position interpolation ($y = 1 - d/d_{\max}$).
* **REQ-TRK-062.2** (*Semantic Threat Level Visualization*): Target dots and rail accents SHALL reflect semantic threat levels: Green for clear road, Amber for `APPROACHING`, and pulsing Red for `FAST_APPROACHING`.
* **REQ-TRK-062.3** (*Touch & Gesture Pass-Through*): The vertical rail container SHALL NOT consume touch or scroll gestures, allowing unhindered `HorizontalPager` swiping, Google Map panning/zooming, and Android system edge gestures.
* **REQ-TRK-062.4** (*Fail-Safe Disconnect & Error State*): When radar connection state is `DISCONNECTED_ERROR` or `DISCONNECTED`, the overlay SHALL NOT render a green "All Clear" state; it SHALL display an amber/grey warning badge with disconnected status.
* **REQ-TRK-062.5** (*User Customization & DataStore Integration*): `TuningPreferencesDataStore` SHALL persist `radarEdgeSide` (`LEFT`, `RIGHT`, default `RIGHT`) and `radarClearMode` (`SLIM_RAIL`, `BADGE_ONLY`, `AUTO_HIDE_3S`, default `SLIM_RAIL`), exposed in `RadarSettingsSection.kt`.
* **REQ-TRK-062.6** (*9-Language Localization Parity*): All overlay settings and status labels in `strings_radar.xml` SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (zero inline emojis).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* an active tracking session with a connected radar, *When* vehicles approach at 80m and 30m, *Then* the edge overlay renders distinct animated dots at proportional vertical heights with threat-appropriate colors.
* *Given* a user swiping horizontally across the vertical rail, *When* gestured, *Then* `HorizontalPager` page transitions occur without gesture interruption or drag locking.
* *Given* a radar packet starvation timeout (3.5s), *When* `connectionState` transitions to `DISCONNECTED_ERROR`, *Then* the overlay displays the warning badge and strictly prohibits green "All Clear" rendering.
* *Invariants*: Touch pass-through; zero false green; zero SQLite schema modifications; 100% 9-language translation parity.

---

## 2. Test Specification (TST-TRK-062)

### Test Case 1: Radar Edge Rail Kinematics & Dot Position Unit Tests (`TST-TRK-062.1`)
* **Scope**: Unit Test (`RadarEdgeOverlayTest.kt`)
* **Assertion**: Verify target range normalization ($y = 1 - d/d_{\max}$) and multi-target dot coordinate resolution.

### Test Case 2: Semantic Threat Color & Pulsing Alert Tests (`TST-TRK-062.2`)
* **Scope**: Unit Test (`RadarEdgeOverlayTest.kt`)
* **Assertion**: Verify color mapping (Green = `NONE`, Amber = `APPROACHING`, Red = `FAST_APPROACHING`).

### Test Case 3: Fail-Safe Disconnect Warning & Error State Tests (`TST-TRK-062.3`)
* **Scope**: Unit Test (`RadarEdgeOverlayTest.kt`)
* **Assertion**: Verify `DISCONNECTED_ERROR` and `DISCONNECTED` render warning indicator and strictly prohibit green clear state.

### Test Case 4: TuningPreferencesDataStore Edge Side & Clear Mode Tests (`TST-TRK-062.4`)
* **Scope**: Unit Test (`TuningPreferencesDataStoreTest.kt`)
* **Assertion**: Verify default values (`RIGHT`, `SLIM_RAIL`) and persistence of custom edge side and clear mode selections.

### Test Case 5: 9-Language Localization Parity Test (`TST-TRK-062.5`)
* **Scope**: Contract Test (`TranslationParityTest.kt`)
* **Assertion**: Verify 100% key and format parity across `strings_radar.xml` in all 9 supported locales.

### Test Case 6: Tier 1 Targeted Regression Suite (`TST-TRK-062.6`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.radar.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-062.1` | Unit | `RadarEdgeOverlay.kt`, `RadarEdgeOverlayTest.kt` | `REQ-TRK-062.1` | Specified |
| `TST-TRK-062.2` | Unit | `RadarEdgeOverlay.kt`, `RadarEdgeOverlayTest.kt` | `REQ-TRK-062.2` | Specified |
| `TST-TRK-062.3` | Unit | `RadarEdgeOverlay.kt`, `RadarEdgeOverlayTest.kt` | `REQ-TRK-062.4` | Specified |
| `TST-TRK-062.4` | Unit | `TuningPreferencesDataStore.kt`, `TuningPreferencesDataStoreTest.kt` | `REQ-TRK-062.5` | Specified |
| `TST-TRK-062.5` | Parity | `strings_radar.xml`, `TranslationParityTest.kt` | `REQ-TRK-062.6` | Specified |
| `TST-TRK-062.6` | Regression | `ui.tracking.radar.*`, `settings.*`, `build.*` | `REQ-PRO-001` | Specified |
