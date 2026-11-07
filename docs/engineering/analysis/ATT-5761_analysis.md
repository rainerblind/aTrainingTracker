# Stage 1: Problem Domain & Root Cause Analysis - ATT-5761

**Ticket**: [ATT-5761](https://rainerblind.atlassian.net/browse/ATT-5761)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355): Good and consistent UI | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5761`  
**Requirement Mapping**: `REQ-UI-414` (`REQ-UI-414.3`, `REQ-UI-414.8`), `REQ-UI-408` (`REQ-UI-408.5`)  
**Test Mapping**: `TST-UI-377` (`TST-UI-377.3`, `TST-UI-377.8`), `TST-UI-214-C`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Domain & Background
In *Experteneinstellungen* (*Advanced Settings*), athletes configure section ordering in two distinct surfaces:
1. **Summary-Karten** in `WorkoutMasksAndCardsSection.kt`: Collapsible 11-row table configuring workout summary card display order (`WorkoutSectionType`).
2. **Detail-Tabs** in `WorkoutDetailTabsConfigSheet.kt`: Sheet configuring active sections per detail tab (`WorkoutDetailSectionType`), with `MAP` pinned at index 0.

**Usability Defect**: Both surfaces currently utilize stacked 22dp `IconButton`s (`Icons.Default.KeyboardArrowUp` / `KeyboardArrowDown`, 18dp icon size) within a 32dp column. These violate Material 3 touch target standards ($\ge 44\text{--}48\text{dp}$), requiring tedious, error-prone tapping to move sections.

### Independent Peer Review & Hypothesis Verification (Rule 69 & 70)
* **Codebase Audit**: Verified both call sites (`WorkoutMasksAndCardsSection.kt:240-251` and `WorkoutDetailTabsConfigSheet.kt:306-346`).
* **Gesture Isolation Invariant**: Both lists reside within scrollable containers (`verticalScroll` in `AppModalBottomSheet` and `AdvancedTuningDialog`). Attaching drag detection (`detectVerticalDragGestures`) strictly to the isolated left drag-handle touch area guarantees that vertical scroll on the rest of the row (label, checkbox, background) remains completely unconsumed and smooth.
* **Pinned MAP Invariant**: `WorkoutDetailTabConfig.moveSectionInTab` strictly prohibits moving to/from index 0 when `MAP` is present. The drag handle for `MAP` must render as an immutable pinned indicator (`Icons.Default.PushPin` or disabled handle) without drag listeners.

---

## 2. Chesterton's Fence & Invariant Archaeology
* **Original Requirement ID & Target**: Refines `REQ-UI-414.3` (*Collapsible Summary Cards Reorder List*) and `REQ-UI-408.5` (*Workout Detail Tabs Section Reordering*), targeting `WorkoutMasksAndCardsSection.kt`, `WorkoutDetailTabsConfigSheet.kt`, and introducing modular components in `WorkoutDetailTabsConfigComponents.kt`.
* **Historical Origin & Commit Trace**: `ATT-4043` (`a8e5e356`, Sprint 2026-42.8: 32dp column with 22dp arrows), `ATT-3920` (`ea5fa705`, Sprint 2026-42.8: Up/Down arrows in tab sheet), `ATT-5654` (`c738f195`, Sprint 2026-45.9: `GRADE_DISTRIBUTION` added to both lists).
* **Root Reason for Existing Formulation**: Up/Down buttons were the simplest click-based control avoiding gesture interception in vertical scroll containers before isolated touch-target drag handles were designed.
* **Preservation of Core Invariants**:
  1. *Host Scroll Integrity*: Vertical scrolling of host dialog/sheet remains 100% fluid outside the drag handle.
  2. *MAP Pinning*: `WorkoutDetailSectionType.MAP` remains permanently pinned at index 0 in Detail Tabs.
  3. *Immediate Persistence*: Emits updated list order on swap, persisting instantly via existing DataStore flows.
  4. *Modularity (< 400 Lines)*: `WorkoutMasksAndCardsSection.kt` (currently 381 lines) must remain strictly under 400 lines (`REQ-UI-414.5`, `REQ-PRO-011`) by placing reusable reorder components into `WorkoutDetailTabsConfigComponents.kt`.

---

## 3. Scope Bounding & Technical Architecture
* **In-Scope**:
  1. Create modular `WorkoutDetailTabsConfigComponents.kt` with a reusable `ReorderableSectionRow` / `DragHandleTouchArea` using `Icons.Default.DragHandle` (≡) with a full $\ge 44\text{dp}$ touch target.
  2. Integrate fluid drag & drop reordering with live vertical drag offset, elevation highlight (`surfaceContainerHighest`, subtle shadow/zIndex), and instant neighbor swap when vertical drag exceeds the row threshold.
  3. Integrate into `WorkoutMasksAndCardsSection.kt` (Summary Cards) and `WorkoutDetailTabsConfigSheet.kt` (Detail Tabs).
  4. Enforce pinned `MAP` handling at index 0 (non-draggable anchor).
  5. 9-language localization parity for `action_drag_to_reorder`.
  6. Update contract tests (`WorkoutMasksAndCardsLayoutTest.kt`, `WorkoutSectionReorderContractTest.kt`, `WorkoutDetailTabsTest.kt`).
* **Out-of-Scope**:
  - Horizontal tab selector pills in `WorkoutDetailTabsConfigSheet.kt` (retain existing tab controls).
* **Complexity Rating**: **2 SP (Standard)**. Multi-file UI polish without database or architecture alterations.

---

## 4. Blast Radius & Side-Effect Audit (Rule 68)
| Affected Component | Impact / Verification |
| :--- | :--- |
| `WorkoutMasksAndCardsSection.kt` | Replace 22dp arrows with drag handle; keep file strictly < 400 lines. |
| `WorkoutDetailTabsConfigSheet.kt` | Replace 22dp arrows with drag handle; preserve `MAP` index 0 anchor. |
| `WorkoutDetailTabsConfigComponents.kt` | New modular file housing reusable drag-and-drop row logic. |
| `values*/strings.xml` (9 locales) | Add `action_drag_to_reorder` for accessibility content descriptions. |
| Contract Tests (`WorkoutMasksAndCardsLayoutTest`, `WorkoutSectionReorderContractTest`) | Update assertions from 22dp arrows to drag-handle touch area. |

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Zero scroll contention on host screens; `MAP` pinned at index 0; instant DataStore serialization; `< 400` lines file limit.
* **Risk Rating**: **LOW** (Targeted Compose UI interaction improvement; zero risk to database schemas, sensors, or background services).
