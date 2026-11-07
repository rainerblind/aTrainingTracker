# Stage 1 Analysis: ATT-5664 - ClusterNamingDialog: Explicitly label new favorite route creation

**Ticket**: [ATT-5664](https://rainerblind.atlassian.net/browse/ATT-5664)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5664`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
Following ATT-5560, `ClusterNamingDialog` includes both a Workout Name field (`cluster_naming__workout_name_label`: *"Name der Einheit"*) and a Route Cluster section. However, the route section labels remain ambiguous:
* Upper assignment label (`cluster_naming__assignment_label`): *"Lieblingsstrecke:"* (DE) / *"Route Assignment:"* (EN) does not clarify that an *existing* route is selected.
* New route prompt (`cluster_naming__new_name_prompt`): *"Oder neuen Namen vergeben:"* (DE) / *"Or give it a new name:"* (EN).
* Text field label (`cluster_naming__new_route_name_label`): *"Neuer Name"* (DE) / *"New Route Name"* (EN).

Without explicit favorite route (*Lieblingsstrecke*) phrasing, users perceive the bottom section as asking for a duplicate workout name.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic RCA**:
  * String resources in `res/values*/strings.xml` use generic naming terminology inherited from early implementations (ATT-494).
  * In `ImportBackupTabsScreen.kt` (L965–989) and `WorkoutClusterComponents.kt` (L855–864), the prompts lack disambiguation against the preceding workout name input field.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  * Inspected `ImportBackupTabsScreen.kt` (L955–990): confirmed `workoutNameInput` renders directly above `cluster_naming__assignment_label`, followed by `cluster_naming__new_name_prompt` and `cluster_naming__new_route_name_label` when `selectedCluster == null`.
  * Inspected `WorkoutClusterComponents.kt` (L854–864): confirmed `WorkoutClusterSelectionDialog` also references `cluster_naming__new_name_prompt` and `cluster_naming__new_route_name_label` when creating a new cluster inline. Updating these strings simultaneously clarifies both dialogs without code changes.
  * Verified no tests or callers rely on string literal contents of these three keys.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Update `cluster_naming__assignment_label` across all 9 locales (DE: *"Bestehende Lieblingsstrecke auswählen:"*, EN: *"Select existing favorite route:"*).
  * Update `cluster_naming__new_name_prompt` across all 9 locales (DE: *"Oder neue Lieblingsstrecke anlegen:"*, EN: *"Or create a new favorite route:"*).
  * Update `cluster_naming__new_route_name_label` across all 9 locales (DE: *"Name der neuen Lieblingsstrecke"*, EN: *"New Favorite Route Name"*).
  * Maintain 100% 9-language translation parity verified by `TranslationParityTest`.
* **Out-of-Scope Non-Goals**:
  * Zero changes to Kotlin logic, Compose layouts, or cluster decision models (`ClusterDecision`).
  * No changes to `EditWorkoutScreen.kt` (which uses `cluster_naming__selected_route_label`).
* **Complexity Rating**: **1 SP (Trivial)**.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-123` (*Localized Cluster Naming Interface*), targeting `ImportBackupTabsScreen.kt`, `WorkoutClusterComponents.kt`, and `strings.xml`.
* **Historical Origin & Commit Trace**: `ATT-494` (initial localization of cluster naming), `ATT-5560` (introduction of workout name field and bottom sheet conversion).
* **Root Reason for Existing Formulation**: Generic "name" labels were adequate when the dialog only named clusters; introduction of the workout name field created visual ambiguity.
* **Preservation of Core Invariants**: Key names remain identical; zero breaking changes to existing call sites; zero emoji inclusions (`ATT-5400`).

---

## 5. Architectural Strategy & High-Level Solution
* Update the 3 string resources in all 9 locale directories: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* Verify with `TranslationParityTest` and `verify_requirement_governance.py`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. String resource keys (`cluster_naming__assignment_label`, `cluster_naming__new_name_prompt`, `cluster_naming__new_route_name_label`) MUST NOT change.
  2. 100% translation parity across all 9 languages MUST be maintained.
  3. No decorative inline Unicode emojis in strings (`ATT-5400`).
* **Risk Rating**: **LOW** (Pure string resource update with 100% backward-compatible key names and no code modifications).
