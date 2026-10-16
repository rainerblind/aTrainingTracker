# Stage 1 Analysis: ATT-3578 - Replace Misleading Segment Offset Display on Finished and Approaching Live Segments with Past-Finish Distance and PR Time Delta

**Ticket**: [ATT-3578](https://rainerblind.atlassian.net/browse/ATT-3578)  
**Sub-task**: [ATT-3710](https://rainerblind.atlassian.net/browse/ATT-3710) (`[Analysis]`)  
**Parent Epic**: [ATT-2582](https://rainerblind.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Branch**: `feature/ATT-3578`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Problem Statement & Motivation

During live workout tracking with active Strava / local segments in Cockpit (`LiveSegmentSheet.kt` / `SegmentLiveDetails.kt`), several data-modeling and UX anomalies degrade the athlete's in-ride experience:

1. **Misleading Red "Daneben: 210 m" & Redundant "Ziel in: 0 m" Upon Segment Finish**:
   - In `LiveSegmentsRepository.kt:388, 396`, when a live segment transitions to `LiveSegmentStatus.FINISHED`, the repository overloads the fields `segmentOffset` and `segmentOffset_raw` with `distanceToEnd` (the distance ridden past the finish line while waiting for the `SEGMENT_POST_END_DISTANCE_THRESHOLD` auto-dismissal window of 500m).
   - In `SegmentLiveDetails.kt:63–82`, the UI unconditionally renders both `R.string.segment_status_remaining` ("Ziel in: 0 m") and `R.string.segment_status_offset` ("Daneben: 210 m").
   - Furthermore, `SegmentLiveDetails` applies cross-track error threshold coloring to `segmentOffset_raw`: any value $\ge 37.5\,\text{m}$ is rendered in bright alarm red (`TTColor.Zone4`).
   - To an athlete who just finished an intense segment effort, seeing bright red "Daneben: 210 m" looks like a critical error warning claiming they ran or rode 210m off-course, while "Ziel in: 0 m" is completely redundant once finished.

2. **Useless "Daneben: --" in Approaching State**:
   - When `segmentStatus == LiveSegmentStatus.APPROACHING`, `SegmentLiveDetails.kt:72–82` renders "Daneben: --". Because the athlete has not yet crossed the start gate, lateral cross-track tracking has not commenced, cluttering the compact sheet with meaningless placeholder data.

3. **Missing Personal Record (PR) Time Delta Upon Completion**:
   - Even though `SegmentSummary` carries the athlete's Personal Record (`prTime_raw > 0`) and `SegmentLiveDetails` displays `timeOnSegment`, the athlete is not provided with immediate post-finish feedback regarding their time delta against their PR (e.g. "-0:10 vs. PR" in green or "+0:05 vs. PR" in neutral grey).

4. **Locale Mistranslation of `segment_status_offset`**:
   - In `values-ja/strings.xml:873`, `segment_status_offset` was translated as "タイム差: %1$s" ("Time difference"), conflating lateral course deviation (distance) with split time.
   - In other locales (`es`, `fr`, `nl`, `pl`, `pt`), the string was translated ambiguously as "Difference" rather than lateral track deviation.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Overloaded Data Modeling in `LiveSegmentsRepository.kt`
In `LiveSegmentsRepository.kt`:
```kotlin
// Lines 50-61
data class LiveSegmentData(
    val segmentStatus: LiveSegmentStatus,
    val timeOnSegment: String = "--:--",
    val distanceToStart: String = "--",
    val distanceToStart_raw: Double = Double.MAX_VALUE,
    val distanceOnSegment: String = "--",
    val distanceOnSegment_raw: Double = 0.0,
    val remainingDistance: String = "--",
    val remainingDistance_raw: Double = Double.MAX_VALUE,
    val segmentOffset: String = "--",
    val segmentOffset_raw: Double = 0.0
)
```
When `LiveSegmentStatus.FINISHED` occurs (lines 373–401):
```kotlin
liveSegment.copy(
    liveData = LiveSegmentData(
        segmentStatus = newSegmentStatus,
        timeOnSegment = tf.format_with_units(timeOnSegment),
        distanceOnSegment = df.format_with_units(distanceOnSegment),
        distanceOnSegment_raw = distanceOnSegment,
        remainingDistance = df.format_with_units(0),
        remainingDistance_raw = 0.0,
        distanceToStart_raw = 0.0,
        segmentOffset = df.format_with_units(distanceToEnd),   // BUG: Overloading segmentOffset with distance past finish!
        segmentOffset_raw = distanceToEnd
    )
)
```
And on subsequent GPS updates while remaining `FINISHED`:
```kotlin
liveSegment.copy(
    liveData = liveSegment.liveData.copy(
        segmentOffset = df.format_with_units(distanceToEnd),   // Continues updating distance past finish as offset
        segmentOffset_raw = distanceToEnd
    )
)
```
Because `segmentOffset` was originally the only distance field available to show progress past finish, developers reused it rather than adding dedicated domain fields (`distancePastFinish`, `prDeltaFormatted`).

### 2.2 Unconditional, State-Agnostic UI Rendering in `SegmentLiveDetails.kt`
In `SegmentLiveDetails.kt` (lines 49–83):
```kotlin
Column {
    Text(
        text = if (liveSegmentData.segmentStatus == LiveSegmentStatus.APPROACHING) {
            stringResource(id = R.string.segment_status_start_in, liveSegmentData.distanceToStart)
        } else {
            liveSegmentData.distanceOnSegment
        },
        ...
    )
    Text(
        text = stringResource(id = R.string.segment_status_remaining, liveSegmentData.remainingDistance),
        ...
    )
    Text(
        text = stringResource(id = R.string.segment_status_offset, liveSegmentData.segmentOffset),
        style = MaterialTheme.typography.bodyMedium,
        color = if (liveSegmentData.segmentOffset_raw < LiveSegmentsRepository.SEGMENT_DISTANCE_THRESHOLD * 0.5f) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else if (liveSegmentData.segmentOffset_raw < LiveSegmentsRepository.SEGMENT_DISTANCE_THRESHOLD * 0.75f) {
            TTColor.Zone3
        } else {
            TTColor.Zone4  // Renders RED if distanceToEnd >= 37.5m!
        }
    )
}
```
The composable did not distinguish between active track deviation and post-finish rollout, nor did it suppress the lateral offset row during `APPROACHING` or `FINISHED`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Domain Model Extension (`LiveSegmentData`)**:
     - Add explicit fields:
       - `val timeOnSegment_raw: Long = 0L`
       - `val distancePastFinish: String = "--"`
       - `val distancePastFinish_raw: Double = 0.0`
       - `val prDeltaSeconds: Long? = null`
       - `val prDeltaFormatted: String? = null`
  2. **Repository Decoupling (`LiveSegmentsRepository.kt`)**:
     - In `LiveSegmentStatus.FINISHED`, populate `distancePastFinish` and `distancePastFinish_raw` with `distanceToEnd`.
     - Stop overloading `segmentOffset` / `segmentOffset_raw` with `distanceToEnd` (set to `"--"` / `0.0`).
     - Calculate PR delta when `staticData.summary.prTime_raw > 0`: `delta = timeOnSegment - prTime_raw`. Format with explicit sign (`"-0:10"` or `"+0:05"`).
  3. **State-Aware UI Layout (`SegmentLiveDetails.kt`)**:
     - `APPROACHING`: Render `distanceToStart` and `remainingDistance`. Suppress lateral offset.
     - `ON_SEGMENT` / `ON_SEGMENT_CLOSE_TO_FINISH`: Render `distanceOnSegment`, `remainingDistance`, and `segmentOffset` with cross-track warning colors.
     - `FINISHED`: Render completed `distanceOnSegment`, `distancePastFinish` in neutral `onSurfaceVariant` (never red/orange). Suppress `remainingDistance` ("Ziel in: 0 m") and `segmentOffset`. In right column, render `timeOnSegment` and `prDeltaFormatted` (in `TTColor.Zone2` green if $\le 0\text{ s}$, neutral `onSurfaceVariant` if $> 0\text{ s}$).
  4. **9-Language Localization Parity (Rule 29)**:
     - Add `segment_status_past_finish` and `segment_status_pr_delta` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
     - Correct `segment_status_offset` in JA (`コースずれ: %1$s`), ES (`Desvío: %1$s`), FR (`Écart latéral : %1$s`), NL (`Afwijking: %1$s`), PL (`Odchylenie: %1$s`), PT (`Desvio: %1$s`).
  5. **Unit & Contract Testing**:
     - Update `LiveSegmentsRepositoryTest.kt`, `LiveSegmentSheetContractTest.kt`, and `LiveSegmentSheetLayoutTest.kt`.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying GPS segment projection algorithms in `PolylineProgressTracker.kt`.
  - Changing SQLite database schemas for segments (`SegmentsDatabaseManager.kt`).
  - Altering climb tracking sheets (`LiveClimbSheet.kt`).
  - Modifying segment auto-dismissal thresholds (`SEGMENT_POST_END_DISTANCE_THRESHOLD = 500.0`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-379`). Complements `REQ-MAP-027` (*ClimbPro Cockpit Sheet*), `REQ-UI-196` (*Live Segment Bottom Sheet Design*), `REQ-UI-340` (*LiveSegmentSheet Type-Safe Numeric Metric Evaluation on Segment Finish*), and `REQ-UI-366` (*Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture*).
* **Historical Origin & Commit Trace**: `LiveSegmentData` and `SegmentLiveDetails` were introduced in Sprint 2026-40.1 (`ATT-1644` / `ATT-1735`, commits `50123512`, `7d488f51`) and refined in Sprint 2026-41.8 (`ATT-3108`, commit `91b12a9a`).
* **Root Reason for Existing Formulation**: When segment tracking was first implemented, `segmentOffset` was the only secondary distance field on `LiveSegmentData`. To display how far the rider had traveled after crossing the finish line before auto-dismissal, `distanceToEnd` was shoved into `segmentOffset`. At the time, `SegmentLiveDetails` simply displayed all three lines statically without state-based visibility branches.
* **Preservation of Core Invariants**: Active cross-track deviation warnings while on segment, along-track orthogonal projection, finish line virtual gate detection, and 100% full clean-room test suite pass rate are strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Domain Model Updates (`LiveSegmentData`)
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

### 5.2 PR Delta Math in `LiveSegmentsRepository.kt`
```kotlin
val prRaw = liveSegment.staticData.summary.prTime_raw
val (prDeltaSec, prDeltaFmt) = if (prRaw > 0) {
    val delta = timeOnSegment - prRaw.toLong()
    val sign = if (delta <= 0) "-" else "+"
    val absSec = kotlin.math.abs(delta)
    val mins = absSec / 60
    val secs = absSec % 60
    val formatted = String.format(java.util.Locale.US, "%s%d:%02d", sign, mins, secs)
    Pair(delta, formatted)
} else {
    Pair(null, null)
}
```

### 5.3 State-Aware Composable in `SegmentLiveDetails.kt`
```kotlin
@Composable
fun SegmentLiveDetails(
    liveSegmentData: LiveSegmentData,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Distance Progress
            Column {
                when (liveSegmentData.segmentStatus) {
                    LiveSegmentStatus.APPROACHING -> {
                        Text(
                            text = stringResource(id = R.string.segment_status_start_in, liveSegmentData.distanceToStart),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.segment_status_remaining, liveSegmentData.remainingDistance),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LiveSegmentStatus.FINISHED -> {
                        Text(
                            text = liveSegmentData.distanceOnSegment,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.segment_status_past_finish, liveSegmentData.distancePastFinish),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> { // ON_SEGMENT, ON_SEGMENT_CLOSE_TO_FINISH, etc.
                        Text(
                            text = liveSegmentData.distanceOnSegment,
                            style = if (liveSegmentData.segmentStatus != LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.segment_status_remaining, liveSegmentData.remainingDistance),
                            style = if (liveSegmentData.segmentStatus == LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.segment_status_offset, liveSegmentData.segmentOffset),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (liveSegmentData.segmentOffset_raw < LiveSegmentsRepository.SEGMENT_DISTANCE_THRESHOLD * 0.5f) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else if (liveSegmentData.segmentOffset_raw < LiveSegmentsRepository.SEGMENT_DISTANCE_THRESHOLD * 0.75f) {
                                TTColor.Zone3
                            } else {
                                TTColor.Zone4
                            }
                        )
                    }
                }
            }

            // Right: Live Time & PR Delta
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = liveSegmentData.timeOnSegment,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                if (liveSegmentData.segmentStatus == LiveSegmentStatus.FINISHED && liveSegmentData.prDeltaFormatted != null) {
                    Text(
                        text = stringResource(id = R.string.segment_status_pr_delta, liveSegmentData.prDeltaFormatted!!),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if ((liveSegmentData.prDeltaSeconds ?: 1L) <= 0L) {
                            TTColor.Zone2 // Green for PR beat or tie
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}
```

---

## 6. Localization Strategy (Rule 29: 9 Languages)

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

## 7. Verification Strategy & Test Planning

1. **Repository Unit Tests (`LiveSegmentsRepositoryTest.kt`)**:
   - Add test `testFinished_populatesDistancePastFinishAndPrDelta_withoutOverloadingOffset`:
     - Test faster than PR: verifies `prDeltaSeconds < 0`, `prDeltaFormatted == "-0:10"`, `distancePastFinish == "210 m"`, `segmentOffset == "--"`.
     - Test slower than PR: verifies `prDeltaSeconds > 0`, `prDeltaFormatted == "+0:05"`.
     - Test no PR (`prTime_raw == 0`): verifies `prDeltaSeconds == null`, `prDeltaFormatted == null`.
2. **UI & Layout Contract Tests (`LiveSegmentSheetLayoutTest.kt`)**:
   - Verify `SegmentLiveDetails` renders approaching, on-segment, and finished states cleanly.
   - Verify `segment_status_offset` is not present when `APPROACHING` or `FINISHED`.
   - Verify `segment_status_past_finish` and `segment_status_pr_delta` are rendered in `FINISHED`.
3. **9-Language Localization Parity Test**:
   - Verify 100% presence and format specifier matching across all 9 locales.
4. **Clean-Room Regression Suite**:
   - Run full `./gradlew testDebugUnitTest` suite (2,964+ tests).
