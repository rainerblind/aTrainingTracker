# Stage 2: Requirement & Test Specification - ATT-5662: Clarify unassigned route subtitle copy

**Ticket**: [ATT-5662](https://rainerblind.atlassian.net/browse/ATT-5662)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5662`  
**Requirement Mapping**: `REQ-UI-123` | **Test Spec ID**: `TST-UI-078`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-UI-123`)

### 1.1 Problem Statement & Rationale
In `ClusterNamingDialog` (`ImportBackupTabsScreen.kt`), the subtitle string `cluster_naming__found_recurring` previously stated *"Neue Lieblingsstrecke vom %1$s gefunden."* (EN: *"Found a new route from %1$s."*). This copy prematurely assumed the imported workout was a new favorite route (*Lieblingsstrecke*), confusing users who intended to assign it to an existing route or leave it unclustered. The copy must be updated to a neutral, accurate description (*"Nicht automatisch zuweisbare Strecke vom %1$s."* / *"Unassigned route from %1$s."*).

### 1.2 Functional Requirements & 9-Locale Translation Matrix
The system SHALL update `cluster_naming__found_recurring` across all 9 supported locales preserving the single `%1$s` date placeholder:

| Locale | `cluster_naming__found_recurring` |
| :--- | :--- |
| **EN** (`values/`) | Unassigned route from %1$s. |
| **DE** (`values-de/`) | Nicht automatisch zuweisbare Strecke vom %1$s. |
| **ES** (`values-es/`) | Ruta no asignada del %1$s. |
| **FR** (`values-fr/`) | Itinéraire non attribué du %1$s. |
| **IT** (`values-it/`) | Percorso non assegnato del %1$s. |
| **JA** (`values-ja/`) | %1$s からの未割り当てルート。 |
| **NL** (`values-nl/`) | Niet-toegewezen route van %1$s. |
| **PL** (`values-pl/`) | Nieprzypisana trasa z %1$s. |
| **PT** (`values-pt/`) | Rota não atribuída de %1$s. |

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (Subtitle Copy)**: *Given* an imported workout that cannot be auto-assigned to an existing cluster, *When* `ClusterNamingDialog` opens, *Then* the subtitle displays `cluster_naming__found_recurring` with the formatted date injected into `%1$s`.
* **AC-2 (9-Locale Parity)**: *Given* all 9 locale directories, *When* `TranslationParityTest` executes, *Then* 100% string presence and exact `%1$s` specifier parity are verified.
* **Invariants**: Resource key `cluster_naming__found_recurring` unchanged; zero layout/logic alterations; no decorative emojis (`ATT-5400`).

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-123` (*Localized Cluster Naming Interface*), targeting `ImportBackupTabsScreen.kt` and `strings.xml`.
* `Historical Origin & Commit Trace`: Introduced in `ATT-494` / `ATT-565`; updated in `ATT-5560` and `ATT-5664`.
* `Root Reason for Existing Formulation`: Early assumptions treated all unassigned tracks as novel recurring routes; neutral copy clarifies the state prior to user decision.
* `Preservation of Core Invariants`: Key name preserved; `%1$s` format token preserved; 100% 9-language parity maintained.

---

## 2. Test Specification (`TST-UI-078`)

### Test Case 1: 9-Language Translation Parity Audit (`TST-UI-078.1`)
* **Scope**: Localization Unit Test (`app/src/test/.../TranslationParityTest.kt`)
* **Preconditions**: All 9 locale `strings.xml` files updated.
* **Action**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.TranslationParityTest"`
* **Expected Result**: 100% string presence and `%1$s` placeholder parity across all 9 locales.

### Test Case 2: Visual Contract & Copy Correctness Test (`TST-UI-078.2`)
* **Scope**: Contract Test (`app/src/test/.../ui/clusters/ClusterNamingDialogLabelsVisualContractTest.kt`)
* **Action**: Verify `cluster_naming__found_recurring` in German contains *"Nicht automatisch zuweisbare Strecke"* and in English contains *"Unassigned route"*.
* **Expected Result**: 100% PASS.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-078.1` | Localization | `strings.xml` (9 locales) | `REQ-UI-123`, `REQ-UI-106` | Specified |
| `TST-UI-078.2` | Contract | `ClusterNamingDialogLabelsVisualContractTest` | `REQ-UI-123` | Specified |
