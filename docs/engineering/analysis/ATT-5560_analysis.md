# Stage 1 Analysis: ATT-5560 - Import Cluster Resolution (ClusterNamingDialog): Convert to AppModalBottomSheet and Edit Workout Name

**Ticket**: [ATT-5560](https://rainerblind.atlassian.net/browse/ATT-5560)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (Auto Name / Route Clusters) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-45.8` | **Branch**: `feature/ATT-5560`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
During file imports (FIT, TCX, GPX) or bulk cloud recovery, workouts without an automated route cluster match trigger `ClusterNamingDialog` in `ImportBackupTabsScreen.kt`. Two key UX/architectural gaps exist:
1. **Legacy Centered `AlertDialog`**: `ClusterNamingDialog` still renders as a classic centered dialog, violating `docs/design_guidelines.md` Sec. 5.3 (`AppModalBottomSheet` standard, `ATT-5399`).
2. **Workout Name Neither Shown nor Editable**: `ClusterInteraction` carries `workoutName: String?`, but `ClusterNamingDialog` never displays it or allows edits. If left unclustered or assigned, user name edits cannot be made or persisted.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic RCA**:
  * In `ImportBackupTabsScreen.kt` (L860–1003), `ClusterNamingDialog` uses `AlertDialog` with horizontal actions (violating Rule 73 stacked buttons for German/Polish/Dutch labels $> 15$ chars) and lacks `DarkMapAntiFlashOverlay`.
  * `ClusterInteraction` (L69–82) in `BackupRestoreViewModel.kt` and `LegacyImportEngine.ProgressListener.onNewClusterCandidate` (L115–126) use `Pair<Long?, String?>`, omitting a channel for `customWorkoutName`.
  * In `LegacyImportEngine.kt` (L1368–1400), `assignClusterToWorkout` is called with `customWorkoutName = null` and unclustered exits leave `WorkoutSummaries.WORKOUT_NAME` un-updated.
* **Independent Codebase Verification (4-Augen-Prinzip)**:
  * Verified `WorkoutClusterEngine.assignClusterToWorkout` (L687–715) ALREADY accepts `customWorkoutName: String? = null` and writes it to `WorkoutSummaries.WORKOUT_NAME` when present.
  * Verified `WorkoutClusterEngine.stripHitCount` exists as `@JvmStatic` utility.
  * Verified `isMapLoaded` map retention across sequential queue items (`REQ-MAP-031`) must be preserved.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope**:
  * Convert `ClusterNamingDialog` to `AppModalBottomSheet` with `DarkMapAntiFlashOverlay`, queue badge in header, and Rule 73 stacked full-width action buttons.
  * Display and enable editing of Workout Name (`OutlinedTextField`), pre-filled from `state.workoutName ?: ""`.
  * Pre-fill new cluster name with `WorkoutClusterEngine.stripHitCount(state.workoutName ?: "")` when `selectedCluster == null`.
  * Introduce `ClusterDecision(clusterId: Long?, newClusterName: String?, customWorkoutName: String?)` replacing `Pair<Long?, String?>` across `BackupRestoreViewModel.kt` and `LegacyImportEngine.kt`.
  * Persist `customWorkoutName` in `LegacyImportEngine.kt` (via `assignClusterToWorkout` or direct `summariesDb` update for unclustered).
  * 9-language localization parity for `cluster_naming__workout_name_label`.
* **Out-of-Scope**:
  * Modifying spatial scoring math (`calculateSimilarity` / `scoreClusters`), `WorkoutClusterSelectionDialog`, or `PreImportTuningBottomSheet`.
* **Complexity Rating**: **2 SP (Standard)**.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirements**: Amends `REQ-MIG-025` (*Cluster Decision Integrity & Unclustered Preservation*), `REQ-MIG-027` (*Cluster Assignment*), `REQ-MIG-029` (*Spatial Scoring Parity*), `REQ-MAP-031` (*Map Lifecycle Retention*), and `REQ-UI-123` (*Localized Cluster Naming Interface*).
* **Historical Origin**: `ATT-316` / `ATT-349` (foreground cluster resolution & backpressure semaphore), `ATT-413` (repository refresh), `ATT-5399` (`AppModalBottomSheet` standard).
* **Preservation of Core Invariants**: `Semaphore(3)` backpressure throttling, `recalculationMutex.withLock` thread safety, `isMapLoaded` retention across sequential items, and user decision sovereignty (`REQ-MIG-025`, "Leave Unclustered" keeps `clusterId = -1L`).

---

## 5. Architectural Strategy & High-Level Solution
* **Data Model**: Define `data class ClusterDecision(val clusterId: Long? = null, val newClusterName: String? = null, val customWorkoutName: String? = null)` in `LegacyImportEngine.kt`.
* **ViewModel**: Update `ClusterInteraction.deferred` to `CompletableDeferred<ClusterDecision>` and `provideClusterDecision(decision: ClusterDecision)`.
* **UI**: Replace `AlertDialog` with `AppModalBottomSheet` in `ImportBackupTabsScreen.kt`, stacking `Button` (Save) and `OutlinedButton` (Leave Unclustered) vertically (`Modifier.fillMaxWidth()`).
* **Import Pipeline**: In `LegacyImportEngine.kt`, pass `decision.customWorkoutName` into `assignClusterToWorkout` or update `WorkoutSummaries.WORKOUT_NAME` when unclustered.

---

## 6. System Invariants & Target Release Verification
* **Target Release**: `V5.0.0` (verified active unreleased version in Jira via `jira_util.py versions`).
* **Risk Rating**: **LOW** (Standard UI refactoring and typed decision payload; zero SQLite schema migrations).
