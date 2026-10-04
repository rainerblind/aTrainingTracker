# Stage 2: Requirement & Test Specification - ATT-2304: Restore Immediate Collapsing Header Expansion When Scrolling Down in Workout Details

**Ticket**: [ATT-2304](https://rainerblind.atlassian.net/browse/ATT-2304)  
**Sub-task**: [ATT-2343](https://rainerblind.atlassian.net/browse/ATT-2343) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-266` (*Immediate Collapsing Header Quick Return Expansion upon Downward Scrolling*)  
**Test Spec ID**: `TST-UI-225`  
**Branch**: `feature/ATT-2304`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (`REQ-UI-266`)

### 1.1 Problem Statement & Rationale
In Sprint Review testing of ATT-2178, an essential navigation and browsing gesture was altered in `MapDetailLayout.kt` and `CollapsingAppBarNestedScrollConnection.kt`. When scrolling upward through detailed workout sessions (such as the Einstein Halbmarathon with 40+ segment efforts and laps), the upper metadata header smoothly collapses. However, upon scrolling downward anywhere in the view, the header failed to scroll back down into view. The athlete was forced to scroll all the way back to the topmost boundary (item 0) before the header reappeared.

This requirement restores the standard Material Design "Quick Return" app bar interaction pattern across `CollapsingAppBarNestedScrollConnection.kt` while preserving sub-pixel float precision and preventing gesture trapping.

### 1.2 Functional & Architectural Requirements
The system SHALL restore immediate Quick Return downward header expansion in `CollapsingAppBarNestedScrollConnection.kt` and `MapDetailLayout.kt` (ATT-2304):
1. **Immediate Downward Expansion in `onPreScroll`**:
   - When the user scrolls downward (`available.y > 0f`) and the header is currently in a collapsed or partially collapsed state (`rawOffset < 0f`), `CollapsingAppBarNestedScrollConnection.onPreScroll` SHALL consume available delta up to `0f`, immediately translating `appBarOffset` towards `0`.
   - The delta consumed SHALL be `(rawOffset + available.y).coerceIn(-appBarMaxHeight.toFloat(), 0f) - rawOffset`.
   - Once `rawOffset == 0f` (header fully expanded), `onPreScroll` SHALL return `Offset.Zero` for downward gestures, allowing all subsequent downward delta to pass uninhibited to child scroll containers.
2. **Smooth Upward Collapse Preservation**:
   - For upward gestures (`available.y < 0f`), when `rawOffset > -appBarMaxHeight`, `onPreScroll` SHALL continue to consume deltas to smoothly collapse `rawOffset` towards `-appBarMaxHeight.toFloat()`.
   - Once `rawOffset == -appBarMaxHeight.toFloat()`, `onPreScroll` SHALL return `Offset.Zero`, allowing child scroll containers to scroll their contents upward without locking.
3. **Sub-Pixel Precision Invariant**:
   - The connection SHALL maintain high-precision floating-point delta accumulation in `rawOffset: Float`, eliminating integer truncation micro-stutters (`available.y.toInt()`), with `appBarOffset` exposed as `rawOffset.roundToInt()`.
4. **Child Scroll Coordination**:
   - Child scroll containers (e.g. `lowerColumn` in `MapDetailLayout`, `LazyColumn` in `WorkoutTabsScreen`, `WorkoutSummariesListFragment`, `SegmentsTabsScreen`, etc.) SHALL receive full unconsumed scroll deltas once the header reaches its respective boundary (0 when expanding downward, `-appBarMaxHeight` when collapsing upward).
5. **Configurable Quick Return Mode**:
   - `CollapsingAppBarNestedScrollConnection` SHALL provide a `quickReturn: Boolean = true` parameter (defaulting to `true`), allowing callers full control while restoring Quick Return globally by default.

### 1.3 Chesterton's Fence Audit (`REQ-PRO-022`)
* **Original Requirement ID & Target**: Refines and supersedes Clause 1 of `REQ-UI-254` (*Aftermath/Details: Non-Blocking Nested Scroll Dispatch, Sub-Pixel Precision & Optimized High-Density Metadata Rendering in MapDetailLayout*).
* **Historical Origin & Commit Trace**: Sprint `2026-40.14` (`ATT-2178`, commit `91fb65c1` and `083d1e04`).
* **Root Reason for Existing Formulation**: Clause 1 in `REQ-UI-254` deferred downward scroll consumption to `onPostScroll` based on a concern that downward gestures in lower analytics charts would squash the view. Physical testing revealed this broke the Quick Return gesture expected by users.
* **Preservation of Core Invariants**: Sub-pixel floating precision (`rawOffset`), bounded height translation `[-appBarMaxHeight, 0]`, minimum map height protection (`SplitPaneMath.MIN_MAP_HEIGHT` = 120dp), and smooth 60/120 fps rendering are 100% strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Immediate Downward Expansion / Quick Return)**:
  * *Given* a workout detail view where the header is partially or fully collapsed (`appBarOffset < 0`),
  * *When* the user scrolls downward (`available.y > 0`),
  * *Then* the header SHALL immediately begin expanding and scrolling back down into view in `onPreScroll`.
* **Criterion 2 (Unimpeded Child Scrolling upon Boundary Reach)**:
  * *Given* the header expanding downward,
  * *When* `appBarOffset` reaches `0` (fully expanded),
  * *Then* all remaining and subsequent downward scroll delta SHALL pass to child scroll containers without gesture trapping.
* **Criterion 3 (Unimpeded Child Scrolling upon Full Collapse)**:
  * *Given* the header collapsing upward,
  * *When* `appBarOffset` reaches `-appBarMaxHeight` (fully collapsed),
  * *Then* all remaining and subsequent upward scroll delta SHALL pass to child scroll containers.
* **Criterion 4 (Zero Integer Truncation Jitter)**:
  * *Given* fractional sub-pixel scroll movements (e.g. `0.3f`),
  * *When* deltas are dispatched,
  * *Then* fractional offsets SHALL accumulate in `rawOffset` without discarding sub-pixel movements.

---

## 2. Test Specification (`TST-UI-225`)

### Test Case 1: Downward Scroll Immediate Expansion in `onPreScroll` (`TST-UI-225.1`)
* **Scope**: Unit Test (`CollapsingAppBarNestedScrollConnectionTest.kt`)
* **Preconditions**: `connection` initialized with `initialAppBarMaxHeight = 300`, collapsed to `-200`.
* **Action**: Dispatch downward scroll `onPreScroll(available = Offset(0f, 50f), source = NestedScrollSource.UserInput)`.
* **Expected Result**: 
  - `consumed.y` equals `50f`.
  - `connection.appBarOffset` updates immediately from `-200` to `-150`.

### Test Case 2: Downward Scroll Clamping at 0 and Child Excess Handoff (`TST-UI-225.2`)
* **Scope**: Unit Test (`CollapsingAppBarNestedScrollConnectionTest.kt`)
* **Preconditions**: `connection` collapsed to `-100`.
* **Action**: Dispatch downward scroll `onPreScroll(available = Offset(0f, 250f), source = NestedScrollSource.UserInput)`.
* **Expected Result**:
  - `consumed.y` equals `100f` (only amount needed to reach 0).
  - `connection.appBarOffset` equals `0`.
  - Unconsumed delta (`150f`) remains available for child containers.

### Test Case 3: Fully Expanded Downward Scroll Defers 100% to Child (`TST-UI-225.3`)
* **Scope**: Unit Test (`CollapsingAppBarNestedScrollConnectionTest.kt`)
* **Preconditions**: `connection` at `appBarOffset = 0` (fully expanded).
* **Action**: Dispatch downward scroll `onPreScroll(available = Offset(0f, 100f), source = NestedScrollSource.UserInput)`.
* **Expected Result**:
  - `consumed` equals `Offset.Zero`.
  - `connection.appBarOffset` remains `0`.

### Test Case 4: Upward Scroll Collapse & Boundary Clamping (`TST-UI-225.4`)
* **Scope**: Unit Test (`CollapsingAppBarNestedScrollConnectionTest.kt`)
* **Preconditions**: `connection` at `appBarOffset = 0`.
* **Action**: Dispatch upward scroll `onPreScroll(available = Offset(0f, -500f), source = NestedScrollSource.UserInput)`.
* **Expected Result**:
  - `consumed.y` equals `-300f`.
  - `connection.appBarOffset` equals `-300`.
  - Further upward scroll returns `Offset.Zero`.

### Test Case 5: Sub-Pixel Precision Accumulation (`TST-UI-225.5`)
* **Scope**: Unit Test (`CollapsingAppBarNestedScrollConnectionTest.kt`)
* **Preconditions**: `connection` initialized.
* **Action**: Dispatch multiple sub-pixel deltas (`-0.4f`, `+0.3f`, etc.).
* **Expected Result**: Sub-pixel deltas accumulate accurately without integer truncation micro-stutter.

### Test Case 6: Clean-Room Full Suite Regression (`TST-UI-225.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate across all project unit tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-225.1` | Unit | `CollapsingAppBarNestedScrollConnectionTest` | `REQ-UI-266` (Clause 1) | Specified |
| `TST-UI-225.2` | Unit | `CollapsingAppBarNestedScrollConnectionTest` | `REQ-UI-266` (Clause 1, 4) | Specified |
| `TST-UI-225.3` | Unit | `CollapsingAppBarNestedScrollConnectionTest` | `REQ-UI-266` (Clause 4) | Specified |
| `TST-UI-225.4` | Unit | `CollapsingAppBarNestedScrollConnectionTest` | `REQ-UI-266` (Clause 2) | Specified |
| `TST-UI-225.5` | Unit | `CollapsingAppBarNestedScrollConnectionTest` | `REQ-UI-266` (Clause 3) | Specified |
| `TST-UI-225.6` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
