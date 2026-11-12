# Stage 2: Requirement & Test Specification - ATT-7319: Fix TuningConfig initialization race resetting saved expert settings to defaults

**Ticket**: [ATT-7319](https://issue-tracker/ATT-7319)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-UI-357` (*Dedicated Full-Screen Advanced Settings Screen Architecture, Zero-Dismissal Scrolling, and Lifecycle-Driven Category Reorganization*)  
**Test Spec ID**: `TST-UI-317`  
**Branch**: `sprint/2026-46.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-UI-357)

### 1.1 Problem Statement & Rationale
When opening `AdvancedTuningScreen` or `AdvancedTuningDialog`, the UI must correctly reflect previously saved `TuningConfig` values loaded from disk rather than defaulting on Frame 0 and rejecting subsequent disk emissions.

### 1.2 Functional & Architectural Requirements
1. **Asynchronous Initial State Emission Contract**:
   - `AdvancedTuningScreen` and `AdvancedTuningDialog` SHALL collect `tuningDataStore.tuningConfigFlow` with `initial = null` so that initialization (`applyConfig`) only executes upon the first authentic disk emission.
   - `LaunchedEffect(persistedConfig)` SHALL trigger initialization only when `persistedConfig != null && !isInitialized`.
2. **Lifecycle State Alignment**:
   - `isInitialized` in `AdvancedTuningScreen` and `AdvancedTuningDialog` SHALL utilize `remember { mutableStateOf(false) }` (aligning with all local state variables) rather than `rememberSaveable`, ensuring clean reinitialization when returning from navigation saveState transitions.
3. **Singleton DataStore Acquisition**:
   - `tuningDataStore` in `AdvancedTuningScreen` and `AdvancedTuningDialog` SHALL be acquired via `TuningPreferencesDataStore.getInstance(context)` to prevent dual instance memory drift.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Non-default initialization on disk emission)**:
  * *Given* a persisted `TuningConfig` in DataStore with custom values,
  * *When* `AdvancedTuningScreen` or `AdvancedTuningDialog` is opened,
  * *Then* the UI state SHALL populate with the persisted values on emission without being pre-empted by default `TuningConfig()`.
* **Criterion 2 (Drawer navigation state alignment)**:
  * *Given* the user navigating back and forth via the drawer,
  * *When* `AdvancedTuningScreen` is recomposed,
  * *Then* `isInitialized` SHALL NOT prematurely latch to `true` while state variables reset to defaults.

### 1.4 System Invariants
- All 44+ preference keys and default fallbacks in `TuningPreferencesDefaults` MUST remain unchanged.
- Backward compatibility with existing user DataStore files MUST be 100% preserved.

---

## 2. Test Specification (TST-UI-317)

### Test Case 1: `testAdvancedTuning_initializationContracts` (`TST-UI-317.7`)
* **Scope**: JVM Unit & Source Architecture Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreenTest.kt`
* **Preconditions**: `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` compiled.
* **Action**: Audit source code and runtime AST to ensure:
  - `tuningConfigFlow.collectAsState(initial = null)` is used.
  - `TuningPreferencesDataStore.getInstance` is used.
  - `isInitialized` uses `remember`, not `rememberSaveable`.
* **Expected Result**: Assertions pass with zero violations.

### Test Case 2: Targeted Unit Regression (`TST-UI-317.8`)
* **Scope**: Targeted Test Suite Execution
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuning*"`
* **Expected Result**: 100% pass rate across all tuning tests.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-357` (Clause 1) | Asynchronous Initial State Emission Contract (`initial = null`) | `TST-UI-317.7` | Contract Test |
| `REQ-UI-357` (Clause 2) | Lifecycle State Alignment (`remember` vs `rememberSaveable`) | `TST-UI-317.7` | Contract Test |
| `REQ-UI-357` (Clause 3) | Singleton DataStore Instance Acquisition | `TST-UI-317.7` | Contract Test |
| `REQ-UI-357` (Clause 5) | Non-regression across tuning suite | `TST-UI-317.8` | JUnit Suite |
