# Stage 3: Implementation Plan - ATT-5664: Explicitly label new favorite route creation

**Ticket**: [ATT-5664](https://rainerblind.atlassian.net/browse/ATT-5664)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5664`  
**Requirement Mapping**: `REQ-UI-123` | **Test Mapping**: `TST-UI-078`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
In `ClusterNamingDialog` (`ImportBackupTabsScreen.kt`) and `WorkoutClusterSelectionDialog` (`WorkoutClusterComponents.kt`), the route assignment/creation section ambiguously asked for a generic "new name" rather than explicitly identifying favorite route (*Lieblingsstrecke*) creation, causing confusion with the preceding workout name field (`cluster_naming__workout_name_label`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-123` (*Localized Cluster Naming Interface*)
* **Test Mapping**: `TST-UI-078` (`TST-UI-078.1` 9-Language Parity, `TST-UI-078.2` Tier 1 Regression)

---

## 3. System Invariants & Preserved Behavior
1. **Resource Key Stability**: Key names (`cluster_naming__assignment_label`, `cluster_naming__new_name_prompt`, `cluster_naming__new_route_name_label`) MUST NOT change.
2. **100% 9-Language Parity**: All 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) must provide complete translations.
3. **Emoji Prohibition (`ATT-5400`)**: Zero inline Unicode emojis in strings.
4. **Zero Layout/Logic Alterations**: Compose layouts, database schema, and Kotlin models remain untouched.

---

## 4. UI Consistency & Architectural Strategy
* **Reference Screen / Dialog**: `ClusterNamingDialog` in `ImportBackupTabsScreen.kt` and `WorkoutClusterSelectionDialog` in `WorkoutClusterComponents.kt`.
* **Reused Components**: Existing `AppModalBottomSheet`, `OutlinedTextField`, and `Text` composables.
* **Theme Tokens**: Standard `MaterialTheme.typography.labelLarge` and `labelMedium`.
* **New One-Off Styles**: None. Pure string resource updates.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update String Resources Across All 9 Locales (`REQ-UI-123`)
* **Files**: `app/src/main/res/values*/strings.xml` (9 files)
* **Changes**: Update `cluster_naming__assignment_label`, `cluster_naming__new_name_prompt`, and `cluster_naming__new_route_name_label` per the Stage 2 translation matrix:
  * EN: *"Select existing favorite route:"*, *"Or create a new favorite route:"*, *"New Favorite Route Name"*
  * DE: *"Bestehende Lieblingsstrecke auswählen:"*, *"Oder neue Lieblingsstrecke anlegen:"*, *"Name der neuen Lieblingsstrecke"*
  * ES, FR, IT, JA, NL, PL, PT updated with 100% parity.

### Step 2: Contract Unit Test Verification (`TST-UI-078.1`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterNamingDialogLabelsVisualContractTest.kt`
* **Changes**: Add unit test verifying that `ImportBackupTabsScreen.kt` and `WorkoutClusterComponents.kt` reference the 3 keys and that the updated German strings contain *"Lieblingsstrecke"*.

### Step 3: Targeted Verification Suite (`TST-UI-078.1`, `TST-UI-078.2`)
* **Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.clusters.ClusterNamingDialogLabelsVisualContractTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Immediate unit test suite and governance checks; ADB screenshot visual inspection in Stage 5.
* **Rollback**: Clean git branch revert on `feature/ATT-5664` with zero impact on `sprint/2026-45.9`.
