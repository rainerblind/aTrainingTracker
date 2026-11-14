# Stage 3: Implementation Plan - ATT-7743: Borg CR10 RPE 7-Zone Physiological Palette & Slider Ergonomics

**Ticket**: [ATT-7743](https://atrainingtracker.atlassian.net/browse/ATT-7743)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feat/ATT-7743`  
**Requirement Mapping**: `REQ-UI-529` (`REQ-UI-529.1`..`REQ-UI-529.5`)  
**Test Mapping**: `TST-UI-529` (`TST-UI-529.1`..`TST-UI-529.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-14  

---

## 1. Problem Description & Background
Following the verification of ATT-7515 on Pixel 10 (`rpe_feel_review.png`), four distinct ergonomics, styling, and accessibility issues were discovered in `WorkoutRpeFeelSection.kt`:
1. `TTColor.rpeColor` mapped RPE 1–10 to only 5 zones, ignoring Zone 6 (#8B5A2B) and Zone 7 (#000000) and inverting Zone 1 and 2.
2. The 10 numbered boxes row ([1]..[10]) below the slider is redundant alongside the discrete slider and wastes vertical dialog height.
3. The right-end stop indicator dot at position 10 erroneously renders in the active track color (red when RPE = 7) because Material 3 `SliderDefaults.Track` hardcodes active track color for stop indicators.
4. The single-row header groups title, dynamic value, and Clear button, forcing strings like "6 — Schwer bis sehr schwer" onto two lines and allowing the dragging thumb to occlude the readout.

---

## 2. Traceability & Requirements Mapping
* **REQ-UI-529.1**: Full 7-Zone physiological color mapping in `Color.kt` (1–2 -> Zone1, 3–4 -> Zone2, 5–6 -> Zone3, 7 -> Zone4, 8 -> Zone5, 9 -> Zone6, 10 -> Zone7) $\rightarrow$ `TST-UI-529.1`.
* **REQ-UI-529.2**: Dark Theme high-contrast accessibility for RPE 10 (Zone 7) via `onSurface` text and `outlineVariant` borders $\rightarrow$ `TST-UI-529.2`.
* **REQ-UI-529.3**: Removal of redundant 1–10 number box row from `RpeFeelEditorControls` $\rightarrow$ `TST-UI-529.3`.
* **REQ-UI-529.4**: Uniform inactive slider track stop-dot rendering via `drawStopIndicator = null` $\rightarrow$ `TST-UI-529.4`.
* **REQ-UI-529.5**: Dedicated full-width dynamic RPE descriptor line positioned above slider to prevent wrapping and finger occlusion $\rightarrow$ `TST-UI-529.5`.

---

## 3. System Invariants & Preserved Behavior
1. **Model & DB Schema Invariance**: `WorkoutData.rpe` (`Int?`, `1..10`), `WorkoutData.feel` (`Int?`, `1..5`), and `WorkoutSummaries` DB schema (`DB_VERSION = 30`) remain 100% untouched.
2. **Physiology & FIT Parity**: Foster Session-RPE (`PowerPhysiologyCalculator.calculateSessionRpe`) and Garmin FIT codec (fields 192/193) unchanged.
3. **Feel Controls Invariance**: Feel (1–5) M3 tonal cards with sentiment vector icons (`REQ-WKO-037.2`) remain completely unaltered.
4. **Localization Parity**: 100% 9-language parity in `strings_rpe_feel.xml` strictly preserved.

---

## 4. Proposed Architectural Changes

### Component 1: Theme & Color Palette (`Color.kt`)
* Update `TTColor.rpeColor(rpe: Int): Color`:
  - `1, 2` $\rightarrow$ `Zone1` (Chartreuse `#7FFF00`, Active Recovery)
  - `3, 4` $\rightarrow$ `Zone2` (Green `#008000`, Endurance)
  - `5, 6` $\rightarrow$ `Zone3` (Orange `#FFA500`, Tempo / Sweet Spot)
  - `7` $\rightarrow$ `Zone4` (Red `#FF0000`, Threshold)
  - `8` $\rightarrow$ `Zone5` (Dark Violet `#9400D3`, VO2max)
  - `9` $\rightarrow$ `Zone6` (Warm Earthy Brown `#8B5A2B`, Anaerobic Capacity)
  - `10` $\rightarrow$ `Zone7` (Pure Black `#000000`, Neuromuscular / Max)
  - Clamped defaults: $\le 0 \rightarrow \text{Zone1}$, $\ge 11 \rightarrow \text{Zone7}$.

### Component 2: Presentation & Ergonomics (`WorkoutRpeFeelSection.kt`)
* **WorkoutRpeFeelSection (Badge Row)**:
  - When `rpe == 10`, badge surface uses `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)` with `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)`, and indicator dot uses `Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)`.
* **RpeFeelEditorControls (Editor)**:
  - **Decoupled Title Row**: Row containing `workout_rpe_title` on the left and Clear `IconButton` (`Icons.Default.Clear`, tag `RPE_CLEAR`) on the right when `rpe != null`.
  - **Dedicated Dynamic Readout Row**: Positioned immediately above the `Slider`. When `rpe != null`, renders `stringResource(getRpeAnchorStringRes(rpe))` with `MaterialTheme.typography.titleSmall` and `color = if (rpe == 10) MaterialTheme.colorScheme.onSurface else rpeColor`. Spans full width (`Modifier.fillMaxWidth()`), eliminating line wrapping.
  - **Discrete Slider with Uniform Stop-Dot & Contrast Thumb**:
    - Pass `track = { sliderState -> SliderDefaults.Track(sliderState = sliderState, colors = sliderColors, drawStopIndicator = null) }` to prevent M3 active color leakage on tick 10.
    - Pass `thumb = { SliderDefaults.Thumb(interactionSource = remember { MutableInteractionSource() }, colors = sliderColors, modifier = if (rpe == 10) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape) else Modifier) }`.
  - **Removal of Redundant Boxes**: Delete lines 299–334 (`Row { for (score in 1..10) ... }`).

### UI Consistency (`docs/design_guidelines.md` §1 & §5)
* **Tokens**: Reuses `TTColor.Zone1..Zone7`, `MaterialTheme.colorScheme.onSurface`, `outlineVariant`, `outline`, and standard typography (`titleSmall`, `labelMedium`).
* **Ergonomics**: Full-width header ensures text is never occluded by the athlete's thumb during right-handed dragging.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Color Mapping Update (`Color.kt`)
* Modify `TTColor.rpeColor` to return `Zone1..Zone7` according to `REQ-UI-529.1` (`TST-UI-529.1`).

### Step 2: Presentation & Ergonomics Refactoring (`WorkoutRpeFeelSection.kt`)
* In `WorkoutRpeFeelSection`, add Dark Theme contrast border/background when `rpe == 10` (`REQ-UI-529.2`).
* In `RpeFeelEditorControls`, decouple title and clear button; add dedicated full-width dynamic readout line above the slider (`REQ-UI-529.5`).
* Update `Slider` to supply `track` with `drawStopIndicator = null` and contrast thumb border for RPE 10 (`REQ-UI-529.2`, `REQ-UI-529.4`).
* Delete redundant 1..10 box row (`REQ-UI-529.3`).

### Step 3: Unit & Contract Tests (`WorkoutRpeFeelTest.kt`)
* Update `WorkoutRpeFeelTest.kt` with unit tests for `rpeColor` 1..10 mapping, boundary clamping, and contract tests for redundant box removal, `drawStopIndicator = null`, and full-width header decoupling (`TST-UI-529.1`..`TST-UI-529.5`).
* Targeted test execution command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRpeFeelTest" --tests "com.atrainingtracker.trainingtracker.build.ThemeColorCentralizationAuditTest"
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: 100% pass rate in `WorkoutRpeFeelTest`, `ThemeColorCentralizationAuditTest`, and `TranslationParityTest`.
* **Visual Verification (Rule 71 & 84)**: Launch emulator / inspect EditWorkoutScreen with RPE 6 (verify German single line "6 — Schwer bis sehr schwer"), RPE 7 (verify ticks 8, 9, 10 are uniform inactive color without red end dot), and RPE 10 in Dark Theme (verify high contrast onSurface text and visible outline borders).
* **Rollback**: Clean branch reset via `git reset --hard sprint/2026-46.6`.
