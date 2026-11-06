# Stage 2: Requirement & Test Specification - ATT-5560: Convert ClusterNamingDialog to AppModalBottomSheet & Edit Workout Name

**Ticket**: [ATT-5560](https://rainerblind.atlassian.net/browse/ATT-5560)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (Auto Name / Route Clusters) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-45.8` | **Branch**: `feature/ATT-5560`  
**Requirement Mapping**: `REQ-MIG-025` (referencing `REQ-UI-149`) | **Test Spec ID**: `TST-MIG-022`, `TST-UI-102`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-MIG-025`, `REQ-UI-149`)

### 1.1 Problem Statement & Rationale
When an imported workout does not match an existing route cluster, `ClusterNamingDialog` in `ImportBackupTabsScreen.kt` prompts the athlete to resolve the cluster. Previously, it rendered as a classic centered `AlertDialog` (violating `AppModalBottomSheet` mobile ergonomic guidelines, `ATT-5399`), dropped `workoutName` display and editing, and did not propagate edited workout names to `LegacyImportEngine` or SQLite persistence.

### 1.2 Functional & Architectural Requirements
1. **AppModalBottomSheet Modernization (`REQ-UI-149`)**: `ClusterNamingDialog` SHALL compose `AppModalBottomSheet(scrollable = true)` with `DarkMapAntiFlashOverlay`, queue count badge in header, route preview map, and Rule 73 vertically stacked full-width buttons.
2. **Workout Name Display & Inline Editing (`REQ-MIG-025`)**: An editable `OutlinedTextField` SHALL display and allow editing the imported workout's title (`customWorkoutName`), pre-filled with `state.workoutName ?: ""`.
3. **Cluster Name Pre-fill**: When creating a new route cluster (`selectedCluster == null`), the cluster name field SHALL pre-fill with `WorkoutClusterEngine.stripHitCount(state.workoutName ?: "")`.
4. **Structured Decision Payload**: The interaction response SHALL propagate as `ClusterDecision(clusterId: Long?, newClusterName: String?, customWorkoutName: String?)` from `BackupRestoreViewModel` to `LegacyImportEngine.ProgressListener.onNewClusterCandidate`.
5. **Persistence Parity**: If a cluster is assigned (existing or new), `customWorkoutName` (if non-blank) SHALL be persisted via `assignClusterToWorkout(context, workoutId, clusterId, true, customWorkoutName)`. If left unclustered, `customWorkoutName` (if non-blank) SHALL be updated in `WorkoutSummaries.WORKOUT_NAME` with `clusterId = -1L`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Bottom Sheet Presentation)**: *Given* an imported workout requiring manual resolution, *When* presented to the athlete, *Then* `ClusterNamingDialog` SHALL render as an `AppModalBottomSheet` with Rule 73 stacked buttons.
* **AC-2 (Workout Name Displayed & Editable)**: *Given* an imported workout with `state.workoutName`, *When* `ClusterNamingDialog` composes, *Then* the workout name SHALL appear in an editable text field.
* **AC-3 (Edited Workout Name Persisted)**: *Given* an edited workout name, *When* confirmed as existing cluster, new cluster, or unclustered, *Then* `WorkoutSummaries.WORKOUT_NAME` SHALL be updated with the edited name.
* **AC-4 (9-Language Parity)**: *Given* string `cluster_naming__workout_name_label`, *When* evaluated, *Then* all 9 supported locales SHALL have matching non-empty translations.

### 1.4 System Invariants
`Semaphore(3)` backpressure throttling, `recalculationMutex.withLock` thread safety, and user decision sovereignty (`clusterId = -1L` when unclustered) MUST NOT be broken.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Amends `REQ-MIG-025` (*TCX Import Cluster Decision Integrity*) and `REQ-UI-149` (*Modernized Modal Bottom Sheet Dialogs*), targeting `ImportBackupTabsScreen.kt`, `BackupRestoreViewModel.kt`, and `LegacyImportEngine.kt`.
* `Historical Origin & Commit Trace`: `ATT-316` (commit `5c5d353d`), `ATT-741` (commit `69df0996`), `ATT-5399` (`AppModalBottomSheet` standard).
* `Root Reason for Existing Formulation`: Centered `AlertDialog` was used before mobile ergonomic bottom sheets were standardized; `Pair<Long?, String?>` omitted a workout name channel.
* `Preservation of Core Invariants`: `Semaphore(3)` backpressure throttling, `recalculationMutex` synchronization, and unclustered workout integrity remain 100% preserved.

---

## 2. Test Specification (`TST-MIG-022`, `TST-UI-102`)

### Test Case 1: Bottom Sheet Contract & Stacked Buttons (`TST-UI-102.14`)
* **Scope**: Contract tests in `ModalBottomSheetDialogsIntegrityTest.kt`.
* **Procedure**: Verify `ClusterNamingDialog` composes `AppModalBottomSheet` with Rule 73 stacked buttons, displays editable Workout Name, and pre-fills stripped cluster name.

### Test Case 2: Custom Workout Name Persistence (`TST-MIG-022.8`)
* **Scope**: Unit tests in `LegacyImportEngineTest.kt` / `BackupRestoreViewModelTest.kt`.
* **Procedure**: Verify `ClusterDecision` propagation and assertion that `WorkoutSummaries.WORKOUT_NAME` is updated for both clustered and unclustered decisions.

### Test Case 3: 9-Language Localization Parity (`TST-UI-078.2`)
* **Scope**: `TranslationParityTest.kt` verifying `cluster_naming__workout_name_label` across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-149` | Convert `ClusterNamingDialog` to `AppModalBottomSheet` | `TST-UI-102.14` | Contract Test (`ModalBottomSheetDialogsIntegrityTest`) |
| `REQ-MIG-025` | Edit and persist imported `customWorkoutName` | `TST-MIG-022.8` | Unit Test (`LegacyImportEngineTest`) |
| `REQ-UI-123` | 9-Language Localization Parity | `TST-UI-078.2` | Clean-Room Suite (`TranslationParityTest`) |
| `REQ-MIG-025` | Requirement Governance & Architecture Invariance | `TST-MIG-022.9` | Governance Script (`verify_requirement_governance.py`) |
