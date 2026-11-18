# Stage 1 Analysis: ATT-8105 - Prevent HorizontalPager Tab-Swipe Interception on HrvDfaA1RangeSlider

**Ticket**: [ATT-8105](https://rainerblind.atlassian.net/browse/ATT-8105) | **Sub-task**: `(Pending submit-stage)` (`[Analysis]`)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV*) | **Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-17_03_Nacht`  
**Branch**: `feature/ATT-8105` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18  

---

## 1. Problem Statement & Motivation
On-device Pixel 10 verification of `ATT-7741` on the 4th tab (**HRV (DFA α1)**, `page == 3`) of `ZonesSettingsActivity` revealed that dragging `HrvDfaA1RangeSlider` thumbs—especially moving right thumb `T2` rightward—lets the parent `HorizontalPager` steal horizontal swipe gestures and initiate a tab switch back to `PWR_BIKE` (page 2).

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * Audited `ZonesSettingsActivity.kt` (`HorizontalPager` L304–336, `HrvDfaA1SettingsContent` L1054–1424, `HrvDfaA1RangeSlider` L1498–1622, `HrvThresholdControlRow` L1628–1713) against Jira hypotheses (`requestDisallowInterceptTouchEvent`, `userScrollEnabled`, `pointerInput`).
  * **RCA 1 (Unconditional Pager Scroll)**: `HorizontalPager(state = pagerState)` (L304) omits `userScrollEnabled`, keeping tab swipes enabled while `RangeSlider` is pressed/dragged. At `page == 3` (last tab), `dx > 0` (dragging `T2`/`T1` right) directly scrolls to `page == 2`, whereas `dx < 0` hits end overscroll.
  * **RCA 2 (Slop Race & Card Hit Margin)**: M3 `RangeSlider` (`rangeSliderPressDragModifier`) waits past horizontal touch slop on `PointerEventPass.Main` before claiming pointers. Touches near `Card` padding (`12.dp`, L1521), slightly outside the thumb hit box, or with vertical jitter under `verticalScroll` (L1084) leave unconsumed horizontal motion for `HorizontalPager`.
  * **RCA 3 (Async DataStore Lag)**: `onValueChange` (L1190–1194, L1552–1571) launches `dataStore.setDfaA1Thresholds(...)` on every move without local drag state, risking thumb snap-back/jitter if DataStore emissions lag or repeat clamped values.
  * **Strategy Evaluation**:
    * `requestDisallowInterceptTouchEvent`: **Ineffective** (`HorizontalPager` and `RangeSlider` are pure Compose in one `ComposeView`, not `ViewPager2`).
    * `NestedScrollConnection` alone: **Ineffective** (M3 `RangeSlider` uses `pointerInput`, not `Modifier.scrollable`).
    * `detectHorizontalDragGestures` wrapper: **Unsafe** (conflicts with M3 `RangeSlider`'s internal dual-thumb detector).
    * **Selected Approach**: Track `PointerEventPass.Initial` press/up on the `HrvDfaA1RangeSlider` container + M3 `startInteractionSource`/`endInteractionSource` to toggle `HorizontalPager(userScrollEnabled = !isSliderActive)` (matching `WorkoutDetailTabbedContent.kt:305`), consume residual horizontal drags in `Main` pass on the slider card (preserving vertical scroll), and hold local drag state until commit.
  * **Sibling & Test Audit**: Tabs 0–2 (`SettingsScreenContent`, L429–709) have no sliders. `HrvThresholdControlRow` (L1700) has `Slider` with `showSlider = false` (L1217/1239); wiring the optional callback there future-proofs it. `HrvDfaA1SettingsContractTest.kt` uses reflection/source checks only—zero Compose touch UI tests or screenshot PNGs exist yet (`Rule 8.5` applies in Stages 4–5).

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**: Isolate `HorizontalPager` swipe gestures during `HrvDfaA1RangeSlider` (and optional `HrvThresholdControlRow` slider) interactions in `ZonesSettingsActivity.kt`; smooth drag state; behavioral Compose touch test + emulator PNG verification.
* **Out-of-Scope**: Sliders in unrelated screens (`WorkoutRpeFeelSection.kt`, `CropWorkoutScreen.kt`), DataStore schema, or threshold bounds (`0.30f..1.20f`, gap `0.05f`).

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-TRK-061.4` (`docs/requirements/req_tracking_cockpit.md:3880–3900`, `ZonesSettingsActivity.kt`).
* **Historical Origin & Commit Trace**: `ATT-3542` (`14292b35`) $\rightarrow$ `ATT-7317` $\rightarrow$ `ATT-7519` (`467f7663`) $\rightarrow$ `ATT-7741` (`5fa1e164`) $\rightarrow$ `ATT-8498` (`81af2506`).
* **Root Reason for Existing Formulation**: Unified inverted 3-zone `RangeSlider` (`TTColor.Zone2/3/4`) in `track` slot without M3 stop dots, preserving `T1 >= T2 + 0.05f`.
* **Preservation of Core Invariants**: Inverted left-green/right-red track, `0.30f..1.20f` range, `0.05f` gap, public composable names (defaulted optional callback), and all `HrvDfaA1SettingsContractTest` assertions remain intact.

## Change Impact Analysis
* **Affected Requirements**: Refines `REQ-TRK-061.4` (`docs/requirements/req_tracking_cockpit.md`) and `TST-TRK-061.4` (`docs/tests/tst_tracking_cockpit.md`) with horizontal-pager gesture isolation & smooth thumb tracking.
* **Affected Architecture Elements**: `ZonesSettingsActivity.kt` (`ZoneSettingsScreen`, `HrvDfaA1SettingsContent`, `HrvDfaA1RangeSlider`, `HrvThresholdControlRow`).
* **Verification Impact & Test Surface**: Existing `HrvDfaA1SettingsContractTest.kt` + new behavioral Robolectric Compose `createComposeRule()` touch-drag test (`REQ-PRO-049.1` compliant) + `emulator-5554` PNG verification (`Rule 8.5`).
* **Risk Assessment & Invariants**: **LOW** — strictly localized Compose gesture arbitration in `ZonesSettingsActivity.kt`; preserves vertical scrolling, `T1 >= T2 + 0.05f`, and tab switching outside the slider region.

## 6. Architectural Strategy & High-Level Solution
* Lift `isSliderGestureActive` state into `ZoneSettingsScreen` (`HorizontalPager(userScrollEnabled = !isSliderGestureActive)`); propagate defaulted `onSliderGestureActiveChange: (Boolean) -> Unit = {}` through `HrvDfaA1SettingsContent`, `HrvDfaA1RangeSlider`, and `HrvThresholdControlRow`.
* On `HrvDfaA1RangeSlider` card/slider: detect `PointerEventPass.Initial` press/release + `MutableInteractionSource` (`startInteractionSource`/`endInteractionSource`) drag/press state, consume unconsumed horizontal-dominant movement in `Main` pass, and maintain local drag values synced with `t1`/`t2` when idle.
