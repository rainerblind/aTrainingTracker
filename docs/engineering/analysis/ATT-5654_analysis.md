# Stage 1 Analysis: ATT-5654 - Steigungsverteilung als sortierbare Bereichslisten-Einträge

**Ticket**: [ATT-5654](https://rainerblind.atlassian.net/browse/ATT-5654)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (Laps)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.9`  
**Branch**: `feature/ATT-5654`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
In Sprint Review of ATT-5563, Steigungsverteilung (`GradeZoneDistributionCard`) was integrated as a subordinate switch (`SubordinateTuningToggleItem`) below *Zonenkarten-Anzeigemodus* in `WorkoutMasksAndCardsSection.kt`.
This placement is inconsistent with app architecture and Design Guidelines Section 3.2:
1. *Domain Mismatch*: The section list explicitly names *Zonenauswertung (HF & Power)*; slope distribution is terrain-based, not physiological HR/Power.
2. *Lack of Reordering & Section Parity*: Athletes cannot reorder Steigungsverteilung independently in Summary Cards (`WorkoutSummary.kt`) or assign/move it across tabs in Detail View (`WorkoutDetailTabsConfigSheet.kt`).
3. *Redundant Configuration Surface*: Subordinate switches under display mode selectors create competing configuration surfaces.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Tight Coupling in `ZONES`)**: `WorkoutSummary.kt:478-522` and `WorkoutDetailTabbedContent.kt:705-738` bundle `GradeZoneDistributionCard` inside `WorkoutSectionType.ZONES` / `WorkoutDetailSectionType.ZONES`, preventing independent positioning.
* **RCA 2 (Missing Enum Entries)**: Neither `WorkoutSectionType` nor `WorkoutDetailSectionType` defines `GRADE_DISTRIBUTION`, excluding it from the sortable matrix and tab configuration.
* **RCA 3 (Subordinate Switch Placement)**: `WorkoutMasksAndCardsSection.kt:307-311, 369-373` hosts two redundant `SubordinateTuningToggleItem`s under `ZoneDisplayModeSelector` instead of tabular matrix rows.
* **Independent Peer Review & Verification**:
  * Confirmed: `WorkoutCardSectionPreferences.showGradeDistribution` (`false`) and `WorkoutDetailPreferences.showGradeDistribution` (`true`) already exist in `MyPreferenceManager.kt:86, 107` and DataStore.
  * Confirmed: `R.string.settings_workout_card_grade_distribution` (*Steigungsverteilung*) is localized across all 9 locales.
  * Confirmed: Removing subordinate switches and wiring `MatrixFeatureRow` maintains `WorkoutMasksAndCardsSection.kt` strictly below the 400-line limit (currently 391 lines).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. *Summary Cards*: Add `WorkoutSectionType.GRADE_DISTRIBUTION` to `WorkoutSectionType.kt` (`DEFAULT_ORDER` after `ZONES`). Render `GradeZoneDistributionCard` in `WorkoutSummary.kt` at its configured slot in `workoutSectionsOrder` based solely on `preferences.showGradeDistribution`.
  2. *Detail Tabs*: Add `WorkoutDetailSectionType.GRADE_DISTRIBUTION` to `WorkoutDetailSectionType.kt` and `DEFAULT_TABS[2]` (`tab_zones` after `ZONES`). Render in `WorkoutDetailTabbedContent.kt` at its `GRADE_DISTRIBUTION` slot.
  3. *Tab Wire Migration*: In `WorkoutDetailTabConfig.deserializeTabs`, auto-migrate persisted configs lacking `GRADE_DISTRIBUTION` by inserting it into `tab_zones` directly after `ZONES`.
  4. *Settings UI*: Remove both `SubordinateTuningToggleItem`s in `WorkoutMasksAndCardsSection.kt`. Bind `showGradeDistribution` directly via `MatrixFeatureRow` in the reorderable summary matrix.
  5. *Contract & Unit Tests*: Update `WorkoutSectionOrderPersistenceTest`, `WorkoutDetailTabsTest`, `WorkoutSummaryDynamicOrderContractTest`, and `WorkoutMasksAndCardsLayoutTest`.
* **Out-of-Scope Non-Goals**:
  * No changes to `GradeZoneDistributionCard` internal calculation or 3-way display mode.
  * No SQLite database schema migrations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-453.4` (*Workout Details & Summary Integration with Independent Visibility Switches*), `REQ-UI-255` (*Customizable Workout Card Sections*), and `REQ-UI-408.1` (*Configurable Detail Tabs*).
* **Historical Origin & Commit Trace**: `ATT-2176` (`e340a631`, section ordering), `ATT-3920` (`c8939a3f`, configurable tabs), `ATT-5563` (`7febe360`, grade distribution subordinate switches).
* **Root Reason for Existing Formulation**: ATT-5563 bundled grade distribution under `ZONES` as a quick subordinate switch without expanding the section enums.
* **Preservation of Core Invariants**: Retains `showGradeDistribution` persistence keys in DataStore; preserves single authoritative configuration surface (Design Guidelines Sec 3.2); keeps `WorkoutMasksAndCardsSection.kt` < 400 lines; guarantees seamless backward-compatible deserialization.

---

## 5. Architectural Strategy & High-Level Solution
* **Enum Parity & Mapping**: Add `GRADE_DISTRIBUTION` to `WorkoutSectionType` and `WorkoutDetailSectionType` with bidirectional `toWorkoutDetailSectionType()` and `toWorkoutSectionTypeOrNull()`.
* **Summary Matrix**: Update `buildMatrixFeatureRow` in `WorkoutMasksAndCardsSection.kt` to handle `GRADE_DISTRIBUTION` -> `showGradeDistribution`. Remove subordinate switches lines 307–311 & 369–373.
* **Decoupled Card Rendering**: In `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt`, render `GradeZoneDistributionCard` under its own `GRADE_DISTRIBUTION` section case, removing it from `ZONES`.
* **Tab Config Migration**: In `WorkoutDetailTabConfig.deserializeTabs()`, if `GRADE_DISTRIBUTION` is missing from all tabs, append it to `tab_zones` directly after `ZONES`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero preference data loss: existing DataStore booleans (`showGradeDistribution`) are preserved and authoritative.
  2. Single authoritative configuration surface: tab config governs Detail View; sortable matrix governs Summary Cards.
  3. File size constraint: `WorkoutMasksAndCardsSection.kt` remains strictly < 400 lines.
* **Risk Rating**: **LOW** — Pure UI enum and layout refactoring with zero DB changes or concurrency impact.
