# Stage 3: Implementation Plan - ATT-6048: `saveTuningConfig` Auto-Pause Keys, `BoundedPref` & Centralized Zone Defaults

**Ticket**: [ATT-6048](https://issue-tracker/ATT-6048)  
**Sub-task**: Pending creation (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467)  
**Requirement Mapping**: `REQ-SET-121` (`REQ-SET-121.1`..`REQ-SET-121.4`)  
**Test Mapping**: `TST-SET-123` (`TST-SET-123.1`..`TST-SET-123.3`)  
**Branch**: `feature/ATT-6048`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-20  

---

## 1. Problem Description & Background
* `TuningPreferencesDataStore.saveTuningConfig(config)` omits 4 Auto-Pause keys (`KEY_START_IN_AUTO_PAUSE_ENABLED`, `KEY_AUTO_PAUSE_{BIKE,RUN,OTHER}_RESUME_DEBOUNCE_SEC`), `.coerceIn(...)` bounds are duplicated across `mapPreferencesToConfig`, `mapAutoPausePreferences`, `saveTuningConfig`, and `update*`/`set*` setters, and `SettingsDataStore.kt` duplicates default `HR_RUN`/`HR_BIKE`/`PWR_BIKE` zone boundaries across 7 sites.

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-121` (`REQ-SET-121.1`..`.4`) — Full `saveTuningConfig` persistence, `BoundedPref` descriptors, centralized `SettingsDataStore` zone defaults, and LoC/hot-path invariants.
* **Test Mapping**: `TST-SET-123` (`TST-SET-123.1`..`.3`) — Reflection-based `TuningConfig` round-trip, `BoundedPref`/dependent clamp/mirror characterization, centralized zone defaults, and `ArchitectureConformanceContractTest` / `DataStoreHotPathContractTest`.

## 3. System Invariants & Preserved Behavior
1. **LoC & Constructor Invariants**: `TuningPreferencesDataStore.kt <= 2,410` LoC (`ArchitectureConformanceContractTest`), constructor remains `private` (`TuningPreferencesDataStoreLifecycleTest`), zero changes to `TuningConfig` public constructor.
2. **Dependent Clamps & Special Rules**: Preserve `mediumDimFactor >= clampedFullDim`, `slopeSteepThreshold >= clampedSlopeFlat`, `dfaA1 T1/T2` `MIN_DFA_A1_THRESHOLD_GAP`, `navigationCueDismissDurationSec == 0 -> DEFAULT`, legacy fallbacks/mirrors, `TrainingApplication` cluster sync, and existing `TrackerService.invalidateAutoPauseCache()` call sites verbatim.
3. **Hot-Path & SWE.2 Invariance**: Zero `runBlocking` added to `SettingsDataStore.kt` (`DataStoreHotPathContractTest`); zero `strings*.xml` edits; no `docs/architecture.md` or `docs/architecture/detailed_design/` update required (`Rule 8.8`: no new subsystem, service, DB, repository, or prefix family).

## 4. Proposed Architectural Changes
### Component 1: `TuningPreferencesDataStore.kt`
* Add `private class BoundedPref<T : Comparable<T>>(val key: Preferences.Key<T>, val default: T, val min: T, val max: T)` with `read(prefs, fallback = default)`, `clamp(value)`, and `write(prefs, value)` plus `clampMediumDim`, `clampSlopeSteep`, `clampNavCueDismiss`, and `clampDfaA1Pair` helpers.
* Inject `private val dataStore: DataStore<Preferences> = context.dataStore` (via `private constructor`, exposed on `@VisibleForTesting createForTesting(..., dataStoreOverride)` with `testScope`) for deterministic JVM unit testing without mocking `filesDir`.
* Replace duplicated `.coerceIn` blocks in `mapPreferencesToConfig`, `mapAutoPausePreferences`, `saveTuningConfig`, and `update*`/`set*` with `BoundedPref` descriptors; add the 4 missing Auto-Pause keys (`KEY_START_IN_AUTO_PAUSE_ENABLED`, `KEY_AUTO_PAUSE_{BIKE,RUN,OTHER}_RESUME_DEBOUNCE_SEC`) in `saveTuningConfig`, shrinking file LoC well below `2,410`.

### Component 2: `SettingsDataStore.kt`
* Define `DEFAULT_HR_RUN_ZONES = listOf(132, 145, 158, 171)`, `DEFAULT_HR_BIKE_ZONES = listOf(127, 140, 153, 166)`, and `DEFAULT_PWR_BIKE_ZONES = listOf(149, 203, 243, 284, 324, 405)` in `SettingsDataStore.Companion` and replace hardcoded literals across all 7 sites (`SettingsSnapshot.DEFAULT`, `getDefaultValue`, `calculateProportionalPowerDefaults`, `getZoneMaxFlow`, `mapSettingsSnapshot`, `checkAndMigratePowerZones`, `resetToDefaults`).

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Gate 3 Check)**: Verify `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` returns `0` (`Rule 4.1`).
2. **Step 2 (`TuningPreferencesDataStore.kt` — `REQ-SET-121.1`, `.2`, `.4`)**: Introduce `BoundedPref` + dependent helpers + `dataStore` test seam in `private constructor`/`createForTesting`, wire all standard bounded prefs, add the 4 missing Auto-Pause keys in `saveTuningConfig`, and verify LoC `<= 2,410`.
3. **Step 3 (`SettingsDataStore.kt` — `REQ-SET-121.3`, `.4`)**: Centralize `DEFAULT_HR_RUN_ZONES`, `DEFAULT_HR_BIKE_ZONES`, `DEFAULT_PWR_BIKE_ZONES` across all 7 sites with zero `runBlocking`.
4. **Step 4 (`TuningPreferencesDataStoreRoundTripTest.kt` & `SettingsDataStoreZoneAtomicTest.kt` — `TST-SET-123.1`, `.2`)**: Add reflection-backed `TuningConfig` round-trip test, `BoundedPref`/dependent-clamp/legacy-mirror characterization tests, and centralized zone-default tests annotated with `TST-SET-123` (`TST-SET-123.1`..`TST-SET-123.3`).
5. **Step 5 (`TST-SET-123.3` Verification)**: Run targeted Gradle tests and `verify_requirement_governance.py`.

## 6. Verification & Rollback Plan
* **Verification**: `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`.
* **Rollback**: Feature branch `feature/ATT-6048` isolation; revert commit before Stage 6 merge if needed.
