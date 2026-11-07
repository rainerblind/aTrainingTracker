# Stage 2: Requirement & Test Specification - ATT-5761: Drag & Drop Section Reordering

**Ticket**: [ATT-5761](https://rainerblind.atlassian.net/browse/ATT-5761)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5761`  
**Requirement Mapping**: `REQ-UI-456` (refining `REQ-UI-414.3`, `REQ-UI-408.5`) | **Test Spec ID**: `TST-UI-456`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-UI-456`)

### 1.1 Problem Statement & Rationale
In *Experteneinstellungen* (*Advanced Settings*), athletes configure section display order in two distinct surfaces: Summary-Karten (`WorkoutMasksAndCardsSection.kt`) and Detail-Tabs (`WorkoutDetailTabsConfigSheet.kt`). Currently, reordering uses stacked 22dp `IconButton`s (`KeyboardArrowUp` / `KeyboardArrowDown`) within a 32dp column. These violate Material 3 touch ergonomics ($\ge 44\text{--}48\text{dp}$) and require tedious, error-prone tapping. The system SHALL replace these arrow buttons with a full-height drag-handle icon (≡) providing fluid vertical drag reordering and strict gesture isolation.

### 1.2 Functional & Architectural Requirements
1. **Full-Height Drag Handle**: In `WorkoutMasksAndCardsSection.kt` and `WorkoutDetailTabsConfigSheet.kt`, section reordering SHALL be triggered via a dedicated drag handle icon (`Icons.Default.DragHandle` / ≡) spanning $\ge 44\text{dp}$ touch target width and full row height, replacing legacy stacked 22dp Up/Down arrow buttons (`REQ-UI-456.1`).
2. **Gesture Isolation**: Vertical drag detection SHALL be strictly bound to the leading drag handle area via `detectVerticalDragGestures`. Swiping across the rest of the row SHALL NOT intercept vertical gestures, preserving smooth parent scrolling (`REQ-UI-456.2`).
3. **Real-Time Drag Swapping & Elevation**: The active dragged item SHALL display visual elevation (`surfaceContainerHighest` container tint, subtle shadow, `zIndex(1f)`). When vertical translation exceeds the neighbor row midpoint, the list SHALL swap items immediately in real time (`REQ-UI-456.3`).
4. **Detail Tabs MAP Pinning Invariant**: In `WorkoutDetailTabsConfigSheet.kt`, `WorkoutDetailSectionType.MAP` SHALL remain permanently pinned at index 0 when active (`normalizedSections`), rendering an immutable non-draggable pin indicator (`Icons.Default.PushPin` or disabled handle) (`REQ-UI-456.4`).
5. **Modularity (< 400 Lines)**: Reusable drag-and-drop row logic and components SHALL reside in `WorkoutDetailTabsConfigComponents.kt`, ensuring `WorkoutMasksAndCardsSection.kt` remains strictly $< 400$ lines (`REQ-UI-456.5`, `REQ-UI-414.5`).
6. **9-Language Parity**: Content descriptions `action_drag_to_reorder` and `workout_detail_tabs_map_pinned_desc` SHALL maintain 100% parity across all 9 locales (`REQ-UI-456.6`).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (Touch Target & Drag Handle)**: *Given* an athlete viewing the reorderable lists in `WorkoutMasksAndCardsSection.kt` or `WorkoutDetailTabsConfigSheet.kt`, *When* inspecting the leading edge, *Then* a prominent $\ge 44\text{dp}$ drag handle (≡) is rendered instead of stacked 22dp arrows.
* **AC-2 (Drag Reorder)**: *Given* the athlete drags a row handle vertically past an adjacent row boundary, *Then* the row elevates visually and positions swap immediately in real time.
* **AC-3 (Host Scroll Fluidity)**: *Given* the athlete swipes vertically outside the drag handle, *Then* the host dialog/sheet scrolls smoothly without initiating reordering.
* **AC-4 (MAP Anchor)**: *Given* `WorkoutDetailSectionType.MAP` in a detail tab, *Then* `MAP` is anchored at index 0 and cannot be dragged or displaced.
* **Invariants**: Zero host scroll interference; `MAP` pinned at index 0; instant DataStore serialization; `< 400` lines file limit.

---

## 2. Test Specification (`TST-UI-456`)

### Test Case 1: Reorder Swap Logic & Boundary Clamping (`TST-UI-456.1`)
* **Scope**: Unit Test (`WorkoutSectionReorderContractTest.kt`)
* **Action**: Verify vertical drag swap calculation on synthetic lists, ensuring out-of-bounds drags clamp safely.

### Test Case 2: Detail Tabs MAP Pinning Invariant (`TST-UI-456.2`)
* **Scope**: Unit Test (`WorkoutDetailTabsTest.kt`)
* **Action**: Verify `WorkoutDetailTabConfig.moveSectionInTab` preserves `MAP` at index 0 and blocks displacing `MAP`.

### Test Case 3: Layout Contract & Touch Target Verification (`TST-UI-456.3`)
* **Scope**: Contract Test (`WorkoutMasksAndCardsLayoutTest.kt`, `WorkoutSectionReorderContractTest.kt`)
* **Action**: Verify 22dp arrow buttons are replaced with $\ge 44\text{dp}$ drag-handle touch area, and `WorkoutMasksAndCardsSection.kt` is $< 400$ lines.

### Test Case 4: 9-Language Localization Audit (`TST-UI-456.4`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Goal**: Verify 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for `action_drag_to_reorder` and `workout_detail_tabs_map_pinned_desc`.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-UI-456.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Goal**: Verify 100% pass rate across affected packages.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-456.1` | Unit | `WorkoutSectionReorderContractTest` | `REQ-UI-456.3` | Specified |
| `TST-UI-456.2` | Unit | `WorkoutDetailTabsTest` | `REQ-UI-456.4` | Specified |
| `TST-UI-456.3` | Contract | `WorkoutMasksAndCardsLayoutTest` | `REQ-UI-456.1`, `REQ-UI-456.5` | Specified |
| `TST-UI-456.4` | Localization | `TranslationParityTest` | `REQ-UI-456.6` | Specified |
| `TST-UI-456.5` | Regression | Affected UI Packages | `REQ-UI-456.2` | Specified |
