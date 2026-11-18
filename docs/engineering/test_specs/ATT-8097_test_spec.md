# Stage 2: Requirement & Test Specification - ATT-8097

**DOC-ID**: `DOC-TST-ATT-8097` | **Ticket**: [ATT-8097](https://atrainingtracker.atlassian.net/browse/ATT-8097) | **Parent Epic**: [ATT-176](https://atrainingtracker.atlassian.net/browse/ATT-176)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_01_Früh` | **Branch**: `feature/ATT-8097`  
**Requirement Mapping**: `REQ-UI-2786` | **Test Spec ID**: `TST-UI-2786` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-UI-2786` `[Planned]`)
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, On-Device Manual | **Localization**: 9 locales (`values*/strings_workout_header.xml`, 4 tooltip/accessibility keys)
- **Implementation**: `WorkoutHeader.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`, `TrackOnMapAftermathViewModel.kt`, `WorkoutClusterRepository.kt`, `WorkoutClustersScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, `values*/strings_workout_header.xml`
- **Rationale**: On-device Sprint Review of `ATT-7754` identified three Workout Detail header defects: untinted `48.dp` `SwapHoriz` (`⇄`) in `WorkoutClustersScreen.kt:186-193`, `[⇄][Tune][Edit][Share]` ordering mixing global layout configuration with per-workout actions without visual/tooltip separation, and missing `⇄` outside Lieblingsstrecken plus lacking non-GPS fallback.
1. **`REQ-UI-2786.1` (Uniform Icon Tint & Sizing)**: All top-right Workout Detail header action icons (`Tune`, `SwapHoriz`, `Edit`, `Share`) SHALL render with `tint = MaterialTheme.colorScheme.primary` and `Modifier.size(32.dp)` (`WarningAmber` retains `MaterialTheme.colorScheme.error` at `32.dp`).
2. **`REQ-UI-2786.2` (Global-vs-Unit Ordering, Visual Grouping, Tooltips & Spacer)**: `WorkoutHeader` SHALL expose `onOpenTabsConfig: (() -> Unit)? = null` and `onChangeCluster: (() -> Unit)? = null` ordered left-to-right as `[Tune]` (subtle `TTAlpha.Faint` primary tonal pill + `16.dp` `VerticalDivider`) before per-workout `[⇄][Edit][Share]`, with 9-locale `TooltipBox`/`PlainTooltip` + `contentDescription`s and complete `actionSpacerWidth` reservation across all rendered buttons (`360–411 dp` overlap safety).
3. **`REQ-UI-2786.3` (Universal `⇄` Visibility & Non-GPS Fallback)**: `TrackOnMapScreen` SHALL centrally wire `onChangeCluster` across all 5 callers (`WorkoutSummariesTabbedScreen`, `WorkoutSummariesListFragment`, `PeriodMapScreen`, `WorkoutClustersScreen`, `WorkoutClusterHeatmapScreen`) via `TrackOnMapAftermathViewModel` / `WorkoutClusterRepository`; for indoor/non-GPS workouts (`startLatLng == null || endLatLng == null`), candidates fall back to all clusters, selection invokes `assignClusterToWorkout` (`forceIdentity = true`), `onCreateNew` is hidden (`null`), and `onUnassign` stays available when `clusterId > 0`.
4. **`REQ-UI-2786.4` (Architecture & Test Hygiene)**: `WorkoutRepository.kt` MUST remain untouched (`<= 5,117` LoC); `ArchitectureConformanceContractTest` (`IF-SWE`), `WorkoutSummary`/`snapshotHeader` defaults, `WorkoutDetailHeaderClickContractTest`, `WorkoutHeaderLandscapeTest` (`REQ-UI-433.2`), and `WorkoutClusterSelectionDialog` signature remain intact; new Robolectric UI tests MUST read `0` `src/main` files (`REQ-PRO-049.1`) and Stage 5 SHALL inspect Light/Dark PNG `docs/engineering/walkthroughs/img/ATT-8097_header_actions_preview.png` (`Rule 8.5`).

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Refines `REQ-UI-408.5` (`WorkoutDetailTabbedContent.kt`), `REQ-UI-447` (`WorkoutHeader.kt`), and `REQ-SET-059.4` (`WorkoutClustersScreen.kt` / `WorkoutClusterHeatmapScreen.kt`) via net-new `REQ-UI-2786`.
2. *Historical Origin & Commit Trace*: `ea5fa705` (`ATT-3920`), `ab9bebee` (`ATT-5809`), and `949078cd` (`ATT-7754`).
3. *Root Reason for Existing Formulation*: `headerActions` served as an ad-hoc slot for cluster screens while `WorkoutHeader` decoupled `Edit`/`Share`, omitting `⇄` on `Trainingsliste`/`Perioden` and placing `Tune` after `headerActions`.
4. *Preservation of Core Invariants*: Preserves `WorkoutSummary` overflow menu, clean `snapshotHeader`, `onClicked = null` (`WorkoutDetailHeaderClickContractTest`), `REQ-UI-433.2` landscape row, `WorkoutClusterSelectionDialog` API, and untouched `WorkoutRepository.kt` (`5,117` LoC).

## 2. Acceptance Criteria (Given-When-Then)
- *Given* `TrackOnMapScreen` from any of the 5 entry points, *When* `WorkoutHeader` renders, *Then* `[Tune]` (tonal pill + `VerticalDivider`) precedes `[⇄][Edit][Share]`, all 4 icons are `32.dp` in `primary` tint, and `actionSpacerWidth` prevents title overlap at `360–411 dp`.
- *Given* an indoor/non-GPS workout (`startLatLng == null`), *When* `⇄` is tapped, *Then* `WorkoutClusterSelectionDialog` lists clusters without `onCreateNew`, and selection calls `assignClusterToWorkout`.
- *Given* all 9 `values*/strings_workout_header.xml` files (`EN, DE, ES, FR, IT, JA, NL, PL, PT`), *When* `TranslationParityTest` runs, *Then* 100% parity passes with zero missing keys.

## 3. Test Specification (`TST-UI-2786`) & Localization Plan
- **`TST-UI-2786.1` (`WorkoutDetailHeaderActionsComposeTest.kt`)**: Robolectric `createComposeRule()` (`0` `src/main` reads, `TST-UI-2786` KDoc) asserting `[Tune] < [⇄] < [Edit] < [Share]` horizontal order, `32.dp` bounds, `primary` tint vs `WarningAmber` `error` tint, `360/411 dp` non-overlapping title, `PlainTooltip` scope clarity, and `WorkoutSummary`/`snapshotHeader` omission.
- **`TST-UI-2786.2` (`TrackOnMapClusterActionViewModelTest.kt`)**: Verify central `TrackOnMapScreen` / `TrackOnMapAftermathViewModel` / `WorkoutClusterRepository` `⇄` handling across all 5 callers plus non-GPS fallback (`getAllClustersSync()`, `assignClusterToWorkout`, `onCreateNew == null`, `onUnassign` when `clusterId > 0`).
- **`TST-UI-2786.3` (9-Locale & Architecture)**: Add `values*/strings_workout_header.xml` (`workout_header_tabs_config_tooltip`, `workout_header_change_cluster_tooltip`, `workout_header_edit_tooltip`, `workout_header_share_tooltip`) across 9 locales; run `TranslationParityTest`, `ArchitectureConformanceContractTest`, `SourceTextReadingAuditContractTest`, `WorkoutHeaderLandscapeTest`, `WorkoutClusterSelectionDialogTest`.
- **`TST-UI-2786.4` (`Rule 8.5` Visual)**: Capture and `read_file`-inspect `docs/engineering/walkthroughs/img/ATT-8097_header_actions_preview.png` (Light/Dark `@Preview`) + `emulator-5554` verification.

## 4. Traceability Matrix
| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-2786.1` | Compose UI (`0` reads) | `WorkoutHeader`, `WorkoutDetailTabbedContent` | `REQ-UI-2786.1`, `.2`, `.4` | Specified |
| `TST-UI-2786.2` | ViewModel / Repo | `TrackOnMapScreen`, `TrackOnMapAftermathViewModel`, `WorkoutClusterRepository` | `REQ-UI-2786.3`, `.4` | Specified |
| `TST-UI-2786.3` | 9-Locale & Contracts | `strings_workout_header.xml`, `ArchitectureConformanceContractTest` | `REQ-UI-2786.2`, `.4` | Specified |
| `TST-UI-2786.4` | Visual (`Rule 8.5`) | `ATT-8097_header_actions_preview.png`, `emulator-5554` | `REQ-UI-2786.1`–`.4` | Specified |
