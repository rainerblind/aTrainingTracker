# Stage 1 Analysis: ATT-5559 - Configurable Cluster Assignment Score Threshold (Default 1.2)

**Ticket**: [ATT-5559](https://rainerblind.atlassian.net/browse/ATT-5559)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (Auto Name / Route Clusters) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-45.8` | **Branch**: `feature/ATT-5559`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
In `WorkoutClusterEngine.suggestCluster(...)`, automatic route cluster candidate assignment hardcodes a similarity score cutoff of `it.second < 1.0`. During Strava bulk imports, legacy file imports, and live tracking, slight GPS variations (endpoints, apex shifts, or elevation extrema drift on rolling terrain) frequently yield composite similarity scores between `1.01` and `1.20`. 

Because `1.0` is hardcoded, these workouts fail automatic assignment, creating unnecessary manual naming prompts or duplicate cluster variants. Furthermore, while `ClusterTuningContent.kt` exposes individual spatial sliders, the overarching **Cluster Assignment Score Threshold** cannot be configured by athletes.

---

## 2. Root Cause Analysis & Forensic Codebase Verification
* **Forensic RCA**:
  * `WorkoutClusterEngine.kt` (L140): `scoreClusters(...).firstOrNull { it.second < 1.0 }?.first` strictly checks `< 1.0`.
  * `TuningPreferencesDefaults.kt` & `TuningPreferencesDataStore.kt`: Define `CLUSTER_TOL_ENDPOINTS`, `CLUSTER_TOL_APEX`, `CLUSTER_TOL_DISTANCE`, `CLUSTER_TOL_ALTITUDE_POS`, `CLUSTER_USE_SPORT_TYPE`, `CLUSTER_USE_ALTITUDE_POS`, but lack `CLUSTER_SCORE_THRESHOLD` (range: `0.5f..2.5f`, default `1.2f`, step `0.1f`).
  * `TrainingApplication.java`: Lacks `getClusterScoreThreshold()` and `setClusterScoreThreshold(float)` with SharedPreferences key `SP_CLUSTER_SCORE_THRESHOLD = "cluster_score_threshold"`.
  * `ClusterTuningContent.kt`: Master sensitivity slider omits score threshold normalization, and detailed controls lack a `TuningSliderItem` for the score threshold.
  * Three callers of `ClusterTuningContent`: `PreImportTuningBottomSheet` (`ImportBackupTabsScreen.kt`), `ClusterTuningScreen.kt` (`WorkoutClustersViewModel.kt`), and `ClusterTuningSection.kt` (`AdvancedTuningScreen.kt` / `AdvancedTuningDialog.kt` / `WorkoutClusterRepository.recalculateClustersAsync`).
* **Independent Codebase Verification (4-Augen-Prinzip)**:
  * In `WorkoutClusterRepository.recalculateClustersAsync(...)`, parameters are persisted before running `recalculateClustersWithProgress()`, which invokes `suggestCluster(...)`.
  * Static mock isolation: Numerous existing unit tests mock `TrainingApplication::class` without stubbing net-new methods; `suggestCluster` must use defensive fallback `runCatching { TrainingApplication.getClusterScoreThreshold().toDouble() }.getOrDefault(1.2)`.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope**:
  * Add `CLUSTER_SCORE_THRESHOLD = 1.2f` (min `0.5f`, max `2.5f`, step `0.1f`) in `TuningPreferencesDefaults`, `TuningConfig`, `TuningPreferencesDataStore`, and `TrainingApplication`.
  * Update `WorkoutClusterEngine.suggestCluster(...)` to evaluate `it.second < scoreThreshold` (defaulting to configured threshold).
  * Expose `clusterScoreThreshold` in `ClusterTuningContent.kt` (master slider coupling + detail `TuningSliderItem`) and wire through all 3 callers.
  * Add explanatory scale direction (`docs/design_guidelines.md` Sec. 3.1) and 9-language localization (`cluster_tuning_score_threshold_label` and `cluster_tuning_score_threshold_helper`).
  * Update `WorkoutClusterRepository.recalculateClustersAsync(...)` to accept and persist `scoreThreshold: Float? = null`.
* **Out-of-Scope**:
  * Modifying spatial similarity scoring math (`calculateSimilarity`).
* **Complexity Rating**: **1 SP (Standard)**.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirements**: Amends `REQ-SET-065` (*Altitude Extrema-Aware 3D Route Clustering*), `REQ-SET-075` (*Centralized Clustering Controls in Advanced Settings*), `REQ-SET-077` (*Standardized Tuning Form Controls*), and `REQ-MIG-028` (*Pre-Import Sensitivity Tuning*).
* **Preservation of Core Invariants**: Spatial normalization bounds, sport type firewall (`REQ-SET-064`), 3D altitude extrema logic (`REQ-SET-065`), and unclustered decision sovereignty (`REQ-MIG-025`) remain 100% invariant.

---

## 5. Architectural Strategy & High-Level Solution
* **Preferences & Persistence**: Add `cluster_score_threshold` float key in DataStore and SharedPreferences with default `1.2f`.
* **Engine Integration**: `WorkoutClusterEngine.suggestCluster` evaluates candidate matches against `scoreThreshold: Double = runCatching { TrainingApplication.getClusterScoreThreshold().toDouble() }.getOrDefault(1.2)`.
* **UI & Sliders**: `ClusterTuningContent` includes score threshold in master slider normalization and adds `TuningSliderItem` with explanatory direction helper text ("Lower = stricter matching · Higher = more tolerant").
* **Callers**: Wire `clusterScoreThreshold` state through `ImportBackupTabsScreen`, `ClusterTuningScreen`, and `ClusterTuningSection`.

---

## 6. System Invariants & Target Release Verification
* **Target Release**: `V5.0.0` (verified active unreleased version in Jira via `jira_util.py versions`).
* **Risk Rating**: **LOW** (Zero SQLite schema migrations; backward-compatible preference defaults with defensive static mock fallbacks).
