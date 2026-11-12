# Stage 3: Implementation Plan - ATT-7319: Fix TuningConfig initialization race resetting saved expert settings to defaults

**Ticket**: [ATT-7319](https://issue-tracker/ATT-7319)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-UI-357`  
**Test Mapping**: `TST-UI-317`  
**Branch**: `sprint/2026-46.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
In `AdvancedTuningScreen` and `AdvancedTuningDialog`, `tuningConfigFlow` is collected with `collectAsState(initial = TuningConfig())`. On Frame 0 before disk emission, `LaunchedEffect(persistedConfig)` executes with the default config, sets `isInitialized = true`, and ignores subsequent disk emissions. In addition, `isInitialized` uses `rememberSaveable` while local state uses `remember`, causing drawer navigation restore to leave stale default values.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-357` (*Dedicated Full-Screen Advanced Settings Screen Architecture, Zero-Dismissal Scrolling, and Lifecycle-Driven Category Reorganization*)
* **Test Mapping**: `TST-UI-317` (*Advanced Tuning Screen Architecture & Lifecycle Tests*)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Unintended Regressions**: Existing preference keys, defaults, and clamping logic remain intact.
2. **Persistence Integrity**: Custom values saved to DataStore are consistently reloaded upon screen reopening.
3. **Parent Human Gate Invariance**: Terminal completion stops at Final Review (Human).

---

## 4. Proposed Architectural Changes
### Component 1: `AdvancedTuningScreen.kt`
- Change `collectAsState(initial = TuningConfig())` to `collectAsState(initial = null)`.
- Gate `LaunchedEffect(persistedConfig)` with `persistedConfig != null && !isInitialized`.
- Change `isInitialized` from `rememberSaveable` to `remember`.
- Use `TuningPreferencesDataStore.getInstance(context)`.
- Use `persistedConfig ?: TuningConfig()` as base in `onSaveSettings` and radar subtitles.

### Component 2: `AdvancedTuningDialog.kt`
- Apply identical lifecycle, singleton, and null-emission fixes to `AdvancedTuningDialog.kt`.

### Component 3: `AdvancedTuningScreenTest.kt`
- Add unit/contract assertions verifying source invariants (`initial = null`, singleton instance, `remember` lifecycle).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1**: Modify `AdvancedTuningScreen.kt` to resolve initialization race, lifecycle mismatch, and singleton acquisition.
2. **Step 2**: Modify `AdvancedTuningDialog.kt` to align with `AdvancedTuningScreen.kt`.
3. **Step 3**: Add contract tests in `AdvancedTuningScreenTest.kt`.
4. **Step 4**: Verify targeted tests via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningScreenTest"`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted JUnit tests verifying source contract and compile integrity.
* **Rollback**: Revert changes via git.
