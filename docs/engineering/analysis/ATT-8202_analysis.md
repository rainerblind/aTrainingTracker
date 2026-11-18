# Stage 1 Analysis: ATT-8202 - SWE.2 Heap/Cache/Map Budgets & IF-SWE-05 Upward-Import Guard

**Ticket**: [ATT-8202](https://rainerblind.atlassian.net/browse/ATT-8202) | **Sub-task**: `[Analysis]` (assigned on `submit-stage`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8202`  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-18

---

## 1. Problem Statement & Motivation
`docs/architecture.md` §4.9 quantifies heap (`< 40 MB`, `REQ-NFR-005`) solely for `TrackerService`, omitting architectural memory/cache/instance bounds for singleton repositories (`WorkoutRepository`, `PeriodsRepository`, `WorkoutClusterRepository`, `RadarStateRepository`) and `GoogleMap` instances (interactive vs `liteMode`). Additionally, `docs/architecture/interfaces.md` (`IF-SWE-05`) mischaracterizes `WorkoutClusterEngine` as a stateless pure-JVM calculator, omits Swim/RadarLight/TPMS/Polar PFTP components, and `ArchitectureConformanceContractTest.kt:55` fails to catch non-UI-suffix upward imports (`database -> ui.aftermath`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification (against `0e294feb`)**:
  * **Heap & Map Budgets**: Confirmed `WorkoutRepository.kt` (`5,117` LoC — at `MAX_MONOLITH_FILE_LOC_BASELINE` ceiling) retains `_allWorkouts` (strings only, no eager `LatLng` lists) + `MAX_OVERLAY_POINTS = 800`, yet `recentHrvThresholdsCache` (`ConcurrentHashMap`, line 1850) is unbounded. `PeriodsRepository.kt` (`1131-1132`) already nulls `workoutIdToPathMap`/`workoutIdToAllPolylinesMap`, caps `polylines` at `MAX_SUMMARY_THUMBNAIL_TRACKS = 8` (`PeriodData.kt:89`), `workoutIdToPolylineMap` at `<= 5` anchors, `recentlyFinishedWorkoutIds` at `50`, and `PeriodMapUtils.kt:67` uses `LruCache(64)` + `MAX_CARD_MINI_MAP_VERTICES = 2_000` / `MAX_DETAILED_MAP_VERTICES = 8_000`. `RadarStateRepository` is bounded by `RadarSource` enum (`<= 4`). `GoogleMap` uses `liteMode(true)` in `PeriodSummaryCard` (`!hasHeatmap`), `WorkoutClusterComponents:689`, `KnownLocationsScreen:751`, `PathPreviewMap:80`, and interactive GL in `ATrainingTrackerMap` (`beyondViewportPageCount = 0` in `TrackingTabsScreen:920`) and 4 modal/detail maps (`EditKnownLocationDialog`, `ManualClusterScreen`, `LapEditBottomSheet`, `ImportBackupTabsScreen`). None of these bounds are specified in `docs/architecture.md` §4.9.
  * **`IF-SWE-05` & Missing Subsystems**: Confirmed `WorkoutClusterEngine.kt:16-60` lives in `database/`, holds `appContext`/`clusterLock`, mutates SQLite via `WorkoutClusterRepository`, and imports `ui.aftermath.WorkoutRepository` across 15 call sites (`lines 81..1439`). Confirmed Swim (`SwimWorkoutAdjustCard`, `SwimmingSection`, `WorkoutRepository.recalculateSwimWorkout`), RadarLight (`RadarLightController`, `RadarLightDeviceClient`, `SmartRadarLightEngine`, `SmartBrakeLightDetector`), TPMS (`IndirectTpmsEstimator`, `DualWheelTpmsEstimator`), and Polar PFTP (`PolarH10OfflineController`, `PolarVeritySenseOfflineController`, `PolarOnlineStreamingBridge`, `PolarOfflineGattCoordinator`) exist in `app/src/main/` but lack full coverage in `architecture.md` / `interfaces.md`.
  * **Broken Upward-Import Guard**: `ArchitectureConformanceContractTest.kt:55` restricts `.ui.` matching to `.*(?:Screen|Dialog|BottomSheet|Fragment|Activity|View|Sheet)\b`, missing **11 files** in `trainingtracker/database/` (`WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, `WorkoutDeletionHelper.java`, `WorkoutSummariesDatabaseManager.java`, `ErtsPostWorkoutRecomputer.kt`, `RoutesDatabaseManager.kt`, `WorkoutPhotoManager.kt`, `ClimbsDatabaseManager.kt`, `WorkoutClusterCorridorDetector.kt`, `HrvPostWorkoutRecomputer.kt`, `TrackingViewsDatabaseManager.java`). `banalservice/database/` has `0` `.ui.` imports.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Document quantitative heap, cache, vertex, and `GoogleMap` (interactive vs `liteMode`) budgets in `docs/architecture.md` §4.9 (and bound `WorkoutRepository.recentHrvThresholdsCache` cleanly without inflating LoC baseline).
  * Update `docs/architecture.md` & `docs/architecture/interfaces.md` for Swim, RadarLight, TPMS, Polar PFTP, and truthful `IF-SWE-05` persistence/callback boundaries (verified via `AspiceSweAuditContractTest` & `InterfaceSignatureDriftContractTest`).
  * Broaden `ArchitectureConformanceContractTest.kt:55` to all `com.atrainingtracker..*.ui.**` imports, decouple `WorkoutClusterEngine -> WorkoutRepository`, and establish a downward-ratcheting baseline for remaining legacy `database -> ui.*` DTO/repo imports.
* **Out-of-Scope Non-Goals**: No wholesale relocation of `WorkoutData`/`PathPoint`/`TrackType` across 100+ UI/DB files (keeps ticket within 2 SP); no SQLite schema changes; `Localization Parity: N/A - no UI strings`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-PRO-050` (`docs/requirements/req_process_stability_part1.md:3513`), `REQ-PRO-052` (`part1.md:3598`), and `REQ-PRO-115` (`part2.md:1942`) with net-new `REQ-PRO-130` (`req_process_stability_part2.md`, `tst_process_stability_part2.md`).
* **Historical Origin & Commit Trace**: `f1bc836e` (`ATT-6182`), `fd1c481e` (`ATT-6734` added suffix-restricted `database/` upward guard), `074bd857` (`ATT-7666`), `6dffe9e7` (`ATT-7874`), `0e294feb` (`ATT-8203` `MAX_MONOLITH_FILE_LOC_BASELINE`).
* **Root Reason for Existing Formulation**: `ATT-6734` guarded against Compose UI screen imports in `database/` using a suffix regex so existing DTO/repository imports (`WorkoutData`, `PathPoint`, `WorkoutRepository`) would not fail CI prematurely.
* **Preservation of Core Invariants**: Broadening to `.ui.**` with a ratcheted legacy baseline (excluding decoupled `WorkoutClusterEngine -> WorkoutRepository`) prevents net-new layering violations while preserving `LEGITIMATE_GOVERNANCE_GUARDS == 22`, `app/source-reading-baseline.txt == 105`, and `MAX_MONOLITH_FILE_LOC_BASELINE` (`<= 16` files, `WorkoutRepository <= 5,117`, `WorkoutClusterEngine <= 1,635`).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-130` in `docs/requirements/req_process_stability_part2.md` (`2,341` LoC) & `TST-PRO-130` in `docs/tests/tst_process_stability_part2.md` (`1,465` LoC); refines `REQ-PRO-050`, `REQ-PRO-052`, `REQ-PRO-115`.
* **Affected Architecture Elements**: `docs/architecture.md`, `docs/architecture/interfaces.md`, `ArchitectureConformanceContractTest.kt`, `WorkoutClusterEngine.kt`, `WorkoutRepository.kt` (bounded `recentHrvThresholdsCache`).
* **Verification Impact & Test Surface**: `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`, `AspiceSweAuditContractTest`, `SourceTextReadingAuditContractTest`, `WorkoutClusterEngine*Test`, `WorkoutRepository*Test`.
* **Risk Assessment & Invariants**: Medium risk (`IF-SWE-05` decoupling + monolith LoC ceiling). Invariants: zero SQLite schema changes, `ANT-Android-SDKs` untouched, `LEGITIMATE_GOVERNANCE_GUARDS == 22`, `N/A - no UI strings`.

---

## 6. Architectural Strategy & High-Level Solution
1. Extend `docs/architecture.md` §4.9 with quantified singleton cache/memory/vertex/map-instance ceilings for `WorkoutRepository`, `PeriodsRepository`/`PeriodMapUtils`, `WorkoutClusterRepository`, `RadarStateRepository`, and `GoogleMap` (`liteMode` vs interactive GL), plus Swim/RadarLight/TPMS/Polar PFTP in §2 & `interfaces.md`; fix `IF-SWE-05` to reflect `WorkoutClusterEngine`'s SQLite/context reality vs pure-JVM `PowerPhysiologyCalculator`/`AutoPauseManager`.
2. Bound `WorkoutRepository.recentHrvThresholdsCache` (e.g., `LruCache` or capped eviction) without exceeding `5,117` LoC; decouple `WorkoutClusterEngine` from `ui.aftermath.WorkoutRepository` via injected/callback data port without exceeding `1,635` LoC.
3. Broaden `ArchitectureConformanceContractTest.FORBIDDEN_DATABASE_UPWARD_PATTERNS` to `\bcom\.atrainingtracker\..*\.ui\b` with a downward-ratcheting baseline for the remaining 10 legacy `database -> ui.*` files and a negative tripwire test catching `ui.aftermath` imports.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: `MAX_MONOLITH_FILE_LOC_BASELINE` (`WorkoutRepository <= 5,117`, `WorkoutClusterEngine <= 1,635`), `LEGITIMATE_GOVERNANCE_GUARDS == 22`, `100%` `InterfaceSignatureDriftContractTest` bytecode reflection parity, `<= 4,000`-line shard cap.
* **Risk Rating**: **MEDIUM** — Cross-layer `IF-SWE-05` decoupling and strict upward-import regex broadening require careful monolith LoC and reflection parity management without changing runtime behavior.
