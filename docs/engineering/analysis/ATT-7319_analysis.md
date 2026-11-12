# Stage 1 Analysis: ATT-7319 - Fix TuningConfig initialization race resetting saved expert settings to defaults

**Ticket**: [ATT-7319](https://issue-tracker/ATT-7319)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `sprint/2026-46.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Opening `AdvancedTuningScreen` (or `AdvancedTuningDialog`), toggling any `TuningConfig` switch/slider (e.g. Morning Sleep & Wellness, PMC, Cardio-Power Dynamics, FIETS Climb Formula, Typography, Clustering) and tapping Save fails to display saved state when reopening Expert Settings. Subsequent saves overwrite DataStore values with factory defaults.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause 1 (Frame 0 Default Emission)**: In `AdvancedTuningScreen.kt:86` and `AdvancedTuningDialog.kt:84`, `tuningConfigFlow` is collected with `collectAsState(initial = TuningConfig())` instead of `null`. On Frame 0 prior to disk emission, `LaunchedEffect(persistedConfig)` executes with default `TuningConfig()`, applies default values, and sets `isInitialized = true`. When the real DataStore value emits on Frame 1, `if (!isInitialized)` blocks the real persisted state from populating UI state.
* **Root Cause 2 (Lifecycle Mismatch across Drawer Navigation)**: `isInitialized` uses `rememberSaveable`, whereas the 44 local state variables use plain `remember`. On drawer navigation (`saveState = true / restoreState = true`), `isInitialized` restores as `true` while local states re-initialize to default values.
* **Root Cause 3 (Instance Duplication)**: Instantiating `TuningPreferencesDataStore(context)` directly instead of `TuningPreferencesDataStore.getInstance(context)` causes duplicate memory caches between screens and child composables.
* **Independent Peer Review & Verification**:
  * Verified lines 85-86 and 165-171 in `AdvancedTuningScreen.kt`, and lines 83-84 and 177-183 in `AdvancedTuningDialog.kt`.
  * Verified that `persistedWorkoutCardPrefs` in lines 88 and 131-135 correctly uses `initial = null` and gates with `persistedWorkoutCardPrefs?.let { if (!isAftermathPrefsInitialized) ... }`.
  * Confirmed that `TuningPreferencesDataStore.getInstance(context)` is already implemented in `TuningPreferencesDataStore.kt:799`.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  * Set `initial = null` for `tuningConfigFlow.collectAsState()`.
  * Guard `LaunchedEffect(persistedConfig)` with `persistedConfig != null && !isInitialized`.
  * Change `isInitialized` from `rememberSaveable` to `remember` matching local state lifecycles.
  * Use `TuningPreferencesDataStore.getInstance(context)`.
  * Fall back to `TuningPreferencesDefaults` or `TuningConfig()` defaults for radar subtitles when `persistedConfig` is null.
  * Add unit/contract tests for persistence round-trip.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Restructuring `TuningConfig` schema or adding new preferences.
  * Changing `WorkoutSectionType.DEFAULT_ORDER` or detail tabs (handled in ATT-7320).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-357` (Advanced Tuning & Expert Configuration UI).
* **Historical Origin & Commit Trace**: `ATT-5670` / `ATT-5761` (Modularized tuning dialog and full-screen tuning view).
* **Root Reason for Existing Formulation**: `rememberSaveable` was introduced during initial dialog extraction without accounting for the fact that other tuning state variables were plain `remember` and that `collectAsState` emitted an immediate dummy initial object.
* **Preservation of Core Invariants**: Retaining `TuningConfig` structure, keeping single-shot initialization once disk emission arrives, and preserving all DataStore keys.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-UI-357`.
* **Affected Architecture Elements**: `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`.
* **Verification Impact & Test Surface**: `AdvancedTuningScreenTest`, `AdvancedTuningScreenContractTest`.
* **Risk Assessment & Invariants**: Low risk UI initialization race fix; ensures state from DataStore disk accurately initializes Compose state.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Tuning settings must accurately persist to DataStore and restore identically when reopening.
* **Risk Rating**: `LOW` (Deterministic state initialization timing fix).
