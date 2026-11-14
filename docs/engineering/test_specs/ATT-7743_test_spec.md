# Stage 2: Requirement & Test Specification - ATT-7743: Borg CR10 RPE 7-Zone Physiological Palette & Slider Ergonomics

**Ticket**: [ATT-7743](https://atrainingtracker.atlassian.net/browse/ATT-7743)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feat/ATT-7743`  
**Requirement Mapping**: `REQ-UI-529` (`REQ-UI-529.1`..`REQ-UI-529.5`)  
**Test Spec ID**: `TST-UI-529` (`TST-UI-529.1`..`TST-UI-529.6`)  
**Author**: AI Agent 2 (Flash Agent) | **Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-UI-529)

### 1.1 Problem Statement & Rationale
In `WorkoutRpeFeelSection.kt`, the initial Borg CR10 slider prototype from ATT-7515 mapped RPE 1–10 to only 5 zones, leaving Zone 6 (#8B5A2B) and Zone 7 (#000000) unused and inverting Zone 1 and Zone 2. Furthermore, the redundant row of 10 number boxes beneath the slider wasted vertical space, the Compose Material 3 slider track stop indicator erroneously painted tick 10 in the active track color when dragging at lower values, and sharing a single row between section title, dynamic value, and Clear button caused line wrapping and finger occlusion during right-handed interaction. Mapping RPE 1–10 to all 7 physiological zones with OLED dark theme high-contrast protection, removing the redundant number boxes, ensuring uniform track stop-dot rendering, and elevating the dynamic RPE descriptor to a dedicated full-width line establishes ergonomic, accessible, and sports-scientifically accurate workout perception editing.

### 1.2 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit, On-Device Manual

### 1.3 Functional & Architectural Requirements
* **REQ-UI-529.1** (*Borg CR10 7-Zone Physiological Color Mapping*): `TTColor.rpeColor(rpe: Int): Color` in `Color.kt` SHALL map all 10 Borg CR10 exertion scores strictly to `TTColor.Zone1..Zone7`:
  - `1, 2` $\rightarrow$ `TTColor.Zone1` (Chartreuse `#7FFF00`, Active Recovery)
  - `3, 4` $\rightarrow$ `TTColor.Zone2` (Green `#008000`, Endurance)
  - `5, 6` $\rightarrow$ `TTColor.Zone3` (Orange `#FFA500`, Tempo / Sweet Spot)
  - `7` $\rightarrow$ `TTColor.Zone4` (Red `#FF0000`, Threshold)
  - `8` $\rightarrow$ `TTColor.Zone5` (Dark Violet `#9400D3`, VO2max)
  - `9` $\rightarrow$ `TTColor.Zone6` (Warm Earthy Brown `#8B5A2B`, Anaerobic Capacity)
  - `10` $\rightarrow$ `TTColor.Zone7` (Pure Black `#000000`, Neuromuscular / Max)
  - Values out of range ($< 1$) SHALL default to `TTColor.Zone1`; values $> 10$ SHALL default to `TTColor.Zone7`.
* **REQ-UI-529.2** (*Dark Theme High-Contrast Accessibility for RPE 10 / Zone 7*): When `rpe == 10` (or `TTColor.rpeColor(rpe) == TTColor.Zone7`), the dynamic RPE score and Borg descriptor text in `RpeFeelEditorControls` SHALL be rendered using `MaterialTheme.colorScheme.onSurface` (achieving $\ge 12:1$ contrast against dark surfaces and $\ge 14:1$ on light surfaces). In `WorkoutRpeFeelSection` badge row, when `rpe == 10`, the badge surface border and inner indicator dot SHALL render an outline border (`0.5.dp` to `1.dp`, `MaterialTheme.colorScheme.outlineVariant`). In `RpeFeelEditorControls` slider, when `rpe == 10`, the slider thumb SHALL render a subtle border (`1.dp`, `MaterialTheme.colorScheme.outline`).
* **REQ-UI-529.3** (*Removal of Redundant 1–10 Number Boxes*): `RpeFeelEditorControls` in `WorkoutRpeFeelSection.kt` SHALL omit the secondary 10-box button row beneath the slider, relying solely on the discrete Material 3 `Slider` (1f..10f, `steps = 8`) for RPE selection, freeing vertical space in `EditWorkoutScreen`.
* **REQ-UI-529.4** (*Slider Track Stop-Dot Uniformity*): In `RpeFeelEditorControls`, the Material 3 `Slider` SHALL supply `track = { sliderState -> SliderDefaults.Track(sliderState = sliderState, colors = colors, drawStopIndicator = null) }`, preventing `SliderDefaults.Track` from hardcoding the active track color at the rightmost inactive stop tick (position 10) when the slider thumb is set to values $< 10$.
* **REQ-UI-529.5** (*Dedicated Non-Wrapping, Non-Occluding Dynamic RPE Header*): `RpeFeelEditorControls` SHALL decouple the section title and dynamic RPE readout:
  - *Title Row*: Standard `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween)` containing `workout_rpe_title` ("Belastungsempfinden (RPE 1–10)") on the start and the Clear action (`Icons.Default.Clear`, `WorkoutRpeFeelTestTags.RPE_CLEAR`) on the end when `rpe != null`.
  - *Dynamic Readout Line*: Dedicated full-width line positioned immediately above the `Slider`. When `rpe != null`, it SHALL display the full formatted anchor descriptor (`stringResource(getRpeAnchorStringRes(rpe))`) in bold typography, ensuring zero line-wrapping across all 9 localized string sets and zero occlusion by the athlete's dragging finger.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (7-Zone Mapping Parity)**: *Given* `TTColor.rpeColor(score)`, *When* evaluated for scores 1 through 10, *Then* scores map 1:1 to Zone1..Zone7 without inversions or omissions.
* **Criterion 2 (Dark Mode RPE 10 Contrast)**: *Given* Dark Theme active and RPE set to 10, *When* displayed in `RpeFeelEditorControls` and `WorkoutRpeFeelSection`, *Then* text uses `onSurface` and badges/thumbs have outline borders preserving $\ge 3.0:1$ non-text contrast.
* **Criterion 3 (Redundant Buttons Removed & Stop Dot Fixed)**: *Given* `RpeFeelEditorControls`, *When* inspecting the composable hierarchy, *Then* the 10 number boxes are removed, and dragging the slider to 7 renders ticks 8, 9, and 10 in uniform inactive color.
* **Criterion 4 (Single-Line Readout & No Finger Occlusion)**: *Given* `RpeFeelEditorControls` with RPE 6 in German locale, *When* dragging across the track, *Then* "6 — Schwer bis sehr schwer" renders on a dedicated full-width row above the slider without wrapping or thumb occlusion.

### 1.5 System Invariants & Scope Bounding
* Nullability (`rpe: Int?`, `feel: Int?`) and numeric range (`1..10`, `1..5`) strictly preserved.
* Foster Session-RPE calculation (`PowerPhysiologyCalculator.calculateSessionRpe`) and Garmin FIT codec parity (fields 192/193) unchanged.
* Feel (1–5) M3 tonal cards and vector sentiment icons completely preserved.

---

## 2. Test Specification (TST-UI-529)

### Test Case 1: `testRpeColor_mapsToAll7Zones` (`TST-UI-529.1`)
* **Scope**: Unit Test (`WorkoutRpeFeelTest.kt`)
* **Action**: Assert `TTColor.rpeColor(1..10)` returns `Zone1` (1,2), `Zone2` (3,4), `Zone3` (5,6), `Zone4` (7), `Zone5` (8), `Zone6` (9), `Zone7` (10), and clamps out-of-range values.

### Test Case 2: `testRpeColor_zone7DarkThemeContrast` (`TST-UI-529.2`)
* **Scope**: Unit & Contract Test (`WorkoutRpeFeelTest.kt`, `ThemeColorCentralizationAuditTest.kt`)
* **Action**: Assert WCAG 2.1 relative luminance and ensure RPE 10 text is not pure black in dark theme.

### Test Case 3: `testRpeEditor_numberBoxesRemovedAndStopIndicatorFixed` (`TST-UI-529.3` / `TST-UI-529.4`)
* **Scope**: Composable Contract Test (`WorkoutRpeFeelTest.kt`)
* **Action**: Assert `RpeFeelEditorControls` source does not contain number box row and uses `drawStopIndicator = null`.

### Test Case 4: `testRpeEditor_dedicatedFullWidthHeaderLayout` (`TST-UI-529.5`)
* **Scope**: Composable Contract Test (`WorkoutRpeFeelTest.kt`)
* **Action**: Assert title row decouples from dynamic anchor readout and readout sits above slider on its own row.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-529.1` | Unit | `Color.kt` | `REQ-UI-529.1` | Specified |
| `TST-UI-529.2` | Unit / Contrast | `WorkoutRpeFeelSection.kt`, `Color.kt` | `REQ-UI-529.2` | Specified |
| `TST-UI-529.3` | Contract / UI | `WorkoutRpeFeelSection.kt` | `REQ-UI-529.3` | Specified |
| `TST-UI-529.4` | Contract / UI | `WorkoutRpeFeelSection.kt` | `REQ-UI-529.4` | Specified |
| `TST-UI-529.5` | Contract / UI | `WorkoutRpeFeelSection.kt` | `REQ-UI-529.5` | Specified |
| `TST-UI-529.6` | Suite Regression | `./gradlew testDebugUnitTest` | `REQ-UI-529` | Specified |
