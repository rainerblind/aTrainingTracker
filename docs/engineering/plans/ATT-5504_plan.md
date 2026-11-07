# Stage 3: Implementation Plan - ATT-5504: Restrict Altitude Histogram exclusively to Workout Details

**Ticket**: [ATT-5504](https://rainerblind.atlassian.net/browse/ATT-5504)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5504`  
**Requirement Mapping**: `REQ-UI-446` (`REQ-UI-446.1`, `REQ-UI-446.3`)  
**Test Mapping**: `TST-UI-446` (`TST-UI-446.3`, `TST-UI-446.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
Following `ATT-5370`, `MapDetailLayout.kt` hardcoded `alignWithTelemetryCharts = true` and `WorkoutSummary.kt:342` forwarded `preferences.showTelemetryCharts && telemetryPoints.isNotEmpty()`. This inadvertently caused the right-hand marginal Altitude Histogram (and its 52dp margin reduction) to appear across non-workout inspection views (Live Segments, Segment Details, Climb Details, Route Details, Live Map) and compact feed list cards. ATT-5504 restricts the altitude histogram exclusively to Workout Details (`workoutData != null`) and restores clean full-width profiles elsewhere.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-UI-446.1` (Altitude histogram restricted to Workout Details), `REQ-UI-446.3` (Clean full-width elevation profiles in non-workout/feed callers).
* **Test Mapping**: `TST-UI-446.3` (`ElevationProfileHistogramAlignmentContractTest`), `TST-UI-446.4` (Targeted `ui.map.*` suite + `ThemeColorCentralizationAuditTest`).

---

## 3. System Invariants & Preserved Behavior
1. **Rule 64 Multi-Chart Alignment**: Stacked Workout Details graphs (`TrackOnMapScreen`, `WorkoutDetailTabbedContent`) retain 1:1 horizontal plot width (`startPadding = 50.dp`, `endPadding = 52.dp` portrait; 72%/28% landscape).
2. **Profile Mathematics & Scrubbing**: Distance-altitude mapping, smoothing, climb grade coloring, and gesture scrubbing remain identical.
3. **Clean Code Lock**: Production code modifications locked until Gate 3 audit passes (`Rule 4`).

---

## 4. Proposed Architectural Changes
* **Shared Map Container (`MapDetailLayout.kt`)**: Add parameter `showElevationHistogram: Boolean = workoutData != null`. Forward `alignWithTelemetryCharts = showElevationHistogram` to `ElevationProfile` (line 389).
* **Feed Summary Card (`WorkoutSummary.kt`)**: In `WorkoutSectionType.ELEVATION` (line 342), set `alignWithTelemetryCharts = false`.
* **UI Consistency**: Reuses existing `ElevationProfile` tokens and layout structure; restores standard `25.dp` portrait padding on non-workout screens. Zero new one-off styles.
* **SWE.2 Architecture**: No new architectural components, schemas, or dispatchers; `docs/architecture.md` remains stable.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `MapDetailLayout.kt` Parameter Gating
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* Add `showElevationHistogram: Boolean = workoutData != null` to signature.
* Pass `alignWithTelemetryCharts = showElevationHistogram` to `ElevationProfile`.

### Step 2: Suppress Histogram in `WorkoutSummary.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt`
* Set `alignWithTelemetryCharts = false` in `WorkoutSectionType.ELEVATION` (line 342).

### Step 3: Implement Contract & Unit Tests
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileHistogramAlignmentContractTest.kt`
* Assert `showElevationHistogram` default in `MapDetailLayout`, suppression in `WorkoutSummary`, and non-workout caller pass-through.
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted unit test suite and requirement governance parity check.
* **Rollback**: Clean branch isolation on `feature/ATT-5504` allows instant revert (`git checkout feature/ATT-5504 -- .`).
