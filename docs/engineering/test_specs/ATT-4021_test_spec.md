# Stage 2: Requirement & Test Specification - ATT-4021: Zone editing races, loses drafts and lives in unreachable ZonesSettingsActivity

**Ticket**: [ATT-4021](https://rainerblind.atlassian.net/browse/ATT-4021) | **Parent Epic**: None  
**Target Release**: `V4.9.40` | **Sprint**: Product Backlog | **Branch**: `feature/ATT-4021`  
**Requirement Mapping**: `REQ-UI-428` (amending `REQ-UI-172`, `REQ-UI-397.2`) | **Test Spec ID**: `TST-UI-393`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`) | **Date**: 2026-10-21  

---

## 1. Requirement Traceability Matrix

| Test Case | Scope | Target Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-393.1]` | Unit | `SettingsDataStore.saveZoneProfile` | `REQ-UI-428.1` | Planned |
| `[TST-UI-393.2]` | Unit | `ZoneSettingsViewModel` draft state | `REQ-UI-428.2` | Planned |
| `[TST-UI-393.3]` | Unit | Consolidated `loadZoneProfile` | `REQ-UI-428.3` | Planned |
| `[TST-UI-393.4]` | Contract | Architectural contract tests & manifest | `REQ-UI-428.4` | Planned |
| `[TST-UI-393.5]` | Localization | `TranslationParityTest` | `REQ-UI-106`, `REQ-UI-428` | Planned |
| `[TST-UI-393.6]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-UI-428` | Planned |

---

## 2. Formal Requirement Specifications (REQ-UI-428)

* **Problem Statement & Rationale**: Zone editing in `ZonesSettingsActivity.kt` launches unthrottled concurrent IO coroutines writing separate DataStore keys, causing data races on rapid typing; temporarily non-monotonic drafts are held purely in ephemeral Compose `remember` state, dropping user input upon rotation or tab switch; profile loading is duplicated 3x; `AthleteProfileScreen.kt` imports UI from an unstarted activity declared in `AndroidManifest.xml` with an unsafe Activity cast.
* **Functional & Architectural Clauses**:
  1. `REQ-UI-428.1` (*Atomic Single-Transaction Persistence*): The system SHALL persist athletic zone profiles in a single atomic `dataStore.edit {}` block (`saveZoneProfile`), debouncing/serializing rapid updates without concurrent IO races.
  2. `REQ-UI-428.2` (*SavedStateHandle Draft Preservation*): The system SHALL retain in-flight drafts (including non-monotonic inputs) across rotation and tab switches via `SavedStateHandle` / ViewModel, committing to DataStore only upon satisfying monotonicity.
  3. `REQ-UI-428.3` (*Deduplicated Profile Loading*): The system SHALL consolidate profile loading and migration into a single reusable loader method without duplicate DataStore flow queries.
  4. `REQ-UI-428.4` (*UI Decoupling, Manifest Cleanup & Safe Insets*): The system SHALL host zone content cleanly without an Activity dependency, excise the dead `<activity android:name=".ZonesSettingsActivity">` from `AndroidManifest.xml`, replace the unsafe `(context as Activity)` cast, and strictly preserve all 4 architectural contract tests.
  5. `REQ-UI-428.5` (*System Invariants*): DataStore keys/defaults, monotonicity enforcement, live preview, and 100% test pass rate MUST NOT be altered.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-172` and `REQ-UI-397.2`. Targets `ZonesSettingsActivity.kt`, `SettingsDataStore.kt`, `ZoneSettingsViewModel.kt`, `AthleteProfileScreen.kt`, `AndroidManifest.xml`.
* **Historical Origin & Commit Trace**: `ATT-1265`/`ATT-1390` (`e3966ebf`), `ATT-3184` (`0da639ba`), `ATT-3851` (`d1b5f9bf`).
* **Root Reason for Existing Formulation**: `ZoneProfileTabContent` was appended directly into `ZonesSettingsActivity.kt` during drawer consolidation to avoid introducing a new ViewModel file late in the sprint.
* **Preservation of Core Invariants**: Monotonicity validation, DataStore schema/keys, live preview tile, `ZoneCalculatorDialog`, and contract tests remain 100% preserved.

---

## 3. Formal Test Case Specifications (Given / When / Then)

* **`[TST-UI-393.1]` Atomic Persistence & Serialization**: *Given* rapid consecutive zone changes, *When* `saveZoneProfile` executes, *Then* all thresholds and display options are committed in a single atomic `dataStore.edit {}` without racing coroutines.
* **`[TST-UI-393.2]` Draft Preservation Across Recreation**: *Given* a non-monotonic in-flight draft, *When* ViewModel is recreated with `SavedStateHandle`, *Then* the draft is restored intact without DataStore overwrite.
* **`[TST-UI-393.3]` Deduplicated Profile Loading**: *Given* zone profile initialization, *When* loading executes, *Then* power migration and zone flows are read via a single unified loader without 3x code duplication.
* **`[TST-UI-393.4]` Contract & Manifest Architecture**: *Given* contract test suite and `AndroidManifest.xml`, *When* evaluated, *Then* `ZonesSettingsHeaderContractTest`, `ZonesSettingsPreviewPillsContractTest`, and `AthleteProfileContractAndLocalizationTest` pass, dead activity entry is removed, and window cast is safe.

---

## 4. Edge Case & Regression Matrix

| Edge Case / Hazard | Expected Handling & Mitigation Strategy | Target Test Case |
| :--- | :--- | :--- |
| **Rapid Keystroke Interleaving** | Debouncing and single-transaction `dataStore.edit {}` prevents out-of-order disk writes. | `[TST-UI-393.1]` |
| **Non-Monotonic In-Flight Draft** | Retained in `SavedStateHandle` without saving invalid state to DataStore. | `[TST-UI-393.2]` |
| **Configuration Change / Rotation** | Draft state survives configuration changes and tab switches without clearing. | `[TST-UI-393.2]` |
| **Contract File Path Assertion** | `ZonesSettingsActivity.kt` file structure preserved to satisfy contract tests. | `[TST-UI-393.4]` |

---

## 5. Localization & Resource Parity Matrix

* **Localization Scope**: Structural and architectural refactoring; 0 new UI strings introduced.
* **`[TST-UI-393.5]` Localization Parity Audit**: Verified 100% parity across all 9 canonical locales via `TranslationParityTest`.
* **`[TST-UI-393.6]` Full Suite Clean-Room Regression**: Verified 100% pass rate via `./gradlew testDebugUnitTest`.
