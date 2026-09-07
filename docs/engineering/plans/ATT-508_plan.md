# Implementation Plan: Altitude Bounds Sanitization, Strict Containment & Extrema Self-Healing (ATT-508)

## 1. Problem Statement & Context
* **Original Issue**: In workout "Einkaufstour (Ca Savio) #9" (04.08.2026, 19:39) recorded in Ca' Savio (Italy, sea level ~0-3 m elevation), the elevation profile chart displayed unplausible bounds of **-63 m** and **+60 m** (123 m span), compressing a flat 0 m route to a horizontal line in the center.
* **New Finding (Iteration 2)**: In user on-device verification of workout "Kurz zum Bäcker" (07.09.2026, 06:21, 2.20 km in Schönaich), the chart bounds were 364 m to 384 m, but the elevation curve between 0.1 km and 0.4 km dipped below 364 m (~360 m), rendering below the bottom axis baseline into the padding area (`Screenshot_20260907-170700_cropping_issue.png`).
* **Root Causes**:
  1. `calculateElevationBounds` in `ElevationProfile.kt` accepted `minAltitudeOverride` if it was within 15 m of `streamMin`, even when `minAltitudeOverride > streamMin`. This resulted in `bounds.min` being higher than actual rendered points, causing negative normalized coordinates and clipping.
  2. `reconcileAltitudeExtrema` in `WorkoutDataMapper.kt` only detected negative rogue spikes (`recordedMin < streamMin - 15.0`) and absurd values, but did not heal when `recordedMin > streamMin` or `recordedMax < streamMax`.

---

## 2. User Review Required

> [!IMPORTANT]
> **Strict Containment Invariant**: The chart's vertical bounds `[min, max]` MUST strictly envelope all points to be rendered: $[S_{\min}, S_{\max}] \subseteq [\text{bounds.min}, \text{bounds.max}]$. Under no circumstances will any point on the profile curve or scrubber marker ever be plotted below the chart bottom bound or above the ceiling.

> [!NOTE]
> **Extrema Self-Healing**: If persisted SQLite extrema are higher than the stream minimum (`recordedMin > streamMin`) or lower than the stream maximum (`recordedMax < streamMax`), `WorkoutDataMapper` reconciles them to the true stream extrema and updates SQLite `extrema_values`.

---

## 3. Proposed Changes

### Component 1: `ElevationProfile.kt` (UI Presentation Layer)
**File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`

#### [MODIFY] `calculateElevationBounds`
* Refine bounds sanitization with strict containment:
  ```kotlin
  val sanitizedMin = if (minAltitudeOverride != null) {
      if (minAltitudeOverride < streamMin - outlierToleranceMeters || minAltitudeOverride > streamMin) {
          streamMin
      } else {
          minAltitudeOverride
      }
  } else {
      streamMin
  }

  val sanitizedMax = if (maxAltitudeOverride != null) {
      if (maxAltitudeOverride > streamMax + outlierToleranceMeters || maxAltitudeOverride < streamMax) {
          streamMax
      } else {
          maxAltitudeOverride
      }
  } else {
      streamMax
  }

  val effectiveMinRaw = minOf(sanitizedMin, streamMin)
  val effectiveMaxRaw = maxOf(sanitizedMax, streamMax)
  val currentSpan = effectiveMaxRaw - effectiveMinRaw

  return if (currentSpan < minSpanMeters) {
      val mid = (effectiveMinRaw + effectiveMaxRaw) / 2.0
      val expandedMin = minOf(mid - (minSpanMeters / 2.0), streamMin)
      val expandedMax = maxOf(mid + (minSpanMeters / 2.0), streamMax)
      ElevationBounds(expandedMin, expandedMax, expandedMax - expandedMin)
  } else {
      ElevationBounds(effectiveMinRaw, effectiveMaxRaw, currentSpan.coerceAtLeast(1.0))
  }
  ```

---

### Component 2: `WorkoutDataMapper.kt` (Self-Healing Data Layer)
**File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt`

#### [MODIFY] `reconcileAltitudeExtrema`
* Detect and heal extrema that contradict stream envelope:
  ```kotlin
  val authoritativeMin = if (recordedMin == null || recordedMin < streamMin - 15.0 || recordedMin > streamMin) {
      needUpdate = true
      streamMin
  } else {
      recordedMin
  }

  val authoritativeMax = if (recordedMax == null || recordedMax > streamMax + 15.0 || recordedMax < streamMax) {
      needUpdate = true
      streamMax
  } else {
      recordedMax
  }
  ```

---

### Component 3: Verification Test Suites
**Directory**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/` and `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/`

#### [MODIFY] `ElevationProfileBoundsTest.kt`
* Add tests verifying containment when `minAltitudeOverride > streamMin` (e.g. 368 m vs 360 m), narrow span expansion without clipping, and `maxAltitudeOverride < streamMax`.

#### [MODIFY] `WorkoutDataMapperAltitudeTest.kt`
* Add tests verifying self-healing when `recordedMin > streamMin` and `recordedMax < streamMax`.

---

## 4. System Invariant Checklist
* **REQ-MAP-010 (Grade Legend)**: Grade calculation unaltered.
* **REQ-UI-013 (Harmonized Extrema)**: Summary table and elevation chart display identical, non-clipped values.
* **REQ-UI-126 (Strict Containment)**: All plotted points strictly satisfy $\text{bounds.min} \le p.\text{altitude} \le \text{bounds.max}$.
* **REQ-DAT-009 (Authoritative Reconciliation)**: Clean records untouched; contradictory records reconciled.
* **Non-destructive Invariant**: Workouts without streams or with valid matching extrema are never modified.

---

## 5. Verification Plan
* Run targeted unit tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileBoundsTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMapperAltitudeTest"
  ```
* Run full clean-room suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
