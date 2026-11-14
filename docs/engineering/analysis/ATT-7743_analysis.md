# Stage 1 Analysis: ATT-7743 - EditWorkoutScreen RPE Slider Ergonomics & 7-Zone Color Palette

**Ticket**: [ATT-7743](https://atrainingtracker.atlassian.net/browse/ATT-7743)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6`  
**Branch**: `feat/ATT-7743` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Following the Sprint 2026-46.5 verification of ATT-7515 on physical Pixel 10 devices (`rpe_feel_review.png`), four distinct ergonomics, styling, and contrast deficiencies were identified in `WorkoutRpeFeelSection.kt`:
1. **Incomplete Zone Color Mapping**: `TTColor.rpeColor` mapped the 10 RPE levels to only 5 zones (Zone1..Zone5). The full 7-zone physiological palette established in `Color.kt` and `req_ui_design_system.md` (`TTColor.Zone1..Zone7`) was not utilized.
2. **Redundant 1–10 Number Boxes**: With the discrete 1–10 Material 3 Slider present, the secondary row of 10 numbered boxes directly beneath the slider is redundant, consumes excessive vertical viewport space, and clutters the workout edit dialog.
3. **M3 Track Stop Indicator Bug**: At sub-maximal RPE values (e.g., RPE 7), inactive ticks 8 and 9 render in inactive tick color, but the right-end track stop indicator dot at position 10 erroneously renders in the active track color (red in `rpe_feel_review.png`), due to Compose Material 3 `SliderDefaults.Track` hardcoding `colors.trackColor(enabled, active = true)` in its default `drawStopIndicator`.
4. **Header Line-Wrapping & Dragging Occlusion**: Sharing a single row between "Belastungsempfinden (RPE 1–10)", the dynamic value/anchor text, and the Clear button forces longer localized strings (e.g. DE "6 — Schwer bis sehr schwer", FR "6 — Dur à très dur", PL "6 — Ciężki do bardzo ciężkiego") to wrap onto two lines. Furthermore, a right-handed finger dragging the slider on the right half of the track occludes the right-aligned readout.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (`TTColor.rpeColor` Zone Mapping)**: `Color.kt:212-219` maps `1, 2 -> Zone2`, `3, 4 -> Zone1`, `5, 6 -> Zone3`, `7, 8 -> Zone4`, `9, 10 -> Zone5`. It omits `Zone6` (#8B5A2B) and `Zone7` (#000000). When RPE 10 maps to `Zone7` (Pure Black), dark theme surfaces (`#121214` / `#16161A`) cause black text and UI elements to lose WCAG 2.1 contrast unless rendered with high-contrast text (`MaterialTheme.colorScheme.onSurface`) and outline borders (`outlineVariant`).
* **RCA 2 (Redundant Number Row)**: `WorkoutRpeFeelSection.kt:299-334` renders an equal-width 10-box row duplicating the slider's 10 discrete positions.
* **RCA 3 (M3 Slider Stop Indicator)**: Compose Material 3 `SliderDefaults.Track` defines `drawStopIndicator` with `color = colors.trackColor(enabled, active = true)`. Because `Slider` does not provide an explicit `stopIndicatorColor`, passing `drawStopIndicator = null` allows `drawTick` to render tick 10 with `inactiveTickColor` when inactive and `activeTickColor` when active, restoring tick uniformity.
* **RCA 4 (Layout Congestion & Occlusion)**: `WorkoutRpeFeelSection.kt:243-280` groups title, dynamic anchor, and Clear button in a single `Row(horizontalArrangement = Arrangement.SpaceBetween)`.
* **Independent Peer Review & Verification**:
  * Verified `Color.kt#L212-219`: Confirmed only 5 zones mapped and Zone1/Zone2 inverted.
  * Verified `rpe_feel_review.png`: Confirmed red stop-dot at tick 10 when slider is at 7, and confirmed text wrapping and thumb occlusion.
  * Verified `material3-android-1.4.0-sources.jar` (`Slider.kt#L1491-1496` & `L2068-2074`): Confirmed `drawStopIndicator` hardcodes active track color and skips `index == tickFractions.size - 1` when non-null. Setting `drawStopIndicator = null` cleanly renders the final tick via `drawTick`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Map RPE 1–10 in `TTColor.rpeColor` across all 7 zones: 1–2 -> Zone1, 3–4 -> Zone2, 5–6 -> Zone3, 7 -> Zone4, 8 -> Zone5, 9 -> Zone6, 10 -> Zone7.
  2. Implement high-contrast Dark Theme handling for RPE 10 (`Zone7`): text rendered via `MaterialTheme.colorScheme.onSurface`, and borders on badge/thumb using `outlineVariant`.
  3. Remove redundant 1–10 number box row from `RpeFeelEditorControls`.
  4. Pass custom `track` to `Slider` with `drawStopIndicator = null` to resolve the mismatched end tick.
  5. Refactor RPE header layout: title + clear button on top row; dedicated full-width line for dynamic RPE score and Borg descriptor above the slider to eliminate wrapping and finger occlusion.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Zero changes to database schema (`DB_VERSION = 30` preserved).
  * Zero changes to Feel (1–5) cards or icons (`REQ-WKO-037.2` preserved).
  * Zero changes to Foster sRPE calculation or PMC training load models.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Refines `REQ-WKO-037.1` (*Discrete Borg CR10 Slider*) and `REQ-WKO-038` (*Color Mapping & Descriptors*), and formally codified as `REQ-UI-529` in `docs/requirements/req_ui_design_system.md`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-6879` (commit `2c999318`) and refined in `ATT-7515` (commit `91555983`).
* **Root Reason for Existing Formulation**: `ATT-7515` introduced the discrete slider alongside the button row as a transitional migration, and initially mapped to 5 zones before 7-zone telemetry consolidation.
* **Preservation of Core Invariants**: The discrete integer domain `1..10`, nullable persistence in `WorkoutData`, and 100% 9-language localization parity are strictly preserved.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: Formally specified as `REQ-UI-529` (refining `REQ-WKO-037.1` and `REQ-WKO-038`).
* **Affected Architecture Elements**: `Color.kt`, `WorkoutRpeFeelSection.kt`, `docs/requirements/req_ui_design_system.md`, `docs/tests/tst_ui_design_system.md`.
* **Verification Impact & Test Surface**: `WorkoutRpeFeelTest.kt`, `ThemeColorCentralizationAuditTest.kt`, `TranslationParityTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Strictly visual and ergonomic presentation refinement in Compose UI and Color definitions.

---

## 6. Architectural Strategy & High-Level Solution
1. **7-Zone Palette Mapping (`Color.kt`)**: Update `TTColor.rpeColor` to return `Zone1` (1–2), `Zone2` (3–4), `Zone3` (5–6), `Zone4` (7), `Zone5` (8), `Zone6` (9), `Zone7` (10).
2. **High-Contrast Dark Theme Accessibility**: When `rpe == 10`, dynamically resolve text readout color to `MaterialTheme.colorScheme.onSurface`, and add `outlineVariant` border to badge dot/thumb.
3. **Slider Stop Indicator Fix**: Use `Slider` with `track = { SliderDefaults.Track(it, colors = colors, drawStopIndicator = null) }`.
4. **Header Layout & Occlusion Fix**: Title row contains section title and optional Clear button. Dynamic readout occupies a full-width line directly above the slider.
5. **Number Boxes Removal**: Delete lines 299–334 in `WorkoutRpeFeelSection.kt`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Nullable `1..10` range intact; sRPE calculations unaffected; zero regression to Feel 1–5 controls.
* **Risk Rating**: **LOW** (Presentation-layer layout and palette polish).
