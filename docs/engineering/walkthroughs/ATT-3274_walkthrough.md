# Stage 5 Walkthrough - ATT-3274: Fix Nested Scroll Infinite Height Constraint Crash in CropWorkoutScreen

**Ticket**: [ATT-3274](https://atrainingtracker.atlassian.net/browse/ATT-3274)  
**Parent Epic**: [ATT-3267](https://atrainingtracker.atlassian.net/browse/ATT-3267) (*Workout Analysis, Streams & Data Management*)  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Target Release**: `V4.9.40`  
**Requirement Mapping**: `REQ-UI-368` (*Interactive Start/End Workout Cropping with Destructive Sample Pruning and Full Metric Recalculation*)  
**Test Spec ID**: `TST-UI-331` (*Interactive Start/End Workout Cropping with Destructive Sample Pruning and Full Metric Recalculation Verification*)  
**Branch**: `feature/ATT-3274`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Executive Summary

During Sprint 2026-42.4 review, opening `CropWorkoutScreen` on a physical device triggered a fatal Compose layout crash:
```
java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints, which is disallowed.
```
Ticket **ATT-3274** (Sprint 2026-42.5 revision) identifies and eliminates the architectural root cause:
1. **Root Cause**: `AppModalBottomSheet` already wraps bottom sheet content in a vertically scrollable container: `Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))` while pinning modal action buttons (`actions`) to the bottom. In `CropWorkoutScreen.kt`, the root content `Column` redundantly applied `Modifier.verticalScroll(rememberScrollState())`. Because a scrollable container measures its children with infinite maximum height constraints, nesting another vertical scroll modifier inside it violates Compose layout contracts and immediately crashes with `IllegalStateException`.
2. **Layout Fix**: Removed the redundant `.verticalScroll(rememberScrollState())` modifier from the inner `Column` in `CropWorkoutScreen.kt` and cleaned up unused scroll imports (`rememberScrollState`, `verticalScroll`).
3. **Architecture Invariant KDoc**: Added extensive KDoc to `CropWorkoutScreen` documenting the `AppModalBottomSheet` parent hierarchy, scroll container delegation, and the invariant that the inner composable must never declare vertical scrolling.
4. **ACID SQLite Rollback Test**: Enhanced `WorkoutRepositoryCropTest` with `testSQLiteTransactionRollback_guaranteesZeroPartialDeletionOnFailure`, asserting strict MockK SQLite transaction lifecycle semantics (`beginTransaction()`, failure prior to `setTransactionSuccessful()`, `endTransaction()`).
5. **Portable Layout Contract Test**: Enhanced `CropWorkoutScreenContractTest` with multi-path source candidate discovery and comment-stripping regex to ensure continuous verification that `CropWorkoutScreen.kt` contains no inner vertical scrolling.
6. **9-Language Localization**: Verified 100% translation parity across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with `CropWorkoutLocalizationTest`.

---

## 2. Changes Implemented

### 2.1 Layout Hierarchy Fix (`CropWorkoutScreen.kt`)
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/crop/CropWorkoutScreen.kt`:
* Removed redundant `Modifier.verticalScroll(rememberScrollState())` on the root content `Column`.
* Removed unused imports `androidx.compose.foundation.rememberScrollState` and `androidx.compose.foundation.verticalScroll`.
* Content (Map preview, Elevation profile, RangeSlider, summary metrics) now scrolls seamlessly within `AppModalBottomSheet`'s native scroll container, while the modal action bar ("Abbrechen" / "Track zuschneiden") remains pinned to the bottom.

```kotlin
Column(
    modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
) {
    // Child composables measure bounded height within AppModalBottomSheet's scroll container
    ...
}
```

### 2.2 Comprehensive Architecture KDoc (`CropWorkoutScreen.kt`)
Documented the layout hierarchy contract directly in `CropWorkoutScreen`:
```kotlin
/**
 * Interactive bottom-sheet composable allowing athletes to trim leading and trailing GPS/sensor
 * samples from a recorded workout.
 *
 * ### Layout & Constraint Architecture
 * This screen is designed to be hosted exclusively inside [AppModalBottomSheet].
 * [AppModalBottomSheet] wraps its content inside a scrollable body container:
 * `Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))`
 * and pins its [AppModalBottomSheetDefaults.actions] row anchored at the bottom.
 *
 * **CRITICAL CONSTRAINT INVARIANT**:
 * Because [AppModalBottomSheet] already provides the vertical scroll container, this composable's
 * root [Column] **MUST NOT** apply `Modifier.verticalScroll(rememberScrollState())`. Nesting an inner
 * vertically scrollable component inside an outer vertically scrollable container results in infinite
 * maximum height constraints and triggers `IllegalStateException: Vertically scrollable component was
 * measured with an infinity maximum height constraints, which is disallowed.`
 *
 * By delegating scroll behavior entirely to [AppModalBottomSheet], the body content (map preview,
 * elevation profile, dual RangeSlider, and summary metrics) scrolls smoothly on small-screen or high-font-scaling
 * displays while the primary action buttons remain pinned, anchored, and accessible at the bottom.
 */
```

### 2.3 ACID SQLite Rollback Verification (`WorkoutRepositoryCropTest.kt`)
Added `testSQLiteTransactionRollback_guaranteesZeroPartialDeletionOnFailure` verifying MockK SQLite transaction lifecycle:
* `db.beginTransaction()` is called.
* An intentional `IllegalStateException("Disk I/O error during sample deletion")` is thrown during transaction processing.
* `db.setTransactionSuccessful()` is **never** called.
* `db.endTransaction()` is guaranteed to execute in a `finally` block, ensuring zero partial row deletions or corrupted database state.

### 2.4 Multi-Path Layout Contract Test (`CropWorkoutScreenContractTest.kt`)
Added `testCropWorkoutScreen_doesNotDeclareNestedVerticalScroll_preventingInfinityHeightConstraintCrash`:
* Tests `CropWorkoutScreen.kt` using portable path resolution (`candidatePaths`) supporting Gradle CLI, IDE runners, and CI container workspaces.
* Strips block comments (`/* ... */`) and line comments (`// ...`) prior to scanning code tokens.
* Asserts that `verticalScroll(` and `rememberScrollState` do not appear in active code.

---

## 3. Verification & Test Evidence

### 3.1 Targeted Test Execution
Command:
```bash
./gradlew testDebugUnitTest \
  --tests com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropWorkoutScreenContractTest \
  --tests com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropWorkoutLocalizationTest \
  --tests com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryCropTest \
  --tests com.atrainingtracker.trainingtracker.domain.crop.CropWorkoutMathTest
```
Result: **21 tests completed, 0 failed, 0 skipped** (PASSED in 17s).

Key targeted tests:
1. `CropWorkoutScreenContractTest`:
   - `testCropWorkoutScreen_doesNotDeclareNestedVerticalScroll_preventingInfinityHeightConstraintCrash`: PASSED
   - `testCropPreconditions_buttonDisabledWhenUnderMinPointsOrDuration`: PASSED
   - `testCropConfirmation_dialogWarnsPermanentDeletion`: PASSED
   - `testBackHandler_blocksNavigationWhileCropping`: PASSED
   - `testForceReExportButton_visibleWhenExportError`: PASSED
2. `WorkoutRepositoryCropTest`:
   - `testSQLiteTransactionRollback_guaranteesZeroPartialDeletionOnFailure`: PASSED
   - `testCropWorkout_preFlightStorageCheck_failsWhenUnder10MB`: PASSED
   - `testCropWorkout_ephemeralJsonBackupCreatedAndCleanedUp`: PASSED
   - `testCropWorkout_postOpChecksumAssertion`: PASSED
   - `testCropWorkout_concurrencyMutexSerialization`: PASSED
   - `testCropWorkout_synchronousExportInvalidation`: PASSED
   - `testCropWorkout_workManagerEnqueueFailure_setsExportStatusError`: PASSED
3. `CropWorkoutLocalizationTest`:
   - 10 required keys verified across 9 locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt-rBR`): PASSED
4. `CropWorkoutMathTest`:
   - Cumulative monotonic clamping (`EPSILON = 1e-6`), zero-anchoring, `LINE_DISTANCE_m` Haversine row recalculation, extrema re-derivation, `Double.isFinite` filtering: PASSED

### 3.2 Clean-Room Full Regression Suite
Command:
```bash
./gradlew testDebugUnitTest
```
Result: **BUILD SUCCESSFUL** - 100% unit test pass rate with zero regressions across the entire codebase.

---

## 4. Visual Consistency & Layout Invariants (Rule 23)

* **Layout Host**: Hosted inside `AppModalBottomSheet`.
* **Pinned Action Bar**: Cancel and Crop action buttons remain fixed at the bottom outside the scrollable body, providing immediate accessibility.
* **Scroll Container**: The scroll container wraps map preview, elevation profile, dual RangeSlider, and summary metrics. On small screens or with accessibility font scaling ($2.0\times$), content scrolls freely between the title header and pinned actions.
* **No Infinity Height Crash**: The inner `Column` has no scroll modifier, so its height is bounded by the outer scroll container's available height.

---

## 5. Requirement Governance & ASPICE Traceability

* **Requirement**: `REQ-UI-368` -> **Status**: `Verified`
* **Test Case**: `TST-UI-331` -> **Status**: `Verified`
* **Verification Script**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-42.5` -> **PASSED (0 errors)**.
