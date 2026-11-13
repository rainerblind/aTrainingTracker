# Stage 2: Requirement & Test Specification - ATT-7537: Full-Screen Radar Overlay Mode

**Ticket**: [ATT-7537](https://rainerblind.atlassian.net/browse/ATT-7537)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-TRK-062` (*Live Rearview Radar Vertical Edge Overlay Across Tracking Tabs*)  
**Test Spec ID**: `TST-TRK-062` (`TST-TRK-062.1`..`TST-TRK-062.6`) | **Branch**: `feature/ATT-7537`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (REQ-TRK-062)

### 1.1 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit

### 1.2 Functional & Architectural Requirements
1. `TuningPreferencesDataStore.kt`: `RadarEdgeSide` enum SHALL support `LEFT`, `FULL_SCREEN`, `RIGHT` (default `RIGHT`), preserving backward-compatible deserialization via `safeEnumValueOf(raw, RIGHT)` (`REQ-TRK-062.5a`).
2. `RadarSettingsSection.kt`: SHALL present a 3-way segmented button `Links | Vollbild | Rechts`. When `FULL_SCREEN` is selected, `RadarClearMode` selection SHALL be hidden (`REQ-TRK-062.5b`).
3. `AdvancedTuningAccordion.kt`: `formatCyclingRadarSubtitle` SHALL format all three `RadarEdgeSide` variants without binary ternary fallback (`REQ-TRK-062.5c`).
4. `RadarEdgeOverlay.kt` & `TrackingTabsScreen.kt`: When `edgeSide == RadarEdgeSide.FULL_SCREEN`:
   - *Clear State*: Only clickable `RadarStatusBadgePill` is rendered; zero track rail background (`REQ-TRK-062.3a`).
   - *Active Threats*: Container spans full content area (`fillMaxSize()`) with 100% touch pass-through; approaching vehicles render as high-contrast badges traveling upward ($y = 1.0 \to 0.0$) with lateral lane mapping across screen width (`REQ-TRK-062.1a`).
   - *Disconnect/Error*: Amber hazard pill displayed; zero background track rail clutter (`REQ-TRK-062.4a`).
5. `strings_radar.xml`: `radar_overlay_mode_fullscreen` SHALL maintain 100% parity across all 9 supported locales (`REQ-TRK-062.6a`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC1 (3-Way Position Selector & Conditional Clear-Mode Visibility)**: Given user navigates to Radar Settings, When viewing overlay position, Then segmented button presents `Links | Vollbild | Rechts`, and selecting `Vollbild` (FULL_SCREEN) hides `RadarClearMode`.
* **AC2 (Full-Screen Mode Clear State)**: Given `radarEdgeSide == FULL_SCREEN` and road is clear, When viewing Tracking Screen, Then only `RadarStatusBadgePill` renders with zero background track rail.
* **AC3 (Full-Screen Active Threats & Touch Pass-Through)**: Given `radarEdgeSide == FULL_SCREEN` and vehicles approach, When viewing Tracking Screen, Then vehicle badges travel upward over the screen according to range and lane offset, while tab swipes and map gestures pass through unimpeded.
* **AC4 (Disconnect / Error State in Full-Screen)**: Given radar in `DISCONNECTED_ERROR` with `FULL_SCREEN`, When rendered, Then amber disconnect badge is displayed with 1-tap reconnect action and zero rail background.

### 1.4 System Invariants
- 100% touch pass-through along overlay container; zero gesture interception over MapView or HorizontalPager.
- Upward vehicle approach kinematics ($140\text{m}/175\text{m} \to 0\text{m}$) strictly preserved.
- Zero SQLite schema modifications; 100% 9-language translation parity.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Amends `REQ-TRK-062` (`REQ-TRK-062.1`, `REQ-TRK-062.3`, `REQ-TRK-062.5`, `REQ-TRK-062.6`), targeting `RadarEdgeOverlay.kt`, `TrackingTabsScreen.kt`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `AdvancedTuningAccordion.kt`, `strings_radar.xml`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-5085` (`671c4c1a`), refined in `ATT-6703` (kinematics inversion, rail auto-hide scoping, nav bar insets).
* **Root Reason for Existing Formulation**: Initial design prioritized a narrow peripheral rail (`LEFT`/`RIGHT`) to prevent cockpit metric occlusion before full-screen overlay architecture was specified.
* **Preservation of Core Invariants**: Physical rearview perspective (upward approach); 100% gesture pass-through; fail-safe amber disconnect indication (zero false green); system nav-bar clearance; 100% 9-language translation parity.

---

## 3. Test Specification (TST-TRK-062)

### Test Case 1: Full-Screen Kinematics & Lateral Mapping (`TST-TRK-062.1`)
* **Scope**: Unit (`RadarEdgeOverlayTest.kt`)
* **Verification**: Verify upward range normalization ($140\text{m} \to 1.0$, $0\text{m} \to 0.0$); verify full-screen lateral mapping across screen width for RHT vs LHT.

### Test Case 2: Full-Screen Clear Road & Disconnect State (`TST-TRK-062.2`)
* **Scope**: Unit (`RadarEdgeOverlayTest.kt`)
* **Verification**: Verify `FULL_SCREEN` renders zero rail background when clear or disconnected; verify status badge pill remains visible and interactive.

### Test Case 3: DataStore 3-Way Enum & Backward Compatibility (`TST-TRK-062.3`)
* **Scope**: Unit (`TuningPreferencesDataStoreTest.kt`)
* **Verification**: Verify `RadarEdgeSide.FULL_SCREEN` serialization, default `RIGHT`, and backward-compatible fallback for unparseable values.

### Test Case 4: Settings UI & Accordion Subtitle Formatting (`TST-TRK-062.4`)
* **Scope**: Unit (`RadarSettingsSectionTest.kt`, `RadarAccordionSectionTest.kt`)
* **Verification**: Verify 3 options in segmented button; verify `RadarClearMode` visibility conditional on `edgeSide != FULL_SCREEN`; verify `formatCyclingRadarSubtitle` formats `FULL_SCREEN`.

### Test Case 5: 9-Language Localization Parity (`TST-TRK-062.5`)
* **Scope**: Localization (`TranslationParityTest.kt`)
* **Verification**: Verify `radar_overlay_mode_fullscreen` across all 9 supported locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).

### Test Case 6: Tier 1 Targeted Regression Suite (`TST-TRK-062.6`)
* **Scope**: Regression (`com.atrainingtracker.trainingtracker.ui.tracking.radar.*`, `com.atrainingtracker.trainingtracker.ui.settings.radar.*`, `com.atrainingtracker.trainingtracker.build.*`)

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-062.1` | Unit | `RadarOverlayMath.normalizeRadarRange` / lateral mapping | `REQ-TRK-062.1a` | Specified |
| `TST-TRK-062.2` | Unit | `RadarEdgeOverlay` clear & disconnect layout | `REQ-TRK-062.3a`, `REQ-TRK-062.4a` | Specified |
| `TST-TRK-062.3` | Unit | `TuningPreferencesDataStore.radarEdgeSideFlow` | `REQ-TRK-062.5a` | Specified |
| `TST-TRK-062.4` | Unit | `RadarSettingsSection` & `AdvancedTuningAccordion` | `REQ-TRK-062.5b`, `REQ-TRK-062.5c` | Specified |
| `TST-TRK-062.5` | Localization | `TranslationParityTest` | `REQ-TRK-062.6a` | Specified |
| `TST-TRK-062.6` | Regression | Targeted unit test suite | `REQ-PRO-001` | Specified |
