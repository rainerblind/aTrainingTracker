# Stage 3: Implementation Plan - ATT-2015: Render Training Zone Badge in Active Zone Color in ElevationProfile Scrubbing Card

**Ticket**: [ATT-2015](https://rainerblind.atlassian.net/browse/ATT-2015)  
**Sub-task**: [ATT-2099](https://rainerblind.atlassian.net/browse/ATT-2099) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.12`  
**Requirement Mapping**: `REQ-UI-242` (*Aftermath/Scrubbing: Render Training Zone Badge Suffix in Active Zone Color in Scrubbing Telemetry Badge*)  
**Test Mapping**: `TST-UI-201` (*Aftermath/Scrubbing: Render Training Zone Badge Suffix in Active Zone Color in Scrubbing Telemetry Badge Verification*)  
**Branch**: `feature/ATT-2015`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

In the Aftermath workout inspection screens ([TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TrackOnMapScreen.kt), [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt), and [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt)), the floating multi-metric telemetry card (`ScrubbingTelemetryBadge`) displays instantaneous sensor readouts during touch inspection and scrubbing.

When heart rate or cycling power data is present alongside user-configured training zone thresholds, `ScrubbingTelemetryBadge` displays the active zone tag (e.g. `145 bpm • Z2` or `220 W • Z3`). Previously:
* Heart rate text color was hardcoded statically to `TTColor.Zone4` (orange) regardless of whether the athlete was in Zone 1, 2, 3, 4, or 5.
* Power text color was hardcoded statically to `TTColor.Zone5` (red) regardless of the actual active power zone.

This created visual dissonance and violated zone color semantics: an athlete running in Zone 1 (active recovery) or Zone 2 (aerobic) saw `• Z1` or `• Z2` rendered in an alarming orange tone.

The objective of ATT-2015 is to split the metric label into a neutral base metric value rendered in high-contrast text (`MaterialTheme.colorScheme.onSurface`) and an active zone suffix (` • Z<n>`) rendered directly in that zone's specific semantic color (`TTColor.Zone1` .. `TTColor.Zone5`) via `buildAnnotatedString`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-242` (*Aftermath/Scrubbing: Render Training Zone Badge Suffix in Active Zone Color in Scrubbing Telemetry Badge*)
* **Test Mapping**: `TST-UI-201` (*Aftermath/Scrubbing: Render Training Zone Badge Suffix in Active Zone Color in Scrubbing Telemetry Badge Verification*)
  * `TST-UI-201.1`: Structural & Zone Styling Contract Tests (`ScrubbingBadgeZoneColorContractTest.kt`)
  * `TST-UI-201.2`: Invariant & Regression Tests (`ElevationProfileLayoutTest.kt`, `TelemetryMetricGraphZoneTest.kt`, `MapDetailLayoutScrubbingBadgeContractTest.kt`)
  * `TST-UI-201.3`: 9-Language Localization Audit (`TranslationParityTest.kt`)
  * `TST-UI-201.4`: Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing test suites must continue to pass cleanly with 100% pass rate.
2. **Single-Line Inline Layout**: The combination of base metric and zone suffix must not wrap or cause baseline jitter during scrubbing across points with different zones.
3. **WCAG Contrast & Theme Safety**: Base numeric values must use `MaterialTheme.colorScheme.onSurface`, ensuring crisp contrast against the translucent `surfaceVariant` card background across both Light and Dark themes.
4. **Zone Math Integrity**: Zone threshold resolution and determination must continue to rely on `TelemetryZoneMath.determineHeartRateZone` and `TelemetryZoneMath.determinePowerZone`.
5. **Neutral Graceful Fallback**: When zone thresholds are missing or zone resolution returns `null`, only the base metric value (e.g. `145 bpm`) must be displayed without trailing delimiters or miscolored characters.
6. **Subtask Direct Completion**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
7. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`. AI agents must never transition parent tickets to `Erledigt`.

---

## 4. Proposed Architectural Changes

### Component 1: `ElevationProfile.kt` (`ScrubbingTelemetryBadge`)
* Import `androidx.compose.ui.text.SpanStyle`, `androidx.compose.ui.text.buildAnnotatedString`, and `androidx.compose.ui.text.withStyle`.
* In `ScrubbingTelemetryBadge`:
  * For Heart Rate (`hasHr`):
    ```kotlin
    val hrZone = effectiveHrThresholds?.let {
        TelemetryZoneMath.determineHeartRateZone(point.hr.toDouble(), it)
    }
    val hrAnnotated = buildAnnotatedString {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
            append("${point.hr} bpm")
        }
        if (hrZone != null && hrZone in 1..5) {
            withStyle(
                SpanStyle(
                    color = TelemetryZoneMath.ZONE_COLORS[hrZone - 1],
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(" • Z$hrZone")
            }
        }
    }
    Text(
        text = hrAnnotated,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
    ```
  * For Power (`hasPower`):
    ```kotlin
    val powerZone = effectivePowerThresholds?.let {
        TelemetryZoneMath.determinePowerZone(point.power.toDouble(), it)
    }
    val powerAnnotated = buildAnnotatedString {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
            append("${point.power} W")
        }
        if (powerZone != null && powerZone in 1..5) {
            withStyle(
                SpanStyle(
                    color = TelemetryZoneMath.ZONE_COLORS[powerZone - 1],
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(" • Z$powerZone")
            }
        }
    }
    Text(
        text = powerAnnotated,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
    ```

### Component 2: `ScrubbingBadgeZoneColorContractTest.kt`
* Create unit/contract test verifying:
  1. `ElevationProfile.kt` contains `buildAnnotatedString` for HR and Power in `ScrubbingTelemetryBadge`.
  2. Base metric value is styled with `MaterialTheme.colorScheme.onSurface`.
  3. Zone suffix uses `TelemetryZoneMath.ZONE_COLORS[hrZone - 1]` / `TelemetryZoneMath.ZONE_COLORS[powerZone - 1]` with `FontWeight.Bold`.
  4. Null-zone fallback produces clean string without delimiter.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Check
* Programmatic Rule 3 Pre-Check: Execute `python3 tools/jira_util.py check-gate ATT-2099` before editing application source code.

### Step 2: Source Code Modification (`ElevationProfile.kt`)
* Edit `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`:
  - Add imports for Compose text styling (`SpanStyle`, `buildAnnotatedString`, `withStyle`).
  - Refactor `ScrubbingTelemetryBadge` HR and Power rows to build styled `AnnotatedString` instances with dynamic zone colors and neutral base text.

### Step 3: Unit & Contract Test Implementation
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ScrubbingBadgeZoneColorContractTest.kt` implementing `TST-UI-201.1`.

### Step 4: Targeted Unit Testing
* Execute targeted test suite via Gradle:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"`

### Step 5: Clean-Room Regression & Parity Verification
* Run full regression test suite:
  `./gradlew testDebugUnitTest`
* Verify 100% pass rate across 1,380+ tests.

### Step 6: Summary & Gate Transitions
* Author `docs/engineering/plans/ATT-2015_implementation_summary.md`.
* Submit Stage 4 deliverable for Gate 4 audit.

---

## 6. Verification & Acceptance Criteria Mapping

| Acceptance Criteria | Verification Method | Target File | Status |
| :--- | :--- | :--- | :--- |
| **AC-1** (HR Zone Color Matching) | Contract & Zone Color Tests | `ScrubbingBadgeZoneColorContractTest.kt` | Planned |
| **AC-2** (Power Zone Color Matching) | Contract & Zone Color Tests | `ScrubbingBadgeZoneColorContractTest.kt` | Planned |
| **AC-3** (Neutral Base Metric) | Styling Contract Tests | `ScrubbingBadgeZoneColorContractTest.kt` | Planned |
| **AC-4** (Graceful Fallback) | Null-Zone Contract Tests | `ScrubbingBadgeZoneColorContractTest.kt` | Planned |
