# Stage 1 Analysis: ATT-6567 - Phase out source-text-reading tests in com.atrainingtracker.trainingtracker.ui.settings

**Ticket**: [ATT-6567](https://rainerblind.atlassian.net/browse/ATT-6567)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6567`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

During the ASPICE 4.0 pre-assessment of 2026-11-08 (baseline `develop @ sprint/2026-45.11`), Remediation Item **C-12** (SWE.4 BP) identified extensive legacy unit tests that read production Kotlin/Java source code (`.readText()`) targeting `src/main` to assert token presence or regex patterns rather than exercising runtime bytecode and behavioral contracts.

This pattern violates `docs/coding_standards.md` §5.4 (*Contract Test Standards & Prohibition of UI Literal Source-Grep*). Ticket **ATT-6567** addresses this debt in `com.atrainingtracker.trainingtracker.ui.settings`, replacing legacy source-reading tests with behavioral unit tests, JVM/Kotlin reflection, and `LocalizationTestCache`-backed assertions.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  * The ticket description hypothesized 21 legacy source-reading tests in `ui.settings` (suggesting a ratchet from 236 to 215).
  * An exhaustive audit of `app/source-reading-baseline.txt` on `sprint/2026-46.2` (HEAD `6f43d744`) reveals exactly **25** test classes under `com/atrainingtracker/trainingtracker/ui/settings/` (17 in `tuning`, 2 in `autopause`, 2 in `display`, 2 in `supporter`, 1 in `cloud`, 1 in `strava`).
  * All 25 test classes perform direct file I/O on `src/main/java` or `src/main/res` via `readText()`.
  * To fully eliminate debt in `ui.settings` and avoid leaving orphan violations, all 25 test classes will be refactored, ratcheting `app/source-reading-baseline.txt` from **236 down to 211**.

### Inventory of 25 Target Test Classes

| # | Package / Test Class | Current Anti-Pattern | Behavioral Replacement Strategy |
|---|---|---|---|
| 1 | `autopause/AutoPauseRetirementContractTest` | Reads `SensorsGpsFilterSection.kt` | Reflection on `SensorsGpsFilterSectionKt` (param absence) |
| 2 | `autopause/AutoPauseTabsScreenContractTest` | Reads `AutoPauseTabsScreen.kt` | Reflection on `AutoPauseTabsScreenKt`, `LocalizationTestCache` |
| 3 | `cloud/CloudSettingsComponentContractTest` | Reads header/dialog `.kt` files | Reflection on `CloudConnectionHeaderKt`, `CloudProviderSettingsContentKt` |
| 4 | `display/DisplaySettingsCleanupTest` | Reads `DisplaySettingsDialog.kt` | Reflection on `DisplaySettingsDialogKt` (Aftermath param absence) |
| 5 | `display/DisplaySettingsTest` | Reads `strings.xml` | `LocalizationTestCache.getStrings(locale)` |
| 6 | `strava/StravaSettingsDialogContractTest` | Reads `StravaSettingsDialog.kt` | Reflection on `StravaSettingsDialogKt` (actions, `StravaManualSyncCard`) |
| 7 | `supporter/SupporterCopyContractTest` | Reads `strings_monetization.xml` | `LocalizationTestCache.getStrings/getRawContent` |
| 8 | `supporter/SupporterPlanCardHierarchyTest` | Reads `strings_monetization.xml`, `.kt` | Reflection on `SupporterComponentsKt`, `LocalizationTestCache` |
| 9 | `tuning/AdvancedTuningAftermathContractTest` | Reads `AdvancedTuningScreen.kt` | Reflection on `AdvancedTuningScreenKt` |
| 10 | `tuning/AdvancedTuningElevationSmoothingContractTest` | Reads `ElevationSmoothingSection.kt` | Reflection on `ElevationSmoothingSectionKt`, `LocalizationTestCache` |
| 11 | `tuning/AdvancedTuningModularityTest` | Reads modular category files | Reflection on category section composables |
| 12 | `tuning/AdvancedTuningPaceCeilingContractTest` | Reads `PaceCeilingSection.kt` | Reflection on `PaceCeilingSectionKt`, `LocalizationTestCache` |
| 13 | `tuning/AdvancedTuningScreenTest` | Reads `AdvancedTuningScreen.kt` | Reflection on `AdvancedTuningScreenKt` composable methods |
| 14 | `tuning/AdvancedTuningVisualContractTest` | Reads visual tuning files | Reflection on layout composables, token verification |
| 15 | `tuning/ClusterTuningContractTest` | Reads `ClusterTuningSection.kt` | Reflection on `ClusterTuningSectionKt`, `LocalizationTestCache` |
| 16 | `tuning/CockpitPreviewTileContractTest` | Reads `CockpitPreviewTile.kt` | Reflection on `CockpitPreviewTileKt` |
| 17 | `tuning/CockpitTypographyLocalizationTest` | Reads `strings.xml` | `LocalizationTestCache.getStrings(locale)` across 9 locales |
| 18 | `tuning/DisplayAndExpertSettingsLocalizationContractTest` | Reads `strings.xml` | `LocalizationTestCache.getStrings(locale)` across 9 locales |
| 19 | `tuning/LapDisplayModeSettingsTest` | Reads `LapDisplayModeSettings.kt` | Reflection on `LapDisplayModeSettingsKt`, enum unit tests |
| 20 | `tuning/PhysiologyModelsVisibilityContractTest` | Reads `PhysiologyModelsSection.kt` | Reflection on `PhysiologyModelsSectionKt` |
| 21 | `tuning/PhysiologyTuningSectionContractTest` | Reads `PhysiologyTuningSection.kt` | Reflection on `PhysiologyTuningSectionKt` |
| 22 | `tuning/TuningFormControlsTest` | Reads `TuningFormControls.kt` | Reflection on `TuningFormControlsKt` composables |
| 23 | `tuning/WorkoutMasksAndCardsLayoutTest` | Reads `WorkoutMasksAndCardsSection.kt` | Reflection on `WorkoutMasksAndCardsSectionKt` |
| 24 | `tuning/WorkoutSectionReorderContractTest` | Reads `WorkoutSectionReorder.kt` | Reflection on `WorkoutSectionReorderKt` |
| 25 | `tuning/ZoneCardDisplayModeLocalizationTest` | Reads `strings.xml` | `LocalizationTestCache.getStrings(locale)` across 9 locales |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Remediate all 25 tests in `ui.settings` to eliminate all `.readText()` and direct `src/main` file reads.
  * Ratchet `app/source-reading-baseline.txt` down from 236 to 211 entries.
  * Update ceiling and count assertions in `SourceTextReadingAuditContractTest.kt` to 211.
  * Maintain 9-language translation parity verification via `LocalizationTestCache`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Tests in other packages (`ui.aftermath`, `ui.tracking`, `ui.map` assigned to downstream tickets ATT-6566..ATT-6564).
  * Functional changes to preference persistence, SharedPreferences keys, or UI designs.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PRO-049` (`REQ-PRO-049.1`–`REQ-PRO-049.4`), `docs/coding_standards.md` §5.4.
* **Historical Origin & Commit Trace**: ASPICE 4.0 Pre-Assessment Item C-12; initial ratchet framework in ATT-6186 (`c91e62b6`), baseline stabilization in ATT-6731 (`88ce1d51`), prior package batches in ATT-6569 (`bed1157d`) and ATT-6568 (`17300170`).
* **Root Reason for Existing Formulation**: Brittle text grep was used to ensure sections weren't accidentally deleted; replacing with JVM reflection preserves structural safety while removing refactoring fragility.
* **Preservation of Core Invariants**: No governance guard removed; all functional contracts (9-language strings, token compliance, parameter signatures, preference bindings) strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Localization Parity**: Migrate XML string checks to `LocalizationTestCache.getStrings(locale, filename)` across all 9 supported locales for both `strings.xml` and `strings_monetization.xml`.
2. **Component & Composable Reflection**: Inspect generated `*Kt` classes (`Class.forName(...)`, `declaredMethods`, `parameterTypes`) to verify composable presence, parameters, and absence of retired controls.
3. **Pure Behavioral Verification**: Retain and execute pure preference and state tests (e.g. `DisplaySettingsTest`, `LapDisplayMode` enums).
4. **Baseline Ratchet**: Remove all 25 lines from `app/source-reading-baseline.txt` and update `SourceTextReadingAuditContractTest.kt` assertions to 211.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression across JVM unit test suite (`./gradlew testDebugUnitTest`).
  2. 100% 9-language string parity preserved.
  3. Strict ratcheting of baseline debt from 236 to 211.
* **Risk Rating**: **LOW** (Scope strictly bounded to test suites and baseline debt ratcheting; zero production code modifications).
