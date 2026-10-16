# Stage 3 Implementation Plan - ATT-3578: Replace Misleading Segment Offset Display on Finished and Approaching Live Segments with Past-Finish Distance and PR Time Delta

**Ticket**: [ATT-3578](https://rainerblind.atlassian.net/browse/ATT-3578)  
**Sub-task**: [ATT-3712](https://rainerblind.atlassian.net/browse/ATT-3712) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2582](https://rainerblind.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Requirement Mapping**: `REQ-UI-379` (*State-Aware Live Segment Lifecycle Metrics, Past-Finish Rollout & PR Delta Feedback*)  
**Test Spec ID**: `TST-UI-342` (*Live Segment State-Aware Metrics, Past-Finish Rollout & PR Delta Verification Suite*)  
**Branch**: `feature/ATT-3578`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Executive Summary & Goals

Ticket ATT-3578 addresses a significant UX confusion on the Live Segment tracking bottom sheet (`LiveSegmentSheet.kt` / `SegmentLiveDetails.kt`):
1. **Eliminate False Off-Course Warning on Finish**: Stop overloading `segmentOffset` / `segmentOffset_raw` with post-finish rollout distance (`distanceToEnd`). Replace the alarming red "Daneben: 210 m" and redundant "Ziel in: 0 m" with clean "Nach Ziel: 210 m" ("Past finish: 210 m") rendered in neutral `MaterialTheme.colorScheme.onSurfaceVariant`.
2. **Immediate Motivational PR Feedback**: Display the athlete's Personal Record delta (e.g. "-0:10 vs. PR" in `TTColor.Zone2` green for PR beat/tie or "+0:05 vs. PR" in neutral grey) immediately below `timeOnSegment` upon completion.
3. **Clean Approaching State**: Suppress the lateral offset row ("Daneben: --") during `APPROACHING` state before the start gate is crossed.
4. **Correct 9-Language Localization**: Fix the Japanese mistranslation in `values-ja/strings.xml` ("タイム差" -> "コースずれ") and provide natural, accurate translations for all 9 supported locales.

---

## 2. Architecture & Design (SWE.2)

### 2.1 Component Boundaries
```
+------------------------------------------------------------------------+
|                      UI Layer (Jetpack Compose)                        |
|                                                                        |
|  +--------------------+         +-----------------------------------+  |
|  | LiveSegmentSheet   | ------> | SegmentLiveDetails                |  |
|  +--------------------+         |  - APPROACHING: Start in + Rem.   |  |
|                                 |  - ON_SEGMENT: Dist + Rem + Offset|  |
|                                 |  - FINISHED: Dist + PastFinish    |  |
|                                 |              + Time + PR Delta    |  |
|                                 +-----------------------------------+  |
+------------------------------------------------------------------------+
                                    ^
                                    | StateFlow<List<LiveSegment>>
+------------------------------------------------------------------------+
|                          Repository Layer                              |
|                                                                        |
|  +------------------------------------------------------------------+  |
|  | LiveSegmentsRepository                                           |  |
|  |  - LiveSegmentData: dedicated distancePastFinish & prDelta       |  |
|  |  - updateLiveSegments(): computes prDelta on FINISHED            |  |
|  |  - Keeps segmentOffset clean (not overloaded with distanceToEnd) |  |
|  +------------------------------------------------------------------+  |
+------------------------------------------------------------------------+
```

### 2.2 Domain Model Extension (`LiveSegmentData`)
In `app/src/main/java/com/atrainingtracker/trainingtracker/segments/LiveSegmentsRepository.kt`:
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

### 2.3 PR Delta Math & Formatting
In `LiveSegmentsRepository.kt:373–401`:
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

### 2.4 Race Condition & Concurrency Mitigation
1. **Atomic Repository State Transitions**:
   - `LiveSegmentsRepository.updateLiveSegments()` updates the `_liveSegments` flow within synchronous, single-threaded coroutine execution.
   - When the finish line is crossed forward, the transition from `ON_SEGMENT_CLOSE_TO_FINISH` to `FINISHED` constructs a complete, immutable `LiveSegmentData` instance where `segmentStatus`, `timeOnSegment`, `distancePastFinish`, and `prDeltaFormatted` are assigned simultaneously.
   - Because `StateFlow` emits the entire `List<LiveSegment>` atomically, partial or intermediate states can never be observed by Compose collectors, eliminating UI visual jitter or flicker.
2. **Terminal State Invariant & Forward-Only Lifecycle**:
   - `LiveSegmentStatus.FINISHED` acts as a terminal execution state for the segment effort.
   - Once a segment enters `FINISHED`, subsequent GPS updates execute exclusively within the `LiveSegmentStatus.FINISHED` branch, updating only `distancePastFinish` during the 500m rollout window until `distanceToEnd > SEGMENT_POST_END_DISTANCE_THRESHOLD` triggers transition to `FAR_FAR_AWAY`.
   - The engine is architecturally barred from transitioning backwards from `FINISHED` to `ON_SEGMENT` or `APPROACHING`, ensuring the UI cannot accidentally re-enter active tracking mode even if the user loiters or circles near the finish line.
3. **Null-State Invariant for Missing / Unconfigured PR (`prTime_raw <= 0`)**:
   - When an athlete rides a segment for the first time without an existing Personal Record, `prTime_raw` evaluates to `0` (or negative sentinel).
   - In this state, `prDeltaSeconds` and `prDeltaFormatted` are strictly evaluated as `null`.
   - In `SegmentLiveDetails`, the PR delta subtitle is enclosed in `if (liveSegmentData.segmentStatus == LiveSegmentStatus.FINISHED && liveSegmentData.prDeltaFormatted != null)`. When PR data is absent, the row is completely omitted from the composition tree, preventing any empty strings, visual whitespace gaps, or "0:00 vs. PR" false comparisons.

---

## 3. UI Consistency (Rule 23 & Design Guidelines §5)

In strict adherence to `docs/design_guidelines.md` and Rule 23:
* **Reference Screen**: `LiveSegmentSheet.kt` / `SegmentLiveDetails.kt` mounted inside `LiveTrackingSheetContainer` in Cockpit (`SensorGridScreen.kt`).
* **Typography Tokens**:
  - Distance headers: `MaterialTheme.typography.titleLarge` with `FontWeight.Bold`.
  - Secondary distance subtitles: `MaterialTheme.typography.bodyMedium`.
  - Time readout: `MaterialTheme.typography.headlineMedium` with `FontWeight.Bold`, `FontFamily.Monospace`.
  - PR Delta subtitle: `MaterialTheme.typography.bodyMedium` with `FontWeight.SemiBold`.
* **Color Tokens**:
  - Neutral subtitles (`distancePastFinish`): `MaterialTheme.colorScheme.onSurfaceVariant`.
  - PR Beat / Tie ($\le 0\text{L}$): `TTColor.Zone2` (#00E676 Green), providing immediate positive reinforcement.
  - Slower than PR ($> 0\text{L}$): Neutral `MaterialTheme.colorScheme.onSurfaceVariant`. Never red (finishing a segment is an achievement, not a failure).
  - Cross-track error (while `ON_SEGMENT`): Retains existing tiered thresholds ($< 0.5 \times \text{thresh} \to \text{onSurfaceVariant}$, $< 0.75 \times \text{thresh} \to \text{TTColor.Zone3}$, $\ge 0.75 \times \text{thresh} \to \text{TTColor.Zone4}$).
* **Spacing & Touch Targets**: Maintains standard 16dp horizontal padding and standard column spacing.

---

## 4. Invariants & Governance Protection

1. **Gate 3 Prerequisites Guard**: Gate 3 audit must be approved before modifying production source files.
2. **Zero Regressions to Polyline Tracking Math**: Orthogonal projection in `PolylineProgressTracker.kt` and virtual gate crossing detection in `LiveSegmentsRepository.kt` must remain 100% untouched.
3. **Atomic State & Terminal Guard**: `FINISHED` status, `distancePastFinish`, and `prDeltaFormatted` emit atomically without partial states; `FINISHED` never transitions back to `ON_SEGMENT`.
4. **Missing PR Null-Safety**: If `prTime_raw <= 0`, `prDeltaFormatted = null` and UI hides PR delta row completely.
5. **Living Documentation Synchronization**: `docs/requirements.md` (`REQ-UI-379`) and `docs/tests.md` (`TST-UI-342`) synchronized to `Specified`.
6. **9-Language Translation Parity (Rule 29)**: All new and corrected string tokens verified across EN, DE, ES, FR, IT, JA, NL, PL, PT.
7. **Clean-Room Regression Suite**: 100% test pass rate across the full 2,964+ unit test suite.
8. **Strategy A Integration**: Feature branch merges cleanly into `sprint/2026-42.5` (`--no-ff`).
9. **Human Final Review Authority**: AI agents never transition parent tickets to `Erledigt`.

---

## 5. Step-by-Step Construction Plan (Atomic Steps)

### Step 1: String Resources (9-Language Parity)
* Add `segment_status_past_finish` and `segment_status_pr_delta` to `res/values*/strings.xml`.
* Fix `segment_status_offset` in `res/values-ja/strings.xml` and clarify in `es`, `fr`, `nl`, `pl`, `pt`.
* Target files:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`

### Step 2: Domain Model & Repository Logic
* In `LiveSegmentsRepository.kt`:
  - Extend `LiveSegmentData` with `timeOnSegment_raw`, `distancePastFinish`, `distancePastFinish_raw`, `prDeltaSeconds`, and `prDeltaFormatted`.
  - In `LiveSegmentStatus.FINISHED`, atomically populate `distancePastFinish` and compute `prDeltaFormatted`. Do NOT overwrite `segmentOffset`.
  - In subsequent `FINISHED` updates, update `distancePastFinish` instead of `segmentOffset`.
  - Ensure null-safety: if `prTime_raw <= 0`, `prDeltaSeconds` and `prDeltaFormatted` remain `null`.
* Target file:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/segments/LiveSegmentsRepository.kt`

### Step 3: Composable State-Aware Rendering
* In `SegmentLiveDetails.kt`:
  - Refactor left column to use `when (liveSegmentData.segmentStatus)`.
  - In `APPROACHING`: Render `distanceToStart` and `remainingDistance`. Omit `segmentOffset`.
  - In `FINISHED`: Render `distanceOnSegment` and `distancePastFinish`. Omit `remainingDistance` and `segmentOffset`.
  - In `ON_SEGMENT` / `ON_SEGMENT_CLOSE_TO_FINISH`: Render `distanceOnSegment`, `remainingDistance`, and `segmentOffset`.
  - In right column: Below `timeOnSegment`, conditionally render `prDeltaFormatted` in `TTColor.Zone2` ($\le 0\text{L}$) or `onSurfaceVariant` ($> 0\text{L}$) when `FINISHED` and `prDeltaFormatted != null`.
* Target file:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentLiveDetails.kt`

### Step 4: Unit & Contract Tests with Boundary Analysis
* In `LiveSegmentsRepositoryTest.kt`:
  - Add test `testFinished_populatesDistancePastFinishAndPrDelta_withoutOverloadingOffset`:
    - Faster than PR: `timeOnSegment = 219`, `prTime_raw = 229` -> `prDeltaSeconds = -10L`, `prDeltaFormatted = "-0:10"`.
    - Exact tie with PR: `timeOnSegment = 229`, `prTime_raw = 229` -> `prDeltaSeconds = 0L`, `prDeltaFormatted = "-0:00"`.
    - Slower than PR: `timeOnSegment = 234`, `prTime_raw = 229` -> `prDeltaSeconds = 5L`, `prDeltaFormatted = "+0:05"`.
    - Extreme outlier: `timeOnSegment = 3829`, `prTime_raw = 229` -> `prDeltaSeconds = 3600L`, `prDeltaFormatted = "+60:00"`.
    - Missing PR: `prTime_raw = 0` -> `prDeltaSeconds = null`, `prDeltaFormatted = null`.
* In `LiveSegmentSheetLayoutTest.kt`:
  - Add test verifying state-aware rendering in `SegmentLiveDetails`:
    - In `APPROACHING`: `segment_status_offset` is omitted.
    - In `FINISHED`: `segment_status_past_finish` is rendered, while `segment_status_offset` and `segment_status_remaining` are omitted.
    - In `FINISHED` with PR: `segment_status_pr_delta` is rendered and colored `TTColor.Zone2` for $\le 0\text{L}$.
    - In `FINISHED` without PR: `segment_status_pr_delta` is omitted.
* Create `SegmentLiveDetailsLocalizationTest.kt`:
  - Verify all 9 locales define required strings with matching `%1$s` specifiers and valid Japanese translations.
* Target files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/segments/LiveSegmentsRepositoryTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheetLayoutTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentLiveDetailsLocalizationTest.kt`

### Step 5: Full Clean-Room Regression Suite Execution
* Run `./gradlew testDebugUnitTest` asserting 100% pass rate.

---

## 6. 9-Language Localization Strategy (Rule 29)

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

## 7. Verification & Rollback Strategy

* **Targeted Verification Commands**:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepositoryTest
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.segments.LiveSegmentSheetLayoutTest
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.segments.SegmentLiveDetailsLocalizationTest
  ```
* **Full Clean-Room Suite Command**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* **Rollback Strategy**:
  Changes are purely in the live segments repository, presentation layer, and strings. In the event of an unexpected regression, rollback is achieved cleanly via `git checkout origin/sprint/2026-42.5 -- <target_files>` with zero impact on persisted SQLite database tables.
