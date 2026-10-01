# Stage 2: Requirement & Test Specification - ATT-1713

## 1. Ticket & Metadata
- **Ticket**: [ATT-1713](https://atrainingtracker.atlassian.net/browse/ATT-1713) - `[Feature] [Aftermath/EditWorkout] Configurable Fields in Edit Workout Dialog`
- **Subtask**: [ATT-1806](https://atrainingtracker.atlassian.net/browse/ATT-1806) - `Stage 2: Requirement & Test Specification`
- **Target Version**: `V4.9.38`
- **Target Branch**: `feature/ATT-1713`
- **Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)
- **Author**: AI Agent 1 (Implementer)
- **Date**: 2026-10-01

---

## 2. Requirement Specification (REQ-UI-211)

### REQ-UI-211: Aftermath: Configurable Fields in Edit Workout Dialog
The system SHALL provide athlete-configurable metadata field visibility for the "Edit Workout" dialog in Aftermath (`EditWorkoutScreen.kt`), allowing athletes to streamline the form layout according to their personal training and journaling workflow (ATT-1713):

1. **Configurable Preferences Model (`EditWorkoutFieldPreferences`)**:
   - The system SHALL define an immutable data structure `EditWorkoutFieldPreferences` containing 6 boolean toggles:
     - `showCluster: Boolean = true` (Route / Cluster assignment)
     - `showCommuteTrainer: Boolean = true` (Commute and Trainer checkboxes)
     - `showStravaUpload: Boolean = true` (Individual Strava upload checkbox)
     - `showDescription: Boolean = true` (Description / Notes multiline field)
     - `showGoal: Boolean = true` (Goal single-line field)
     - `showMethod: Boolean = true` (Method single-line field)
   - The system SHALL persist these preferences in `MyPreferenceManager` (DataStore) with atomic reading/updating and expose `editWorkoutFieldPreferencesFlow: Flow<EditWorkoutFieldPreferences>`.

2. **Conditional Form Layout (`EditWorkoutScreen.kt`)**:
   - In `EditWorkoutScreen.kt`, optional fields SHALL be conditionally rendered according to active `EditWorkoutFieldPreferences`:
     - Route/Cluster selection: rendered only when `showCluster` is true.
     - Commute / Trainer checkboxes: rendered only when `showCommuteTrainer` is true.
     - Strava upload checkbox: rendered only when `showStravaUpload` is true (and community upload is enabled in application).
     - Description textfield: rendered only when `showDescription` is true.
     - Goal textfield: rendered only when `showGoal` is true.
     - Method textfield: rendered only when `showMethod` is true.

3. **Data Preservation Invariant (`EditWorkoutViewModel.kt`)**:
   - When saving changes (`saveChanges()`) while certain fields are hidden via preferences, existing values of hidden fields (e.g. `goal`, `method`, `commute`, `trainer`, `clusterId`) SHALL remain completely intact in the saved `WorkoutData` entity.

4. **Settings UI Integration (`DisplaySettingsDialog.kt`)**:
   - `DisplaySettingsDialog.kt` SHALL incorporate a dedicated configuration category titled *"Training bearbeiten"* / *"Edit Workout"* with 6 toggle switches for each configurable field.

5. **100% 9-Language Localization Parity**:
   - All setting category and toggle titles SHALL be defined across all 9 supported locales: English (values), German (values-de), Spanish (values-es), French (values-fr), Italian (values-it), Japanese (values-ja), Dutch (values-nl), Polish (values-pl), and Portuguese (values-pt).

6. **Preservation of Core Invariants**:
   - Mandatory Core Fields: Workout Name (`hint_workout_name`), Sport Type (`Sport`), and Equipment (`Equipment`) SHALL remain permanently rendered and non-configurable on every invocation.

---

## 3. Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-211`), extending Epic `ATT-355` (*Good and consistent UI*) and companion to `REQ-UI-210` (`ATT-1714`).
2. **Historical Origin & Commit Trace**: `EditWorkoutScreen.kt` and `EditWorkoutViewModel.kt` were created in `ATT-140` and enhanced in `ATT-318` (cluster assignment) and `ATT-388`.
3. **Root Reason for Existing Formulation**: All fields were previously hardcoded sequentially. Athletes requested field customization to avoid excessive vertical scrolling and irrelevant fields.
4. **Preservation of Core Invariants**: Mandatory core fields, existing database values, cluster assignment logic, and dropdown selectors remain strictly intact.

---

## 4. Given-When-Then Acceptance Criteria

- **Criterion 1 (Optional Field Toggling)**:
  - *Given* an athlete disables "Goal" and "Method" in display settings,
  - *When* opening the Edit Workout dialog for any activity,
  - *Then* the Goal and Method text fields SHALL be omitted from the dialog.

- **Criterion 2 (Core Fields Integrity)**:
  - *Given* any combination of field preferences (including all 6 optional fields disabled),
  - *When* opening the Edit Workout dialog,
  - *Then* Workout Name, Sport Type dropdown, and Equipment dropdown SHALL remain fully visible and operational.

- **Criterion 3 (Data Preservation of Hidden Fields)**:
  - *Given* a workout with existing values for Goal (`"Sub-40 10k"`) and Method (`"Tempo intervals"`),
  - *When* edited while Goal and Method are hidden, and the user saves changes to Workout Name,
  - *Then* the saved workout in the database SHALL retain the original Goal and Method values without data corruption.

---

## 5. Test Specification (TST-UI-165)

| Test ID | Target Component | Description |
| :--- | :--- | :--- |
| `TST-UI-165.1` | `EditWorkoutFieldPreferencesTest.kt` | Verify default preferences (all true), copy immutability, and full-fidelity custom configurations. |
| `TST-UI-165.2` | `EditWorkoutFieldsLayoutTest.kt` | Verify structural contracts, conditional field visibility, and mandatory core anchors. |
| `TST-UI-165.3` | `EditWorkoutDataPreservationTest.kt` | Verify that saving a workout with hidden fields retains original database values. |
| `TST-UI-165.4` | `EditWorkoutSettingsLocalizationTest.kt` | Verify presence and validity of all 7 localized string keys across all 9 locales. |
| `TST-UI-165.5` | Full Test Suite | Clean-room `./gradlew testDebugUnitTest` execution with 100% pass rate. |
