# Stage 5: Walkthrough & Verification - ATT-5761: Drag & Drop Section Reordering

**Ticket**: [ATT-5761](https://rainerblind.atlassian.net/browse/ATT-5761)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5761`  
**Requirement Mapping**: `REQ-UI-456` (`REQ-UI-456.1`–`REQ-UI-456.6`) | **Test Mapping**: `TST-UI-456` (`TST-UI-456.1`–`TST-UI-456.5`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Executive Summary & Verification Overview
Replaced legacy stacked 22dp Up/Down arrow buttons (`KeyboardArrowUp` / `KeyboardArrowDown`) in *Experteneinstellungen* (`WorkoutMasksAndCardsSection.kt` and `WorkoutDetailTabsConfigSheet.kt`) with a prominent, full-row-height `ReorderDragHandle` (≡) with a $\ge 44\text{dp}$ touch target area. Extracted modular drag-to-reorder components (`ReorderDragState`, `rememberReorderDragState`, `ReorderDragHandle`, `Modifier.reorderableRowElevation`) into `WorkoutDetailTabsConfigComponents.kt`, strictly isolating vertical drag detection to the drag handle and preserving smooth host scrolling across the rest of the row. Enforced pinned `MAP` invariant at index 0 in Detail Tabs.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-456.1` | `TST-UI-456.3` | `WorkoutMasksAndCardsLayoutTest` (44dp handle & spacer) | **PASSED** | `Verified` |
| `REQ-UI-456.2` | `TST-UI-456.5` | Strict gesture isolation to handle | **PASSED** | `Verified` |
| `REQ-UI-456.3` | `TST-UI-456.1` | `WorkoutSectionReorderContractTest` (Drag Swap & Clamp) | **PASSED** | `Verified` |
| `REQ-UI-456.4` | `TST-UI-456.2` | `WorkoutDetailTabsTest` (MAP pinned at index 0) | **PASSED** | `Verified` |
| `REQ-UI-456.5` | `TST-UI-456.3` | Modularity test (394 lines strictly < 400 lines) | **PASSED** | `Verified` |
| `REQ-UI-456.6` | `TST-UI-456.4` | `TranslationParityTest` (9 locales parity) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-456.5` | Tier 1 Targeted Test Suite (664 tests) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Runtime: ~58s)
* **Executed Scope**:
  * `--tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.aftermath.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `--tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
  * `--tests "com.atrainingtracker.trainingtracker.build.*"`
* **Result**: `BUILD SUCCESSFUL` (664 tests completed, 0 failed, 100% PASS).
* **Resource Compilation**: `./gradlew processDebugResources` -> `BUILD SUCCESSFUL in 1s`.

### Living Documentation Governance Check
```text
python3 tools/verify_requirement_governance.py
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
Net-new requirement(s) detected: REQ-UI-456. Bypassing archaeology check cleanly.
```

---

## 4. Key Implementation Highlights

```kotlin
// Reusable ReorderDragHandle with >= 44dp touch area:
ReorderDragHandle(
    index = index,
    dragState = reorderDragState,
    isPinned = isMap,
    dragContentDescription = stringResource(R.string.action_drag_to_reorder),
    pinnedContentDescription = stringResource(R.string.workout_detail_tabs_map_pinned_desc)
)
```

---

## 5. Invariants & Preserved Behavior
1. **Host Scroll Fluidity (`REQ-UI-456.2`)**: Drag detection isolated to 44dp handle; remainder of row scrolls host dialogs smoothly.
2. **Detail Tabs MAP Pinning Invariant (`REQ-UI-456.4`)**: `MAP` permanently anchored at index 0 with non-draggable `PushPin` indicator.
3. **Immediate Persistence (`REQ-UI-456.3`)**: Real-time position swaps invoke DataStore flows instantly.
4. **Modularity (`REQ-UI-456.5`, `REQ-UI-414.5`)**: `WorkoutMasksAndCardsSection.kt` is 394 lines (strictly $< 400$ lines).
