# Stage 2: Requirement & Test Specification - ATT-8202

**DOC-ID**: `DOC-TST-ATT-8202` | **Ticket**: [ATT-8202](https://rainerblind.atlassian.net/browse/ATT-8202) (`2 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8202`  
**Requirement Mapping**: `REQ-PRO-130` (`[Planned]`, refining `REQ-PRO-050`, `REQ-PRO-052`, `REQ-PRO-115`) | **Test Spec ID**: `TST-PRO-130` (`[Planned]`)  
**Localization Parity**: `N/A - no UI strings` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-PRO-130` `[Planned]`)
* **Attributes**: Priority: `Medium` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **Implementation**: `docs/architecture.md`, `docs/architecture/interfaces.md`, `WorkoutRepository.kt`, `WorkoutClusterEngine.kt`, `ArchitectureConformanceContractTest.kt`, `InterfaceSignatureDriftContractTest.kt`, `AspiceSweAuditContractTest.kt`, `NonFunctionalRequirementsContractTest.kt`, `SystemArchitectureDesignContractTest.kt`
* **REQ-PRO-130.1 (SWE.2 Heap/Cache/Map Budgets & Bounded `recentHrvThresholdsCache`)**: `docs/architecture.md` §4.9 SHALL document exact bounds and lifecycle cleanup for `WorkoutRepository` (`MAX_OVERLAY_POINTS = 800`, lazy track loading without eager `LatLng` lists, bounded `recentHrvThresholdsCache` keeping `WorkoutRepository.kt <= 5,117` LoC), `PeriodsRepository`/`PeriodMapUtils` (`MAX_SUMMARY_THUMBNAIL_TRACKS = 8`, `<= 5` spatial anchors, `recentlyFinishedWorkoutIds <= 50`, `LruCache(64)`, `2_000`/`8_000` vertices, nullified `workoutIdToPathMap`/`workoutIdToAllPolylinesMap`), `RadarStateRepository` (`<= 4` `RadarSource`), `WorkoutClusterRepository` (`MutableSharedFlow(extraBufferCapacity = 16)`), and `GoogleMap` (`liteMode(true)` thumbnails + `beyondViewportPageCount = 0` GL isolation). `WorkoutRepository.recentHrvThresholdsCache` SHALL enforce bounded eviction + cleanup verified in `WorkoutRepository*Test`.
* **REQ-PRO-130.2 (Subsystem Parity & Truthful `IF-SWE-05` Boundary)**: `docs/architecture.md` §2/§5.1 and `docs/architecture/interfaces.md` SHALL document Swim (`SwimWorkoutAdjustCard`, `SwimmingSection`, `WorkoutRepository.recalculateSwimWorkout`), RadarLight (`RadarLightController`, `RadarLightDeviceClient`, `SmartRadarLightEngine`, `SmartBrakeLightDetector`), TPMS (`IndirectTpmsEstimator`, `DualWheelTpmsEstimator`), and Polar PFTP (`PolarH10OfflineController`, `PolarVeritySenseOfflineController`, `PolarOnlineStreamingBridge`, `PolarOfflineGattCoordinator`). `WorkoutClusterEngine` (`<= 1,635` LoC) SHALL be decoupled from `ui.aftermath.WorkoutRepository` via an injected/callback boundary, and `IF-SWE-05` SHALL truthfully reflect `WorkoutClusterEngine`'s `Context`/SQLite/callback boundary vs pure-JVM calculators with `100%` JVM reflection parity (`InterfaceSignatureDriftContractTest`).
* **REQ-PRO-130.3 (`IF-SWE-05` Upward-Import Ratchet)**: `ArchitectureConformanceContractTest.kt` SHALL replace the weak suffix regex (`L55`) with full `\bcom\.atrainingtracker\..*\.ui\b` detection across `trainingtracker/database/` and `banalservice/database/` against an explicit downward-ratcheting baseline of legacy `database -> ui.*` files (`11` legacy -> `10` after decoupling `WorkoutClusterEngine`, `0` new upward imports, `0` stale baseline entries, keeping `banalservice/database/` at `0`), plus a negative `ui.aftermath` tripwire test.
* **REQ-PRO-130.4 (Invariants)**: `Localization Parity: N/A - no UI strings`, `LEGITIMATE_GOVERNANCE_GUARDS == 22`, `app/source-reading-baseline.txt == 105`, `MAX_MONOLITH_FILE_LOC_BASELINE` (`<= 16` files, `WorkoutRepository.kt <= 5,117`, `WorkoutClusterEngine.kt <= 1,635`), zero SQLite schema changes, `ANT-Android-SDKs` untouched, all `28` living-doc shards `<= 4,000` lines.
* **Given-When-Then Acceptance Criteria**:
  * *Given* `docs/architecture.md` §4.9 and bounded `WorkoutRepository.recentHrvThresholdsCache`, *When* `WorkoutRepository*Test`, `ArchitectureConformanceContractTest`, `NonFunctionalRequirementsContractTest`, and `SystemArchitectureDesignContractTest` run, *Then* all repository/cache/vertex/`GoogleMap` budgets pass, cache eviction works deterministically, and `WorkoutRepository.kt <= 5,117` LoC holds.
  * *Given* updated `docs/architecture.md` §2/§5.1, `docs/architecture/interfaces.md`, and decoupled `WorkoutClusterEngine` (`<= 1,635` LoC), *When* `InterfaceSignatureDriftContractTest` and `AspiceSweAuditContractTest` run, *Then* Swim/RadarLight/TPMS/Polar PFTP and truthful `IF-SWE-05` boundaries pass `100%` via JVM reflection.
  * *Given* `ArchitectureConformanceContractTest` with `\bcom\.atrainingtracker\..*\.ui\b` and ratcheted baseline, *When* run on the live tree and a `ui.aftermath` tripwire fixture, *Then* `0` unallowlisted imports, `0` stale entries, `banalservice/database/ == 0`, and the tripwire catches `ui.aftermath`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-PRO-130` (`.1`–`.4`), refining `REQ-PRO-050` (`ArchitectureConformanceContractTest.kt`), `REQ-PRO-052` (`docs/architecture.md` §4.9), and `REQ-PRO-115` (`docs/architecture/interfaces.md` & `InterfaceSignatureDriftContractTest.kt`).
* **Historical Origin & Commit Trace**: `f1bc836e` (`ATT-6182`), `fd1c481e` (`ATT-6734` added suffix-restricted `.ui.` regex in `database/`), `074bd857` (`ATT-7666`), `6dffe9e7` (`ATT-7874`), and `0e294feb` (`ATT-8203` `MAX_MONOLITH_FILE_LOC_BASELINE`).
* **Root Reason for Existing Formulation**: `ATT-6734` matched only UI class suffixes so existing `database -> ui.*` DTO/repo imports (`WorkoutData`, `PathPoint`, `WorkoutRepository`) would not break CI prematurely; §4.9 focused initially on `TrackerService` (`REQ-NFR-003`..`014`).
* **Preservation of Core Invariants**: Broadening to `\bcom\.atrainingtracker\..*\.ui\b` with a shrinking legacy baseline (excluding decoupled `WorkoutClusterEngine -> WorkoutRepository`) blocks net-new upward imports without an out-of-scope 100+ file DTO relocation, preserving `LEGITIMATE_GOVERNANCE_GUARDS == 22`, `app/source-reading-baseline.txt == 105`, `WorkoutRepository.kt <= 5,117`, `WorkoutClusterEngine.kt <= 1,635`, and `REQ-NFR-003`..`014`.

---

## 2. Test Specification (`TST-PRO-130` `[Planned]`)
* **`TST-PRO-130.1` (`WorkoutRepository*Test` & `ArchitectureConformanceContractTest` / `NonFunctionalRequirementsContractTest` / `SystemArchitectureDesignContractTest`)**: Verify bounded `recentHrvThresholdsCache` eviction + deletion/refresh cleanup, `WorkoutRepository.kt <= 5,117` LoC, `docs/architecture.md` §4.9 repository/cache/vertex/`GoogleMap` budgets, and `REQ-NFR-003`..`014` traceability.
* **`TST-PRO-130.2` (`InterfaceSignatureDriftContractTest` & `AspiceSweAuditContractTest`)**: Verify Swim/RadarLight/TPMS/Polar PFTP documentation parity and truthful `IF-SWE-05` `WorkoutClusterEngine` (`<= 1,635` LoC) boundary via `100%` JVM reflection with zero `src/main/` text reads.
* **`TST-PRO-130.3` (`ArchitectureConformanceContractTest.kt`)**: Verify `\bcom\.atrainingtracker\..*\.ui\b` detects all legacy `database -> ui.*` files against the downward-ratcheting baseline (`0` unallowlisted, `0` stale, `banalservice/database/ == 0`) plus negative `ui.aftermath` tripwire.
* **`TST-PRO-130.4` (`SourceTextReadingAuditContractTest`, `shard_living_docs.py`, `verify_requirement_governance.py`)**: Verify `LEGITIMATE_GOVERNANCE_GUARDS == 22`, `app/source-reading-baseline.txt == 105`, `28/28` living-doc shards `<= 4,000` lines, and governance `exit 0` (`Localization Parity: N/A - no UI strings`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-130.1` | Unit + Contract | `recentHrvThresholdsCache`, `docs/architecture.md` §4.9, `NonFunctionalRequirementsContractTest`, `SystemArchitectureDesignContractTest` | `REQ-PRO-130.1`, `REQ-PRO-052` | Planned |
| `TST-PRO-130.2` | Reflection + Audit | `docs/architecture.md` §2/§5.1, `docs/architecture/interfaces.md`, `WorkoutClusterEngine`, `InterfaceSignatureDriftContractTest`, `AspiceSweAuditContractTest` | `REQ-PRO-130.2`, `REQ-PRO-115` | Planned |
| `TST-PRO-130.3` | Conformance Contract | `ArchitectureConformanceContractTest.kt` (`.ui.` ratchet + `ui.aftermath` tripwire) | `REQ-PRO-130.3`, `REQ-PRO-050` | Planned |
| `TST-PRO-130.4` | Governance + Size Gate | `SourceTextReadingAuditContractTest`, `28` living shards (`<= 4,000`), `verify_requirement_governance.py` | `REQ-PRO-130.4` | Planned |
