# Stage 1: Problem Domain & Root Cause Analysis - ATT-5674

**Ticket**: [ATT-5674](https://rainerblind.atlassian.net/browse/ATT-5674)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176): Auto Name / Route Clusters | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5674`  
**Requirement Mapping**: `REQ-UI-123` (*Localized Cluster Naming Interface*), `REQ-UI-149` (*Modernized Modal Bottom Sheet Dialogs*)  
**Test Mapping**: `TST-UI-078`, `TST-UI-102`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Statement & Motivation
In `ClusterNamingDialog` (`ImportBackupTabsScreen.kt`, lines 788, 967–990), `selectedCluster` starts as `null`. The route selection field displays `+ Neue Strecke erstellen...` (`R.string.cluster_naming__create_new`), and the input field for naming a new favorite route (`if (selectedCluster == null)`) is visible.

**The Deadlock:**
When the athlete taps *„Gewählte Strecke“* and selects an existing favorite route in `WorkoutClusterSelectionDialog` (`WorkoutClusterComponents.kt`, lines 669–733), `selectedCluster` becomes non-null:
1. The new route name field disappears (`if (selectedCluster == null)` becomes `false`).
2. Re-opening `WorkoutClusterSelectionDialog` only displays candidate clusters (`candidates: List<Pair<WorkoutCluster, Double>>`) with no option to revert to creating a new route; "Abbrechen" dismisses the sheet while leaving `selectedCluster` set.
3. The selection field lacks a clear/reset button (`Icons.Default.Clear`).
4. Closing `ClusterNamingDialog` bypasses the queue by treating the workout as unassigned (`LegacyImportEngine.ClusterDecision(clusterId = null, newClusterName = null)`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification (4-Augen-Prinzip)
* **Forensic RCA**:
  - `WorkoutClusterSelectionDialog` composable contract currently lacks an action callback to signal "create new cluster" / revert selection.
  - `OutlinedTextField` in `ClusterNamingDialog` (`ImportBackupTabsScreen.kt`, L967–978) renders a fixed `IconButton` with `R.drawable.my_locations` without conditional reset capability when `selectedCluster != null`.
* **Independent Code & Call-Site Audit**:
  - `ImportBackupTabsScreen.kt` (L851–862): Passes candidates to `WorkoutClusterSelectionDialog`.
  - `WorkoutClustersScreen.kt` (L168, L291) & `WorkoutClusterHeatmapScreen.kt` (L305): Call `WorkoutClusterSelectionDialog` to reassign workouts between existing clusters. These callers do NOT require route creation.
  - `EditWorkoutClusterDialog` (L813–840): Serves as the canonical UI pattern for the `+ Neue Strecke erstellen...` card using `Card`, `Icons.Default.Add`, and `R.string.cluster_naming__create_new`.
  - Reference Screenshot (`docs/attachments/cluster_naming_dialog_screenshot.png`): Inspected via `read_file` (`Rule 71`), confirming dialog layout, existing route selector, and new route name fields.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  1. Add optional `onCreateNew: (() -> Unit)? = null` parameter to `WorkoutClusterSelectionDialog`. When non-null, display a `+ Neue Strecke erstellen...` card above candidates.
  2. In `ClusterNamingDialog`, wire `onCreateNew = { selectedCluster = null; showSelectionDialog = false }`.
  3. In `ClusterNamingDialog`'s route selection `OutlinedTextField`, add an `Icons.Default.Clear` button in `trailingIcon` when `selectedCluster != null` to reset `selectedCluster = null` in a single tap (`R.string.cluster_clear_content_desc`).
* **Out-of-Scope Non-Goals**:
  - Modifying cluster candidate scoring (`scoreClusters`), similarity math, or SQLite schema.
  - Changing behavior of reassign callers (`WorkoutClustersScreen`, `WorkoutClusterHeatmapScreen`), where `onCreateNew` remains `null`.
* **Complexity Rating**: **1 SP (Trivial)**. Isolated UI interaction fix in 2 Compose files.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-123` (*Localized Cluster Naming Interface*), `REQ-UI-149` (*Modernized Modal Bottom Sheet Dialogs*).
* **Historical Origin & Commit Trace**: `ATT-305` / `ATT-494` (initial cluster naming), `ATT-900` (`AppModalBottomSheet` migration), `ATT-5560` (workout name & stacked buttons).
* **Root Reason for Existing Formulation**: `WorkoutClusterSelectionDialog` was originally designed solely for picking from candidates during workout reassignment (`SCRUM-214`). Reusing it in `ClusterNamingDialog` without a "create new" escape hatch inadvertently created the selection trap.
* **Preservation of Core Invariants**: Existing callers without `onCreateNew` remain 100% functionally and visually identical; `name` input state in `ClusterNamingDialog` is preserved across resets; zero hardcoded strings.

---

## 5. Architectural Strategy & High-Level Solution
* **Component Parameterization**: Extend `WorkoutClusterSelectionDialog` with `onCreateNew: (() -> Unit)? = null`. If present, render a styled `Card` above `candidates` calling `onCreateNew()` and `onDismiss()`.
* **Direct Reset Action**: In `ClusterNamingDialog`, provide `IconButton(onClick = { selectedCluster = null })` with `Icons.Default.Clear` in `trailingIcon` alongside the route selection icon when `selectedCluster != null`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Binary backward compatibility for `WorkoutClusterSelectionDialog` default arguments; 9-language localization parity (`TranslationParityTest`); no regression in `ModalBottomSheetDialogsIntegrityTest`.
* **Risk Rating**: **LOW** (Additive UI callback with default `null`, localized strings reuse existing keys).
