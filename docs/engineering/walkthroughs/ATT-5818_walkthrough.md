# Stage 5: Walkthrough & Verification - ATT-5818: ClusterNamingDialog Route Picker Tap Handling

**Ticket**: [ATT-5818](https://rainerblind.atlassian.net/browse/ATT-5818)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5818`  
**Requirement Mapping**: `REQ-UI-462` (`REQ-UI-462.4`) | **Test Mapping**: `TST-UI-462` (`TST-UI-462.5`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Executive Summary & Verification Overview
Resolved the tap consumption defect on the route assignment field in `ClusterNamingDialog`:
* **InteractionSource Tap Handling (`REQ-UI-462.4`)**: Bound a `MutableInteractionSource` (`remember(state) { MutableInteractionSource() }`) to the read-only route assignment `OutlinedTextField` and added `LaunchedEffect(routePickerInteractionSource)` observing `PressInteraction.Release` to set `showSelectionDialog = true` whenever the athlete taps anywhere across the text field surface.
* **Dead Modifier Cleanup**: Removed outer dead `Modifier.clickable { showSelectionDialog = true }` from `OutlinedTextField` (which was consumed by Compose's internal `BasicTextField` pointer handler on `enabled = true` fields, Rule 77 / Design Guidelines §5.19).
* **Trailing Icon & State Invariants**: Retained `readOnly = true` state and independent click handlers on trailing icons (`Icons.Default.Clear` for clearing assignment and `my_locations` for opening the picker).
* **Contract Test**: Extended `ClusterNamingDialogEscapeContractTest.kt` with `testRoutePickerTextField_bindsInteractionSourceContract()` (`TST-UI-462.5`) verifying interaction source binding and absence of dead clickable modifiers.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-462.4` | `TST-UI-462.5` | `ClusterNamingDialogEscapeContractTest` (Reflection & Source Contract) | **PASSED** | `Verified` |
| `REQ-UI-462.2` | `TST-UI-462.2` | `ClusterNamingDialogEscapeContractTest`, `TranslationParityTest` | **PASSED** | `Verified` |
| `REQ-UI-462.3` | `TST-UI-462.3` | `ClusterNamingDialogEscapeContractTest`, `ModalBottomSheetDialogsIntegrityTest` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Runtime: 1m 28s / cached 36s)
* **Executed Scope**:
  * `--tests "com.atrainingtracker.trainingtracker.ui.clusters.*"`
  * `--tests "com.atrainingtracker.trainingtracker.migration.*"`
  * `--tests "com.atrainingtracker.trainingtracker.localization.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `--tests "com.atrainingtracker.trainingtracker.build.*"`
* **Result**: `BUILD SUCCESSFUL in 1m 28s` (32 actionable tasks, 12 executed, 20 up-to-date, 100% test pass rate across targeted suites).
* **Build Verification**: `./gradlew assembleDebug` -> `BUILD SUCCESSFUL in 36s` (43 actionable tasks, 4 executed, 39 up-to-date).
* **Governance Check**: `python3 tools/verify_requirement_governance.py` -> `PASS: Living documentation master tables match all 9 domain shards with 100% parity; REQ-UI-462 archaeology verified.`

---

## 4. Key Implementation Highlights

```kotlin
// app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt
val routePickerInteractionSource = remember(state) { MutableInteractionSource() }
LaunchedEffect(routePickerInteractionSource) {
    routePickerInteractionSource.interactions.collect { interaction ->
        if (interaction is PressInteraction.Release) {
            showSelectionDialog = true
        }
    }
}

OutlinedTextField(
    value = selectedCluster?.name ?: stringResource(R.string.cluster_naming__create_new),
    onValueChange = {},
    readOnly = true,
    modifier = Modifier.fillMaxWidth(),
    interactionSource = routePickerInteractionSource,
    label = { Text(stringResource(R.string.cluster_naming__selected_route_label)) },
    ...
)
```

---

## 5. Invariants & Preserved Behavior
1. **Trailing Icon Actions**: Clear button (`X`) and picker icon (`my_locations`) retain independent click handlers and function unchanged.
2. **Read-Only Preservation**: Text field remains strictly `readOnly = true` (no soft keyboard popup, no cursor).
3. **Queue Integrity**: Dialog reset keyed to `state` (`remember(state)`) ensures seamless progression across multiple import candidate tasks.
4. **Localization Parity**: 100% translation parity preserved across all 9 locales with zero inline emojis (`ATT-5400`).
