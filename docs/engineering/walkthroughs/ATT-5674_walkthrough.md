# Stage 5: Walkthrough & Verification - ATT-5674: ClusterNamingDialog Return to Create New

**Ticket**: [ATT-5674](https://rainerblind.atlassian.net/browse/ATT-5674)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5674`  
**Requirement Mapping**: `REQ-UI-462` (`REQ-UI-462.1`–`REQ-UI-462.3`) | **Test Mapping**: `TST-UI-462` (`TST-UI-462.1`–`TST-UI-462.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Executive Summary & Verification Overview
Resolved the selection deadlock in `ClusterNamingDialog` when an athlete picks an existing favorite route:
* **Selection Dialog Escape Hatch (`onCreateNew`)**: Added optional `onCreateNew: (() -> Unit)? = null` to `WorkoutClusterSelectionDialog` (`WorkoutClusterComponents.kt`). When non-null, a selectable Card with `Icons.Default.Add` and `R.string.cluster_naming__create_new` renders above suggestions, reverting `selectedCluster = null` and dismissing the sheet.
* **Direct Clear Action on Selected Route Field**: In `ClusterNamingDialog` (`ImportBackupTabsScreen.kt`), wrapped `trailingIcon` with a `Row` displaying an `IconButton` with `Icons.Default.Clear` (`R.string.clear`) when `selectedCluster != null` for 1-tap reset without opening the picker.
* **Input State Restoration**: Resetting `selectedCluster = null` immediately re-displays the new favorite route name input while strictly preserving previously entered or pre-filled `name` text.
* **Contract & Localization Tests**: Authored `ClusterNamingDialogEscapeContractTest.kt` verifying composable reflection signatures, default argument bitmasks, 9-locale parity, and hit counter stripping invariance.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-462.1` | `TST-UI-462.1` | `ClusterNamingDialogEscapeContractTest` (Reflection & Contract) | **PASSED** | `Verified` |
| `REQ-UI-462.2` | `TST-UI-462.2` | `ClusterNamingDialogEscapeContractTest`, `TranslationParityTest` | **PASSED** | `Verified` |
| `REQ-UI-462.3` | `TST-UI-462.3` | `ClusterNamingDialogEscapeContractTest`, `ModalBottomSheetDialogsIntegrityTest` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Runtime: 55s / 1s cached)
* **Executed Scope**:
  * `--tests "com.atrainingtracker.trainingtracker.migration.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.clusters.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `--tests "com.atrainingtracker.trainingtracker.build.*"`
* **Result**: `BUILD SUCCESSFUL in 55s` (373 tests executed, 100% PASS across targeted migration, cluster UI dialogs, theme audit, and build suites).
* **Governance Check**: `python3 tools/verify_requirement_governance.py` -> `PASS: Living documentation master tables match all 9 domain shards with 100% parity.`

---

## 4. Key Implementation Highlights

```kotlin
// app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt
if (onCreateNew != null) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCreateNew(); onDismiss() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = stringResource(R.string.cluster_naming__create_new), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
```

---

## 5. Invariants & Preserved Behavior
1. **Existing Caller Invariance (`REQ-UI-462.3`)**: Callers omitting `onCreateNew` (`WorkoutClustersScreen`, `WorkoutClusterHeatmapScreen`) remain 100% visually and functionally identical.
2. **State & Input Retention**: Tapping clear or create-new resets `selectedCluster = null` while preserving the user's typed `name`.
3. **9-Language Parity & Zero Emojis (`ATT-5400`)**: Zero hardcoded strings; reuses existing localized resources across all 9 locales.
