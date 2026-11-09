# Stage 3 Implementation Plan - ATT-6208: WorkoutShareBottomSheet Automated Map Snapshot & Share

## 1. Metadata & Invariants
* **Ticket Key**: ATT-6208
* **Target Release**: V5.0.0
* **Active Sprint**: 2026-45.13
* **Requirement Mapping**: REQ-PER-029 (`REQ-PER-029.1`..`REQ-PER-029.4`)
* **Test Mapping**: TST-PER-037 (`TST-PER-037.1`..`TST-PER-037.5`)
* **Critical Invariant (ATT-6214 / REQ-UI-500)**: Preserve `showFloatingShareButton: Boolean = workoutData == null` in `MapDetailLayout.kt` and `top = 16.dp` floating Layers FAB positioning.

## 2. Architectural Design (SWE.2) & Dynamic Sequence Flow
```
User selects Option A in WorkoutShareBottomSheet
  ├── [WorkoutDetailTabbedContent] ──> Switch to mapTabIndex ──> delay(350ms) ──> externalSnapshotTrigger=true
  └── [WorkoutSummary (List)] ────────> onShareMapSnapshot ────> initialAutoShareSnapshot=true ──> TrackOnMapScreen ──> (same handoff)
        │
        ▼
   MapDetailLayout (isSharing=true) ──> ATrainingTrackerMap.snapshot ──> combineWorkoutAndShare ──> Intent.ACTION_SEND
        │
        ▼
   onSnapshotComplete() ──> externalSnapshotTrigger=false, isSharing=false
```

## 3. Atomic Implementation Steps
| Step | Action & File | Description | Verification |
| :--- | :--- | :--- | :--- |
| **1** | Edit `MapDetailLayout.kt` | Add `externalSnapshotTrigger: Boolean = false` and `onSnapshotComplete: (() -> Unit)? = null`. Wire `LaunchedEffect(externalSnapshotTrigger)` to set `isSharing = true`. Call `onSnapshotComplete?.invoke()` after `combineWorkoutAndShare` / error. Preserve `showFloatingShareButton: Boolean = workoutData == null` intact. | `TST-PER-037.1`, `TST-PER-037.4` |
| **2** | Edit `WorkoutDetailTabbedContent.kt` | Add `initialAutoShareSnapshot: Boolean = false`. Maintain `triggerMapSnapshot` and `pendingAutoShare`. When Option A is selected or `pendingAutoShare == true`, scroll/switch pager to `mapTabIndex`, wait 350-400ms settlement delay, and set `triggerMapSnapshot = true`. Forward `externalSnapshotTrigger = (pageIndex == mapTabIndex) && triggerMapSnapshot` and `onSnapshotComplete = { triggerMapSnapshot = false }`. | `TST-PER-037.2` |
| **3** | Edit `TrackOnMapScreen.kt` | Accept `initialAutoShareSnapshot: Boolean = false` and pass to `WorkoutDetailTabbedContent`. | `TST-PER-037.2` |
| **4** | Edit `WorkoutSummary.kt`, `WorkoutList.kt`, `WorkoutTabsScreen.kt` | Add `onShareMapSnapshot: ((WorkoutData) -> Unit)?`. In `WorkoutSummary`, Option A selection invokes `onShareMapSnapshot ?: onMapClick`. | `TST-PER-037.3` |
| **5** | Edit `WorkoutSummariesTabbedScreen.kt` & `WorkoutSummariesListFragment.kt` | Track `autoShareOnDetailOpen: Boolean`. Wire `onShareMapSnapshot` to set `selectedWorkout = workoutData.id`, `autoShareOnDetailOpen = true`, and pass to `TrackOnMapScreen`. | `TST-PER-037.3` |
| **6** | Update & Expand Contract Tests | Add tests in `WorkoutShareBottomSheetContractTest.kt` verifying `externalSnapshotTrigger`, `onSnapshotComplete`, `initialAutoShareSnapshot`, and invariant audit. | `TST-PER-037.1`..`TST-PER-037.4` |
| **7** | Tier 1 Unit Test & Governance Verification | Execute targeted tests and verify living documentation parity. | `TST-PER-037.5` |

## 4. UI Consistency & Theme Conformance
* **Reused Components**: `AppModalBottomSheet`, `ShareModeOptionCard`, `MapDetailLayout`, `WorkoutHeader`.
* **Tokens**: Reuses existing `TTAlpha.Overlay`, `MaterialTheme.colorScheme.primary`, and standard 16.dp padding. No new one-off UI styles.
* **Layout Invariants**: Preserves `showFloatingShareButton: Boolean = workoutData == null` and ensures top-end action stacking (`Arrangement.spacedBy(16.dp)`) per `REQ-UI-500`.

## 5. Verification Commands
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"
python3 tools/verify_requirement_governance.py
```
