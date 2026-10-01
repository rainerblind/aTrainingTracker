# Stage 1 Analysis: ATT-1713 - Configurable Fields in Edit Workout Dialog

## 1. Ticket & Metadata
- **Ticket**: [ATT-1713](https://atrainingtracker.atlassian.net/browse/ATT-1713) - `[Feature] [Aftermath/EditWorkout] Configurable Fields in Edit Workout Dialog`
- **Subtask**: [ATT-1805](https://atrainingtracker.atlassian.net/browse/ATT-1805) - `Stage 1: Problem Domain & Root Cause Analysis`
- **Target Version**: `V4.9.38`
- **Target Branch**: `feature/ATT-1713`
- **Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)
- **Author**: AI Agent 1 (Implementer)
- **Date**: 2026-10-01

---

## 2. Problem Statement & User Value Proposition
Currently, `EditWorkoutDialog` in `EditWorkoutScreen.kt` renders an exhaustive, fixed sequence of metadata fields:
1. Workout Name
2. Route / Cluster assignment
3. Sport Type & Equipment dropdowns
4. Commute and Trainer checkboxes
5. Individual Strava Upload checkbox
6. Description / Notes
7. Goal
8. Method

Different athletes have distinct training logging routines:
- Recreational cyclists and commuters rarely use structured training tags such as "Goal" or "Method".
- Runners running outdoors on known loops rarely track indoor trainer tags or commuting flags.
- Conversely, athletes with structured training plans frequently edit Goal and Method, but may not utilize individual Strava overrides.

Rendering every possible metadata field on every edit interaction creates unnecessary visual noise and forces mobile athletes to scroll through irrelevant fields to reach the Save button. By introducing athlete-configurable field preferences, each athlete can tailor the edit dialog to their workflow while preserving mandatory core fields (Name, Sport, Equipment) and ensuring that hidden fields never have their existing data wiped.

---

## 3. Scope Bounding & Invariants

### In-Scope
1. **Field Preferences Data Model (`EditWorkoutFieldPreferences`)**:
   - Define an immutable data structure containing 6 boolean toggles:
     - `showCluster: Boolean = true`
     - `showCommuteTrainer: Boolean = true`
     - `showStravaUpload: Boolean = true`
     - `showDescription: Boolean = true`
     - `showGoal: Boolean = true`
     - `showMethod: Boolean = true`
2. **DataStore Persistence in `MyPreferenceManager.kt`**:
   - Store and retrieve `EditWorkoutFieldPreferences` asynchronously with atomic updates and reactive flow.
3. **Display Settings UI Integration in `DisplaySettingsDialog.kt`**:
   - Provide a dedicated category *"Training bearbeiten"* / *"Edit Workout"* with 6 toggles.
4. **Conditional Layout in `EditWorkoutScreen.kt`**:
   - Observe preferences and conditionally render optional fields.
5. **Data Preservation Invariant**:
   - When saving an edited workout where certain fields are hidden, existing values for hidden fields must remain completely intact.
6. **100% 9-Language Localization Parity**:
   - Complete translations across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Out-of-Scope (Chesterton's Fence Audit)
1. **Mandatory Core Fields**:
   - Workout Name (`hint_workout_name`), Sport Type (`Sport`), and Equipment (`Equipment`) are foundational data anchors required for aggregation, statistics, and sensor linking; they are strictly non-configurable.
2. **Detailed Card Visibility**:
   - Detailed card sections in `WorkoutSummary.kt` are governed exclusively by [ATT-1714](https://atrainingtracker.atlassian.net/browse/ATT-1714) / `REQ-UI-210`.

---

## 4. Technical Architecture & Component Interaction

```
                     DisplaySettingsDialog
                               │ (User configures 6 field toggles)
                               ▼
                    MyPreferenceManager (DataStore)
                    [EditWorkoutFieldPreferences]
                               │
                               ▼
                      EditWorkoutViewModel
                               │ (Exposes fieldPreferences: StateFlow)
                               ▼
                      EditWorkoutScreen
     ┌────────────────────────────────────────────────────────┐
     │ 1. Workout Name (ALWAYS MANDATORY)                     │
     │ 2. Route / Cluster (if showCluster)                     │
     │ 3. Sport & Equipment (ALWAYS MANDATORY)                │
     │ 4. Commute & Trainer (if showCommuteTrainer)           │
     │ 5. Strava Upload (if showStravaUpload & community on)  │
     │ 6. Description (if showDescription)                   │
     │ 7. Goal (if showGoal)                                  │
     │ 8. Method (if showMethod)                              │
     │ [Cancel] [Save]                                        │
     └────────────────────────────────────────────────────────┘
```

---

## 5. Risk Analysis & Mitigation
- **Risk 1: Accidental overwriting of hidden fields**: If the ViewModel cleared fields that were not displayed, user data would be lost.
  - *Mitigation*: In `EditWorkoutViewModel`, the backing `_workoutData` state retains its loaded database values. If a field composable is not rendered, its update callback is never invoked; hence `repository.saveWorkout(workoutData.value)` saves the existing untouched value. A targeted unit test will verify this contract.
- **Risk 2: Dialog layout shifts**: Toggling fields could cause unexpected layout jank.
  - *Mitigation*: The bottom sheet uses standard Compose spacing inside a scrollable column with stable keys.

---

## 6. Definition of Done
- [x] Subtask ATT-1805 created and moved to In Bearbeitung.
- [x] Analysis deliverable authored and scope bounded.
- [ ] Gate 1 audit approved.
