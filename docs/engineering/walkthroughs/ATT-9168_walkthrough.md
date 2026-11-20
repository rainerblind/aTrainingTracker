# Walkthrough — ATT-9168: Fix Invisible Offscreen `WorkoutHeader` Intercepting Clicks in `MapDetailLayout`

- **Jira Ticket**: `ATT-9168` (`1 SP`, Bug, `severity-major`, Epic `ATT-68`)
- **Living Specs**: `REQ-REV-015` (`REQ-REV-015.1`..`REQ-REV-015.2`) & `TST-REV-016` (`TST-REV-016.1`..`TST-REV-016.2`) — `[Verified]`
- **Code Commit (Gate 4 Bounce 1 Certified)**: `408d9fb0` (`ATT-9314`) on `feature/ATT-9168` (`Rule 7.5` doc-only Stage 5 Bounce 1: `git diff 408d9fb0..HEAD -- app/` is empty)
- **Localization**: `Localization Parity: N/A - no new or modified UI strings`

## 1. Root Cause & Implemented Fix (`MapDetailLayout.kt`, `1,096 / 1,500` LoC)
1. **Root Cause**: All 3 tabbed modes in `WorkoutDetailTabbedContent.kt` pass `showHeader = false` and `header = snapshotHeader` (`WorkoutHeader`) to `MapDetailLayout`. Previously, `MapDetailLayout` always composed `header()` inside its `.zIndex(1f)` upper `Column` using `layout(0, 0) { placeable.place(0, 0) }` and `drawWithContent { headerLayer.record { ... } }` (without `drawContent()`), leaving an invisible `WorkoutHeader` (with `WorkoutRouteClusterBadge`'s clickable modifier even when `onClusterClick == null`) sitting directly on top of `Sichtbare Diagramme` and the `Tempo` multi-source layers icon on the `Übersicht` tab, while leaking duplicate nodes into TalkBack.
2. **On-Demand Non-Interactive Offscreen Capture (`REQ-REV-015.1` — Bounce 1 Fix)**:
   - Removed the always-composed `else` block from inside the `.zIndex(1f)` `Column`.
   - Composed the offscreen `snapshotHeader` capture `Box` strictly when `!showHeader && (isSharing || externalSnapshotTrigger)` **outside** `.zIndex(1f)` directly inside `BoxWithConstraints` (avoiding `HorizontalPager` draw-culling), with `.fillMaxWidth()`, `.zIndex(-1f)`, `.clearAndSetSemantics {}`, and `.layout { measurable, layoutConstraints -> val placeable = measurable.measure(layoutConstraints.copy(minHeight = 0)); layout(0, 0) { placeable.place(0, -placeable.height - 10_000) } }`.
   - **Bounce 1 Pointer Isolation**: Moved `.pointerInput(Unit)` (`awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }`) **after** `.layout(0, 0)` onto the inner full-width `Box` wrapping `header()`, ensuring coordinator hit-test bounds match `fullWidth x headerHeight` instead of `(0, 0)` even over transparent foreground layers, while empirically confirming `headerLayer.record { this@drawWithContent.drawContent() }` executes when Robolectric's native draw pass runs (`onRoot().captureToImage()`) even inside `.clipToBounds()` and at `placeable.place(0, -placeable.height - 10_000)`.
3. **Frame-Synchronized `GraphicsLayer` Capture (`REQ-REV-015.2`)**:
   - Added `isHeaderLayerRecorded` (set in `drawWithContent` after `headerLayer.record { this@drawWithContent.drawContent() }`) and `readyToTakeMapSnapshot` synchronized in `LaunchedEffect(isSharing, externalSnapshotTrigger, showHeader, isHeaderLayerRecorded)` with `withFrameNanos { }`, resetting both cleanly when idle and gating `ATrainingTrackerMap(shouldTakeSnapshot = readyToTakeMapSnapshot)` across both `onSelectMapSnapshot` and `externalSnapshotTrigger` flows.

## 2. Verified Test Evidence (`77/77 PASS` across 12 Suites @ Gate 4 Bounce 1 `408d9fb0`)
*Scope: Tier 1 targeted behavioral Compose UI, snapshot contract, and architecture/audit suites executed under `tools/parallel_sprint_lock.py` (`--rerun`) at Gate 4 Bounce 1 (`ATT-9314` @ `408d9fb0`) with `0` compiler warnings on `ATT-9168` files and `4/4` Python safety linters PASS (`app/` unchanged since `408d9fb0`).*

| Test Suite | Result | Key `TST-REV-016` Verbatim Methods / Checks |
| :--- | :---: | :--- |
| `MapDetailLayoutOffscreenHeaderUiTest` | 4/4 PASS | `idleShowHeaderFalse_allowsTempoLayersClickAndSuppressesDuplicateHeaderSemantics_tstRev016_1`, `idleShowHeaderFalse_allowsSichtbareDiagrammeClick_tstRev016_1`, `onDemandSnapshotTrigger_recordsFullWidthHeaderInsideClippedContainerWithoutSemanticsLeak_tstRev016_2`, `activeSnapshotWithTransparentForeground_blocksPointerClicksOnOffscreenHeader_tstRev016_2` (`v2.createComposeRule`, `onRoot().captureToImage()`, `0` deprecation warnings) |
| `MapDetailLayoutSnapshotContractTest` | 5/5 PASS | `executeSnapshotCaptureAndShare` UI-thread `GraphicsLayer` rasterization & `NonCancellable` cleanup |
| `MapDetailLayoutElevationVisibilityUiTest` | 1/1 PASS | Behavioral visibility non-regression |
| `MapDetailLayoutCollapsingHeaderContractTest` | 8/8 PASS | Collapsing header & viewport clearance contracts |
| `MapDetailLayoutTest` | 12/12 PASS | Layout structural contracts |
| `WorkoutShareBottomSheetContractTest` | 12/12 PASS | Option A snapshot trigger contracts |
| `WorkoutDetailHeaderClickContractTest` | 1/1 PASS | Header click contract |
| `WorkoutDetailTabbedContentTest` | 7/7 PASS | Tabbed layout mode & pager contract |
| `ArchitectureConformanceContractTest` | 11/11 PASS | `MapDetailLayout.kt = 1,096 / 1,500` LoC; `0` protected monolith edits |
| `DeadCodeEliminationContractTest` | 6/6 PASS | `0` dead code violations |
| `SourceTextReadingAuditContractTest` | 6/6 PASS | `0` `src/main` file reads in tests |
| `UiReflectionOnlyTestAuditContractTest` | 4/4 PASS | Real `v2.createComposeRule` behavioral UI test compliance (`REQ-PRO-132`) |

- **Safety & Governance**: `check_secrets_hygiene.py`, `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, `verify_requirement_governance.py --sync-indexes`, `--base-ref "$BASE"`, and `--check-verified-evidence --test-results-dir /tmp/empty_check_ATT-9168_s5b1` all pass with **EXIT 0**.
