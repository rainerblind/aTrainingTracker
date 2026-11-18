# Stage 3: Implementation Plan - ATT-8105: Gesture-Isolated HrvDfaA1RangeSlider in ZonesSettingsActivity

**Ticket**: [ATT-8105](https://rainerblind.atlassian.net/browse/ATT-8105) | **Sub-task**: `(Pending submit-stage)` (`[Impl-Plan]`)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV*) | **Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-17_03_Nacht`  
**Requirement Mapping**: `REQ-TRK-086` (refining `REQ-TRK-061.4`) | **Test Mapping**: `TST-TRK-086`  
**Branch**: `feature/ATT-8105` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18  

---

## 1. Problem Description & Background
On tab 3 (`HRV (DFA α1)`, `page == 3`) of `ZonesSettingsActivity.kt`, `HorizontalPager` leaves `userScrollEnabled = true` and lacks gesture arbitration around `HrvDfaA1RangeSlider`. Dragging thumbs rightward (`T2`/`T1`, `dx > 0`) or touching `Card` padding loses the slop race and swipes to `PWR_BIKE` (`page == 2`), while unbuffered async DataStore writes risk mid-drag thumb snap-back.

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-086` (`REQ-TRK-086.1`..`REQ-TRK-086.3`, refines `REQ-TRK-061.4`)
* **Test Mapping**: `TST-TRK-086` (`TST-TRK-086.1`..`TST-TRK-086.3`)
* **SWE.2 / SWE.3 Docs**: `No new subsystem/service/repo/DB; docs/architecture.md and detailed_design/* untouched.`

## 3. System Invariants & Preserved Behavior
1. **Inverted 3-Zone Track & Math**: `0.30f..1.20f`, $T_1 \ge T_2 + 0.05f$, `TTColor.Zone2/3/4` left-to-right track untouched.
2. **Navigation & Scroll Parity**: Outside-slider horizontal swipes switch tabs (`page 3 -> 2`); vertical scrolling (`verticalScroll`) works unimpeded.
3. **Backward Compatibility**: Public composable names preserved with defaulted `onSliderGestureActiveChange: (Boolean) -> Unit = {}`; `HrvDfaA1SettingsContractTest` & 9-locale `strings_hrv.xml` preserved.
4. **Test Governance (`REQ-PRO-049.1`)**: New tests use Compose UI behavior only—zero `app/src/main/` file reads.

## 4. Proposed Architectural Changes
* **Component 1 (`ZonesSettingsActivity.kt`)**: Shared `Modifier.isolateFromHorizontalPager(onGestureActiveChange)` helper using `PointerEventPass.Initial` down/up/cancel detection + `PointerEventPass.Main` horizontal-dominant residual consumption (leaving vertical deltas for `verticalScroll`), paired with M3 `MutableInteractionSource` (`startInteractionSource`/`endInteractionSource`) and `DisposableEffect` cleanup, lifting `isSliderGestureActive` to `HorizontalPager(userScrollEnabled = !isSliderGestureActive)`. Local drag range state in `HrvDfaA1RangeSlider` tracks thumb position synchronously while dragging (`!isDragging` syncs from `t1`/`t2`), deduplicating unchanged threshold emits and committing via `onValueChangeFinished`.
* **Component 2 (`HrvDfaA1RangeSliderGestureTest.kt`)**: Behavioral Robolectric `createComposeRule()` (`performTouchInput`) suite + Light/Dark PNG capture (`docs/engineering/walkthroughs/img/ATT-8105_hrv_slider_zones.png`).
* **UI Consistency (`Rule 23` / `docs/design_guidelines.md`)**:
  * *Reference screen / component*: `WorkoutDetailTabbedContent.kt` (`userScrollEnabled` + `PointerEventPass.Initial` isolation) & `ZonesSettingsActivity.kt` (`HrvDfaA1RangeSlider`).
  * *Reused components*: M3 `RangeSlider`, `Slider`, `Card`, `HorizontalPager`, `ATrainingTrackerTheme`.
  * *Theme tokens*: Existing `MaterialTheme.colorScheme.*`, `TTColor.Zone2/3/4`, `MaterialTheme.shapes.medium`, `12.dp` padding.
  * *New one-off styles & justification*: None (zero visual/string changes).

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Gesture-Isolation Modifier & Lifted `userScrollEnabled` Wiring (`REQ-TRK-086.1`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt`
* Changes: Verify `check-gate` `GATE_PASSED` first (`Rule 4.1`). Add `isSliderGestureActive` in `ZoneSettingsScreen` driving `HorizontalPager(userScrollEnabled = !isSliderGestureActive)` (resetting if `pagerState.currentPage != 3`). Add `onSliderGestureActiveChange: (Boolean) -> Unit = {}` to `HrvDfaA1SettingsContent`, `HrvDfaA1RangeSlider`, and `HrvThresholdControlRow`; apply `PointerEventPass.Initial` + `Main` horizontal isolation modifier + `DisposableEffect` cleanup + M3 `MutableInteractionSource` tracking (`startInteractionSource`/`endInteractionSource`, and `Slider` `interactionSource` when `showSlider == true`).

### Step 2: Local Drag Buffering in `HrvDfaA1RangeSlider` (`REQ-TRK-086.2`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt`
* Changes: Track `isDragging` and local `dragT1`/`dragT2` synced with `(t1, t2)` only when `!isDragging`; update local state smoothly on every `onValueChange`, emit `onThresholdsChange` only when rounded clamped values actually change, and clear `isDragging` + final sync in `onValueChangeFinished`.

### Step 3: Behavioral Robolectric Compose & `Rule 8.5` PNG Tests (`TST-TRK-086.1..3`)
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/HrvDfaA1RangeSliderGestureTest.kt`, `docs/engineering/walkthroughs/img/ATT-8105_hrv_slider_zones.png`
* Changes: Add `createComposeRule()` tests with `performTouchInput` for rightward/leftward `T1`/`T2` drags, `Card`-padding drags, delayed DataStore stability, outside-slider `swipeRight` tab switch (`page 3 -> 2`), `verticalScroll`, `HrvThresholdControlRow(showSlider = true)` isolation, and `@GraphicsMode(NATIVE)` Light/Dark PNG capture (`read_file` 4-point inspection).
* Command: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1RangeSliderGestureTest" --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1SettingsContractTest"`

## 6. Verification & Rollback Plan
* **Verification**: Targeted Robolectric Compose + contract tests in Stage 4; emulator-5554 interactive check + clean-room Stage 5 verification; `verify_requirement_governance.py` exit `0`.
* **Rollback**: Branch isolation on `feature/ATT-8105` allows full revert without impacting `sprint/2026-11-17_03_Nacht`.
