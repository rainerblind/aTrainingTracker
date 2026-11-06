# Stage 2: Requirement & Test Specification - ATT-5559: Configurable Cluster Assignment Score Threshold

**Ticket**: [ATT-5559](https://rainerblind.atlassian.net/browse/ATT-5559)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (Auto Name / Route Clusters) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-45.8` | **Branch**: `feature/ATT-5559`  
**Requirement Mapping**: `REQ-SET-075`, `REQ-SET-077` (referencing `REQ-MIG-012`) | **Test Spec ID**: `TST-SET-077`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-SET-075`, `REQ-SET-077`)

### 1.1 Problem Statement & Rationale
`WorkoutClusterEngine.suggestCluster(...)` hardcodes an internal cluster candidate similarity score cutoff of `it.second < 1.0`. Real-world GPS drift, device variances, and elevation fluctuations regularly yield scores between `1.01` and `1.20` on identical courses, producing false-negative rejections and forcing manual cluster naming dialogs. Furthermore, `ClusterTuningContent.kt` and `PreImportTuningBottomSheet` lack an overarching score threshold control.

### 1.2 Functional & Architectural Requirements
1. **Configurable Threshold Parameter (`REQ-SET-075`)**: `TuningPreferencesDefaults` SHALL define `CLUSTER_SCORE_THRESHOLD = 1.2f` (min `0.5f`, max `2.5f`, step `0.1f`). `TuningConfig` and `TuningPreferencesDataStore` SHALL manage persistence to DataStore and `TrainingApplication.cSharedPreferences` (`SP_CLUSTER_SCORE_THRESHOLD = "cluster_score_threshold"`).
2. **Engine Evaluation & Fallback Safety**: `WorkoutClusterEngine.suggestCluster` SHALL evaluate candidates via `it.second < scoreThreshold`, resolving threshold via defensive fallback `runCatching { TrainingApplication.getClusterScoreThreshold().toDouble() }.getOrDefault(1.2)` to ensure test static mock resilience.
3. **Master Sensitivity Coupling & Slider Presentation (`REQ-SET-077`)**: `ClusterTuningContent.kt` SHALL incorporate `clusterScoreThreshold` into derived master sensitivity and render a dedicated `TuningSliderItem` with title `R.string.cluster_tuning_score_threshold_label`, step `0.1f`, formatted `%.1f`, and directional helper text `R.string.cluster_tuning_score_threshold_helper` ("Lower = stricter matching · Higher = more tolerant").
4. **Multi-Consumer Propagation**: Wire `clusterScoreThreshold` through `ClusterTuningScreen.kt`, `ClusterTuningSection.kt`, and `PreImportTuningBottomSheet` (`ImportBackupTabsScreen.kt`).
5. **Localization Parity**: All new strings SHALL provide 100% translation parity across 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Threshold Evaluation)**: *Given* candidate clusters and a workout with similarity score `1.15`, *When* evaluated with default threshold `1.2`, *Then* `WorkoutClusterEngine.suggestCluster` SHALL return the cluster candidate instead of rejecting.
* **AC-2 (UI Slider & Master Coupling)**: *Given* `ClusterTuningContent`, *When* rendered, *Then* a dedicated score threshold slider (`0.5..2.5`, step `0.1`) SHALL be present and coupled to master sensitivity.
* **AC-3 (Preferences Persistence)**: *Given* an adjusted score threshold, *When* saved or reset to defaults, *Then* the value SHALL update in DataStore and SharedPreferences immediately.
* **AC-4 (9-Language Parity)**: *Given* label and helper string tokens, *When* validated across 9 locales, *Then* 100% non-empty translations SHALL exist.

### 1.4 System Invariants
Spatial normalization math (`calculateSimilarity`), sport type firewall (`REQ-SET-064`), 3D altitude extrema logic (`REQ-SET-065`), and unclustered workout integrity MUST NOT be broken.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Amends `REQ-SET-075` (*Dedicated Route Clustering Section in Advanced Settings*) and `REQ-SET-077` (*Standardized Route Clustering Form Controls*), targeting `WorkoutClusterEngine.kt`, `TuningPreferencesDataStore.kt`, and `ClusterTuningContent.kt`.
* `Historical Origin & Commit Trace`: Sprint 2026-40.2 (`REQ-SET-010`), Sprint 2026-40.5 (`ATT-502` / `ATT-503`, `REQ-SET-065`), Sprint 2026-42.1 (`ATT-3355`, `REQ-SET-075`), Sprint 2026-42.2 (`ATT-3452`), Sprint 2026-45.8 (`ATT-5559`).
* `Root Reason for Existing Formulation`: Hardcoded cutoff `1.0` was established when routes were tested with synthetic traces; real-world GPS jitter regularly scores between `1.01` and `1.20`. ATH-5559 makes this threshold configurable (default `1.2f`) without altering component tolerances.
* `Preservation of Core Invariants`: Tolerance ranges, DataStore keys, recalculation locking, 400-line modularity cap, and 9-locale parity remain 100% strictly intact.

---

## 2. Test Specification (`TST-SET-077`)

### Test Case 1: Engine Similarity Cutoff Evaluation (`TST-SET-077.5a`)
* **Scope**: Unit tests in `WorkoutClusterEngineTest.kt`.
* **Procedure**: Verify `suggestCluster` matches at `1.15` when threshold is `1.2`, rejects when threshold is `1.0`, and validates defensive fallback when static mocks are un-stubbed.

### Test Case 2: Preferences & Factory Reset Persistence (`TST-SET-077.5b`)
* **Scope**: Unit tests in `TuningPreferencesDataStoreTest.kt` / `TrainingApplicationTest.kt`.
* **Procedure**: Verify DataStore and SharedPreferences write/read parity, default value `1.2f`, range clamp (`0.5f..2.5f`), and atomic reset to default.

### Test Case 3: UI Slider & Contract Integrity (`TST-SET-077.5c`)
* **Scope**: UI / Contract tests in `ClusterTuningContractTest.kt`.
* **Procedure**: Verify `TuningSliderItem` integration, master sensitivity calculation coupling, and propagation across `ClusterTuningScreen`, `ClusterTuningSection`, and `PreImportTuningBottomSheet`.

### Test Case 4: 9-Language Localization Parity (`TST-UI-078.3`)
* **Scope**: Suite in `TranslationParityTest.kt` verifying `cluster_tuning_score_threshold_label` and `cluster_tuning_score_threshold_helper` across all 9 locales.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-SET-075` | Configurable cluster score threshold (default 1.2, range 0.5..2.5) | `TST-SET-077.5a`, `TST-SET-077.5b` | Unit Test (`WorkoutClusterEngineTest`, `TuningPreferencesDataStoreTest`) |
| `REQ-SET-077` | UI Slider Item & Master Sensitivity Coupling | `TST-SET-077.5c` | Contract Test (`ClusterTuningContractTest`) |
| `REQ-UI-123` | 9-Language Localization Parity | `TST-UI-078.3` | Clean-Room Suite (`TranslationParityTest`) |
| `REQ-PRO-022` | Requirement Archaeology & Invariants Governance | `TST-PRO-015` | Automated Governance (`verify_requirement_governance.py`) |
