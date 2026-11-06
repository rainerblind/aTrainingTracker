# Stage 3: Implementation Plan - ATT-5662: Clarify unassigned route subtitle copy

**Ticket**: [ATT-5662](https://rainerblind.atlassian.net/browse/ATT-5662)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5662`  
**Requirement Mapping**: `REQ-UI-123` | **Test Mapping**: `TST-UI-078`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
In `ClusterNamingDialog` (`ImportBackupTabsScreen.kt`), `cluster_naming__found_recurring` previously read *"Neue Lieblingsstrecke vom %1$s gefunden."* (EN: *"Found a new route from %1$s."*). This copy prematurely assumed the imported workout was a new favorite route (*Lieblingsstrecke*), creating confusion when the user intended to assign it to an existing route or leave it unclustered. The copy must be updated across all 9 locales to neutral phrasing (*"Nicht automatisch zuweisbare Strecke vom %1$s."* / *"Unassigned route from %1$s."*).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-123` (*Localized Cluster Naming Interface*)
* **Test Mapping**: `TST-UI-078` (`TST-UI-078.1` 9-Language Parity, `TST-UI-078.2` Visual Contract Test)

---

## 3. System Invariants & Preserved Behavior
1. **Resource Key Stability**: Key `cluster_naming__found_recurring` MUST NOT change.
2. **Positional Placeholder Invariant**: Single positional placeholder `%1$s` (date string) MUST be preserved in all 9 locale files.
3. **100% 9-Language Parity**: All 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) maintain complete translations.
4. **Emoji Prohibition (`ATT-5400`)**: Zero decorative inline Unicode emojis in strings.
5. **Zero Layout/Logic Alterations**: Compose layouts, database schema, and Kotlin models remain untouched.

---

## 4. UI Consistency & Architectural Strategy
* **Reference Screen / Dialog**: `ClusterNamingDialog` in `ImportBackupTabsScreen.kt`.
* **Reused Components**: Existing `Text` composable inside `AppModalBottomSheet`.
* **Theme Tokens**: Standard typography and colors.
* **New One-Off Styles**: None. Pure string resource updates.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `cluster_naming__found_recurring` Across All 9 Locales (`REQ-UI-123`)
* **Files**: `app/src/main/res/values*/strings.xml` (9 files)
* **Changes**: Update `cluster_naming__found_recurring` per the Stage 2 translation matrix:
  * EN (`values/`): *"Unassigned route from %1$s."*
  * DE (`values-de/`): *"Nicht automatisch zuweisbare Strecke vom %1$s."*
  * ES, FR, IT, JA, NL, PL, PT updated with 100% parity preserving `%1$s`.

### Step 2: Contract Unit Test Extension (`TST-UI-078.2`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterNamingDialogLabelsVisualContractTest.kt`
* **Changes**: Add assertions verifying that `cluster_naming__found_recurring` contains *"Nicht automatisch zuweisbare Strecke"* (DE) and *"Unassigned route"* (EN) and preserves `%1$s`.

### Step 3: Targeted Verification Suite (`TST-UI-078.1`, `TST-UI-078.2`)
* **Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.clusters.ClusterNamingDialogLabelsVisualContractTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit test suite and governance checks; visual inspection on device in Stage 5.
* **Rollback**: Clean git branch revert on `feature/ATT-5662` with zero impact on `sprint/2026-45.9`.
