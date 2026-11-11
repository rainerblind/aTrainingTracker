# Stage 1 Analysis: ATT-6566 - Phase out source-text-reading tests in com.atrainingtracker.trainingtracker.ui.aftermath

**Ticket**: [ATT-6566](https://rainerblind.atlassian.net/browse/ATT-6566)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6566`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

During the ASPICE 4.0 pre-assessment of 2026-11-08 (baseline `develop @ sprint/2026-45.11`), Remediation Item **C-12** (SWE.4 BP) identified extensive legacy unit tests that read production Kotlin/Java source code (`.readText()`) targeting `src/main` to assert token presence or regex patterns rather than exercising runtime bytecode and behavioral contracts.

This pattern violates `docs/coding_standards.md` §5.4 (*Contract Test Standards & Prohibition of UI Literal Source-Grep*). Ticket **ATT-6566** addresses this debt in `com.atrainingtracker.trainingtracker.ui.aftermath`, replacing legacy source-reading tests with behavioral unit tests, JVM/Kotlin reflection, and `LocalizationTestCache`-backed assertions.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  * The ticket description hypothesized 24 legacy source-reading tests in `ui.aftermath`.
  * An exhaustive audit of `app/source-reading-baseline.txt` on `sprint/2026-46.2` (HEAD `3b5a5b41`) reveals exactly **30** test classes under `com/atrainingtracker/trainingtracker/ui/aftermath/` (10 in root `aftermath`, 1 in `crop`, 2 in `editworkout`, 2 in `header`, 1 in `periodlist`, 2 in `photos`, 1 in `splits`, 6 in `workoutlist`, 5 in `zones`).
  * All 30 test classes perform direct file I/O on `src/main/java` or `src/main/res` via `readText()`.
  * To fully eliminate debt in `ui.aftermath` and avoid leaving orphan violations, all 30 test classes are scoped for replacement, ratcheting `app/source-reading-baseline.txt` from **211 down to 181**.

### Inventory of 30 Target Test Classes

| # | Package / Test Class | Current Anti-Pattern | Behavioral Replacement Strategy |
|---|---|---|---|
| 1 | `TrackOnMapScreenDetailPreferencesContractTest` | Reads `TrackOnMapScreen.kt` | Reflection on `TrackOnMapScreenKt` (parameter types) |
| 2 | `TrackOnMapScreenLapRoutingContractTest` | Reads `TrackOnMapScreen.kt` | Reflection on `TrackOnMapScreenKt` (routing parameters) |
| 3 | `TrackOnMapScreenMetadataRoutingContractTest` | Reads `TrackOnMapScreen.kt` | Reflection on `TrackOnMapScreenKt` |
| 4 | `TrackOnMapScreenSectionSlottingContractTest` | Reads `TrackOnMapScreen.kt` | Reflection on `TrackOnMapScreenKt` (slotting composables) |
| 5 | `WorkoutDetailHeaderClickContractTest` | Reads `WorkoutDetailTabbedContent.kt` | Reflection on `WorkoutDetailTabbedContentKt` |
| 6 | `WorkoutDetailTabbedContentTest` | Reads `WorkoutDetailTabbedContent.kt` | Reflection on `WorkoutDetailTabbedContentKt` |
| 7 | `WorkoutDetailTabsTest` | Reads `WorkoutDetailTabs.kt` | Reflection on `WorkoutDetailTabsKt`, tab enum checks |
| 8 | `WorkoutPhotoUiContractTest` | Reads photo composable files | Reflection on photo UI composables |
| 9 | `WorkoutRepositoryCropTest` | Reads `WorkoutRepository.kt` | Behavioral repository crop unit tests |
| 10 | `WorkoutRepositoryDeleteTest` | Reads `WorkoutRepository.kt` | Behavioral repository delete unit tests |
| 11 | `crop/CropWorkoutScreenContractTest` | Reads `CropWorkoutScreen.kt` | Reflection on `CropWorkoutScreenKt` |
| 12 | `editworkout/EditWorkoutFieldsLayoutTest` | Reads `EditWorkoutFields.kt` | Reflection on `EditWorkoutFieldsKt` |
| 13 | `editworkout/EditWorkoutLayoutContractTest` | Reads `EditWorkoutScreen.kt` | Reflection on `EditWorkoutScreenKt` |
| 14 | `header/WorkoutHeaderRaceBadgeVisualContractTest` | Reads header badge files | Reflection on badge composables |
| 15 | `header/WorkoutHeaderSourceBadgeVisualContractTest` | Reads header badge files | Reflection on badge composables |
| 16 | `periodlist/PeriodMapAdaptiveLayeringTest` | Reads period map files | Behavioral layering checks, reflection on composables |
| 17 | `photos/PhotoThumbnailSizeTest` | Reads photo components | Reflection on thumbnail composables |
| 18 | `photos/WorkoutTimePhotoPickerContractTest` | Reads photo picker files | Reflection on `WorkoutTimePhotoPickerKt` |
| 19 | `splits/LapSplitVisualizerTest` | Reads `LapSplitVisualizer.kt` | Behavioral split math, reflection on composable |
| 20 | `workoutlist/ActiveFilterChipsRowTest` | Reads filter chips files | Reflection on `ActiveFilterChipsRowKt` |
| 21 | `workoutlist/WorkoutFilterBottomSheetClusterRemovalTest` | Reads filter bottom sheet | Reflection on `WorkoutFilterBottomSheetKt` |
| 22 | `workoutlist/WorkoutSummariesTabbedScreenTest` | Reads `WorkoutSummariesTabbedScreen.kt` | Reflection on `WorkoutSummariesTabbedScreenKt` |
| 23 | `workoutlist/WorkoutSummaryClickContractTest` | Reads summary card files | Reflection on `WorkoutSummaryCardKt` |
| 24 | `workoutlist/WorkoutSummaryDynamicOrderContractTest` | Reads summary card files | Behavioral order evaluation, reflection |
| 25 | `workoutlist/WorkoutSummaryZoneDisplayModeTest` | Reads summary card files | Reflection on zone display mode composables |
| 26 | `zones/GradeZoneDistributionCardTest` | Reads grade card files | Reflection on `GradeZoneDistributionCardKt`, model tests |
| 27 | `zones/TelemetryHistogramCardTest` | Reads histogram card files | Reflection on `TelemetryHistogramCardKt` |
| 28 | `zones/ZoneDistributionCardTest` | Reads distribution card files | Reflection on `ZoneDistributionCardKt` |
| 29 | `zones/ZoneDistributionStackedBarLocalizationTest` | Reads `strings.xml` | `LocalizationTestCache.getStrings(locale)` across 9 locales |
| 30 | `zones/ZoneDistributionStackedBarTest` | Reads stacked bar files | Reflection on `ZoneDistributionStackedBarKt` |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Remediate all 30 tests in `ui.aftermath` to eliminate all `.readText()` and direct `src/main` file reads.
  * Ratchet `app/source-reading-baseline.txt` down from 211 to 181 entries.
  * Update ceiling and count assertions in `SourceTextReadingAuditContractTest.kt` to 181.
  * Maintain 9-language translation parity verification via `LocalizationTestCache`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Tests in other packages (`ui.tracking`, `ui.map` assigned to downstream tickets ATT-6565, ATT-6564).
  * Functional changes to workout deletion, cropping, histogram rendering, or database schemas.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PRO-049` (`REQ-PRO-049.1`–`REQ-PRO-049.4`), `docs/coding_standards.md` §5.4.
* **Historical Origin & Commit Trace**: ASPICE 4.0 Pre-Assessment Item C-12; initial ratchet framework in ATT-6186 (`c91e62b6`), baseline stabilization in ATT-6731 (`88ce1d51`), prior package batches in ATT-6569 (`bed1157d`), ATT-6568 (`17300170`), and ATT-6567 (`3b5a5b41`).
* **Root Reason for Existing Formulation**: Brittle text grep was used to assert layout slots and parameter defaults; replacing with JVM reflection and behavioral tests preserves structural safety while removing refactoring fragility.
* **Preservation of Core Invariants**: No governance guard removed; all functional contracts (9-language strings, token compliance, parameter signatures, repository crop/delete logic) strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Localization Parity**: Migrate XML string checks to `LocalizationTestCache.getStrings(locale)` across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
2. **Component & Composable Reflection**: Inspect generated `*Kt` classes (`Class.forName(...)`, `declaredMethods`, `parameterTypes`) to verify composable presence, parameters, and slots without reading `.kt` source text.
3. **Pure Behavioral Verification**: Retain and execute pure data model, algorithm, and repository behavior tests (e.g. `LapSplitVisualizer`, `WorkoutRepositoryCropTest`, `WorkoutRepositoryDeleteTest`).
4. **Baseline Ratchet**: Remove all 30 lines from `app/source-reading-baseline.txt` and update `SourceTextReadingAuditContractTest.kt` assertions to 181.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression across JVM unit test suite (`./gradlew testDebugUnitTest`).
  2. 100% 9-language string parity preserved.
  3. Strict ratcheting of baseline debt from 211 to 181.
* **Risk Rating**: **LOW** (Scope strictly bounded to test suites and baseline debt ratcheting; zero production code modifications).
