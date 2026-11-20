# Walkthrough — ATT-9067: Fix Workout Details Map Round Layers Icon Gesture Isolation & Hoist `TrackLayersMenuButton`

- **Jira Ticket**: `ATT-9067` (`1 SP`, Bug, `severity-minor`, Epic `ATT-68`)
- **Living Specs**: `REQ-REV-016` (`REQ-REV-016.1`..`REQ-REV-016.2`) & `TST-REV-017` (`TST-REV-017.1`..`TST-REV-017.2`) — `[Verified]`
- **Code Commit (Gate 4 Certified)**: `1c1df08e` (`ATT-9334`) on `feature/ATT-9067` (`Rule 7.5` doc-only Stage 5: `git diff 1c1df08e..HEAD -- app/` is empty)
- **Localization**: `Localization Parity: N/A - no new or modified UI strings`

## 1. Root Cause & Implemented Fix
1. **Root Cause (`MapDetailLayout.kt` + `TrackOnMapScreen.kt`)**:
   - While `ATT-9168` (`ba5e86e4`) resolved `TopEnd` `snapshotHeader` occlusion for workouts with `recordingErrors`, in live `MapDetailLayout.kt` (`mapBox`), `Modifier.pointerInput(Unit)` emitting `onInteractiveCanvasTouch(true)` on `PointerEventPass.Initial` wrapped the outer `Box` enclosing **both** `ATrainingTrackerMap` and `FLOATING CONTROLS` (`overlay()` + `topEndActions`), coupling taps on the round Layers button inside `WorkoutDetailTabbedContent` (`HorizontalPager`) to mid-gesture `userScrollEnabled` flips.
   - `TrackOnMapScreen.kt` also duplicated the inline `showTrackMenu` `Surface` + `DropdownMenu` block across tabbed (`mapTopEndActions`) and fallback (`topEndActions`) paths without `testTag`s or behavioral Compose UI test coverage.
2. **Floating Controls Gesture & Z-Order Isolation (`MapDetailLayout.kt`, `1,113 / 1,500` LoC — `REQ-REV-016.1`)**:
   - Moved `onInteractiveCanvasTouch(true)` onto a `Box` wrapping **strictly** `ATrainingTrackerMap`, preserving 2D map-pan gesture isolation (`REQ-UI-408.4`).
   - Added `.zIndex(1f)` to the `FLOATING CONTROLS` `Box` (without attaching `pointerInput` to the full-size container, so empty map touches still pass cleanly to `ATrainingTrackerMap`) and attached a non-consuming `PointerEventPass.Initial` `onInteractiveCanvasTouch(false)` listener strictly to the wrap-content `topEndActions` `Column`.
3. **Deduplicated `TrackLayersMenuButton` (`TrackOnMapScreen.kt`, `1,013 / 1,500` LoC, `-17` LoC — `REQ-REV-016.2`)**:
   - Extracted `internal TrackLayersMenuButton` + `TrackOnMapTestTags` (`track_layers_button`, `track_layers_menu`, `track_layers_item_<TrackType.name>`), replacing both inline blocks while preserving `controlsTopOffsetDp` (`REQ-UI-491`), `44.dp` `CircleShape` overlay styling (`REQ-UI-500.5`), `TrackType.entries` (`BEST`, `GPS`, `FUSED`, `ATT_FUSED`), and `enabledTrackTypes` persistence (`REQ-MAP-013`).

## 2. Verified Test Evidence (`103/103 PASS` across 25 Suites @ Gate 4 `1c1df08e`)
*Scope: Tier 1 targeted behavioral Compose UI, layout/contract, ViewModel, and build-audit suites executed under `tools/parallel_sprint_lock.py` (`--rerun`) at Gate 4 (`ATT-9334` @ `1c1df08e`) with `0` compiler warnings on `ATT-9067` files and `4/4` Python safety linters PASS (`app/` unchanged since `1c1df08e`).*

| Test Suite(s) | Result | Key `TST-REV-017` Verbatim Methods / Checks |
| :--- | :---: | :--- |
| `TrackOnMapLayersUiTest` | 3/3 PASS | `TrackOnMapLayersUiTest#tabbedMap_trackLayersButtonTapOpensMenuAndTogglesTrackWithoutLockingPager_tstRev017_1`, `TrackOnMapLayersUiTest#mapDetailLayout_isolatesFloatingControlsFromCanvasTouchCallback_tstRev017_1`, `TrackOnMapLayersUiTest#nonTabbedFallback_trackLayersButtonOpensMenuAndTogglesTrack_tstRev017_2` (`@GraphicsMode(NATIVE)`, `v2.createComposeRule`, `0` deprecation warnings) |
| `MapDetailLayoutOffscreenHeaderUiTest` | 4/4 PASS | `REQ-REV-015` offscreen `snapshotHeader` isolation & capture parity |
| `MapDetailLayoutTest`, `MapDetailLayoutCollapsingHeaderContractTest`, `MapDetailLayoutSnapshotContractTest`, `MapDetailLayoutElevationVisibilityUiTest` | 26/26 PASS | `MapDetailLayout` stacking (`REQ-UI-500`), collapsing header (`12/12`, `8/8`, `5/5`, `1/1`) |
| `WorkoutDetailTabbedContentTest`, `WorkoutDetailTabsTest`, `WorkoutSectionReorderContractTest`, `WorkoutShareBottomSheetContractTest`, `TracklessAftermathVisualContractTest`, `TelemetryMetricGraphMarginalBarsContractTest` | 12/12 PASS | Tabbed container, `mapTopEndActions` forwarding, share & chart contracts (`7/7`, `1/1` x5) |
| `TrackOnMap*` (`ScreenDetailPreferencesContractTest`, `ScreenLapRoutingContractTest`, `ScreenMetadataRoutingContractTest` x2, `ScreenSectionSlottingContractTest`, `AftermathViewModelTest`, `AftermathViewModelFallbackTest`, `ClusterActionViewModelTest`, `ViewModelTelemetryTest`) | 31/31 PASS | `TrackOnMapScreen` slotting & `TrackOnMapAftermathViewModel` track toggling/persistence (`1/1`, `4/4`, `3/3`, `2/2`, `3/3`, `10/10`, `2/2`, `4/4`, `2/2`) |
| `ArchitectureConformanceContractTest`, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest`, `UiReflectionOnlyTestAuditContractTest` | 27/27 PASS | `MapDetailLayout.kt = 1,113/1,500`, `TrackOnMapScreen.kt = 1,013/1,500`, `0` dead code, `0` `src/main` reads, `REQ-PRO-132` (`11/11`, `6/6`, `6/6`, `4/4`) |

- **Safety & Governance**: `check_secrets_hygiene.py`, `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, `verify_requirement_governance.py --sync-indexes`, `--base-ref refs/heads/sprint/2026-11-20_02_Spät`, and `--check-verified-evidence --test-results-dir /tmp/empty_check_ATT-9067_s5` all pass with **EXIT 0**.
