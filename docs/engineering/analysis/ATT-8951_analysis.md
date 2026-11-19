# Stage 1 Analysis: ATT-8951 - Move HRV Threshold Test CTA Above Threshold Controls

**Ticket**: [ATT-8951](https://rainerblind.atlassian.net/browse/ATT-8951) | **Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) | **Severity/Type**: Improvement (`Medium`) | **SP**: 1  
**Branch**: `feature/ATT-8951` (off `sprint/2026-11-19_01_Früh` @ `05096023`) | **Author**: Agent 1 | **Date**: 2026-11-19

## 1. Problem Statement & Motivation
In `ZonesSettingsActivity.kt` -> `HrvDfaA1SettingsContent` (tab 3: *HRV (DFA α1)*), the Guided Threshold Test CTA (`OutlinedButton` invoking `onOpenHrvTest` / `R.string.hrv_start_guided_ramp_test`) sits at the bottom of the scrollable `Column` (`:1414-1428`), forcing users to scroll past all zone cards, sliders, display toggles, and preview before seeing it. Moving it directly below the intro guidance text places it above the fold right after the user reads the explanation.

## 2. Gap Analysis & Independent Hypothesis Verification
- **Current Layout (`ZonesSettingsActivity.kt:1102-1431`)**:
  1. `:1111-1130`: Header (`R.string.hrv_info_zones_title`) + `Info` `IconButton`.
  2. `:1133-1137`: Intro explanation (`R.string.hrv_zones_guidance_desc`).
  3. `:1143-1164`: Three `HrvZoneBandCard` items (Zones 2–4).
  4. `:1168-1266`: `title_edit_zones` + `reset_to_defaults` + `HrvDfaA1RangeSlider` + `HrvThresholdControlRow` (T1/T2).
  5. `:1270-1412`: Zone Display card + live `SensorFieldView` preview.
  6. `:1414-1428`: `if (onOpenHrvTest != null)` `OutlinedButton(fillMaxWidth)` with `Icons.AutoMirrored.Filled.TrendingUp` + `R.string.hrv_start_guided_ramp_test` (wired via `onNavigateToHrvTest` at `:331`).
- **Independent Peer Review & Verification**:
  - **Exact Target Slot**: Placing `if (onOpenHrvTest != null) { OutlinedButton(...) }` directly after `Text(hrv_zones_guidance_desc)` (`:1137`) and before `Spacer(4.dp)` + `HrvZoneBandCard`s (`:1139-1143`) satisfies both "between the explanatory intro text at the top and the threshold configuration controls (`HrvDfaA1RangeSlider` / `HrvThresholdControlRow` / zone cards)" and immediate above-the-fold visibility (`~120-180dp` from top).
  - **Viewport Shift Check**: `HrvDfaA1RangeSliderGestureTest.testZoneSettingsScreen_tab3PreservesVerticalScrollAndOutsideSwipe()` (`:285-351`) uses `height(680.dp)` with `onNavigateToHrvTest = {}` and asserts `HrvDfaA1RangeSliderCard.assertIsDisplayed()` at `:304`. Shifting content down by `~56dp` must be verified in Stage 4.
  - **Contract & String Parity Check**: `HrvRampTestScreenTest.kt:163` asserts `Icons.AutoMirrored.Filled.TrendingUp` in `ZonesSettingsActivity.kt`; `HrvDfaA1SettingsContractTest.kt:296-298` slices `HrvDfaA1SettingsContent(`..`HrvZoneBandCard(` for `zone_display_gauge` / `showBar = true` / `hrv_preview_pill_*`. All strings (`hrv_start_guided_ramp_test`: EN 27 chars, DE 31 chars) exist across all 9 locales -> **`Localization Parity: N/A`**.

## 3. User Scope Grounding & Non-Goals (ATT-1250)
- **In-Scope**: Reposition `if (onOpenHrvTest != null)` `OutlinedButton` in `HrvDfaA1SettingsContent` between `hrv_zones_guidance_desc` and the zone cards/threshold controls; verify vertical ordering via behavioral `createComposeRule()` test (`0` `src/main` reads) + `Rule 8.5` Light/Dark PNG inspection in Stage 4/6. Complexity confirmed **1 SP** (no split needed).
- **Non-Goals**: No edits to `HrvRampTestScreen`, `NavRoutes.HRV_TEST`, `ZoneDisplayOptions`, `SettingsDataStore`, `strings*.xml`, or tabs 0–2 (`SettingsScreenContent`).

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- **Original Requirement ID & Target**: `REQ-TRK-072.7` (`docs/requirements/req_tracking_cockpit_part2.md:1525-1527`) & `REQ-TRK-061.4` (`ZonesSettingsActivity.kt`).
- **Historical Origin & Commit Trace**: `ATT-7317` (`fce6777b`) built `HrvDfaA1SettingsContent` with the CTA at the bottom; `ATT-7527` (`b7d3ce84`) consolidated the Guided Threshold Test entry point exclusively into `ZonesSettingsActivity.kt` with `Icons.AutoMirrored.Filled.TrendingUp`.
- **Root Reason for Existing Formulation**: Appended at the bottom as an initial optional CTA; later additions (display card + preview) pushed it further below the fold.
- **Preservation of Core Invariants**: Exclusive Training Zones entry point (`NavRoutes.HRV_TEST`), `Icons.AutoMirrored.Filled.TrendingUp` (`TST-TRK-072.9`), 4-toggle card + `showBar = true` preview (`REQ-TRK-087`), `ATT-8105` slider gesture isolation, $T_1 \ge T_2 + 0.05f$, and 9-locale parity remain intact.

## 5. Change-Request Impact Analysis (SUP.10 / REQ-PRO-064)
- **Affected Requirements**: Refines `REQ-TRK-072.7` / `REQ-TRK-061.4` (ergonomic CTA vertical placement in `HrvDfaA1SettingsContent`). **`SYS.2` / `SYS.3` Impact (`REQ-PRO-131.4`)**: No impact (`SYSREQ-TRK-006` unchanged; zero sensor, RF, HAL, or REST changes).
- **Affected Architecture Elements**: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt` (`HrvDfaA1SettingsContent` only). `0` ViewModel/DataStore/Navigation/Resource changes.
- **Verification Impact & Test Surface**: `HrvDfaA1RangeSliderGestureTest.kt` (`createComposeRule()` ordering + `Rule 8.5` Light/Dark PNG, `0` `src/main` reads), `HrvDfaA1SettingsContractTest.kt`, `HrvRampTestScreenTest.kt` (`TST-TRK-072.9`). `Localization Parity: N/A`.
- **Risk Assessment & Invariants**: **LOW** risk (single-composable vertical move). Invariants: `onOpenHrvTest != null` guard, M3 `OutlinedButton` Light/Dark contrast, single-line DE label at `360dp`/`412dp`, `0` new `src/main` reads, and 4 dirty `tools/` files untouched.
