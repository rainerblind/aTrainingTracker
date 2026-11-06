# Stage 3: Implementation Plan - ATT-5559: Configurable Cluster Assignment Score Threshold

**Ticket**: [ATT-5559](https://rainerblind.atlassian.net/browse/ATT-5559)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (Auto Name / Route Clusters) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-45.8` | **Branch**: `feature/ATT-5559`  
**Requirement Mapping**: `REQ-SET-075`, `REQ-SET-077` (referencing `REQ-MIG-012`, `REQ-UI-123`)  
**Test Mapping**: `TST-SET-077` (`TST-SET-077.5a`, `TST-SET-077.5b`, `TST-SET-077.5c`), `TST-UI-078.3`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
`WorkoutClusterEngine.suggestCluster(...)` hardcodes a cluster candidate similarity cutoff of `it.second < 1.0`. Real-world GPS drift and subtle elevation differences frequently yield scores between `1.01` and `1.20`, causing false-negative cluster rejections and redundant naming prompts. Furthermore, `ClusterTuningContent.kt` and `PreImportTuningBottomSheet` lack an overarching score threshold slider. This plan defines the end-to-end architecture to expose a user-configurable threshold (default `1.2f`, range `0.5f..2.5f`, step `0.1f`) across DataStore, SharedPreferences, cluster engine matching, and all 3 tuning UI entry points.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-SET-075` (Configurable cluster score threshold parameter & persistence), `REQ-SET-077` (Standardized `TuningSliderItem` & Master Sensitivity coupling), `REQ-UI-123` (9-language localization parity).
* **Test Mapping**: `TST-SET-077.5a` (`WorkoutClusterEngineScoreThresholdTest.kt`), `TST-SET-077.5b` (`TuningPreferencesDataStoreClusterTest.kt`), `TST-SET-077.5c` (`ClusterTuningContractTest.kt`), `TST-UI-078.3` (`TranslationParityTest.kt`).

---

## 3. System Invariants & Preserved Behavior
1. **Code Lock (Rule 4)**: Zero production code edits in `app/src/` until Gate 3 passes audit (`check-gate`).
2. **Defensive Mock Resilience**: `WorkoutClusterEngine.suggestCluster` resolves threshold via `runCatching { TrainingApplication.getClusterScoreThreshold().toDouble() }.getOrDefault(1.2)` to prevent un-stubbed static mock failures in existing test suites.
3. **Mathematical Invariants**: Centroid calculations, spatial normalization bounds, sport type firewall (`REQ-SET-064`), and 3D altitude extrema logic (`REQ-SET-065`) remain 100% strictly unchanged.
4. **Design Guidelines Consistency (Sec. 3.1)**: Expose directional helper text ("Lower = stricter matching · Higher = more tolerant") under the slider in all 9 languages.

---

## 4. Proposed Architectural Changes
### Component 1: `TuningPreferencesDataStore.kt` & `TrainingApplication.java` (Preferences Layer)
* `TuningPreferencesDefaults`: Add `CLUSTER_SCORE_THRESHOLD = 1.2f`, `MIN_CLUSTER_SCORE_THRESHOLD = 0.5f`, `MAX_CLUSTER_SCORE_THRESHOLD = 2.5f`, `STEP_CLUSTER_SCORE_THRESHOLD = 0.1f`.
* `TuningConfig`: Add `clusterScoreThreshold: Float = TuningPreferencesDefaults.CLUSTER_SCORE_THRESHOLD`.
* `TuningPreferencesDataStore`: Add `KEY_CLUSTER_SCORE_THRESHOLD`, `clusterScoreThreshold` Flow, clamp on save, and reset handling in `resetToDefaults()`.
* `TrainingApplication`: Add `SP_CLUSTER_SCORE_THRESHOLD = "cluster_score_threshold"`, `getClusterScoreThreshold()`, and `setClusterScoreThreshold(float)`.

### Component 2: `WorkoutClusterEngine.kt` & `WorkoutClusterRepository.kt` (Domain Layer)
* `WorkoutClusterEngine.suggestCluster`: Replace `it.second < 1.0` with `it.second < scoreThreshold` where `scoreThreshold: Double = runCatching { TrainingApplication.getClusterScoreThreshold().toDouble() }.getOrDefault(1.2)`.
* `WorkoutClusterRepository.recalculateClustersAsync`: Accept and persist optional `scoreThreshold: Float? = null`.

### Component 3: `ClusterTuningContent.kt` & UI Consumers (Presentation Layer)
* `ClusterTuningContent`: Incorporate `clusterScoreThreshold` into master sensitivity derivation (average of normalized values) and add `TuningSliderItem` under detailed parameters with step `0.1f`, format `%.1f`, and directional helper text.
* Wire through all 3 callers:
  1. `PreImportTuningBottomSheet` (`ImportBackupTabsScreen.kt` & `BackupRestoreViewModel.kt`).
  2. `ClusterTuningScreen.kt` & `WorkoutClustersViewModel.kt`.
  3. `ClusterTuningSection.kt` (`AdvancedTuningScreen.kt` / `AdvancedTuningDialog.kt`).

### Component 4: 9-Language Localization (`res/values*/strings.xml`)
* Define `cluster_tuning_score_threshold_label` and `cluster_tuning_score_threshold_helper` across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: 9-Language Localization Resources (`REQ-UI-123`)
* Add `cluster_tuning_score_threshold_label` and `cluster_tuning_score_threshold_helper` across all 9 `strings.xml` files.

### Step 2: Storage & Preferences Integration (`REQ-SET-075`)
* Add constants and getter/setter in `TrainingApplication.java`.
* Update `TuningPreferencesDefaults.kt`, `TuningConfig`, and `TuningPreferencesDataStore.kt`.

### Step 3: Engine Threshold Evaluation & Repository Wiring (`REQ-SET-075`)
* Update `suggestCluster` in `WorkoutClusterEngine.kt` and `recalculateClustersAsync` in `WorkoutClusterRepository.kt`.

### Step 4: UI Sliders & Multi-Consumer Wiring (`REQ-SET-077`)
* Update `ClusterTuningContent.kt` (master coupling + detailed slider).
* Update `BackupRestoreViewModel` & `ImportBackupTabsScreen`.
* Update `WorkoutClustersViewModel` & `ClusterTuningScreen`.
* Update `ClusterTuningSection`, `AdvancedTuningScreen`, and `AdvancedTuningDialog`.

### Step 5: Unit & Contract Tests (`TST-SET-077.5a/b/c`, `TST-UI-078.3`)
* Add unit tests in `TuningPreferencesDataStoreClusterTest` and `WorkoutClusterEngineScoreThresholdTest`.
* Update `ClusterTuningContractTest` and verify `TranslationParityTest` + `verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStoreClusterTest" --tests "com.atrainingtracker.trainingtracker.database.*Cluster*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.ClusterTuningContractTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.
* **Rollback**: Clean branch isolation on `feature/ATT-5559` allows reverting via `git checkout feature/ATT-5559 -- .`.
