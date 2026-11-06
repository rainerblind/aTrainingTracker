# Stage 2: Requirement & Test Specification - ATT-5654: Steigungsverteilung als sortierbare Bereichslisten-Einträge

**Ticket**: [ATT-5654](https://rainerblind.atlassian.net/browse/ATT-5654)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (Laps) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5654`  
**Requirement Mapping**: `REQ-UI-453` (`REQ-UI-453.4`), `REQ-UI-255`, `REQ-UI-408` (`REQ-UI-408.1`, `REQ-UI-408.2`) | **Test Spec ID**: `TST-UI-453` (`TST-UI-453.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-UI-453.4`, `REQ-UI-255`, `REQ-UI-408`)

### 1.1 Problem Statement & Rationale
1. In ATT-5563, Steigungsverteilung (`GradeZoneDistributionCard`) was added as a subordinate switch under *Zonenkarten-Anzeigemodus* in `WorkoutMasksAndCardsSection.kt`.
2. This creates domain coupling: slope distribution is geographic terrain, not physiological HR/Power zone telemetry.
3. Athletes cannot reorder Steigungsverteilung in Summary Cards (`WorkoutSummary.kt`) or configure/move it across tabs in Workout Details (`WorkoutDetailTabsConfigSheet.kt`).
4. Dual subordinate switches under mode selectors introduce competing configuration surfaces contrary to Design Guidelines Section 3.2.

### 1.2 Functional Specification
1. **Summary Cards Sortable Entry (`REQ-UI-255`, `REQ-UI-453.4`)**:
   - Add `WorkoutSectionType.GRADE_DISTRIBUTION` (*Steigungsverteilung*) to `WorkoutSectionType` (`DEFAULT_ORDER` after `ZONES`).
   - Wire `buildMatrixFeatureRow` in `WorkoutMasksAndCardsSection.kt` to bind `GRADE_DISTRIBUTION` to `WorkoutCardSectionPreferences.showGradeDistribution`.
   - Render `GradeZoneDistributionCard` in `WorkoutSummary.kt` exclusively at its dynamic slot in `workoutSectionsOrder` (decoupling it from `ZONES`).
2. **Detail Tabs Sortable Entry (`REQ-UI-408.1`, `REQ-UI-453.4`)**:
   - Add `WorkoutDetailSectionType.GRADE_DISTRIBUTION` to `WorkoutDetailSectionType` with bidirectional enum mapping (`toWorkoutDetailSectionType()` and `toWorkoutSectionTypeOrNull()`).
   - Add `GRADE_DISTRIBUTION` to `DEFAULT_TABS[2]` (`tab_zones`) directly following `ZONES`.
   - Render `GradeZoneDistributionCard` in `WorkoutDetailTabbedContent.kt` at its configured `GRADE_DISTRIBUTION` slot.
   - Wire auto-migration in `WorkoutDetailTabConfig.deserializeTabs`: if `GRADE_DISTRIBUTION` is missing across all tabs, inject it into `tab_zones` directly after `ZONES`.
3. **Subordinate Switch Decommissioning (`REQ-UI-453.4`)**:
   - Remove both `SubordinateTuningToggleItem`s under `ZoneDisplayModeSelector` in `WorkoutMasksAndCardsSection.kt` (lines 307–311 and 369–373), preserving `< 400` line ceiling.

### 1.3 Acceptance Criteria & Invariants
- **AC-1 (Summary Reorder & Render)**: *Given* a workout summary card, *When* `GRADE_DISTRIBUTION` is enabled in `workoutSectionsOrder`, *Then* `GradeZoneDistributionCard` is rendered at its configured position.
- **AC-2 (Detail Tab Configuration)**: *Given* `WorkoutDetailTabsConfigSheet`, *When* toggling/reordering `GRADE_DISTRIBUTION`, *Then* `GradeZoneDistributionCard` renders at that exact tab and position.
- **AC-3 (Settings Parity & Cleanup)**: *Given* `WorkoutMasksAndCardsSection`, *When* viewing Zone Analysis, *Then* zero subordinate switches appear under display mode selectors.
- **AC-4 (Wire Auto-Migration)**: *Given* a persisted tab string without `GRADE_DISTRIBUTION`, *When* `deserializeTabs` runs, *Then* `GRADE_DISTRIBUTION` is appended to `tab_zones`.
- **Invariants**: Single authoritative configuration surface; DataStore backwards compatibility (`showGradeDistribution`); `WorkoutMasksAndCardsSection.kt` < 400 lines; 100% 9-language translation parity.

### Requirement Archaeology & Chesterton's Fence Audit
- *Original Requirement ID & Target*: Amends `REQ-UI-453.4`, `REQ-UI-255`, and `REQ-UI-408.1`, targeting `WorkoutSectionType.kt`, `WorkoutDetailTabConfig.kt`, `WorkoutMasksAndCardsSection.kt`, `WorkoutSummary.kt`, and `WorkoutDetailTabbedContent.kt`.
- *Historical Origin & Commit Trace*: `ATT-2176` (`e340a631`), `ATT-3920` (`c8939a3f`), `ATT-5563` (`7febe360e2`), `ATT-5654`.
- *Root Reason for Existing Formulation*: ATT-5563 placed Steigungsverteilung under `ZoneDisplayModeSelector` as a quick visibility switch without expanding section enums.
- *Preservation of Core Invariants*: Replaces competing controls with single authoritative surfaces; preserves DataStore keys; keeps `WorkoutMasksAndCardsSection.kt` < 400 lines; guarantees seamless deserialization.

---

## 2. Test Specification (`TST-UI-453.4`)

- **TST-UI-453.4a (Summary Section Enum & Serialization)**: Verify `WorkoutSectionType.GRADE_DISTRIBUTION` serialization, deserialization, and `DEFAULT_ORDER` parity in `WorkoutSectionOrderPersistenceTest.kt`.
- **TST-UI-453.4b (Detail Tab Enum & Migration Contract)**: Verify `WorkoutDetailSectionType.GRADE_DISTRIBUTION` presence in `DEFAULT_TABS[2]`, bidirectional mapping, and `deserializeTabs` migration in `WorkoutDetailTabsTest.kt`.
- **TST-UI-453.4c (Settings Matrix & Subordinate Removal)**: Verify `WorkoutMasksAndCardsSection.kt` renders `GRADE_DISTRIBUTION` in `buildMatrixFeatureRow`, excludes subordinate switches under `ZoneDisplayModeSelector`, and satisfies the `< 400` lines constraint (`WorkoutMasksAndCardsLayoutTest.kt`).
- **TST-UI-453.4d (Decoupled Rendering Contract)**: Verify `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt` render `GradeZoneDistributionCard` strictly at `GRADE_DISTRIBUTION` slot (`WorkoutSummarySectionsTest.kt`, `WorkoutDetailTabbedContentTest.kt`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-453.4a` | Unit | `WorkoutSectionType.kt` | `REQ-UI-255`, `REQ-UI-453.4` | Specified |
| `TST-UI-453.4b` | Unit | `WorkoutDetailTabConfig.kt` | `REQ-UI-408.1`, `REQ-UI-453.4` | Specified |
| `TST-UI-453.4c` | Unit / UI | `WorkoutMasksAndCardsSection.kt` | `REQ-UI-453.4` | Specified |
| `TST-UI-453.4d` | Unit / UI | `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt` | `REQ-UI-453.4` | Specified |
