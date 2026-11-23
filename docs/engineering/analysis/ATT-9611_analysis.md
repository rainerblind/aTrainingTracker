# Stage 1 Analysis: ATT-9611 - Polar Verity Sense Offline HR Backfill Constant-Value Corruption

**Ticket**: [ATT-9611](https://rainerblind.atlassian.net/browse/ATT-9611) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Story Points**: `3 SP` (`severity-major`, `tier-a` — no split required per `REQ-PRO-029`)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-23_01_Früh` (Sprint 474) | **Base SHA**: `3c36ab24`  
**Date**: 2026-11-23 | **Author**: Lane 2 Author (`--as agent1`)

---

## 1. Problem Statement & Field Evidence
On Pixel 10 (`5.0.0-dev-6cba8883`, `2026-11-22`, Europe/Berlin `CET = UTC+1`, Workout `2430` / `2026-11-22_104306`, Polar Verity Sense `24825632`, `polarH10OfflineRecording = 1`, `polarLiveImuEnabled = 1`):
- `10:43:06` (`09:43:06Z`): Workout started; `10:43:08.778` armed Verity Sense offline HR recording.
- `rows 0..12` (`10:43:08–10:43:19`): Empty initial HR (13 s); `rows 13..73`: Live HR `87..101..98 bpm`; `rows 74..133` (`10:44:20–10:45:20`): 60 s BLE out-of-range gap (`status=147`); `rows 134..140` (`10:45:21–10:45:27`): Live HR `108, 108, 107, 106, 106, 105, 105 bpm`.
- `10:45:29`: Stop-time `PolarVeritySenseOfflineController.stopAndBackfillWorkout()` ran with **zero `Log.i` diagnostics**, patched **all 73 missing `HR` rows** (both initial 13 s and mid-workout 60 s gap) with a single constant **`106 bpm`**, and deleted the genuine recording from Verity Sense flash.

---

## 2. Independent Peer Review & Verification (`Rules 69 & 70`)
Audited against live `3c36ab24` and cached `polar-ble-sdk:8.6.0` bytecode (`OfflineRecordingData$Companion.class`, `BDBleApiImpl.class`, `PolarTimeUtils.class`, `PolarOfflineRecordingUtils.class`):
1. **Confirmed — UTC vs. `ZoneId.systemDefault()` Shift (`PolarVeritySenseOfflineController.kt:1238-1244`)**: `BDBleApiImpl.setLocalTime()` issues two PFTP RPCs: `setSystemTime()` (converting `LocalDateTime` into a **UTC** `Calendar`) and `PbPFtpSetLocalTimeParams` (local time + `tzOffset`). Verity Sense stamps the PMD offline header in **UTC**, and Polar SDK `OfflineRecordingData$Companion.parseStartTime()` appends `"Z"` (`BootstrapMethod #6: "\u0001Z"`) and parses with `ISO_OFFSET_DATE_TIME` in `ZoneId.of("UTC")`, returning `HrOfflineRecording.startTime` as a **UTC `LocalDateTime`** (`09:43:08`). Calling `.atZone(ZoneId.systemDefault())` in `CET (UTC+1)` interpreted `09:43:08` as `09:43:08 CET = 08:43:08Z` — shifting the entire HR series **-3,600 s earlier** than SQLite `WorkoutSamples.time` (`DEFAULT CURRENT_TIMESTAMP` UTC, queried via `strftime('%s', time) * 1000L` -> `09:43:06Z..09:45:29Z`). Consequently every missing row had `tsMs > hrMap.lastKey()` by ~1 hour and clamped to `hrMap.lastEntry() = 106 bpm` (matching the `10:45:25..29` end-of-workout HR!). Existing `PolarVeritySenseOfflineControllerTest` masked this by passing local `LocalDateTime.now()` as `startTime`.
2. **Confirmed — Unbounded Endpoint Clamping (`WorkoutSamplesDatabaseManager.java:1002-1011`)**: `patchMissingHeartRateSamples()` unconditionally falls back to `floor.getValue()` or `ceiling.getValue()` with zero maximum timestamp tolerance, smearing a single boundary sample across arbitrarily distant gaps whenever an offline series is misaligned or truncated.
3. **New Defect Found — Unconditional Commit & Flash Deletion on `patchedCount == 0` (`PolarVeritySenseOfflineController.kt:1211-1236`)**: `applyBackfillAndCommit()` ignores the `Int` returned by `PolarOfflineGattCoordinator.patchMissingHeartRateAndRecordSensorType()`, unconditionally clears `polarSyncPending`, and returns `true` even when `patchedCount == 0`, `hrPairs.isEmpty()`, or `baseFileName.isNullOrEmpty()` — causing `handleStopBackfillMatch()` (`:994-997`) and `executePendingSyncSession()` (`:859-861`) to permanently delete the sensor's flash recording (`api.removeOfflineRecord`) when zero valid aligned rows were committed.
4. **Confirmed — Zero Diagnostics & Sibling Parity (`PolarH10OfflineController.kt:490-532, 689-725`)**: `PolarVeritySenseOfflineController` emits zero `Log.i` lines across stop/retry backfill, entry match, anchor resolution, HR range, `patchedCount`, or flash removal. `PolarH10OfflineController` already anchors on UTC epoch `effectiveStartTimeMs` (`ATT-8124`, immune to PMD `parseStartTime` UTC shift; H10 `listExercises` returns local `LocalDateTime`), automatically inherits the `<= 5 s` DB tolerance guard, and needs matching structured `Log.i` diagnostics (`anchorMs`, `hrPairs`, `hrRange`, `patchedHrRows`, `beats`, `flashRemoved`). Verified: `HR (Polar Verity Sense)` remaining empty in CSV is expected by design (`REQ-SET-105.3`/`REQ-SET-109.1` patch canonical `SensorType.HR` only; per-device columns hold live BLE packets only). Also audited `parsePbLocalDateTime` (`:1143-1157`): `Types.PbLocalDateTime` exposes `hasTimeZoneOffset()` / `timeZoneOffset` (minutes) which should be honored when present.

---

## 3. Root Cause Summary
1. `PolarVeritySenseOfflineController.resolveRecordingAnchorMs()` converts UTC `HrOfflineRecording.startTime` with `ZoneId.systemDefault()` instead of `ZoneOffset.UTC`, shifting offline samples by `-tzOffset` (`-1 h` CET / `-2 h` CEST / `+5 h` EST).
2. `WorkoutSamplesDatabaseManager.patchMissingHeartRateSamples()` lacks a maximum timestamp distance check (`<= 5_000 ms`), flat-line clamping all out-of-window missing rows to a single boundary sample (`106 bpm`).
3. `PolarVeritySenseOfflineController.applyBackfillAndCommit()` discards `patchedCount` and returns `true` even on `0` patched rows, triggering destructive `removeOfflineRecord()` flash deletion.
4. Stop/retry backfill lacks structured `Log.i` diagnostics across Verity Sense and H10 controllers.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: Refines `REQ-SET-109` (`ATT-7924`), `REQ-SET-105` (`ATT-7918`), `REQ-TRK-084.4`, `REQ-TRK-093` (`ATT-8124`) across `PolarVeritySenseOfflineController.kt`, `PolarH10OfflineController.kt`, and `WorkoutSamplesDatabaseManager.java`.
- **Historical Origin & Commit Trace**: `ATT-7918` added `WorkoutSamplesDatabaseManager.patchMissingHeartRateSamples()`; `ATT-7924` added `PolarVeritySenseOfflineController.resolveRecordingAnchorMs()` / `applyBackfillAndCommit()`; `ATT-8124` anchored H10 on post-arming UTC epoch `recordingStartTimeMs`.
- **Root Reason for Existing Formulation**: `ATT-7924` assumed Polar SDK `LocalDateTime` fields used device local time; `ATT-7918` used nearest-neighbor `floorEntry`/`ceilingEntry` to bridge 1-Hz clock jitter (`~1 s`) without anticipating multi-second or hour-scale clock misalignment.
- **Preservation of Core Invariants**: Preserve canonical `SensorType.HR` gap-only patching (never overwriting live HR, GPS, speed, cadence, power), empty-table (`totalRows == 0`) swim insert path, strict `WorkoutRr.db` / `HrvPostWorkoutRecomputer` exclusion on Verity Sense (`REQ-SET-109.2`), H10 RR-only backfill when `patchedHrRows == 0 && intervals.isNotEmpty()`, zero DB schema bump, `InterfaceSignatureDriftContractTest` pinned signatures, and `< 1,500` LoC monolith budgets.

---

## 5. Change-Request Impact Analysis (`REQ-PRO-064` / ASPICE SUP.10 & `REQ-PRO-131.4`)
- **Affected Requirements**: Reserve `REQ-SET-130` (`REQ-SET-130.1`..`.4`) and `TST-SET-132` (`TST-SET-132.1`..`.4`) in `docs/requirements/req_settings_profiles.md` and `docs/tests/tst_settings_profiles.md`. Refines `SYSREQ-DEX-007` (`docs/system/sysreq_data_exchange.md`, `docs/system/system_software_traceability.md`); no new hardware profiles or `SYS-EL-*`/`SYS-IF-*` changes (`REQ-PRO-131.4`). `Localization Parity: N/A - 0 string changes`.
- **Affected Architecture Elements**: New pure-JVM helper `PolarOfflineHrTimestampAligner.kt` (`~160` LoC, zero `android.*` imports — frees monolith headroom on `PolarVeritySenseOfflineController.kt` from `1,492 -> ~1,430 / 1,500` LoC), `WorkoutSamplesDatabaseManager.java` (`1,472 -> ~1,482 / 1,500` LoC, exact signature preserved), `PolarH10OfflineController.kt` (`898 -> ~916 / 1,500` LoC). **Untouched**: `PolarOfflineGattCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `TrackerService.java`, `strings*.xml`, DB schemas.
- **Verification Impact & Test Surface**: New pure-JVM `PolarOfflineHrTimestampAlignerTest`, expanded `WorkoutSamplesHrBackfillTest`, `PolarVeritySenseOfflineControllerTest`, `PolarH10OfflineControllerTest`, plus Contract suites (`ArchitectureConformance`, `InterfaceSignatureDrift`, `DeadCodeElimination`, `SourceTextReadingAudit`, `SystemDataExchangeRequirements`).
- **Risk Assessment & Invariants**: **HIGH (`tier-a`)** — offline telemetry persistence & irreversible sensor flash cleanup. Mitigated by UTC-authoritative anchor conversion backed by SDK bytecode, `<= 5_000 ms` matching tolerance rejecting out-of-window rows, `patchedCount > 0` flash-deletion gate, multi-timezone (`UTC`, `Europe/Berlin` CET/CEST, `America/New_York`) unit tests, and preserved JVM/DB contracts.

---

## 6. Scope Bounding
- **In Scope**: Pure-JVM `PolarOfflineHrTimestampAligner`, UTC `HrOfflineRecording.startTime` resolution + entry matching + `PbLocalDateTime` offset support, `MAX_HR_MATCH_TOLERANCE_MS = 5_000L` bounded matching in `WorkoutSamplesDatabaseManager.patchMissingHeartRateSamples()`, `patchedCount > 0` commit/flash-deletion guard in `PolarVeritySenseOfflineController`, structured `Log.i` diagnostics across Verity Sense & H10, and regression tests.
- **Out of Scope**: Per-device live columns (`HR (Polar Verity Sense)`), GATT/retry state machine (`ATT-9612`), UI/banners, DB schema changes, localization strings, physical Pixel 10 testing (PO Ceremony 2 per `Rule 7.6`).
