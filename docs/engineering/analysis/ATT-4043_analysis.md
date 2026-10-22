# Stage 1 Analysis: ATT-4043 - Clean up WorkoutMasksAndCardsSection: remove redundant Details column, collapse Summary list by default, and split Zone Display Mode into Summary vs Details

**Ticket**: [ATT-4043](https://rainerblind.atlassian.net/browse/ATT-4043)  
**Sub-task**: [ATT-4221](https://rainerblind.atlassian.net/browse/ATT-4221) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Branch**: `feature/ATT-4043`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-42.8 Joint Review of *ATT-3920* (*Workout Details: Individually configurable tabs*), inspecting *Advanced Settings -> Trainingsliste & Detail-Ansicht* (`WorkoutMasksAndCardsSection.kt`) revealed key UX discrepancies and architectural redundancies:

1. **Redundant & Conflicting "Details" Checkbox Column**:
   - Following ATT-3920, the Workout Details screen utilizes its own dedicated tab configuration sheet (`WorkoutDetailTabsConfigSheet`, `WorkoutDetailTabConfig`), allowing athletes to define tabs and assign sections per tab.
   - However, `WorkoutMasksAndCardsSection.kt` retained a legacy 2-column matrix table ("Liste" and "Details").
   - Unchecking a section in the "Details" column silently suppressed that section from rendering even when explicitly assigned to a tab in `WorkoutDetailTabsConfigSheet`, confusing users with dual, conflicting visibility controls.

2. **Excessive Vertical Height of Always-Expanded 9-Row Section List**:
   - The 9-row reorderable list (with Up/Down arrow buttons and checkboxes) applies strictly to the *Trainingsliste (Summary Cards)*.
   - Displaying all 9 rows expanded by default consumes substantial vertical space in the settings accordion, forcing users to scroll past the entire list to reach subsequent settings (Lap Display Mode, Zone Display Mode, and Detail Tabs configuration).

3. **Conflated Summary vs. Details Zone Display Mode Selectors**:
   - The segmented button selectors for *Zonenkarten-Anzeigemodus* (`ZoneDisplayModeSelector`: Stacked Bar / Columns / Histogram) for Summary and Details were grouped together at the bottom of the section rather than logically placed within their respective Summary vs. Details configuration blocks.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic examination of `WorkoutMasksAndCardsSection.kt`, `WorkoutDetailTabConfig.kt`, and `MyPreferenceManager.kt` reveals:

1. **Historical Origin (`ATT-2030` & `ATT-2383`)**:
   - In Sprint 2026-40.12 (`ATT-2030`, commit `02f3d414`), Workout Details was a single monolithic vertical scroll view. A 2-column matrix table was introduced to toggle section visibility in both the workout list cards (`WorkoutCardSectionPreferences`) and full-screen details (`WorkoutDetailPreferences`).
   - In Sprint 2026-40.14 (`ATT-2383`, commit `6817b037`), `REQ-UI-271` refined the column headers to concise singular nouns ("List" / "Details") within 50dp containers.

2. **Architectural Decoupling in ATT-3920 (`c8939a3f`)**:
   - Sprint 2026-42.8 (`ATT-3920`) introduced `WorkoutDetailTabConfig`, making per-tab section assignment the authoritative source of truth for the Workout Details screen.
   - However, `WorkoutDetailSectionAvailability.isSectionActive()` continued to gate section activation on legacy boolean flags:
     ```kotlin
     fun isSectionActive(section: WorkoutDetailSectionType, detailPreferences: WorkoutDetailPreferences): Boolean = when (section) {
         WorkoutDetailSectionType.MAP -> detailPreferences.showMap && hasMap
         WorkoutDetailSectionType.ELEVATION -> detailPreferences.showElevationProfile && hasElevation
         // ...
     }
     ```
   - When a user assigned `MAP` to a tab in `WorkoutDetailTabsConfigSheet`, but `detailPreferences.showMap` was `false` from an earlier toggle in the settings matrix, the map was silently suppressed, breaking user expectations.

3. **UI Ergonomics in `WorkoutMasksAndCardsSection.kt`**:
   - The composable layout stacked all controls linearly: (1) 9-row table, (2) Lap display mode, (3) Zone display modes for both summary and details, and (4) Detail Tabs button.
   - There was no clear visual or structural boundary between list card settings and detail view settings.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * **Remove Redundant "Details" Column**: Remove the "Details" checkbox column and header from `WorkoutMasksAndCardsSection.kt`. The feature table now features a single checkbox column for `Trainingsliste (Summary-Karten)`.
  * **Authoritative Tab Section Assignment**: Update `WorkoutDetailSectionAvailability.isSectionActive()` so that section activation is governed strictly by data availability (`hasDescription`, `hasExtrema`, `hasMap`, `hasElevation`, `hasCharts`, `hasZones`, `hasLaps`, `hasStrava`, `hasExportStatus`) and tab inclusion, ignoring legacy per-section boolean flags in `WorkoutDetailPreferences`.
  * **Domain Partitioning**: Structure `WorkoutMasksAndCardsSection.kt` into two distinct, well-labeled domains:
    1. **Trainingsliste (Summary-Karten)**:
       - Header / sub-title for summary cards.
       - Collapsible 9-row reorderable section list (collapsed by default) with an expand/collapse toggle showing the count of active cards.
       - Lap Display Mode segmented button (`TABLE_ONLY` vs `VISUALIZER_ONLY`), visible when `workoutCardPrefs.showLaps` is enabled.
       - Zonenkarten-Anzeigemodus segmented button for Summary cards (`workoutCardPrefs.zoneCardDisplayMode`).
    2. **Trainings-Details (Detail-Ansicht)**:
       - Header / sub-title for detail view.
       - Action button (`OutlinedButton`) launching `WorkoutDetailTabsConfigSheet`.
       - Zonenkarten-Anzeigemodus segmented button for Details (`workoutDetailPrefs.zoneCardDisplayMode`).
  * **Strict Modularity & File Size Limit**: Maintain `WorkoutMasksAndCardsSection.kt` strictly under 400 lines (`REQ-PRO-011`).
  * **Test Suite Alignment**: Update `WorkoutMasksAndCardsLayoutTest.kt`, `WorkoutDetailTabsTest.kt`, and `AdvancedTuningAccordionTest.kt` to reflect single-column list checkboxes and authoritative tab assignments.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Refactoring the internal mechanics of `WorkoutDetailTabsConfigSheet.kt` or `WorkoutDetailTabbedContent.kt`.
  * Altering summary card rendering in `WorkoutSummary.kt`.
  * Removing `WorkoutDetailPreferences.zoneCardDisplayMode` from DataStore.
  * Adding or modifying other accordion categories in Advanced Settings.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Refines `REQ-UI-240` (*Matrix Table Presentation for Configurable Aftermath Sections*), `REQ-UI-264` (*Workout Cards & Details Tuning Dialog Layout*), `REQ-UI-271` (*Simplified Preposition-Free Column Headers*), and `REQ-UI-408` (*Individually Configurable Workout Details Tabs*).
* **Historical Origin & Commit Trace**: Commit `02f3d414` (Sprint 2026-40.12, initial 2-column matrix), commit `6817b037` (Sprint 2026-40.14, REQ-UI-271 singular headers), and commit `c8939a3f` (Sprint 2026-42.8, ATT-3920 configurable detail tabs).
* **Root Reason for Existing Formulation**: When the 2-column matrix was introduced, Workout Details had no tabs, so a single unified matrix was the only place to toggle detail sections. Now that tab configuration has dedicated per-tab section toggles, the Details column in the matrix is an obsolete secondary gating mechanism.
* **Preservation of Core Invariants**:
  - Summary card section reordering (`DEFAULT_ORDER`), DataStore persistence (`WorkoutCardSectionPreferences`), and active card visibility in `WorkoutSummary.kt` must remain 100% intact.
  - Tab validation rules (Map pinned at index 0, minimum 1-tab fallback) in `WorkoutDetailTabConfig` must remain intact.
  - Minimum 48dp touch targets on checkboxes and buttons.
  - 100% 9-language translation parity.

---

## 5. Architectural Strategy & UI Layout Design

```
+---------------------------------------------------------------------------------+
| WorkoutMasksAndCardsSection                                                     |
|                                                                                 |
| 1. PART 1: Trainingsliste (Summary-Karten)                                      |
|    - Sub-Header: "Trainingsliste (Summary-Karten)"                              |
|    - Collapsible Toggle Button: "Kartenreihenfolge & Sichtbarkeit (X/9 aktiv)"   |
|      [v] / [^] (Collapsed by default)                                           |
|      + When Expanded:                                                           |
|        - Table Header: [Spacer 32dp] [Element] [Sichtbar / Liste 50dp]          |
|        - 9 Reorderable Rows: [Up/Down 32dp] [Title] [Checkbox 50dp]             |
|    - Runden-Anzeigemodus (if showLaps): [Tabelle | Visualizer]                  |
|    - Zonenkarten-Anzeigemodus (Summary): [Gestapelt | Spalten | Histogramm]     |
|                                                                                 |
| 2. DIVIDER                                                                      |
|                                                                                 |
| 3. PART 2: Trainings-Details (Detail-Ansicht)                                   |
|    - Sub-Header: "Trainings-Details (Detail-Ansicht)"                           |
|    - Action Button: [Tune Icon] "Detail-Tabs konfigurieren"                     |
|      -> opens WorkoutDetailTabsConfigSheet                                      |
|    - Zonenkarten-Anzeigemodus (Details): [Gestapelt | Spalten | Histogramm]     |
+---------------------------------------------------------------------------------+
```

### Decoupling `isSectionActive` in `WorkoutDetailSectionAvailability`
```kotlin
fun isSectionActive(
    section: WorkoutDetailSectionType,
    detailPreferences: WorkoutDetailPreferences = WorkoutDetailPreferences()
): Boolean = when (section) {
    WorkoutDetailSectionType.DETAILS -> hasDetails
    WorkoutDetailSectionType.DESCRIPTION -> hasDescription
    WorkoutDetailSectionType.EXTREMA -> hasExtrema
    WorkoutDetailSectionType.MAP -> hasMap
    WorkoutDetailSectionType.ELEVATION -> hasElevation
    WorkoutDetailSectionType.CHARTS -> hasCharts
    WorkoutDetailSectionType.ZONES -> hasZones
    WorkoutDetailSectionType.LAPS -> hasLaps
    WorkoutDetailSectionType.STRAVA -> hasStrava
    WorkoutDetailSectionType.EXPORT_STATUS -> hasExportStatus
}
```
*Note*: Parameter `detailPreferences` is preserved with its default value for backward compatibility, but its legacy per-section boolean flags are no longer consulted, ensuring tab assignment in `WorkoutDetailTabConfig` is strictly authoritative.
