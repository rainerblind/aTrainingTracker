# Stage 5 Walkthrough - ATT-3578: Replace Misleading Segment Offset Display on Finished and Approaching Live Segments with Past-Finish Distance and PR Time Delta

**Ticket**: [ATT-3578](https://rainerblind.atlassian.net/browse/ATT-3578)  
**Parent Epic**: [ATT-2582](https://rainerblind.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Target Release**: `V4.9.40`  
**Requirement Mapping**: `REQ-UI-379` (*State-Aware Live Segment Lifecycle Metrics, Past-Finish Rollout & PR Delta Feedback*)  
**Test Spec ID**: `TST-UI-342` (*Live Segment State-Aware Metrics, Past-Finish Rollout & PR Delta Verification Suite*)  
**Branch**: `feature/ATT-3578`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Executive Summary

Ticket ATT-3578 resolves a critical usability and motivational UX flaw in the Live Segment tracking bottom sheet (`LiveSegmentSheet.kt` / `SegmentLiveDetails.kt`):
1. **Eliminated False Off-Course Warning on Finish**: Removed the overloading of `segmentOffset` / `segmentOffset_raw` with post-finish rollout distance (`distanceToEnd`). Athletes are no longer greeted at the finish line with an alarming red "Daneben: 210 m" ("Offset: 210 m") and redundant "Ziel in: 0 m".
2. **Dedicated Past-Finish Rollout Metric**: Added `distancePastFinish` ("Nach Ziel: 210 m" / "Past finish: 210 m") styled in neutral `MaterialTheme.colorScheme.onSurfaceVariant`.
3. **Immediate Motivational PR Delta Feedback**: Displays real-time Personal Record delta (e.g. "-0:10 vs. PR" in positive `TTColor.Zone2` green for PR beat/tie or "+0:05 vs. PR" in neutral grey) immediately below `timeOnSegment` upon completion. Null-safe for first-time efforts without an existing PR.
4. **Clean Approaching State**: Suppresses the lateral offset row ("Daneben: --") during `APPROACHING` before crossing the start gate.
5. **9-Language Localization & Japanese Fix**: Corrected the Japanese mistranslation in `values-ja/strings.xml` ("タイム差" -> "コースずれ") and established complete translation parity across EN, DE, ES, FR, IT, JA, NL, PL, and PT.

---

## 2. Changes Implemented

### 2.1 Domain Model (`LiveSegmentData`)
Extended `LiveSegmentData` with dedicated fields for post-finish rollout tracking and PR delta comparisons:
```kotlin
data class LiveSegmentData(
    val segmentStatus: LiveSegmentStatus,
    val timeOnSegment: String = "--:--",
    val timeOnSegment_raw: Long = 0L,
    val distanceToStart: String = "--",
    val distanceToStart_raw: Double = Double.MAX_VALUE,
    val distanceOnSegment: String = "--",
    val distanceOnSegment_raw: Double = 0.0,
    val remainingDistance: String = "--",
    val remainingDistance_raw: Double = Double.MAX_VALUE,
    val segmentOffset: String = "--",
    val segmentOffset_raw: Double = 0.0,
    val distancePastFinish: String = "--",
    val distancePastFinish_raw: Double = 0.0,
    val prDeltaSeconds: Long? = null,
    val prDeltaFormatted: String? = null
)
```

### 2.2 Repository State Transitions (`LiveSegmentsRepository.kt`)
* In `LiveSegmentStatus.FINISHED`, atomically assigns `distancePastFinish = df.format_with_units(distanceToEnd)` and `distancePastFinish_raw = distanceToEnd`.
* Leaves `segmentOffset = "--"` and `segmentOffset_raw = 0.0` untouched.
* Computes PR time delta when `prTime_raw > 0`:
  - `prDeltaSeconds = timeOnSegment - prTime_raw`
  - `prDeltaFormatted` formatted with explicit sign: `"-M:SS"` or `"+M:SS"`
  - Null when `prTime_raw <= 0`.
* In subsequent rollout updates within `FINISHED`, updates `distancePastFinish` while keeping other metrics stable.

### 2.3 State-Aware Composable Rendering (`SegmentLiveDetails.kt`)
* **`APPROACHING`**:
  - Line 1: `R.string.segment_status_start_in` (`titleLarge`, bold)
  - Line 2: `R.string.segment_status_remaining` (`bodyLarge`, bold)
  - Lateral offset row omitted.
* **`ON_SEGMENT` / `ON_SEGMENT_CLOSE_TO_FINISH`**:
  - Line 1: `distanceOnSegment`
  - Line 2: `R.string.segment_status_remaining`
  - Line 3: `R.string.segment_status_offset` with tiered threshold alert colors (`onSurfaceVariant` / `TTColor.Zone3` / `TTColor.Zone4`).
* **`FINISHED`**:
  - Line 1: `distanceOnSegment` (`titleLarge`, bold)
  - Line 2: `R.string.segment_status_past_finish` (`bodyMedium`, `onSurfaceVariant`)
  - Redundant remaining distance and lateral offset omitted.
  - Right column: `timeOnSegment` (`headlineMedium`, monospace, bold), followed conditionally by `R.string.segment_status_pr_delta` in `TTColor.Zone2` ($\le 0\text{L}$) or `onSurfaceVariant` ($> 0\text{L}$). Completely hidden when `prDeltaFormatted == null`.

### 2.4 9-Language Localization Parity (Rule 29)

| Locale | `segment_status_past_finish` | `segment_status_pr_delta` | `segment_status_offset` (Corrected) |
| :--- | :--- | :--- | :--- |
| **EN** (`values`) | `Past finish: %1$s` | `%1$s vs. PR` | `Offset: %1$s` |
| **DE** (`values-de`) | `Nach Ziel: %1$s` | `%1$s vs. PR` | `Daneben: %1$s` |
| **ES** (`values-es`) | `Tras meta: %1$s` | `%1$s vs. PR` | `Desvío: %1$s` |
| **FR** (`values-fr`) | `Après l\'arrivée : %1$s` | `%1$s vs PR` | `Écart latéral : %1$s` |
| **IT** (`values-it`) | `Dopo il traguardo: %1$s` | `%1$s vs. PR` | `Offset: %1$s` |
| **JA** (`values-ja`) | `ゴール通過後: %1$s` | `%1$s (自己ベスト比)` | `コースずれ: %1$s` |
| **NL** (`values-nl`) | `Na finish: %1$s` | `%1$s vs. PR` | `Afwijking: %1$s` |
| **PL** (`values-pl`) | `Za metą: %1$s` | `%1$s vs PR` | `Odchylenie: %1$s` |
| **PT** (`values-pt`) | `Após a meta: %1$s` | `%1$s vs. RP` | `Desvio: %1$s` |

---

## 3. Verification & Test Evidence

### 3.1 Targeted Test Execution
1. **`LiveSegmentsRepositoryTest`**:
   - `testFinished_populatesDistancePastFinishAndPrDelta_withoutOverloadingOffset`: PASSED
   - `testFinished_prDelta_exactTie`: PASSED (0s tie formatted as `"-0:00"`)
   - `testFinished_prDelta_slowerThanPr`: PASSED (+5s slower formatted as `"+0:05"`)
   - `testFinished_prDelta_extremeOutlier`: PASSED (+3600s slower formatted as `"+60:00"`)
   - `testFinished_prDelta_missingPr`: PASSED (`prTime_raw = 0` sets fields to `null`)
   - All standard navigation & gate crossing tests: PASSED

2. **`LiveSegmentSheetLayoutTest`**:
   - `testSegmentLiveDetails_stateAwareVisibilityBranches`: PASSED (verifies state-aware branching, omission of offset in APPROACHING, omission of remaining/offset in FINISHED, and PR delta styling)

3. **`SegmentLiveDetailsLocalizationTest`**:
   - `testPastFinishString_existsAndHasSpecifierInAllNineLocales`: PASSED
   - `testPrDeltaString_existsAndHasSpecifierInAllNineLocales`: PASSED
   - `testSegmentOffset_japaneseMistranslationFixed`: PASSED (asserts "コースずれ", rejects "タイム差")
   - `testSegmentOffset_refinedAcrossAllLocales`: PASSED

### 3.2 Full Clean-Room Regression Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Result**: `BUILD SUCCESSFUL in 4m 41s`
* **Pass Rate**: 100% across all 2,964+ unit tests with zero regressions.

### 3.3 Requirement Governance
* **Command**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-42.5`
* **Result**: `Net-new requirement(s) detected: REQ-UI-379. Bypassing archaeology check cleanly.`

---

## 4. UI Comparison Summary

| Lifecycle State | Previous Visual Presentation | New State-Aware Visual Presentation |
| :--- | :--- | :--- |
| **`APPROACHING`** | Left column: `Start in: 200 m`<br>`Remaining: 1.2 km`<br><span style="color:red">`Offset: --`</span> (redundant) | Left column: `Start in: 200 m`<br>`Remaining: 1.2 km`<br>*(Offset cleanly omitted)* |
| **`ON_SEGMENT`** | Left column: `Distance: 800 m`<br>`Remaining: 400 m`<br>`Offset: 5 m` (alert tiers) | Left column: `Distance: 800 m`<br>`Remaining: 400 m`<br>`Offset: 5 m` (alert tiers preserved) |
| **`FINISHED` (PR Beat)** | Left column: `1.2 km`<br>`Remaining: 0 m`<br><span style="color:red">`Offset: 210 m`</span> (false off-course error!)<br>Right column: `03:39` | Left column: `1.2 km`<br><span style="color:gray">`Past finish: 210 m`</span><br>Right column: `03:39`<br><span style="color:green;font-weight:bold">-0:10 vs. PR</span> |
| **`FINISHED` (No PR)** | Left column: `1.2 km`<br>`Remaining: 0 m`<br><span style="color:red">`Offset: 210 m`</span><br>Right column: `03:39` | Left column: `1.2 km`<br><span style="color:gray">`Past finish: 210 m`</span><br>Right column: `03:39`<br>*(PR delta cleanly hidden)* |
