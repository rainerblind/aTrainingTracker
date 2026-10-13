# Stage 1 Analysis: ATT-3108 - LiveSegmentSheet crashes with NumberFormatException attempting to parse formatted distance string on segment finish

**Ticket**: [ATT-3108](https://atrainingtracker.atlassian.net/browse/ATT-3108)  
**Sub-task**: [ATT-3153](https://atrainingtracker.atlassian.net/browse/ATT-3153) (`[Analysis]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.8`  
**Branch**: `feature/ATT-3108`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & Motivation

When an athlete completes a live segment during active tracking, the app abruptly terminates with a fatal `NumberFormatException`:

```text
10-10 20:07:11.720 23493 23493 E AndroidRuntime: FATAL EXCEPTION: main
10-10 20:07:11.720 23493 23493 E AndroidRuntime: Process: com.atrainingtracker.debug, PID: 23493
10-10 20:07:11.720 23493 23493 E AndroidRuntime: java.lang.NumberFormatException: For input string: "766 m"
10-10 20:07:11.720 23493 23493 E AndroidRuntime: 	at jdk.internal.math.FloatingDecimal.check(FloatingDecimal.java:2324)
10-10 20:07:11.720 23493 23493 E AndroidRuntime: 	at jdk.internal.math.FloatingDecimal.readJavaFormatString(FloatingDecimal.java:1969)
10-10 20:07:11.720 23493 23493 E AndroidRuntime: 	at jdk.internal.math.FloatingDecimal.parseDouble(FloatingDecimal.java:110)
10-10 20:07:11.720 23493 23493 E AndroidRuntime: 	at java.lang.Double.parseDouble(Double.java:661)
10-10 20:07:11.720 23493 23493 E AndroidRuntime: 	at com.atrainingtracker.trainingtracker.ui.segments.LIveSegmentSheetKt.LiveSegmentSheet(LIveSegmentSheet.kt:43)
```

The athlete expects the live segment bottom sheet to transition smoothly from in-progress tracking to the finished state, displaying their completed effort and positioning the elevation profile scrubber marker pin at the end of the segment profile, without interrupting the active workout or crashing the process.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of `LIveSegmentSheet.kt` and `SegmentsRepository.kt` shows the origin of the bug:

### 2.1 String-to-Double Parsing on Localized Metric Field
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt` (lines 40–46, introduced in ATT-2967):
```kotlin
val externalScrubDistance = when (liveSegment.liveData.segmentStatus) {
    LiveSegmentStatus.ON_SEGMENT,
    LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH -> liveSegment.liveData.distanceOnSegment_raw
    LiveSegmentStatus.FINISHED -> liveSegment.staticData.summary.distance.toDouble().coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)
    LiveSegmentStatus.APPROACHING,
    LiveSegmentStatus.FAR_FAR_AWAY -> null
}
```

### 2.2 Domain Model Data Contract in `SegmentsRepository.kt`
In `SegmentSummary` (lines 54–79 of `SegmentsRepository.kt`):
```kotlin
data class SegmentSummary @JvmOverloads constructor(
    val stravaId: Long,
    val name: String,
    val bSportType: BSportType,
    val climbCategory_raw: Int,
    val climbCategory: String,
    val prTime_raw: Int,
    val prTime: String,
    val city: String,
    val distance: String,         // <-- Formatted string with unit, e.g. "766 m" or "1.2 km"
    val distance_raw: Double,     // <-- Canonical unformatted metric Double in meters
    val averageGrade_raw: Double,
    val averageGrade: String,
    val maxGrade: String,
    val elevationGain_raw: Double,
    val elevationGain: String,
    val elevationMin: String,
    val elevationMax: String,
    val map_polyline: String,
    val minLat: Double? = null,
    val minLng: Double? = null,
    val maxLat: Double? = null,
    val maxLng: Double? = null,
    val syncedAt: Long = 0L
)
```

`summary.distance` is the localized, formatted string representation (e.g. `"766 m"`), while `summary.distance_raw` is the unformatted numeric `Double` in meters. Calling `.toDouble()` on `summary.distance` causes an immediate `NumberFormatException` as soon as the segment finishes and `LiveSegmentStatus.FINISHED` is dispatched.

---

## 3. Chesterton's Fence Archaeology & Invariant Preservation

1. **Origin of `externalScrubDistance` in ATT-2967 (Sprint 2026-40.16)**:
   - ATT-2967 introduced `REQ-UI-331` to synchronize the athlete's real-time position with the elevation profile in `LiveSegmentSheet.kt`.
   - The requirement intended that upon completion (`LiveSegmentStatus.FINISHED`), the scrubber marker would rest at the end of the segment profile, which is the total distance of the segment.
   - The implementer mistakenly referenced `summary.distance` instead of `summary.distance_raw`.
   - Replacing `liveSegment.staticData.summary.distance.toDouble()` with `liveSegment.staticData.summary.distance_raw` fulfills the exact intent of REQ-UI-331 with full type safety and zero string parsing.

2. **Contract Test Invariant (`LiveSegmentSheetContractTest.kt`)**:
   - `LiveSegmentSheetContractTest.kt` line 129 currently contains an exact text check matching the old buggy code:
     ```kotlin
     content.contains("LiveSegmentStatus.FINISHED -> liveSegment.staticData.summary.distance.toDouble().coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)")
     ```
   - This contract test must be updated to expect `liveSegment.staticData.summary.distance_raw.coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)`.

3. **Requirement Governance (`REQ-UI-331`)**:
   - In `docs/requirements.md`, `REQ-UI-331` text documents the erroneous `summary.distance.toDouble()`.
   - The requirement specification in `docs/requirements.md` must be corrected to specify `summary.distance_raw`.

4. **UI Invariants**:
   - Bottom sheet wrapping behavior (`wrapContentHeight()`), suppressed map (`showMap = false`), suppressed zoom controls (`showZoomControls = false`), and surface background styling remain completely untouched.

---

## 4. User Scope Grounding (ATT-1250)

In accordance with ATT-1250:
- Scope is strictly limited to fixing the numeric metric resolution for `LiveSegmentStatus.FINISHED` in `LIveSegmentSheet.kt`.
- No database migrations, background service changes, or network API modifications are required.
- All live segment states (`APPROACHING`, `ON_SEGMENT`, `ON_SEGMENT_CLOSE_TO_FINISH`, `FINISHED`, `FAR_FAR_AWAY`) remain supported.

---

## 5. Architectural Solution (SWE.2)

### 5.1 Type-Safe Metric Reference in `LIveSegmentSheet.kt`
Replace line 43 of `LIveSegmentSheet.kt`:
```kotlin
val externalScrubDistance = when (liveSegment.liveData.segmentStatus) {
    LiveSegmentStatus.ON_SEGMENT,
    LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH -> liveSegment.liveData.distanceOnSegment_raw
    LiveSegmentStatus.FINISHED -> liveSegment.staticData.summary.distance_raw.coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)
    LiveSegmentStatus.APPROACHING,
    LiveSegmentStatus.FAR_FAR_AWAY -> null
}
```

### 5.2 Unit & Contract Test Updates
1. Update `LiveSegmentSheetContractTest.kt` to enforce `distance_raw`.
2. Add a direct unit test in `LiveSegmentSheetLayoutTest.kt` verifying that when a live segment has `LiveSegmentStatus.FINISHED` and `summary.distance = "766 m"`, no exception is thrown and the calculation yields the numeric value of `distance_raw`.

---

## 6. Risks & Mitigation

| Risk | Impact | Likelihood | Mitigation |
| :--- | :--- | :--- | :--- |
| `distance_raw` is 0.0 or negative for corrupted segment data | Low | Very Low | `coerceAtLeast(distanceOnSegment_raw)` ensures the scrubber distance will not regress below the athlete's recorded progress. |
| Contract test failure in CI | High | Low | Synchronously update `LiveSegmentSheetContractTest.kt` alongside `LIveSegmentSheet.kt`. |

---

## 7. ASPICE Gate 1 Sign-Off Readiness

All forensic evidence, root cause details, invariant analyses, and architectural solutions are established. Ready for Gate 1 transition and audit.
