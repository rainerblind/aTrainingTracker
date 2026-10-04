# Stage 2: Requirement & Test Specification - ATT-2195: Fix Missing Bullet Point Line Breaks in Cluster Info Explanation Dialog

**Ticket**: [ATT-2195](https://rainerblind.atlassian.net/browse/ATT-2195)  
**Sub-task**: [ATT-2367](https://rainerblind.atlassian.net/browse/ATT-2367) (`[Test-Spec]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-269`  
**Test Mapping**: `TST-UI-228`  
**Branch**: `feature/ATT-2195`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (`REQ-UI-269`)

### 1.1 Formal Specification
| Requirement ID | Module | Title | Target Description |
| :--- | :--- | :--- | :--- |
| **REQ-UI-269** | **UI / Workout Clusters** | **Preserved Bullet Point Line Breaks in ClusterInfoDialog and 9-Language Localization Parity.** | The system SHALL ensure that the 4 spatial criteria in the workout clustering educational bottom sheet (`ClusterInfoDialog.kt`) render on distinct lines separated by clean line breaks across all 9 application locales (ATT-2195):<br>1. *Literal Newline Encoding in String Resources*: In string resource `cluster_info_fingerprint_desc`, all paragraph and bullet point separations SHALL be encoded using literal `\n` escape sequences instead of the XML character entity `&#10;`, preventing AAPT2 from collapsing line breaks into single spaces.<br>2. *9-Language Localization Parity*: String resource `cluster_info_fingerprint_desc` SHALL be updated across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`). Each translation SHALL contain zero instances of `&#10;` and SHALL contain literal `\n` line breaks preceding each bullet point (`•`).<br>3. *Textual and Parameter Invariant Preservation*: All mathematical tolerance values (Start/Finish: 200 m, Apex: 400 m, Total Distance: 20%, Altitude Position: 400 m) and existing localized phrasing across all 9 languages MUST remain completely preserved.<br>4. *Dialog Layout & Visual Invariants*: `ClusterInfoDialog.kt` composable hierarchy, section dividers, typography styles, title icon, close confirmation action (`AppDialogActions.Confirm`), and 100% clean-room test suite pass rate MUST NOT be altered. |

---

### 1.2 Chesterton's Fence Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Refines `REQ-UI-137` (*Cluster Algorithm & Lieblingsstrecken Educational Info Modal*).
2. **Historical Origin & Commit Trace**:
   - Sprint `2026-40.4` (`ATT-1422` / `ATT-1449`, commit `f7136067`).
3. **Root Reason for Existing Formulation**:
   - In `REQ-UI-137`, `cluster_info_fingerprint_desc` was defined to explain the 6-dimensional spatial metric using bullet points. During translation authoring, `&#10;` was used under the assumption that XML entities would be preserved. AAPT2 normalizes unquoted whitespace, causing the bullet points to run together.
4. **Preservation of Core Invariants**:
   - All spatial tolerance values (200 m, 400 m, 20%), educational concepts, and localized translations across all 9 languages are 100% preserved.

---

### 1.3 Given-When-Then Acceptance Criteria

#### Scenario 1: Opening ClusterInfoDialog and Inspecting Topological Fingerprint Section
* **Given** an athlete opening the workout clustering information dialog (`ClusterInfoDialog`),
* **When** viewing the "Topologischer 3D-Fingerabdruck" (or "3D Topological Fingerprint") section,
* **Then** the introductory sentence SHALL be followed by a line break, and each of the 4 bullet points (Start & Finish, Apex, Total Distance, Min & Max Altitude) SHALL begin on its own separate line.

#### Scenario 2: 9-Language Localization Verification
* **Given** any of the 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT),
* **When** `cluster_info_fingerprint_desc` is compiled and rendered,
* **Then** the string SHALL contain zero `&#10;` entities and at least 4 `\n` line breaks separating each bullet criterion.

#### Scenario 3: Dialog Architecture & Action Preservation
* **Given** `ClusterInfoDialog.kt`,
* **When** rendered,
* **Then** it SHALL preserve `AppModalBottomSheet` layout, info icon, and `AppDialogActions.Confirm` button.

---

## 2. Test Specification (`TST-UI-228`)

### 2.1 Test Cases Breakdown

| Test Case ID | Class / Unit Under Test | Description & Validation Target | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `TST-UI-228.1` | `ClusterInfoDialogContractTest` | Verify `cluster_info_fingerprint_desc` exists and is non-empty across all 9 language directories. | Present in all 9 locales. |
| `TST-UI-228.2` | `ClusterInfoDialogContractTest` | Verify `cluster_info_fingerprint_desc` contains zero instances of `&#10;` across all 9 locales. | 0 occurrences in all 9 files. |
| `TST-UI-228.3` | `ClusterInfoDialogContractTest` | Verify `cluster_info_fingerprint_desc` contains at least 4 `\n` line breaks in each locale. | $\ge 4$ line breaks per locale. |
| `TST-UI-228.4` | Full Suite Clean-Room Regression | Run `./gradlew testDebugUnitTest`. | 100% pass rate with zero failures. |

---

## 3. Traceability Matrix

| Requirement Clause | Test Specification | Verification Method |
| :--- | :--- | :--- |
| `REQ-UI-269` Clause 1 (Literal `\n` encoding) | `TST-UI-228.2`, `TST-UI-228.3` | Architectural Contract Test (`ClusterInfoDialogContractTest`) |
| `REQ-UI-269` Clause 2 (9-Language Parity) | `TST-UI-228.1`, `TST-UI-228.2`, `TST-UI-228.3` | Localization Audit Test (`ClusterInfoDialogContractTest`) |
| `REQ-UI-269` Clause 3 (Text & Parameter Invariants) | `TST-UI-228.1` | Automated String Content Check |
| `REQ-UI-269` Clause 4 (System Invariants) | `TST-UI-228.4` | Full Clean-Room Suite (`./gradlew testDebugUnitTest`) |
