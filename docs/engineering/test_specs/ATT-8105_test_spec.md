# Stage 2: Requirement & Test Specification - ATT-8105

**Ticket**: [ATT-8105](https://rainerblind.atlassian.net/browse/ATT-8105) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8105`  
**Requirement Mapping**: `REQ-TRK-086` (refining `REQ-TRK-061.4`) | **Test Spec ID**: `TST-TRK-086` | **Date**: 2026-11-18  

---

## 1. Requirement Specification (`REQ-TRK-086` `[Planned]`)

### 1.1 Attributes & Rationale
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- **Implementation**: `ZonesSettingsActivity.kt` (`ZoneSettingsScreen`, `HrvDfaA1SettingsContent`, `HrvDfaA1RangeSlider`, `HrvThresholdControlRow`)
- **Rationale**: On tab 3 (`HRV (DFA α1)`, `page == 3`) of `ZoneSettingsScreen`, `HorizontalPager` omitted `userScrollEnabled`, causing rightward drags on `HrvDfaA1RangeSlider` thumbs (especially right thumb `T2`) or touches in `Card` padding to lose the slop race and switch tabs to `PWR_BIKE` (`page == 2`), while unbuffered async DataStore writes risked thumb snap-back.

### 1.2 Functional & Architectural Requirements
1. **`REQ-TRK-086.1` (HorizontalPager Gesture Isolation via `PointerEventPass.Initial` & `MutableInteractionSource`)**:
   - `ZoneSettingsScreen` SHALL lift `isSliderGestureActive` state into `HorizontalPager(userScrollEnabled = !isSliderGestureActive)` and propagate a defaulted `onSliderGestureActiveChange: (Boolean) -> Unit = {}` callback into `HrvDfaA1SettingsContent`, `HrvDfaA1RangeSlider`, and `HrvThresholdControlRow`.
   - `HrvDfaA1RangeSlider` (and `HrvThresholdControlRow` when `showSlider == true`) MUST combine `PointerEventPass.Initial` press/release tracking on the slider `Card`/container with M3 `MutableInteractionSource` (`startInteractionSource` / `endInteractionSource`) so `isSliderGestureActive` transitions to `true` immediately on touch down (zero slop-race window) and resets to `false` on release/cancel/disposal (`DisposableEffect`), consuming residual horizontal-dominant movement in `PointerEventPass.Main` while preserving `verticalScroll` and outside-slider tab swipes.
2. **`REQ-TRK-086.2` (Bidirectional Thumb Tracking & Local Drag State)**:
   - Both rightward (`T2`/`T1` right drag on `page == 3`) AND leftward (`T1`/`T2` left drag) gestures on `HrvDfaA1RangeSlider` SHALL track smoothly without triggering tab switches or pager overscroll.
   - `HrvDfaA1RangeSlider` MUST maintain local drag state synced with `t1`/`t2` only when idle (`!isDragging`) to eliminate async DataStore snap-back, while enforcing `0.30f..1.20f` and $T_1 \ge T_2 + 0.05f$.
3. **`REQ-TRK-086.3` (Behavioral Compose Tests, `Rule 8.5` Screenshot & Localization)**:
   - Gesture behavior SHALL be verified via Robolectric `createComposeRule()` (`performTouchInput`) with zero `app/src/main/` file reads (`REQ-PRO-049.1` / `ATT-8198`), plus Light/Dark PNG capture at `docs/engineering/walkthroughs/img/ATT-8105_hrv_slider_zones.png` (`Rule 8.5`; `Localization Parity: N/A - no UI strings added or modified`).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
- *Given* `ZoneSettingsScreen` on tab 3 (`page == 3`), *When* dragging `HrvDfaA1RangeSlider` thumb `T2` or `T1` rightward or leftward (including inside slider `Card` padding), *Then* `pagerState.currentPage` stays `3`, thresholds update smoothly within `0.30f..1.20f` ($T_1 \ge T_2 + 0.05f$) without snap-back, and `userScrollEnabled` restores on release.
- *Given* tab 3 with the slider idle, *When* swiping horizontally outside the slider `Card` OR scrolling vertically, *Then* `HorizontalPager` switches cleanly to `page == 2` and `verticalScroll` advances smoothly.
- **Invariants**: Inverted 3-zone track (`TTColor.Zone2/3/4`); `0.30f..1.20f` & $T_1 \ge T_2 + 0.05f$; backward-compatible signatures; `HrvDfaA1SettingsContractTest` & 9-locale `strings_hrv.xml` parity preserved.

### 1.4 Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: `REQ-TRK-086` refining `REQ-TRK-061.4` (`ZonesSettingsActivity.kt`).
- **Historical Origin & Commit Trace**: `ATT-3542` (`14292b35`) $\rightarrow$ `ATT-7317` $\rightarrow$ `ATT-7519` (`467f7663`) $\rightarrow$ `ATT-7741` (`5fa1e164`) $\rightarrow$ `ATT-8498` (`81af2506`).
- **Root Reason for Existing Formulation**: `ATT-7741` unified the inverted 3-zone M3 `RangeSlider` inside `HorizontalPager` tab 3 without `userScrollEnabled` arbitration or local drag buffering.
- **Preservation of Core Invariants**: Preserves inverted track colors, bounds/gap invariants, composable names, vertical scroll, non-slider tab swipes, and contract assertions.

---

## 2. Test Specification (`TST-TRK-086` `[Proposed]`)
- **`TST-TRK-086.1` (`HrvDfaA1RangeSliderGestureTest.kt` — Robolectric `createComposeRule()`, zero `src/main` reads per `REQ-PRO-049.1`)**:
  - Render `ZoneSettingsScreen` / tab-3 `HorizontalPager` host; perform `performTouchInput` rightward (`T2` right drag, `T1` right drag, padding-edge horizontal drag) and leftward (`T1` left drag, `T2` left drag) gestures. Assert `pagerState.currentPage == 3`, smooth monotonic `T1`/`T2` updates respecting `0.30f..1.20f` and $T_1 \ge T_2 + 0.05f$ under delayed DataStore emission, and `userScrollEnabled` recovery on release.
- **`TST-TRK-086.2` (`HrvDfaA1RangeSliderGestureTest.kt`)**:
  - Perform horizontal `swipeRight` outside the slider `Card` on `page == 3` and assert switch to `page == 2`; perform `swipeUp` / `swipeDown` over `HrvDfaA1SettingsContent` and assert `verticalScroll` advances; verify `HrvThresholdControlRow(showSlider = true)` toggles `onSliderGestureActiveChange`.
- **`TST-TRK-086.3` (`HrvDfaA1SettingsContractTest.kt` & `Rule 8.5` PNG)**:
  - Run `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1RangeSliderGestureTest" --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1SettingsContractTest"` and inspect Light/Dark PNG `docs/engineering/walkthroughs/img/ATT-8105_hrv_slider_zones.png` via `read_file`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-086.1` | Compose UI (Robolectric) | `ZoneSettingsScreen`, `HrvDfaA1RangeSlider` (bidirectional drag + local state) | `REQ-TRK-086.1`, `REQ-TRK-086.2` | Proposed |
| `TST-TRK-086.2` | Compose UI (Robolectric) | `HorizontalPager` outside swipe, `verticalScroll`, `HrvThresholdControlRow` | `REQ-TRK-086.1`, `REQ-TRK-086.3` | Proposed |
| `TST-TRK-086.3` | Contract + `Rule 8.5` PNG | `HrvDfaA1SettingsContractTest`, `ATT-8105_hrv_slider_zones.png` | `REQ-TRK-086.3`, `REQ-TRK-061.4` | Proposed |
