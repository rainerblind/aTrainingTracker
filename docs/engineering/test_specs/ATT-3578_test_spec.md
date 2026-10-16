# Stage 2: Requirement & Test Specification - ATT-3578: Replace Misleading Segment Offset Display on Finished and Approaching Live Segments with Past-Finish Distance and PR Time Delta

**Ticket**: [ATT-3578](https://rainerblind.atlassian.net/browse/ATT-3578)  
**Sub-task**: [ATT-3711](https://rainerblind.atlassian.net/browse/ATT-3711) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2582](https://rainerblind.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Requirement Mapping**: `REQ-UI-379` (*State-Aware Live Segment Lifecycle Metrics, Past-Finish Rollout & PR Delta Feedback*)  
**Test Spec ID**: `TST-UI-342` (*Live Segment State-Aware Metrics, Past-Finish Rollout & PR Delta Verification Suite*)  
**Branch**: `feature/ATT-3578`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Requirement Specification (REQ-UI-379)

### 1.1 Problem Statement & Rationale
On the live segment tracking card (`LiveSegmentSheet.kt` / `SegmentLiveDetails.kt`), athletes face confusing feedback during key lifecycle states:
1. When transitioning to `LiveSegmentStatus.FINISHED`, `LiveSegmentsRepository` previously overloaded `segmentOffset` / `segmentOffset_raw` with the post-finish rollout distance (`distanceToEnd`). Because `SegmentLiveDetails` rendered `segmentOffset` unconditionally with cross-track warning colors, any rollout $\ge 37.5\text{ m}$ displayed as bright red "Daneben: 210 m" ("Offset: 210 m"), giving the false impression of an off-course error, while simultaneously rendering redundant "Ziel in: 0 m" ("Remaining: 0 m").
2. In `APPROACHING` state before crossing the start gate, "Daneben: --" was rendered despite lateral tracking having not yet begun.
3. Upon segment completion, no delta against the athlete's Personal Record (PR) was displayed, missing an essential motivational feedback moment.
4. Several locales (e.g. Japanese `values-ja/strings.xml:873` "タイム差: %1$s") mistranslated lateral offset as split time.

The system requires state-aware metric rendering, dedicated domain fields for post-finish rollout distance and PR time delta, and corrected 9-language translations.

### 1.2 Functional & Architectural Requirements
The system SHALL decouple post-finish rollout tracking from lateral course offset, provide real-time PR time delta upon completion, and conditionally render metrics based on segment lifecycle state (ATT-3578, complementing `REQ-MAP-027`, `REQ-UI-196`, `REQ-UI-340`, and `REQ-UI-366`):

1. *Domain Model Decoupling (`LiveSegmentData`)*:
   - `LiveSegmentData` SHALL declare dedicated fields for post-finish tracking and PR delta:
     - `val timeOnSegment_raw: Long = 0L`
     - `val distancePastFinish: String = "--"`
     - `val distancePastFinish_raw: Double = 0.0`
     - `val prDeltaSeconds: Long? = null`
     - `val prDeltaFormatted: String? = null`
   - In `LiveSegmentStatus.FINISHED`, `LiveSegmentsRepository` SHALL store `distanceToEnd` exclusively in `distancePastFinish` and `distancePastFinish_raw`.
   - `LiveSegmentsRepository` SHALL NOT overload `segmentOffset` or `segmentOffset_raw` with `distanceToEnd`.

2. *Personal Record (PR) Delta Calculation (`LiveSegmentsRepository.kt`)*:
   - When a segment finishes with `staticData.summary.prTime_raw > 0`:
     - `prDeltaSeconds` SHALL equal `timeOnSegment - prTime_raw`.
     - `prDeltaFormatted` SHALL format with explicit sign (`"-M:SS"` if `prDeltaSeconds <= 0`, `"+M:SS"` if `prDeltaSeconds > 0`).
   - If `staticData.summary.prTime_raw <= 0`, `prDeltaSeconds` and `prDeltaFormatted` SHALL remain `null`.

3. *State-Aware Metric Visibility (`SegmentLiveDetails.kt`)*:
   - **When `LiveSegmentStatus.APPROACHING`**:
     - Render Line 1: `R.string.segment_status_start_in` with `distanceToStart` (`titleLarge`, bold).
     - Render Line 2: `R.string.segment_status_remaining` with `remainingDistance` (`bodyLarge`, bold).
     - The lateral offset row (`R.string.segment_status_offset`) SHALL be completely hidden.
   - **When `LiveSegmentStatus.ON_SEGMENT` or `ON_SEGMENT_CLOSE_TO_FINISH`**:
     - Render Line 1: `distanceOnSegment`.
     - Render Line 2: `R.string.segment_status_remaining` with `remainingDistance`.
     - Render Line 3: `R.string.segment_status_offset` with `segmentOffset` and cross-track warning colors (`onSurfaceVariant` / `TTColor.Zone3` / `TTColor.Zone4`).
   - **When `LiveSegmentStatus.FINISHED`**:
     - Render Line 1: `distanceOnSegment` (`titleLarge`, bold).
     - Render Line 2: `stringResource(R.string.segment_status_past_finish, liveSegmentData.distancePastFinish)` in neutral `MaterialTheme.colorScheme.onSurfaceVariant` (`bodyMedium`).
     - Neither `remainingDistance` ("Ziel in: 0 m") nor `segmentOffset` ("Daneben: ...") SHALL be rendered.
     - In the right column, below `timeOnSegment`: when `prDeltaFormatted != null`, render `stringResource(R.string.segment_status_pr_delta, liveSegmentData.prDeltaFormatted)` colored in `TTColor.Zone2` (green) when `(prDeltaSeconds ?: 1L) <= 0L` and `MaterialTheme.colorScheme.onSurfaceVariant` when `> 0L`.

4. *9-Language Localization Parity (Rule 29)*:
   - `segment_status_past_finish` and `segment_status_pr_delta` SHALL exist with non-empty translations and matching format specifiers (`%1$s`) across EN, DE, ES, FR, IT, JA, NL, PL, PT.
   - `segment_status_offset` SHALL be corrected in `values-ja` to "コースずれ: %1$s" and clarified in `es`, `fr`, `nl`, `pl`, `pt`.

5. *Preservation of System Invariants*:
   - Virtual gate finish line crossing math, polyline orthogonal projection, bottom sheet layout height, and 100% full clean-room unit test pass rate MUST NOT be broken.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-379`). Complements `REQ-MAP-027` (*ClimbPro Cockpit Sheet*), `REQ-UI-196` (*Live Segment Bottom Sheet Design*), `REQ-UI-340` (*LiveSegmentSheet Type-Safe Numeric Metric Evaluation on Segment Finish*), and `REQ-UI-366` (*Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture*).
2. **Historical Origin & Commit Trace**: `LiveSegmentData` and `SegmentLiveDetails` were introduced in Sprint 2026-40.1 (`ATT-1644` / `ATT-1735`, commits `50123512`, `7d488f51`) and refined in Sprint 2026-41.8 (`ATT-3108`, commit `91b12a9a`).
3. **Root Reason for Existing Formulation**: When segment tracking was first implemented, `segmentOffset` was the only secondary distance field on `LiveSegmentData`. To display how far the rider had traveled after crossing the finish line before auto-dismissal, `distanceToEnd` was shoved into `segmentOffset`. At the time, `SegmentLiveDetails` simply displayed all three lines statically without state-based visibility branches.
4. **Preservation of Core Invariants**: Active cross-track deviation warnings while on segment, along-track orthogonal projection, finish line virtual gate detection, and 100% full clean-room test suite pass rate are strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1: Finished State Displays Past-Finish Rollout and PR Delta Without False Off-Course Warning**:
  * *Given* a live segment transitioning to `LiveSegmentStatus.FINISHED` with `distanceToEnd = 210.0`, `timeOnSegment = 219`, and `prTime_raw = 229`,
  * *When* `SegmentLiveDetails` renders,
  * *Then* it displays `distanceOnSegment`, "Nach Ziel: 210 m" / "Past finish: 210 m" in neutral `onSurfaceVariant`, and "-0:10 vs. PR" in green (`TTColor.Zone2`), with zero occurrences of "Ziel in: 0 m" or red "Daneben: 210 m".
* **Criterion 2: Approaching State Suppresses Lateral Offset Row**:
  * *Given* a live segment in `LiveSegmentStatus.APPROACHING` state,
  * *When* `SegmentLiveDetails` renders,
  * *Then* it displays `distanceToStart` and `remainingDistance`, and completely omits the lateral offset row ("Daneben: --").
* **Criterion 3: On-Segment State Retains Live Lateral Course Offset**:
  * *Given* a live segment in `LiveSegmentStatus.ON_SEGMENT` with `distanceToSegment = 40.0`,
  * *When* `SegmentLiveDetails` renders,
  * *Then* it displays `distanceOnSegment`, `remainingDistance`, and `segmentOffset` ("Daneben: 40 m") styled with `TTColor.Zone4`.
* **Criterion 4: 9-Locale Resource Parity**:
  * *Given* all 9 supported locale `strings.xml` resource files,
  * *When* resolving `segment_status_past_finish`, `segment_status_pr_delta`, and `segment_status_offset`,
  * *Then* all keys exist, are non-empty, and format strings compile cleanly.

---

## 2. Test Specification (TST-UI-342)

### Test Case 1: Repository Domain Model & PR Delta Math (`TST-UI-342.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/segments/LiveSegmentsRepositoryTest.kt`
* **Test Method**: `testFinished_populatesDistancePastFinishAndPrDelta_withoutOverloadingOffset`
* **Verifications**:
  1. Crossing finish line forward sets `segmentStatus == LiveSegmentStatus.FINISHED`.
  2. `distancePastFinish_raw` equals `distanceToEnd` and `distancePastFinish` is formatted (e.g. `"210 m"`).
  3. `segmentOffset` is not overloaded with `distanceToEnd` (`segmentOffset == "--"`, `segmentOffset_raw == 0.0`).
  4. When `timeOnSegment = 219` and `prTime_raw = 229`, `prDeltaSeconds == -10L` and `prDeltaFormatted == "-0:10"`.
  5. When `timeOnSegment = 234` and `prTime_raw = 229`, `prDeltaSeconds == 5L` and `prDeltaFormatted == "+0:05"`.
  6. When `prTime_raw == 0`, `prDeltaSeconds == null` and `prDeltaFormatted == null`.

### Test Case 2: UI State-Aware Rendering Contract & AST Tests (`TST-UI-342.2`)
* **Scope**: Contract / Layout Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheetLayoutTest.kt`
* **Test Method**: `testSegmentLiveDetails_stateAwareVisibilityBranches`
* **Verifications**:
  1. `SegmentLiveDetails.kt` contains `when (liveSegmentData.segmentStatus)`.
  2. In `APPROACHING`, `segment_status_offset` is omitted.
  3. In `FINISHED`, `segment_status_past_finish` is used, while `segment_status_offset` and `segment_status_remaining` are omitted.
  4. In `FINISHED`, `segment_status_pr_delta` is rendered with `TTColor.Zone2` when $\le 0\text{L}$.

### Test Case 3: 9-Language Localization Parity Audit (`TST-UI-342.3`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentLiveDetailsLocalizationTest.kt`
* **Verifications**:
  1. `segment_status_past_finish` and `segment_status_pr_delta` exist in all 9 locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  2. All 9 locales contain `%1$s` format specifier.
  3. `values-ja/strings.xml` for `segment_status_offset` contains `コースずれ` rather than `タイム差`.

### Test Case 4: Full Clean-Room Regression Suite (`TST-UI-342.4`)
* **Scope**: Full Clean-Room Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% test pass rate with zero regressions across all 2,964+ unit tests.

---

## 3. Traceability Matrix

| Requirement Clause | Verification Procedure | Test Case ID | Target File | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-379.1` (Dedicated Domain Fields) | Repository Finish Transition Test | `TST-UI-342.1` | `LiveSegmentsRepositoryTest.kt` | Specified |
| `REQ-UI-379.2` (PR Delta Math & Format) | Repository PR Delta Comparison Test | `TST-UI-342.1` | `LiveSegmentsRepositoryTest.kt` | Specified |
| `REQ-UI-379.3` (State-Aware UI Visibility) | Composable AST & State Branch Inspection | `TST-UI-342.2` | `LiveSegmentSheetLayoutTest.kt` | Specified |
| `REQ-UI-379.4` (9-Language Parity & JA Fix) | Localization Parity Unit Test | `TST-UI-342.3` | `SegmentLiveDetailsLocalizationTest.kt` | Specified |
| `REQ-PRO-001` (Clean-Room Regression) | Clean-Room Suite Execution | `TST-UI-342.4` | `./gradlew testDebugUnitTest` | Specified |
