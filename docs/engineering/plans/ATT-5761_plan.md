# Stage 3: Implementation Plan - ATT-5761: Drag & Drop Section Reordering

**Ticket**: [ATT-5761](https://rainerblind.atlassian.net/browse/ATT-5761)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5761`  
**Requirement Mapping**: `REQ-UI-456` (`REQ-UI-456.1`–`REQ-UI-456.6`) | **Test Mapping**: `TST-UI-456` (`TST-UI-456.1`–`TST-UI-456.5`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Description & Background
In *Experteneinstellungen*, reordering summary cards (`WorkoutMasksAndCardsSection.kt`) and detail tab sections (`WorkoutDetailTabsConfigSheet.kt`) currently relies on stacked 22dp `IconButton`s (`KeyboardArrowUp` / `KeyboardArrowDown`). These violate touch target standards (< 44dp) and cause tedious misclicks. This plan replaces the arrow buttons with a full-height drag handle (≡) providing fluid vertical drag reordering with live elevation feedback and strict gesture isolation.

---

## 2. Invariants & Preserved Behavior
1. **Host Scroll Fluidity (`REQ-UI-456.2`)**: Drag detection is bound exclusively to the leading drag handle. Vertical swipes across label/checkbox scroll host dialogs/sheets smoothly.
2. **Detail Tabs MAP Pinning Invariant (`REQ-UI-456.4`)**: `WorkoutDetailSectionType.MAP` is permanently anchored at index 0 when enabled (`normalizedSections`). It renders an immutable pin indicator (`PushPin`) and cannot be dragged or displaced.
3. **Immediate Persistence (`REQ-UI-456.3`)**: Live swaps invoke existing `onWorkoutSectionsOrderChange` and `onTabsChange` callbacks, persisting instantly to DataStore.
4. **Modularity & File Length Invariant (`REQ-UI-456.5`, `REQ-UI-414.5`)**: Reusable drag-and-drop components reside in `WorkoutDetailTabsConfigComponents.kt`, ensuring `WorkoutMasksAndCardsSection.kt` remains strictly $< 400$ lines.
5. **Gesture Key Stability (`docs/design_guidelines.md` 2.2)**: `detectVerticalDragGestures` captures mutable indices and callbacks via `rememberUpdatedState`.

---

## 3. UI Consistency & Architectural Strategy
* **Reference Pattern**: Material 3 reorderable list handle (`Icons.Default.DragHandle`), `44.dp` minimum touch target box, and `Icons.Default.PushPin` for pinned anchor.
* **Component Extraction**: Modular `WorkoutDetailTabsConfigComponents.kt` containing `ReorderDragHandle` and dragging state helper.
* **Theme Tokens**: Elevation tint `surfaceContainerHighest`, `zIndex(1f)`, subtle shadow `4.dp`, and `onSurfaceVariant` iconography.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Localization Resource Keys Across All 9 Locales (`REQ-UI-456.6`, `TST-UI-456.4`)
* **Files**: `app/src/main/res/values*/strings.xml` (9 files)
* **Changes**: Add `action_drag_to_reorder` and `workout_detail_tabs_map_pinned_desc` across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Step 2: Create Modular Reorder Components (`REQ-UI-456.1`–`REQ-UI-456.3`, `REQ-UI-456.5`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabsConfigComponents.kt`
* **Changes**: Implement `ReorderDragHandle` (`44.dp` touch box, `detectVerticalDragGestures`, `rememberUpdatedState`, threshold swapping, and pinned state support).

### Step 3: Integrate Drag Handle in Summary Cards (`REQ-UI-456.1`–`REQ-UI-456.3`, `REQ-UI-456.5`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt`
* **Changes**: Replace 22dp arrow buttons with `ReorderDragHandle` (and matching `44.dp` header spacer); verify file length $< 400$ lines.

### Step 4: Integrate Drag Handle in Detail Tabs Sheet (`REQ-UI-456.1`–`REQ-UI-456.4`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabsConfigSheet.kt`
* **Changes**: Replace Up/Down arrow buttons with `ReorderDragHandle`; render `PushPin` for `MAP` at index 0 and enforce `minMovableIdx = 1`.

### Step 5: Unit, Contract & Localization Verification (`TST-UI-456.1`–`TST-UI-456.5`)
* **Files**: `WorkoutMasksAndCardsLayoutTest.kt`, `WorkoutSectionReorderContractTest.kt`, `WorkoutDetailTabsTest.kt`, `TranslationParityTest.kt`
* **Changes**: Update layout contract assertions (`width(44.dp)`, `DragHandle`), verify MAP pinning at index 0, and verify 100% translation parity.

---

## 5. Verification Commands
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* `python3 tools/verify_requirement_governance.py`
