# Stage 3: Implementation Plan - ATT-4026: Move hardcoded user-visible strings, units and content descriptions to resources

**Ticket**: [ATT-4026](https://rainerblind.atlassian.net/browse/ATT-4026)  
**Sub-task**: `[Impl-Plan]` | **Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4026`  
**Requirement Mapping**: `REQ-UI-429` (`REQ-UI-429.1`–`REQ-UI-429.4`)  
**Test Mapping**: `TST-UI-394` (`TST-UI-394.1`–`TST-UI-394.5`)  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-22  

---

## 1. Problem Description & Background

Audits identified UI hardcoded strings across `ImportBackupTabsScreen.kt`, `MainActivityWithNavigation.kt`, `ZoneCalculatorDialog.kt`, `LapEditBottomSheet.kt`, `PeriodSummaryCard.kt`, and map marker titles. `CropWorkoutScreen.kt` contains hardcoded German labels ("Distanz:", "Dauer:", "Ø Geschwindigkeit:") and metric units ("km", "km/h"). `WorkoutFilterBottomSheet.kt` hardcodes metric unit chips and converts text inputs assuming kilometers/meters (`* 1000.0`), breaking imperial filtering. 14 English `contentDescription` literals and interactive `IconButton`s with `contentDescription = null` degrade accessibility.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-429` (*Global UI Localization Parity, Unit Adaptability & Accessibility*), extending `REQ-LOC-001`, `REQ-UI-122`, `REQ-UI-157`.
* **Test Mapping**: `TST-UI-394` (`TST-UI-394.1`–`TST-UI-394.5` verified via `TranslationParityTest`, contract tests, and unit tests).

---

## 3. System Invariants & Preserved Behavior

1. **Translation Parity**: 100% string key parity across all 9 locales (`values/` and `values-{de,es,fr,it,ja,nl,pl,pt}/`) with positional format specifiers (`%1$s`).
2. **SI Metric Storage Invariance**: Stored SQLite telemetry (meters, seconds, mps) remains invariant; unit conversion is strictly presentation-tier.
3. **Parent Human Gate Invariance**: Terminal acceptance remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: 9-Locale String Resources (`res/values*/strings.xml`)
* Define keys for crop labels/trim format, import mapping dialog, toast messages, lap placeholder format, empty state text, marker titles, and accessibility action descriptions across all 9 locales.

### Component 2: Presentation Unit Adaptability (`CropWorkoutScreen.kt`, `WorkoutFilterBottomSheet.kt`)
* In `CropWorkoutScreen.kt`, format distance and speed via `DistanceFormatter` and `SpeedFormatter` (or `LocalMetricFormatter`) based on `TrainingApplication.getUnit()`.
* In `WorkoutFilterBottomSheet.kt`, branch presets and parsing on `MyUnits.IMPERIAL`: display miles/feet, and parse using `BANALService.METER_PER_MILE` and `BANALService.METER_PER_FOOT`.

### Component 3: Accessibility Semantics (`IconButton` & Charts)
* Add localized `contentDescription` to interactive `IconButton`s (`ClusterTuningScreen`, `ManualClusterScreen`, `WorkoutClustersScreen`, `WorkoutClusterHeatmapScreen`, `LapEditBottomSheet`, `ImportBackupTabsScreen`).
* Extract English `contentDescription` literals to string resources.

### UI Consistency (Rule 23)
* Reference screens: `CropWorkoutScreen`, `WorkoutFilterBottomSheet`, `ImportBackupTabsScreen`.
* Reused tokens: M3 typography and color tokens (`colorScheme.onSurface`, `colorScheme.primary`), standard 8.dp/16.dp spacing. No one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

* **Step 1: 9-Locale String Extraction (`REQ-UI-429.1`)**: Add all extracted keys to default `strings.xml` and 8 localized files with positional tokens. Run `TranslationParityTest`.
* **Step 2: CropWorkoutScreen Unit & Label Localization (`REQ-UI-429.1`, `REQ-UI-429.2`)**: Replace German labels with `R.string.*` and format distance/speed using unit formatters.
* **Step 3: WorkoutFilterBottomSheet Imperial Adaptability (`REQ-UI-429.2`)**: Dynamically adapt distance (mi) and elevation (ft) chips, labels, and parse multipliers for imperial users.
* **Step 4: Literal Strings & Marker Titles Cleanup (`REQ-UI-429.1`)**: Replace literals in `ImportBackupTabsScreen.kt`, `MainActivityWithNavigation.kt`, `ZoneCalculatorDialog.kt`, `LapEditBottomSheet.kt`, `PeriodSummaryCard.kt`, `TrackOnMapScreen.kt`, `ClimbOnMapScreen.kt`, and `RouteOnMapScreen.kt`.
* **Step 5: Accessibility & TalkBack Semantics Hardening (`REQ-UI-429.3`)**: Supply non-null localized `contentDescription` to action `IconButton`s and replace English literals.
* **Step 6: Unit & Contract Tests (`TST-UI-394.1`–`TST-UI-394.4`)**: Author `HardcodedStringsAndUnitsContractTest.kt` verifying non-presence of banned literals and testing imperial filter conversion.
* **Step 7: Full Clean-Room Regression (`TST-UI-394.5`)**: Run `./gradlew testDebugUnitTest` to verify 100% test pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"` and `HardcodedStringsAndUnitsContractTest`.
* **Rollback**: Branch isolation on `feature/ATT-4026` allows clean reset without touching `develop`.
