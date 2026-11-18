# Stage 5: Walkthrough & Verification - ATT-8736: Seed PathPreviewMap Camera Bounds Immediately and Memoize Track Decoding in WorkoutSummary

**Ticket**: [ATT-8736](https://atrainingtracker.atlassian.net/browse/ATT-8736) (`2 SP`)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_02_Spät` (ID 460)  
**Requirement Mapping**: `REQ-MAP-068` (`REQ-MAP-068.1`–`REQ-MAP-068.4`) | **Test Mapping**: `TST-MAP-071` (`TST-MAP-071.1`–`TST-MAP-071.5`)  
**System Requirement(s)**: `SYSREQ-TRK-004` | **Branch**: `feature/ATT-8736` (`50ea4121`, rebased onto `sprint/2026-11-18_02_Spät`)  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-18

---

## 1. Executive Summary & Verification Overview
Eliminated the `(0.0, 0.0), zoom = 0f` world-map flash, zero-size stuck-zoom failures, and redundant main-thread track decoding on workout summary preview maps:
* **Synchronous Bounds & Camera Seeding (`REQ-MAP-068.1`, `PathPreviewMap.kt`)**: Added `buildValidatedBounds`, `validateBounds`, `resolvePreviewBounds` (priority: `targetBounds` -> `MappablePath` bounds -> composite `allPaths`/`start`/`end`/`apex`), aspect-aware Web Mercator `estimateInitialCameraPosition` (clamped `[1f, 17f]`, `15f` single-point guard, antimeridian support), and `createLiteModeMapOptions` seeding both `GoogleMapOptions().liteMode(true).camera(...)` and `rememberCameraPositionState`.
* **`onSizeChanged` & `MapEffect` Retry (`REQ-MAP-068.2`, `PathPreviewMap.kt`)**: Replaced `isMapLoaded`-gated `LaunchedEffect` with `Modifier.onSizeChanged` (avoiding `BoxWithConstraints`/`SubcomposeLayout`) + `MapEffect` applying `newLatLngBounds(..., 20)` immediately with bounded `IllegalStateException` retry (`MAX_CAMERA_RETRY_ATTEMPTS = 3`), plus `LocalInspectionMode` schematic preview and `private` Light/Dark `@Preview`s.
* **9-Key Memoization & Bounds Forwarding (`REQ-MAP-068.3`, `WorkoutSummary.kt`)**: Extracted `WorkoutPathPreviewMap` shared by `WorkoutSectionType.MAP` and `WorkoutMediaSection`, memoizing `toMapTrack()` across all 9 read fields (`id`, `bSportType`, `mapPolyline`, `encodedAltitudes`, `encodedDistances`, `minLat`, `minLng`, `maxLat`, `maxLng`) and forwarding validated `targetBounds`.
* **Preserved Contracts (`REQ-MAP-068.4`)**: Preserved `liteMode(true)`, `DarkMapStyle`/`DarkMapAntiFlashOverlay`, `RouteItem.kt` (`targetBounds = null`), `WorkoutClusterSplitMapPreviewTest` / `WorkoutLapsIconContractTest` tokens, `WorkoutRepository.kt <= 5,117` LoC untouched, `0` UI string changes (`Localization Parity: N/A - no UI strings`), and cleaned 6 obsolete `PathPreviewMap.kt` entries in `app/detekt-baseline.xml`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-068.1` | `TST-MAP-071.1`, `TST-MAP-071.2` | `PathPreviewMapBoundsTest` (bounds validation, Mercator zoom, Lite Mode options) | **PASSED** (8/8) | `Verified` |
| `REQ-MAP-068.2` | `TST-MAP-071.3` | `PathPreviewMapBoundsTest` (`onSizeChanged`, `MapEffect` retry, `IntrinsicSize.Min`) | **PASSED** (8/8) | `Verified` |
| `REQ-MAP-068.3` | `TST-MAP-071.4` | `WorkoutSummaryMapMemoizationTest` (9-key `remember`, `targetBounds` forwarding) | **PASSED** (2/2) | `Verified` |
| `REQ-MAP-068.4` | `TST-MAP-071.5` | Stage 4 contract suite + Stage 5 emulator & `Rule 8.5` PNG inspection | **PASSED** | `Verified` |

---

## 3. Automated Test & Emulator Verification Evidence

### Tier 1 Targeted Unit Tests (Executed on unchanged `app/` code; byte-identical to `29537a21` per `Rule 7.5`)
* **Command**:
  `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.PathPreviewMapBoundsTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummaryMapMemoizationTest"`
* **Result**: `BUILD SUCCESSFUL in 6m 51s` — **10/10 PASSED**, 0 failed, 0 skipped (`PathPreviewMapBoundsTest`: 8/8; `WorkoutSummaryMapMemoizationTest`: 2/2).
* *Scope Note*: Targeted Tier 1 subset only (Stage 4 also ran map & governance contract suites: 64/65 PASS, with 1 pre-existing unrelated failure tracked in `ATT-8823` on `EditDeviceDialog.kt` inside `DeadCodeEliminationContractTest.testComposePreviewsVisibilityAudit`); full `:app:testDebugUnitTest` executes at Tier 2 Sprint Integration Gate (`Rule 7.5`).

### Emulator, Visual & Stage 6 Bounce 1/3 Governance Verification (`Rule 7.6`, `Rule 8.5`)
* **Device**: `emulator-5554` (`Pixel_8_Android_15`, `com.atrainingtracker.debug` via `ANDROID_SERIAL=emulator-5554 ./gradlew installDebug`).
* **On-Device Check**: Imported `tools/sample_workout.tcx` and navigated via `uiautomator dump` to `Einheiten` (`WorkoutSummary` card); verified `PathPreviewMap` renders centered & zoomed on the Olympiapark München loop with start/end/apex markers and zero `logcat -b crash -d` entries.
* **Screenshots (`read_file` verified)**:
  * `docs/engineering/walkthroughs/img/ATT-8736_emulator_workout_list.png` (live `emulator-5554` `Einheiten` card)
  * `docs/engineering/walkthroughs/img/ATT-8736_path_preview_map.png` (Light & Dark `@Preview` rendering)
* **Bounce 1/3 Remediation (`50ea4121`)**: Rebased cleanly onto `sprint/2026-11-18_02_Spät` (`bbd6c333`), dropped collateral shard edits (`req_tracking_cockpit_part2.md`, `tst_tracking_cockpit.md`), stacked `REQ-MAP-068` (`1.0.3`–`1.0.4`) & `TST-MAP-071` (`1.0.3`) `[Verified]` Change History rows cleanly after `ATT-8201`, verified `git merge-tree --write-tree` (`exit 0`, zero conflicts) and `verify_requirement_governance.py --repo-root .` (`exit 0`).
