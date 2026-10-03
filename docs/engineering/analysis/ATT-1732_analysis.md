# Stage 1 Analysis: ATT-1732 - Rename 'Lieblingsorte' Section Heading in Filter Dialogs to 'Start at'

**Ticket**: [ATT-1732](https://atrainingtracker.atlassian.net/browse/ATT-1732)  
**Sub-task**: [ATT-1790](https://atrainingtracker.atlassian.net/browse/ATT-1790) (`[Analysis]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Branch**: `feature/ATT-1732`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

During Sprint Review 2026-40.5 (demonstration of [ATT-1642](https://atrainingtracker.atlassian.net/browse/ATT-1642)), the human user evaluated the multi-dimensional filter dialogs for Workouts and Clusters. The user noted that filtering with respect to start position (Lieblingsorte / Known Locations) functions reliably, but requested renaming the section heading from **"Lieblingsorte"** to **"Start at"** (e.g., German: *"Startet bei"*, English: *"Start at"*) to provide better contextual clarity.

In the context of filtering workout history or route clusters:
1. The heading *"Lieblingsorte"* / *"Favorite Locations"* denotes the domain model entity rather than the user's intent.
2. The user is selecting an anchor location to find sessions or routes that *commenced* at that specific location.
3. Renaming the section heading to *"Start at"* (*"Startet bei"*) clearly communicates the filtering semantics ("show items starting at this location") while maintaining familiar chip selection.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Forensic Inspection of Section Headings:
- **[WorkoutFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt)**:
  - Lines 664–670:
    ```kotlin
    // 8. Favorite Locations (Lieblingsorte, REQ-UI-187, REQ-UI-194)
    if (knownLocations.isNotEmpty()) {
        Column {
            Text(
                text = stringResource(R.string.known_locations_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            ...
    ```
    Currently renders `R.string.known_locations_title` ("Favorite Locations" in English, "Lieblingsorte" in German).

- **[ClusterFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterBottomSheet.kt)**:
  - Lines 216–222:
    ```kotlin
    // 5. Favorite Locations Selection (Lieblingsorte, REQ-UI-187, REQ-UI-194)
    if (knownLocations.isNotEmpty()) {
        Column {
            Text(
                text = stringResource(R.string.known_locations_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            ...
    ```
    Also currently renders `R.string.known_locations_title` ("Favorite Locations" / "Lieblingsorte").

- **[KnownLocationsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt)**:
  - Line 122:
    ```kotlin
    text = stringResource(R.string.known_locations_title)
    ```
    This is the screen-level top bar title for the location management screen, which must remain *"Lieblingsorte"* / *"Favorite Locations"*. Therefore, modifying `R.string.known_locations_title` in-place would regress `KnownLocationsScreen`'s top app bar. A dedicated string resource for filter dialogs is required.

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Goals:
1. Define a dedicated filter section heading string resource `filter_section_start_at` across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with 100% translation parity:
   - English (`values`): `Start at`
   - German (`values-de`): `Startet bei`
   - Spanish (`values-es`): `Comienza en`
   - French (`values-fr`): `Départ à`
   - Italian (`values-it`): `Partenza da`
   - Japanese (`values-ja`): `開始地点`
   - Dutch (`values-nl`): `Start bij`
   - Polish (`values-pl`): `Start w`
   - Portuguese (`values-pt`): `Início em`
2. Update `WorkoutFilterBottomSheet.kt` (line 667) to reference `R.string.filter_section_start_at`.
3. Update `ClusterFilterBottomSheet.kt` (line 219) to reference `R.string.filter_section_start_at`.
4. Implement a comprehensive unit test suite (`FilterSectionHeadingLocalizationTest.kt` or `FilterSectionHeadingLayoutTest.kt`) verifying string resolution and localization parity across all 9 languages.
5. Create requirement `REQ-UI-208` and test specification `TST-UI-162`.

### Out-of-Scope Non-Goals (Scope Bounding):
1. **No removal of Lieblingsstrecken in this ticket**: Ticket [ATT-1731](https://atrainingtracker.atlassian.net/browse/ATT-1731) (`[Filter] Remove filtering with respect to Lieblingsstrecken from WorkoutFilterBottomSheet`) is explicitly designated to handle the removal of section 9 in `WorkoutFilterBottomSheet.kt`. Section 9 must remain untouched during ATT-1732.
2. **No modifications to filtering algorithms or database queries**: Spatial geofencing (`WorkoutClusterEngine.distanceBetween`) and SQLite selection logic remain completely unchanged.
3. **No modification to `KnownLocationsScreen` top app bar**: `R.string.known_locations_title` remains strictly reserved for the screen header of `KnownLocationsScreen.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

- **Original Requirement ID & Target**: `REQ-UI-187` (*Selection of Lieblingsorte and Lieblingsstrecken in Workout and Cluster Filter Dialogs*) and `REQ-UI-194` (*Filter Dialogs: Clean Chip Formatting (Emoji Removal) and Prioritized Section Ordering*).
- **Historical Origin & Commit Trace**: 
  - `REQ-UI-187` introduced in commit `91acde33` (`ATT-1402`, Sprint 2026-40.4).
  - `REQ-UI-194` introduced in commit `c57e911a` (`ATT-1642`, Sprint 2026-40.5).
- **Root Reason for Existing Formulation**: 
  When the location filter was originally added to `WorkoutFilterBottomSheet` and `ClusterFilterBottomSheet`, `R.string.known_locations_title` was reused directly because the entity model was named *Lieblingsorte*. During initial sprint testing, this was functional, but user review demonstrated that a verb-phrase heading (*"Startet bei"* / *"Start at"*) provides superior clarity regarding how selecting a location restricts the displayed results.
- **Preservation of Core Invariants**: 
  Filtering behavior, chip selection/toggle semantics (`localStartLocationLat`, `localStartLocationLng`, `localStartLocationRadiusM`), spatial filtering criteria, active filter chips row display, and dialog dismissal flows are 100% strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

```
                         res/values*/strings.xml
                                   │
              ┌────────────────────┴────────────────────┐
              ▼                                         ▼
   WorkoutFilterBottomSheet                  ClusterFilterBottomSheet
              │                                         │
    Section 8 Title                           Section 4/5 Title
   R.string.filter_section_start_at          R.string.filter_section_start_at
   ("Start at" / "Startet bei")              ("Start at" / "Startet bei")
```

1. **Dedicated Resource Key (`filter_section_start_at`)**:
   Add to `res/values/strings.xml` and all 8 localized counterparts alongside other `filter_section_*` keys (`filter_section_time`, `filter_section_sport`, `filter_section_equipment`, etc.).
2. **UI Binding**:
   Replace `R.string.known_locations_title` with `R.string.filter_section_start_at` in both `WorkoutFilterBottomSheet.kt` and `ClusterFilterBottomSheet.kt`.
3. **Verification**:
   Contract test ensuring both bottom sheets reference `filter_section_start_at` and verifying all 9 translation files define non-empty, localized values.

---

## 6. Gate 1 Readiness & Next Steps
- Scope is bounded, non-goals are defined, and Chesterton's Fence archaeology is complete.
- Ready for Gate 1 audit.
