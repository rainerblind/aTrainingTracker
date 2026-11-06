# Stage 2: Requirement & Test Specification - ATT-5504: Restrict right-hand Altitude Histogram exclusively to Workout Details

**Ticket**: [ATT-5504](https://rainerblind.atlassian.net/browse/ATT-5504)  
**Sub-task**: [ATT-5671](https://rainerblind.atlassian.net/browse/ATT-5671) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5504`  
**Requirement Mapping**: `REQ-UI-446` (`REQ-UI-446.1`, `REQ-UI-446.3`) | **Test Spec ID**: `TST-UI-446` (`TST-UI-446.3`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-UI-446`)

### 1.1 Problem Statement & Rationale
Following `ATT-5370`, `MapDetailLayout.kt` hardcoded `alignWithTelemetryCharts = true` and `WorkoutSummary.kt:342` forwarded `preferences.showTelemetryCharts && telemetryPoints.isNotEmpty()`, leaking the right-hand marginal Altitude Histogram and 52dp margin into non-workout inspection views (Live Segments, Segment Details, Climb Details, Route Details, Live Map) and compact list cards. The marginal altitude histogram must be restricted exclusively to Workout Details.

### 1.2 Functional & Architectural Requirements
1. `MapDetailLayout.kt` SHALL declare `showElevationHistogram: Boolean = workoutData != null` and forward `alignWithTelemetryCharts = showElevationHistogram` to `ElevationProfile` (`REQ-UI-446.1`).
2. `WorkoutSummary.kt` SHALL set `alignWithTelemetryCharts = false` on list card `ElevationProfile` (`REQ-UI-446.1`).
3. Non-stacked and non-workout callers SHALL render clean full-width profiles (`alignWithTelemetryCharts = false`) without extra margin offsets or histograms (`REQ-UI-446.3`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Workout Details Alignment)**:
  * *Given* an athlete inspecting Workout Details (`TrackOnMapScreen`, `WorkoutDetailTabbedContent`),
  * *When* `MapDetailLayout` renders with `workoutData != null`,
  * *Then* `alignWithTelemetryCharts = true`, displaying the altitude histogram with 1:1 Rule 64 plot width.
* **AC-2 (Non-Workout & List Card Suppression)**:
  * *Given* an athlete inspecting Live Segments, Climbs, Routes, Live Map, or Workout List Cards,
  * *When* `ElevationProfile` renders (where `workoutData == null` or in `WorkoutSummary`),
  * *Then* `alignWithTelemetryCharts = false`, displaying a clean full-width profile without histogram or 52dp offset.

### 1.4 System Invariants
* 1:1 horizontal plot width (`startPadding = 50.dp`, `endPadding = 52.dp` portrait; 72%/28% landscape) strictly preserved for stacked Workout Details (`Rule 64`).
* Zero schema, state store, or repository data mutations.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Amends `REQ-UI-446.1` and `REQ-UI-446.3` targeting `MapDetailLayout.kt` and `WorkoutSummary.kt`.
* `Historical Origin & Commit Trace`: Commit `75485932fe` (`ATT-5370`, Sprint 2026-45.7) introduced universal stacked marginal histograms.
* `Root Reason for Existing Formulation`: Hardcoded `alignWithTelemetryCharts = true` in shared `MapDetailLayout` in haste without gating on workout callers.
* `Preservation of Core Invariants`: Workout Details retain marginal histograms and plot alignment; non-workout screens regain clean full-width profiles.

---

## 2. Test Specification (`TST-UI-446`)

### Test Case 1: `testMapDetailLayout_and_workoutSummary_elevationProfile_alignmentGating` (`TST-UI-446.3`)
* **Scope**: Compose Contract & Unit Test (`app/src/test/.../ui/map/ElevationProfileAlignmentContractTest.kt`)
* **Preconditions**: Verified `MapDetailLayout` parameter defaults and `WorkoutSummary` configuration.
* **Action**: Verify `showElevationHistogram` is `true` when `workoutData != null` and `false` when `workoutData == null`, and list card uses `false`.
* **Expected Result**: Altitude histogram rendered only when `alignWithTelemetryCharts = true`; suppressed everywhere else.

### Test Case 2: 9-Language Localization & Specifier Audit (`TST-UI-446.4`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Goal**: Confirm zero missing keys and 100% specifier parity across all 9 locales.
* **Expected Result**: 100% PASS.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-UI-446.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
* **Expected Result**: 100% PASS across targeted packages (~10-20s).

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-446.3` | Contract | `MapDetailLayout`, `WorkoutSummary`, `ElevationProfile` | `REQ-UI-446.1`, `REQ-UI-446.3` | Specified |
| `TST-UI-446.4` | Localization | `TranslationParityTest` | `REQ-UI-106`, `REQ-UI-446` | Specified |
| `TST-UI-446.4` | Regression (Tier 1) | `--tests "com.atrainingtracker.trainingtracker.ui.map.*"` | `REQ-PRO-001`, `REQ-UI-446` | Specified |
