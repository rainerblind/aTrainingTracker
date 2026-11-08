# Stage 1 Analysis: ATT-6213 - WorkoutSummary & WorkoutSectionType: Make WorkoutDetails Reorderable

**Ticket**: [ATT-6213](https://rainerblind.atlassian.net/browse/ATT-6213)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath (WorkoutSummaries, Details & Graphs)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Branch**: `feature/ATT-6213`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
In `WorkoutSummary.kt`, `WorkoutHeader` and `WorkoutDetails` (*Zeit aktiv / Distanz / Höhenmeter*) are hardcoded as static anchors at lines 240–255 above the dynamic `workoutSectionsOrder.forEach` loop. In contrast, the multi-tab detail view (`WorkoutDetailSectionType`) already models `DETAILS` as a reorderable section. This static placement prevents athletes from ordering **Photos (`PHOTOS`)** (or `DESCRIPTION`/`MAP`) above `WorkoutDetails` directly below `WorkoutHeader`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: `WorkoutSectionType` lacks a `DETAILS` enum member, `WorkoutDetailSectionType.toWorkoutSectionTypeOrNull()` returns `null` for `DETAILS`, `WorkoutCardSectionPreferences` lacks `showDetails`, and `WorkoutSummary.kt` calls `WorkoutDetails` statically before the dynamic section loop.
* **Independent Peer Review & Verification**:
  * Verified `WorkoutSummary.kt` lines 240–255: static `HorizontalDivider` + `WorkoutDetails` precede the section loop.
  * Verified `WorkoutDetailTabConfig.kt` lines 52–54: `WorkoutDetailSectionType.DETAILS` maps to `null`.
  * Verified migration risk: `WorkoutSectionType.fromSerializedString` appends missing enums at the tail (`parsed + missing`). For existing users without `DETAILS` in saved order, a naive append would move `DETAILS` below Strava/Export. Backwards-compatible migration MUST insert `DETAILS` at index 0 (or index 1 if `PHOTOS` was already placed at index 0 by the user).
  * Verified divider handling: `WorkoutHeader` requires exactly one clean divider to the first visible section; sections rendered first must not render duplicate top dividers.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `DETAILS(R.string.workout_detail_section_details)` to `WorkoutSectionType` and bidirectional mapping in `WorkoutDetailTabConfig.kt`.
  2. Update `DEFAULT_ORDER` (12 elements, `DETAILS` at index 0) and intelligent migration in `fromSerializedString`.
  3. Add `showDetails: Boolean = true` to `WorkoutCardSectionPreferences` and DataStore persistence in `MyPreferenceManager.kt`.
  4. Move `WorkoutDetails(...)` inside `workoutSectionsOrder.forEach` in `WorkoutSummary.kt` under `WorkoutSectionType.DETAILS` (gated by `preferences.showDetails`).
  5. Add `DETAILS` row to `WorkoutMasksAndCardsSection.kt` while maintaining file size $< 400$ lines (currently 394 lines).
  6. Update contract tests (`WorkoutSummarySectionsTest`, `WorkoutSummaryDynamicOrderContractTest`, `WorkoutSectionOrderPersistenceTest`).
* **Out-of-Scope Non-Goals**:
  * No modification to internal metrics layout in `WorkoutDetails.kt`.
  * No modification to multi-tab structure in `WorkoutDetailTabbedContent.kt`.
  * Complexity: 1 SP (Self-contained UI ordering refinement).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-255` (*Customizable Section Reordering for Workout Summaries and Details*), originally created in `ATT-2176` and extending `REQ-UI-210` (`ATT-1714`).
* **Historical Origin & Commit Trace**: Commit `56375eeb7c13` (`ATT-1714`) hardcoded `WorkoutDetails` as a mandatory anchor; `ATT-2176` introduced dynamic order persistence but kept `WorkoutDetails` fixed above the loop; `ATT-5474` made `PHOTOS` dynamic.
* **Root Reason for Existing Formulation**: Core summary metrics were considered mandatory identity info that must never be displaced or toggled off.
* **Preservation of Core Invariants**: Only `WorkoutHeader` remains permanently fixed at the top. `WorkoutDetails` defaults to enabled (`showDetails = true`) and index 0 in `DEFAULT_ORDER`, guaranteeing 100% visual fidelity for existing and default views while unlocking custom photo-first layouts.

---

## 5. Architectural Strategy & High-Level Solution
* **Data Model & Persistence**: Add `WorkoutSectionType.DETAILS`, map 1:1 with `WorkoutDetailSectionType.DETAILS`. In `fromSerializedString`, if `DETAILS !in parsed`: if `parsed.firstOrNull() == PHOTOS` insert at index 1, else insert at index 0. Persist `showDetails` in `WorkoutCardSectionPreferences`.
* **UI Rendering**: Remove static `WorkoutDetails` from `WorkoutSummary.kt`. Render inside loop under `WorkoutSectionType.DETAILS`. Ensure single divider between `WorkoutHeader` and the first active section. Wire into `WorkoutMasksAndCardsSection.kt`.
* **Safety**: Add no-op handling in `TrackOnMapScreen.kt` metadata/analytics `when` blocks to ensure exhaustive enum coverage.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Default and uncustomized cards render identically (`Header -> Details -> Description -> ...`).
  2. Legacy persisted preferences seamlessly insert `DETAILS` without pushing it to the bottom.
  3. `WorkoutMasksAndCardsSection.kt` remains strictly $< 400$ lines.
  4. 100% localization parity maintained across all 9 languages.
* **Risk Rating**: **LOW**. Localized layout change with backward-compatible preference migration and clear unit-test contracts.
