# Stage 1 Analysis: ATT-5662 - ClusterNamingDialog: Clarify unassigned route subtitle copy

**Ticket**: [ATT-5662](https://rainerblind.atlassian.net/browse/ATT-5662)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5662`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
During workout file imports in `ImportBackupTabsScreen.kt`, `ClusterNamingDialog` opens whenever a workout cannot be automatically matched to an existing route cluster (*Lieblingsstrecke*).
The current subtitle string `cluster_naming__found_recurring` reads:
* *DE*: *"Neue Lieblingsstrecke vom %1$s gefunden."*
* *EN*: *"Found a new route from %1$s."*

This phrasing prematurely presumes that the workout represents a new favorite route (*Lieblingsstrecke*). In reality, it is simply a route that could not be automatically assigned—the athlete may assign it to an existing route, create a new one, or leave it unclustered. The copy must be updated to neutral, accurate wording (*"Nicht automatisch zuweisbare Strecke vom %1$s."* / *"Unassigned route from %1$s."*).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic RCA**:
  * The string `cluster_naming__found_recurring` was originally drafted in ATT-494 under the assumption that every unresolved candidate would become a new cluster.
  * In `ImportBackupTabsScreen.kt` (L882), `Text(stringResource(R.string.cluster_naming__found_recurring, state.date))` directly renders this subtitle.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  * Verified `ImportBackupTabsScreen.kt` (L882): this is the sole production call site of `cluster_naming__found_recurring`.
  * Verified `state.date` is formatted as a single string argument, requiring `%1$s` preservation across all 9 locales.
  * Verified `WorkoutClusterComponents.kt` does not call `cluster_naming__found_recurring`.
  * Confirmed that `ClusterNamingDialogLabelsVisualContractTest.kt` can be extended to verify `cluster_naming__found_recurring` copy and placeholder parity.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Update `cluster_naming__found_recurring` across all 9 locales (DE: *"Nicht automatisch zuweisbare Strecke vom %1$s."*, EN: *"Unassigned route from %1$s."*, ES, FR, IT, JA, NL, PL, PT).
  * Preserve `%1$s` positional specifier in all 9 translation files.
  * Verify 100% translation parity via `TranslationParityTest` and contract test.
* **Out-of-Scope Non-Goals**:
  * Zero changes to Kotlin logic, Compose layouts, or cluster decision pipelines.
* **Complexity Rating**: **1 SP (Trivial)**.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-123` (*Localized Cluster Naming Interface*), targeting `ImportBackupTabsScreen.kt` and `strings.xml`.
* **Historical Origin & Commit Trace**: `ATT-494` (initial localization), `ATT-5560` (bottom sheet conversion), `ATT-5664` (favorite route creation labels).
* **Root Reason for Existing Formulation**: Early design assumed imported tracks without auto-match were invariably new recurring favorite routes.
* **Preservation of Core Invariants**: Key identifier `cluster_naming__found_recurring` unchanged; `%1$s` argument structure preserved; no emojis (`ATT-5400`).

---

## 5. Architectural Strategy & High-Level Solution
* Update `cluster_naming__found_recurring` across all 9 locale directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* Extend `ClusterNamingDialogLabelsVisualContractTest.kt` to assert the updated subtitle phrasing.
* Validate with `TranslationParityTest` and `verify_requirement_governance.py`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Key `cluster_naming__found_recurring` remains unchanged.
  2. Single string placeholder `%1$s` preserved across all 9 locales.
  3. No emojis (`ATT-5400`); zero logic mutations.
* **Risk Rating**: **LOW** (Copy refinement across 9 locales; zero schema or architecture changes).
