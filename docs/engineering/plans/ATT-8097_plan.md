# Stage 3: Implementation Plan - ATT-8097: Unify Workout Detail Header Action Icons

**DOC-ID**: `DOC-PLN-ATT-8097` | **Ticket**: [ATT-8097](https://atrainingtracker.atlassian.net/browse/ATT-8097)  
**Parent Epic**: [ATT-176](https://atrainingtracker.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*) | **Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-11-18_01_Früh` | **Requirement Mapping**: `REQ-UI-2786` | **Test Mapping**: `TST-UI-2786`  
**Branch**: `feature/ATT-8097` | **Author**: `--as agent1` | **Date**: 2026-11-18

---

## 1. Problem Description & Traceability
* **Scope**: Unify Workout Detail top-right action icons (`Tune`, `SwapHoriz`/`⇄`, `Edit`, `Share`) on uniform `32.dp` `MaterialTheme.colorScheme.primary` styling, reorder `[Tune]` (global display pill + `16.dp` `VerticalDivider`) before per-workout `[⇄][Edit][Share]` with 9-locale tooltips and complete `actionSpacerWidth` reservation (`360–411 dp`), and wire `⇄` consistently across all 5 `TrackOnMapScreen` entry points with indoor/non-GPS fallback.
* **Traceability**: `REQ-UI-2786` (`.1`–`.4`, refining `REQ-UI-408.5`, `REQ-UI-447`, `REQ-SET-059.4`) $\leftrightarrow$ `TST-UI-2786` (`.1`–`.4`).

## 2. System Invariants & Preserved Behavior
1. **Untouched `WorkoutRepository.kt` (`<= 5,117` LoC)**: Zero edits to `WorkoutRepository.kt`; cluster orchestration lives in `WorkoutClusterRepository.kt` & `TrackOnMapAftermathViewModel.kt` (`IF-SWE` / `ArchitectureConformanceContractTest` clean).
2. **Non-Detail Header Preservation**: `WorkoutSummary` list cards (`menuEnabled = true`, `onOpenTabsConfig = null`, `onChangeCluster = null`) and `snapshotHeader` remain free of `Tune` and `⇄`.
3. **Existing Contracts Intact**: `WarningAmber` retains `MaterialTheme.colorScheme.error` at `32.dp`; `WorkoutDetailHeaderClickContractTest` (`onClicked = null`), `WorkoutHeaderLandscapeTest` (`REQ-UI-433.2`), `WorkoutHeaderExportMenuContractTest` (`fitWrite`), and `WorkoutClusterSelectionDialog` public binary signature remain unaltered.
4. **Test & Locale Hygiene**: New Robolectric UI tests perform `0` `src/main` reads (`REQ-PRO-049.1`); 9-locale parity in `values*/strings_workout_header.xml`; `tools/*` untouched.

## 3. Proposed SWE.2/SWE.3 Architecture & UI Consistency
* **Component 1 — `WorkoutHeader.kt` & `WorkoutDetailTabbedContent.kt` (UI Chrome)**:
  * Add `onOpenTabsConfig: (() -> Unit)? = null` and `onChangeCluster: (() -> Unit)? = null` to `WorkoutHeader(...)`.
  * Render left-to-right: `[Tune]` inside a subtle `Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = TTAlpha.Faint), shape = RoundedCornerShape(8.dp))` global-display pill + `VerticalDivider(Modifier.height(16.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)` (only when `onOpenTabsConfig != null` and at least one trailing workout action is present), followed by `[SwapHoriz]` (`⇄`), `[WarningAmber]` (`error` tint), `[Edit]`, `[Share]`, `[MoreVert]`, and `actions()`. All 4 action `IconButton`s enforce `Modifier.size(32.dp)` and `tint = MaterialTheme.colorScheme.primary`.
  * Wrap `Tune`, `⇄`, `Edit`, `Share` in M3 `TooltipBox(TooltipDefaults.rememberTooltipPositionProvider(), PlainTooltip { Text(...) }, rememberTooltipState())` using 9-locale `strings_workout_header.xml` resources (`workout_header_tabs_config_tooltip`, `workout_header_change_cluster_tooltip`, `workout_header_edit_tooltip`, `workout_header_share_tooltip`) + matching `contentDescription`s.
  * Update portrait `actionSpacerWidth` (`L424`) to sum `onOpenTabsConfig` (`36.dp` + `12.dp` pill/divider clearance), `onChangeCluster` (`36.dp`), `recordingErrors` (`36.dp`), `onEditWorkout` (`36.dp`), `onShareWorkout` (`36.dp`), and `menuEnabled` (`36.dp`) so `titleLarge` never collides on `360–411 dp` viewports.
* **Component 2 — `WorkoutClusterRepository.kt`, `TrackOnMapAftermathViewModel.kt`, `TrackOnMapScreen.kt` (Universal `⇄` + Non-GPS Fallback)**:
  * `WorkoutClusterRepository`: Add `getCandidateClustersForWorkout(workout)` (returns `WorkoutClusterEngine.getClusterScores(...)` when `startLatLng != null && endLatLng != null && maxDisplacementLatLng != null`, else `getAllClustersSync().map { it to Double.NaN }`), `moveOrAssignWorkoutToCluster(workout, targetClusterId)` (calls `moveWorkoutToCluster` if spatial fingerprint exists, else `assignClusterToWorkout(..., forceIdentity = true)`, then `refreshClusters()` + `notifyWorkoutRepository { it.reloadWorkoutData(workout.id) }`), `createNewClusterFromWorkout(workout)`, and `unassignWorkoutFromCluster(workoutId)`.
  * `TrackOnMapAftermathViewModel`: Expose `clusterCandidates: StateFlow<List<Pair<WorkoutCluster, Double>>>`, `loadCandidateClustersForWorkout(workout)` (`ioDispatcher`), `moveOrAssignWorkoutToCluster(...)`, `createNewClusterFromWorkout(...)`, `unassignWorkoutFromCluster(...)`, `getSportName`, `getBSportType`.
  * `TrackOnMapScreen`: Accept optional `onChangeCluster: (() -> Unit)? = null`. When `null`, supply built-in `onChangeCluster` opening `WorkoutClusterSelectionDialog` (`onCreateNew = null` when `!workoutData.hasSpatialFingerprint`, `onUnassign` when `clusterId > 0`). Update `WorkoutClusterSelectionItem` to omit score brackets when `score.isNaN()`.
  * Migrate `WorkoutClustersScreen.kt` & `WorkoutClusterHeatmapScreen.kt` from ad-hoc `headerActions` to `onChangeCluster` (preserving screen-local peek/list refresh and `WorkoutClustersViewModel` non-GPS fallback), automatically giving `WorkoutSummariesTabbedScreen`, `WorkoutSummariesListFragment`, and `PeriodMapScreen` identical `⇄` behavior.
* **UI Consistency (`docs/design_guidelines.md` §5)**: Reuses `WorkoutHeader`, `WorkoutDetailTabbedContent`, `WorkoutClusterSelectionDialog` (`AppModalBottomSheet`), `TooltipBox`/`PlainTooltip` (`StravaActivitySection.kt`), `VerticalDivider` (`ForkDecisionCard.kt`), `TTAlpha.Faint`, `RoundedCornerShape(8.dp)`, and `MaterialTheme.colorScheme.primary`. Zero new `docs/architecture.md` subsystems (`Rule 8.8` N/A).

## 4. Atomic Implementation Sequence (Stage 4)
1. **9-Locale Tooltip Strings (`Rule 9.4`)**: Create `app/src/main/res/values*/strings_workout_header.xml` across all 9 locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`) with the 4 `workout_header_*_tooltip` keys.
2. **Repository & ViewModel Layer**: Add cluster candidate/move-or-assign/create/unassign methods to `WorkoutClusterRepository.kt` (plus `WorkoutClustersViewModel.kt` non-GPS fallback) and `TrackOnMapAftermathViewModel.kt` (no `WorkoutRepository.kt` edits).
3. **Header & Detail Screen Wiring**: Update `WorkoutHeader.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutClusterComponents.kt` (`Double.NaN` score hide), `TrackOnMapScreen.kt`, `WorkoutClustersScreen.kt`, and `WorkoutClusterHeatmapScreen.kt`. Add Light/Dark multi-state `@Preview` in `WorkoutHeader.kt` covering `[Tune] | [⇄][Edit][Share]` on `360 dp`/`411 dp`.
4. **Tests & `Rule 8.5` Preview Capture**: Add `WorkoutDetailHeaderActionsComposeTest.kt` (`0` `src/main` reads, `TST-UI-2786` tag) and `TrackOnMapClusterActionViewModelTest.kt`; capture Light/Dark PNG `docs/engineering/walkthroughs/img/ATT-8097_header_actions_preview.png`.
5. **Tier 1 Targeted Verification**:
   `./gradlew :app:testDebugUnitTest --tests "*.WorkoutDetailHeaderActionsComposeTest" --tests "*.TrackOnMapClusterActionViewModelTest" --tests "*.WorkoutDetailTabbedContentTest" --tests "*.WorkoutHeaderLandscapeTest" --tests "*.WorkoutHeaderExportMenuContractTest" --tests "*.WorkoutDetailHeaderClickContractTest" --tests "*.WorkoutClusterSelectionDialogTest" --tests "*.TranslationParityTest" --tests "*.ArchitectureConformanceContractTest" --tests "*.SourceTextReadingAuditContractTest"`
   Plus Stage 5 `emulator-5554` verification & `read_file` PNG inspection.

## 5. Verification & Rollback Plan
* **Verification**: Targeted unit/contract/Robolectric Compose suite + `TranslationParityTest` in Stage 4; Light/Dark PNG `read_file` audit + `emulator-5554` UI dump check in Stage 5.
* **Rollback**: Isolated on `feature/ATT-8097`; revert cleanly without affecting `sprint/2026-11-18_01_Früh` or `develop`.
