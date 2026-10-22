# Stage 1 Analysis: ATT-3274 - Crop start and end points of a recorded workout (Revision: Nested Scroll Constraint Fix)

**Ticket**: [ATT-3274](https://rainerblind.atlassian.net/browse/ATT-3274)  
**Sub-task**: [ATT-3720](https://rainerblind.atlassian.net/browse/ATT-3720) (`[Analysis]`)  
**Parent Epic**: [ATT-3267](https://rainerblind.atlassian.net/browse/ATT-3267) (*Workout Analysis, Streams & Data Management*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Branch**: `feature/ATT-3274`  
**Requirement Mapping**: `REQ-UI-368` (*Interactive Crop Workout Track View & Metrics Recalculation*)  
**Test Mapping**: `TST-UI-331` (*Crop Workout Validation, Atomic Recalculation & Layout Verification Suite*)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Problem Statement & Motivation

Athletes occasionally start workout tracking prematurely (e.g., while waiting in a parking lot, sitting in a car, or indoors with noisy cold-start GPS fixes) or forget to stop tracking immediately upon finishing an activity (e.g., driving home with GPS recording still active).

Currently, **aTrainingTracker** provides an interactive **Crop Workout** bottom sheet (`CropWorkoutScreen.kt`) allowing athletes to prune unwanted start/end samples, atomically recalculate summary statistics, extrema, streams, and laps, and regenerate clean export files.

However, during physical device verification on Google Pixel 10 in the Sprint 2026-42.4 Review, opening `CropWorkoutScreen` resulted in an immediate fatal crash as soon as workout sample points finished loading:
```
java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints, which is disallowed. One of the common causes is nesting vertically scrollable component inside an already vertically scrollable component...
    at androidx.compose.foundation.CheckScrollableContainerConstraintsKt.checkScrollableContainerConstraints-0kLqBqw(CheckScrollableContainerConstraints.kt:33)
    at androidx.compose.foundation.gestures.ScrollableDefaults$defaultScrollMotionDuration$1.invoke-0kLqBqw(ScrollableDefaults.kt:142)
    at androidx.compose.foundation.ScrollModifierNode.measure-3p2s80s(Scroll.kt:294)
    at com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropWorkoutScreenKt.CropWorkoutScreen(CropWorkoutScreen.kt:143)
```

This fatal runtime exception prevents athletes from accessing or using the crop workout feature altogether.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Nested Scroll Hierarchy in Jetpack Compose
In Jetpack Compose, a vertically scrolling layout (`Modifier.verticalScroll`) measures its children by providing incoming height constraints with an unbounded upper bound:
$$\text{constraints.maxHeight} = \infty\quad (\text{Constraints.Infinity})$$

This measurement allows scrollable content to be arbitrarily tall so that the outer scroll state can calculate the total viewport scroll range:
$$\text{scrollRange} = \text{contentHeight} - \text{viewportHeight}$$

However, Jetpack Compose strictly prohibits placing a second vertically scrolling component directly inside an unbounded height container:
If a child component also declares `Modifier.verticalScroll(...)` while measured under $\text{maxHeight} = \infty$, the child cannot determine its own viewport height relative to its content, triggering Compose's mandatory guard check in `CheckScrollableContainerConstraints.kt`:
```kotlin
check(constraints.maxHeight != Constraints.Infinity) {
    "Vertically scrollable component was measured with an infinity maximum height constraints, which is disallowed."
}
```

### 2.2 Trace of the Conflicting Layouts
Examining the UI hierarchy across `AppModalBottomSheet.kt` and `CropWorkoutScreen.kt`:

1. **Outer Scroll Container (`AppModalBottomSheet.kt:253-264`)**:
   ```kotlin
   // Content Body
   if (scrollable) {
       Column(
           modifier = Modifier
               .fillMaxWidth()
               .weight(1f, fill = false)
               .verticalScroll(rememberScrollState()) // <--- Outer scrollable container (scrollable defaults to true)
               .padding(horizontal = 20.dp, vertical = 12.dp),
           verticalArrangement = Arrangement.spacedBy(16.dp)
       ) {
           content()
           Spacer(modifier = Modifier.height(16.dp))
       }
   }
   ```
   `AppModalBottomSheet` already provides standard, robust scrolling behavior for modal sheets with `scrollable = true` (the default parameter value). It wraps `content()` in a `Column` that applies `Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())`.

2. **Inner Redundant Scroll Container (`CropWorkoutScreen.kt:140-145`)**:
   ```kotlin
   Column(
       modifier = Modifier
           .fillMaxWidth()
           .verticalScroll(rememberScrollState()), // <--- CONFLICT: Nested verticalScroll under infinity height!
       verticalArrangement = Arrangement.spacedBy(16.dp)
   ) {
       // 1. Map preview of cropped segment
       // 2. Dual-range slider
       // 3. Telemetry summary card
       ...
   }
   ```
   When `uiState.isLoading` is `true`, `CropWorkoutScreen` only renders a `Box` with fixed `height(200.dp)` containing a `CircularProgressIndicator`, which measures fine.
   However, as soon as `uiState.isLoading` flips to `false` (when points are loaded from the database), Compose composes lines 140–145: the inner `Column` with `.verticalScroll(rememberScrollState())` is measured inside `AppModalBottomSheet`'s outer `.verticalScroll(...)`, triggering the fatal `IllegalStateException`.

---

## 3. Scope Bounding & Chesterton's Fence Archaeology

### 3.1 Chesterton's Fence Audit
- **Original Requirement**: `REQ-UI-368` (*Interactive Crop Workout Track View & Metrics Recalculation*, Sprint 2026-42.4).
- **Origin of Inner Scroll**: During early standalone prototyping of `CropWorkoutScreen`, it was developed as a standalone test screen before being placed inside `AppModalBottomSheet`. The author added `.verticalScroll(rememberScrollState())` to the root Column so the sliders and map could scroll on small screens. When subsequently integrated into `AppModalBottomSheet`, the redundant inner scroll modifier was accidentally left in place.
- **Invariant Preservation**:
  - The entire content must remain comfortably scrollable on small mobile viewports (e.g., $360\text{ dp}$ width, small vertical heights).
  - `AppModalBottomSheet`'s outer scroll container completely fulfills this requirement, providing correct edge fading, scrollbar support, top/bottom padding, and bottom sheet drag physics.
  - Zero database pruning, metric recalculation, lap adjustment, or export synchronization logic is altered by this UI layout fix.

### 3.2 In-Scope vs. Out-of-Scope
* **In-Scope**:
  1. Remove `.verticalScroll(rememberScrollState())` from the inner `Column` in `CropWorkoutScreen.kt:140-145`, leaving `modifier = Modifier.fillMaxWidth()`.
  2. Verify that `AppModalBottomSheet` correctly hosts the scrolling content.
  3. Author automated layout/contract tests in `CropWorkoutScreenContractTest.kt` asserting that `CropWorkoutScreen` content renders inside `AppModalBottomSheet` without nested scroll constraint exceptions and asserting structural absence of nested `.verticalScroll` on the content container.
  4. Pass all clean-room unit and regression tests.
* **Out-of-Scope**:
  - Altering the mathematical pruning algorithms (`CropWorkoutMath.kt`).
  - Modifying database transaction mechanics (`WorkoutRepository.kt`, `WorkoutSamplesDatabaseManager.java`).
  - Modifying export formats (TCX/FIT/GPX/CSV).

---

## 4. Proposed Solution & Architecture

### 4.1 UI Layout Streamlining in `CropWorkoutScreen.kt`
Replace lines 140–145 of `CropWorkoutScreen.kt`:
```kotlin
// BEFORE (Crash):
Column(
    modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(16.dp)
)

// AFTER (Fixed):
Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(16.dp)
)
```

### 4.2 Automated Layout Verification Test
Add a dedicated test case to `CropWorkoutScreenContractTest.kt`:
1. **Structural AST / Constraint Contract Test**:
   Verify that `CropWorkoutScreen` does not introduce an inner `Modifier.verticalScroll` that conflicts with `AppModalBottomSheet`'s outer scroll container.
2. **Measurement Invariant Test**:
   Verify that the content column can be measured cleanly without throwing `IllegalStateException`.

---

## 5. Acceptance Criteria & Verification Plan

* **Criterion 1 (Crash Elimination)**:
  - *Given* a recorded workout loaded into `CropWorkoutScreen`,
  - *When* sample points finish loading and the content renders inside `AppModalBottomSheet`,
  - *Then* no `IllegalStateException` or nested scroll constraint violation occurs.
* **Criterion 2 (Full Scrollability)**:
  - *Given* a small display screen (e.g. $360\text{ dp} \times 640\text{ dp}$),
  - *When* viewing `CropWorkoutScreen`,
  - *Then* the athlete can scroll smoothly through the track map, dual-range slider, and recalculated metrics card via `AppModalBottomSheet`'s native scroll container.
* **Criterion 3 (Clean-Room Test Pass Rate)**:
  - *Given* the entire project test suite,
  - *When* `./gradlew testDebugUnitTest` is executed,
  - *Then* 100% of tests pass with zero failures.
