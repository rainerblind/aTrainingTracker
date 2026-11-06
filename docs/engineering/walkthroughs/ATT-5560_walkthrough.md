# Stage 5 Walkthrough: ATT-5560 — Convert ClusterNamingDialog to AppModalBottomSheet & Edit Workout Name

**Ticket**: [ATT-5560](https://rainerblind.atlassian.net/browse/ATT-5560)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (Auto Name / Route Clusters) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-45.8` | **Branch**: `feature/ATT-5560`  
**Requirement Mapping**: `REQ-MIG-025` (referencing `REQ-UI-149`, `REQ-UI-123`) | **Test Mapping**: `TST-MIG-022`, `TST-UI-102`, `TST-UI-078`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Executive Summary
During TCX activity import, unassigned route candidates prompted athletes using a legacy centered `AlertDialog` that dropped imported workout title display, lacked custom name editing, and did not propagate edited names to persistence (`ATT-5399`, `ATT-316`).

This ticket completes end-to-end modernization:
1. **AppModalBottomSheet Modernization (`REQ-UI-149`)**: Refactored `ClusterNamingDialog` in `ImportBackupTabsScreen.kt` to `AppModalBottomSheet(scrollable = true)` with `DarkMapAntiFlashOverlay`, header queue count badge (`queueCount > 1`), and Rule 73 vertically stacked full-width primary (`Button`, "Speichern") and secondary (`OutlinedButton`, "Ungruppiert lassen") CTAs.
2. **Workout Name Inline Editing & Pre-Fill (`REQ-MIG-025`)**: Added `OutlinedTextField` pre-filled with imported `state.workoutName ?: ""`. Pre-fills new route cluster name with `WorkoutClusterEngine.stripHitCount(state.workoutName ?: "")`.
3. **Structured `ClusterDecision` Propagation**: Replaced `Pair<Long?, String?>` with `ClusterDecision(clusterId, newClusterName, customWorkoutName)` across `BackupRestoreViewModel.kt` and `LegacyImportEngine.kt`.
4. **Persistence Parity**: Persists `customWorkoutName` via `WorkoutClusterEngine.assignClusterToWorkout(...)` when clustered, or directly into `WorkoutSummaries.WORKOUT_NAME` when left unclustered.
5. **9-Language Parity (`REQ-UI-123`)**: Added `cluster_naming__workout_name_label` across all 9 supported locales.

---

## 2. Key Architectural & Code Changes
| File / Component | Summary of Changes |
| :--- | :--- |
| `ImportBackupTabsScreen.kt` | Modernized `ClusterNamingDialog` to `AppModalBottomSheet`, added `DarkMapAntiFlashOverlay`, queue count badge, editable workout name, and Rule 73 stacked buttons. |
| `LegacyImportEngine.kt` | Introduced `ClusterDecision`, updated `ProgressListener.onNewClusterCandidate`, and persisted `customWorkoutName` for clustered and unclustered paths. |
| `BackupRestoreViewModel.kt` | Plumbed `ClusterDecision` through interaction queue and `provideClusterDecision` overloads. |
| `res/values*/strings.xml` | Added `cluster_naming__workout_name_label` across EN, DE, ES, FR, IT, JA, NL, PL, PT. |
| `ModalBottomSheetDialogsIntegrityTest.kt` | Added contract test asserting `ClusterNamingDialog` composable existence and public visibility. |
| `LegacyImportEngineWorkoutNameTest.kt` | Added unit tests verifying `customWorkoutName` persistence for clustered and unclustered decisions. |

---

## 3. Verification & Test Execution
* **Tier 1 Targeted Test Scope**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.database.TcxImportClusterParityTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*" --max-workers=2`
  -> `BUILD SUCCESSFUL in 1m 27s` (100% pass rate).
* **On-Device / UI Verification**: Deployed `app-debug.apk` to connected Android emulator (`Pixel_5_Android_12`, API 31). Verified navigation drawer and `ImportBackupTabsScreen` (`docs/engineering/walkthroughs/img/ATT-5560_import_backup_screen.png`).
* **Localization & Governance Audit**: `TranslationParityTest` passed 100%; `python3 tools/verify_requirement_governance.py` passed with 0 violations.

---

## 4. Requirement & Test Traceability Matrix
| Requirement ID | Test Case ID | Status | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-149` | `TST-UI-102.14` | `Verified` | Contract test in `ModalBottomSheetDialogsIntegrityTest` |
| `REQ-MIG-025` | `TST-MIG-022.8` | `Verified` | Clustered and unclustered persistence unit tests in `LegacyImportEngineWorkoutNameTest` |
| `REQ-UI-123` | `TST-UI-078.2` | `Verified` | 9-language localization parity in `TranslationParityTest` |
| `REQ-MIG-025` | `TST-MIG-022.9` | `Verified` | Governance audit via `verify_requirement_governance.py` |
