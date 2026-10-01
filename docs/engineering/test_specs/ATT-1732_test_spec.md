# Stage 2 Requirement & Test Specification: ATT-1732

## 1. Ticket & Metadata
- **Parent Ticket**: [ATT-1732](https://atrainingtracker.atlassian.net/browse/ATT-1732) - `[Filter] Rename 'Lieblingsorte' section heading in Filter dialogs to 'Start at'`
- **Subtask**: [ATT-1791](https://atrainingtracker.atlassian.net/browse/ATT-1791) - `Stage 2: Requirement & Test Specification`
- **Target Branch**: `feature/ATT-1732`
- **Target Version**: `V4.9.38`
- **Author**: AI Agent 1 (Implementer)
- **Date**: 2026-10-01

---

## 2. Requirement Specification (`REQ-UI-208`)

### REQ-UI-208: Filter Dialogs: Contextual 'Start at' Section Heading for Location Filters
The system SHALL rename the section heading for location filtering in `WorkoutFilterBottomSheet.kt` and `ClusterFilterBottomSheet.kt` from `known_locations_title` (*"Lieblingsorte"* / *"Favorite Locations"*) to a dedicated localized string resource `filter_section_start_at` (*"Start at"* / *"Startet bei"*) (ATT-1732):

1. **Dedicated Filter Heading String Resource (`filter_section_start_at`)**:
   - The system SHALL define the string resource `filter_section_start_at` across all 9 supported locales with 100% translation parity:
     - English (`values/strings.xml`): `"Start at"`
     - German (`values-de/strings.xml`): `"Startet bei"`
     - Spanish (`values-es/strings.xml`): `"Comienza en"`
     - French (`values-fr/strings.xml`): `"Départ à"`
     - Italian (`values-it/strings.xml`): `"Partenza da"`
     - Japanese (`values-ja/strings.xml`): `"開始地点"`
     - Dutch (`values-nl/strings.xml`): `"Start bij"`
     - Polish (`values-pl/strings.xml`): `"Start w"`
     - Portuguese (`values-pt/strings.xml`): `"Início em"`

2. **Filter Dialog UI Binding**:
   - In `WorkoutFilterBottomSheet.kt`, Section 8 heading text SHALL reference `stringResource(R.string.filter_section_start_at)`.
   - In `ClusterFilterBottomSheet.kt`, Section 4/5 heading text SHALL reference `stringResource(R.string.filter_section_start_at)`.

3. **Preservation of Invariants & Scope Boundaries**:
   - In `KnownLocationsScreen.kt`, the top app bar title SHALL strictly preserve `stringResource(R.string.known_locations_title)` (*"Lieblingsorte"* / *"Favorite Locations"*).
   - In `WorkoutFilterBottomSheet.kt`, Section 9 (*Lieblingsstrecken*) SHALL remain intact, as its removal is designated for companion ticket `ATT-1731`.
   - Chip selection semantics (`localStartLocationLat`, `localStartLocationLng`, `localStartLocationRadiusM`), spatial filtering logic, active filter chips row display, and dialog dismissal flows SHALL remain 100% operational.

### Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
1. **Original Requirement ID & Target**: `REQ-UI-187` (*Selection of Lieblingsorte and Lieblingsstrecken in Workout and Cluster Filter Dialogs*) and `REQ-UI-194` (*Filter Dialogs: Clean Chip Formatting (Emoji Removal) and Prioritized Section Ordering*).
2. **Historical Origin & Commit Trace**: Introduced in commits `91acde33` (`ATT-1402`) and `c57e911a` (`ATT-1642`).
3. **Root Reason for Existing Formulation**: When the location filter was originally introduced, `R.string.known_locations_title` was reused directly. User feedback during Sprint Review 2026-40.5 demonstrated that an action-oriented heading (*"Startet bei"* / *"Start at"*) provides superior clarity regarding filter semantics.
4. **Preservation of Core Invariants**: 100% preservation of spatial matching, chip toggle behavior, active chips row, test tags, and dialog dismissal.

### Acceptance Criteria (Given-When-Then)
- **AC-1 (WorkoutFilterBottomSheet German)**:
  - *Given* an athlete opening `WorkoutFilterBottomSheet` in German locale,
  - *When* the athlete inspects the location filter section,
  - *Then* the section title SHALL render as `"Startet bei"`, and SHALL NOT render `"Lieblingsorte"`.
- **AC-2 (ClusterFilterBottomSheet English)**:
  - *Given* an athlete opening `ClusterFilterBottomSheet` in English (default) locale,
  - *When* the athlete inspects the location filter section,
  - *Then* the section title SHALL render as `"Start at"`, and SHALL NOT render `"Favorite Locations"`.
- **AC-3 (KnownLocationsScreen Unchanged)**:
  - *Given* an athlete opening `KnownLocationsScreen`,
  - *When* the athlete inspects the top app bar,
  - *Then* the screen title SHALL remain `"Lieblingsorte"` (DE) / `"Favorite Locations"` (EN).
- **AC-4 (9-Language Parity)**:
  - *Given* any of the 9 supported application locales,
  - *When* resolving `filter_section_start_at`,
  - *Then* the localized string SHALL be non-empty and semantically accurate.

---

## 3. Test Specification (`TST-UI-162`)

### TST-UI-162: Filter Dialogs 'Start at' Section Heading & 9-Language Localization Verification

1. **Structural Contract Unit Tests (`FilterSectionHeadingLayoutTest.kt`)**:
   - `testWorkoutFilterBottomSheetUsesStartAtHeading()`:
     - Inspect `WorkoutFilterBottomSheet.kt` source.
     - Assert that the location filter section header references `R.string.filter_section_start_at`.
     - Assert that the location filter section header does NOT reference `R.string.known_locations_title`.
   - `testClusterFilterBottomSheetUsesStartAtHeading()`:
     - Inspect `ClusterFilterBottomSheet.kt` source.
     - Assert that the location filter section header references `R.string.filter_section_start_at`.
     - Assert that the location filter section header does NOT reference `R.string.known_locations_title`.
   - `testKnownLocationsScreenPreservesKnownLocationsTitle()`:
     - Inspect `KnownLocationsScreen.kt` source.
     - Assert that the top app bar title strictly retains `R.string.known_locations_title`.

2. **9-Language Localization Audit (`FilterSectionHeadingLocalizationTest.kt`)**:
   - `testFilterSectionStartAtAcrossAllNineLocales()`:
     - Parse `strings.xml` across all 9 localized resource directories (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
     - Assert `filter_section_start_at` exists in all 9 files with zero missing entries.
     - Assert value is non-empty and matches the expected translation per locale.

3. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` across all modules verifying 100% test pass rate with 0 regressions.

---

## 4. Traceability Matrix

| Requirement | Test Specification | Verification Method | Target File(s) | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-208` | `TST-UI-162` | Structural & Localization Unit Tests (`FilterSectionHeadingLayoutTest.kt`, `FilterSectionHeadingLocalizationTest.kt`) | `WorkoutFilterBottomSheet.kt`, `ClusterFilterBottomSheet.kt`, `res/values*/strings.xml` | Specified |
