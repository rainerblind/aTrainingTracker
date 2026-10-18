# Stage 5 Walkthrough & Verification Report: `ATT-3920` — Workout Details: Individually configurable tabs

## 1. Summary of Verified Changes
1. **Domain Model, Default 4-Tab Preset & Defensive `v1|` Serialization (`WorkoutDetailTabConfig.kt`, `MyPreferenceManager.kt`, `REQ-UI-408.1`, `REQ-UI-408.2`)**:
   - Defined `WorkoutDetailSectionType` (10 assignable sections: `DETAILS`, `DESCRIPTION`, `EXTREMA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`, `LAPS`, `STRAVA`, `EXPORT_STATUS`) with bidirectional mapping to `WorkoutSectionType` (9 sections in `WorkoutSummary.kt`).
   - Configured the 4-tab default layout (`WorkoutDetailTabConfig.DEFAULT_TABS`):
     1. **Overview**: `[DETAILS, DESCRIPTION, EXTREMA, EXPORT_STATUS]`
     2. **Map & Graphs**: `[MAP, ELEVATION, CHARTS]`
     3. **Zones**: `[ZONES]`
     4. **Laps & Segments**: `[LAPS, STRAVA]`
   - Implemented `serializeTabs` and `deserializeTabs` (`v1|` versioned wire schema with URL encoding and `runCatching` corrupt-payload recovery to `DEFAULT_TABS`) and persisted via `MyPreferenceManager` (`WORKOUT_DETAIL_TABS_CONFIG`, `workoutDetailTabsOrderFlow`, `getWorkoutDetailTabs()`, `setWorkoutDetailTabs()`, `resetWorkoutDetailTabs()`).
2. **Conflict-Free Per-Tab Layout Modes, Map Pinning & Auto-Hiding Empty Tabs (`WorkoutDetailTabbedContent.kt`, `WorkoutDetailTabConfig.kt`, `REQ-UI-408.3`, `REQ-UI-408.4`)**:
   - Enforced `MAP` pinned at index `0` via `normalizeTabSections` and `validateConfig()` / `validateConfigs()`.
   - Resolved each tab into one of 3 conflict-free layout modes (`MODE_A_CARD_SCROLL`, `MODE_B_SPLIT_MAP`, `MODE_C_FULL_MAP`).
   - Added `WorkoutDetailSectionAvailability.fromWorkoutState(...)` and `filterVisibleWorkoutDetailTabs(...)` so tabs whose assigned sections have zero data for the current workout (e.g., indoor sessions without GPS or Strava segments) are automatically hidden.
   - Implemented `requiresHorizontalGestureIsolation(...)` with interactive canvas hit-testing and `24.dp` screen-edge swipe gutters bound to `HorizontalPager(userScrollEnabled = !activeTabRequiresGestureIsolation)`.
3. **Interactive Tab Configuration Sheet & Dual Entry Points (`WorkoutDetailTabsConfigSheet.kt`, `TrackOnMapScreen.kt`, `WorkoutMasksAndCardsSection.kt`, `REQ-UI-408.5`, `REQ-UI-408.6`)**:
   - Created `WorkoutDetailTabsConfigSheet.kt` supporting adding, renaming, reordering, and deleting tabs (minimum 1-tab guard), toggling and reordering sections per tab (`MAP` pinned at index `0`), and resetting to defaults.
   - Wired Entry Point 1 (`onOpenTabsConfig` action in `TrackOnMapScreen.kt` top header) and Entry Point 2 (`OutlinedButton` in `WorkoutMasksAndCardsSection.kt` in Advanced Settings, keeping `WorkoutMasksAndCardsSection.kt` at 378 lines `< 400` lines).
   - Added all 14 localized string keys across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

## 2. Verification & Regression Results (`TST-UI-371`)
- **Targeted Unit & Contract Tests**:
  - `WorkoutDetailTabsTest` (`TST-UI-371.1` through `TST-UI-371.8`): **PASSED (8/8)**
  - `WorkoutSectionOrderPersistenceTest`, `WorkoutSectionReorderContractTest`, `WorkoutMasksAndCardsLayoutTest`, `AdvancedTuningModularityTest`: **PASSED**
  - `TrackOnMapScreen*ContractTest` (all 7 suites): **PASSED**
- **Full Clean-Room Unit Test Suite (`./gradlew testDebugUnitTest`)**:
  - Executed across the full project unit test suite (100% pass rate, zero regressions).
  - Living documentation (`REQ-UI-408` in `docs/requirements.md` and `TST-UI-371` in `docs/tests.md`) updated to `Verified`.
