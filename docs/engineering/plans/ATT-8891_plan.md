# Stage 3: Implementation Plan - ATT-8891: Kinematic `BSportType` Inference & Moving-Speed Sport Identity

**Ticket**: `ATT-8891` (`3 SP`, `Feature`) | **Parent Epic**: `ATT-5908` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_01_Früh` (`ID 462`) | **Requirement Mapping**: `REQ-FIL-030` (`REQ-FIL-030.1`–`.5`) | **Test Mapping**: `TST-FIL-030`  
**Branch**: `feature/ATT-8891` | **Sub-task**: `[Impl-Plan]` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-19

---

## 1. Problem Description & SWE.2 Architectural Boundaries
* Without explicit sport pre-selection (`mHaveUserSelectedSportType == false`) or paired sport-specific ANT+/BLE sensors (`DeviceManager.getSportType() == UNKNOWN`), `BANALService.getBSportType()` stays `UNKNOWN` live and finalization classifies solely from session-wide `averageSpeed = mDistanceTotal_m / mTimeActive_s`, misclassifying multi-stop errand rides (`Metzger` `1.35 m/s` avg / `p90 = 6.34 m/s`, `Norma #9`, `Zum Bäcker #57`) as walking/running.
* **SWE.2 Threading & Layering**: Pure-JVM `MovingSpeedSummary`, `MovingSpeedHistogram`, and `KinematicSportTypeDiscriminator` (`banalservice.odometry`) run inside `SpeedAndLocationDevice_ATTFused` sensor callbacks (`IF-SWE-04`) with zero Android UI, SQLite, or `TrackerService` coupling. `BANALService` publishes volatile Tier 3 inferred sport and `MovingSpeedSummary` lock-free across `IF-SWE-03` (`BANALServiceComm`). `EquipmentAndSportTypeDiscoveryManager` encapsulates moving-speed identity resolution and `BanalFinalizationSnapshot` sport selection so `TrackerService.java` adds `0` net LoC (`<= 3,556`).
* **UI Consistency (`docs/design_guidelines.md`)**: `N/A` — zero Compose screens/dialogs/widgets modified and zero UI strings (`N/A - no UI strings`).
* **SWE.3 Living Detailed Design (`REQ-PRO-114`)**: `BANALService.java` gains volatile Tier 3 inferred sport state (`mInferredBSportType`, `mMovingSpeedSummary`), `getAuthoritativeBSportType()`, and `mResetAccumulatorsReceiver`; Step 5 explicitly updates `docs/architecture/detailed_design/banal_service.md` (and `docs/architecture.md`) and validates `DetailedDesignParityContractTest`. `DeviceManager.java` and `MyBTLEDevice.java` remain untouched.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-FIL-030` (`.1` pure-Kotlin kinematic discriminator + $O(1)$ histogram + degradation; `.2` anti-circular split sport context + `ATTFused` wiring; `.3` `BANALService` 4-tier precedence + unified `getSportTypeId` + `RESET_ACCUMULATORS_INTENT`; `.4` moving-speed discovery + `>= 10` floor + `LiveWorkoutSession`/`TrackerService` finalization; `.5` monolith LoC bounds + `SWE.2`/`SWE.3`/`SYS.2`/`SYS.3` parity).
* **Tests**: `TST-FIL-030` (`.1`–`.5`) linked to `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-010`.

---

## 3. System Invariants & Preserved Behavior
1. **Strict 4-Tier Precedence & Explicit `WALK`/`OTHER` Sovereignty**: `mHaveUserSelectedSportType` (including `WALK`/`OTHER` whose `BSportType == UNKNOWN`) $>$ `DeviceManager.getSportType()` (`!= UNKNOWN`) $>$ high-confidence `KinematicSportTypeDiscriminator` $>$ speed fallback. `RESET_ACCUMULATORS_INTENT` clears Tier 3 + moving summary while preserving `mHaveUserSelectedSportType`.
2. **Anti-Circular Split Hint & Downstream `STRIDES` Continuity**: `PhoneCarriageClassifier.setSportContext()` reads ONLY `BANALService.getAuthoritativeBSportType()` (Tier 1+2); downstream `SmartphoneImuCadenceEstimator` / `applyCadenceAndStrideSensors()` read full `BANALService.getBSportType()` (Tier 1>2>3). Inferred updates NEVER broadcast `SPORT_TYPE_CHANGED_BY_USER_INTENT`.
3. **`SPEED_mps` Extrema, Rule 20 DTO & Monolith Caps**: `WorkoutSummaries.SPEED_AVERAGE_mps` & `SensorType.SPEED_mps` `AVG` extrema retain `mDistanceTotal_m / mTimeActive_s` (`SCRUM-108`/`REQ-TRK-030.3`); existing `EquipmentAndSportTypeDiscoveryManager` 2/3-arg signatures and `BanalFinalizationSnapshot` 9/13-arg constructors + 4/6-arg `capture()` overloads remain intact; `TrackerService.java <= 3,556` (`0` net growth), `DeviceManager.java <= 1,707` (`0` edits), `SpeedAndLocationDevice_ATTFused.kt < 1,500`, `BANALService.java <= 1,035`; zero IMU/Main-thread SQLite; zero DB migrations; `N/A - no UI strings`; `0` new `src/main` test reads (`REQ-PRO-049.1`).

---

## 4. Proposed Architectural Changes
* **`banalservice/odometry/KinematicSportTypeDiscriminator.kt` (New)**:
  * `MovingSpeedSummary(movingMeanMps, p50Mps, p90Mps, movingSampleCount, coastingSeconds)` + `MovingSpeedHistogram` (`IntArray(293)` `0.1 m/s` bins across `[0.8, 30.0] m/s`, `~1.2 KB` constant memory, strict `>= 1000 ms` timestamp throttle so `MIN_MOVING_SAMPLES_FOR_IDENTITY = 10` counts genuine moving seconds).
  * `KinematicSportTypeDiscriminator`: zero-allocation 50-Hz `ACC` `2.4–3.6 Hz` impact periodicity via `ImuCadenceDsp` (`UniformGridResampler`, `BiquadBandPass`, `DoubleRingBuffer`, `findFundamentalLagResult`) evaluated at `<= 1 Hz` with preallocated buffers and prominence gate ($R(\tau)/R(0) \ge 0.45$, impact RMS floor) rejecting bumpy `2.0 m/s` MTB chatter while locking `Kurze Runde #52` & `5.6 m/s` fast runs within `<= 30 s` without gyro/baro/`ACTIVITY_RECOGNITION`; combines coasting ($v > 3.0\text{ m/s}$ for $\ge 3.0\text{ s}$, low step/oscillation $\to$ `BIKE`), step-length coherence ($L_{\text{step}} \in [0.60, 1.75]\text{ m} \to$ `RUN`; $f_{\text{step}} \approx 0$ or $L > 3.5\text{ m}$ at $v > 5.0\text{ m/s} \to$ `BIKE`), `PhoneCarriageMode` voting, and bounded signed evidence with hysteresis permitting `RUN <-> BIKE` recovery.
* **`BANALService.java` (`~1,025` LoC) & `SpeedAndLocationDevice_ATTFused.kt` (`~1,410` LoC)**:
  * `BANALService`: volatile `mInferredBSportType` & `mMovingSpeedSummary`; `getAuthoritativeBSportType()` / `getAuthoritativeActiveBSportType()`; 4-tier `getBSportType()`, unified `getSportTypeId()` / `getSportTypeId(avgSpd)`; `setInferredSportTelemetry(...)` & `BANALServiceComm.getMovingSpeedSummary()`; `mResetAccumulatorsReceiver` clearing Tier 3 + moving summary while preserving `mHaveUserSelectedSportType` (`onDestroy()` clears all).
  * `SpeedAndLocationDevice_ATTFused`: thin delegation to `sportDiscriminator`; split `resolveCarriageSportContextHint()` (authoritative Tier 1+2) vs `resolveSportContextHint()` / `resolveActiveBSportType()` (full Tier 1>2>3); internal `handleSportTypeChanged()` on inferred transitions; clean reset in `resetOriginAndEkf()`.
* **`EquipmentAndSportTypeDiscoveryManager.kt`, `LiveWorkoutSession.java` & `TrackerService.java` (`<= 3,556` LoC)**:
  * `EquipmentAndSportTypeDiscoveryManager`: `@JvmStatic resolveEffectiveSpeedForIdentity(bSportType, sessionAvgSpeed, movingSummary)` (`< 10` moving s -> `sessionAvgSpeed`; `>= 10` -> `movingMeanMps` for `UNKNOWN`/`RUN`, `max(movingMeanMps, 0.5 * (p50Mps + p90Mps))` for `BIKE`), `@JvmStatic resolveSnapshotSportTypeId(...)` honoring explicit `userSelectedSport != null` (incl. `WALK`/`OTHER`) via `banalService.sportTypeId`, plus backward-compatible moving-speed overloads.
  * `LiveWorkoutSession`: 1-Hz `MovingSpeedHistogram` fallback on `addSample(SensorType.SPEED_mps, ...)`.
  * `TrackerService`: add nullable `movingSpeedSummary` + 14-arg constructor to `BanalFinalizationSnapshot` (retaining 9/13-arg & 4/6-arg overloads); delegate `capture()` `sportTypeId` block (`-8` LoC) to `resolveSnapshotSportTypeId(...)`; resolve `identitySpeed` in `finalizeLiveSession()` and pass into existing 3-arg `resolveIdentity` / 2-arg `getCandidateBSportTypes` (preserving `TrackerServiceFinalizationTest` MockK stubs and `SPEED_mps` `AVG` extrema at `:3247-3249`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
| Step | Action & Target Files | Mapping | Verification (`./gradlew :app:testDebugUnitTest --tests ...`) |
| :---: | :--- | :--- | :--- |
| **1** | Create `KinematicSportTypeDiscriminator.kt` (`MovingSpeedSummary`, `O(1)` `MovingSpeedHistogram` w/ `>= 1000 ms` throttle, `<= 1 Hz` preallocated `ImuCadenceDsp` impact ACF w/ prominence gate, coasting/step-length/carriage fusion & `RUN <-> BIKE` hysteresis); add `KinematicSportTypeDiscriminatorTest.kt`. | `REQ-FIL-030.1` / `TST-FIL-030.1` | `*KinematicSportTypeDiscriminatorTest` |
| **2** | Update `BANALService.java` (`getAuthoritativeBSportType()`, 4-tier `getBSportType()`, unified `getSportTypeId*`, volatile inferred sport & `MovingSpeedSummary`, `mResetAccumulatorsReceiver` preserving user selection, `onDestroy()` full clear); add `BANALServiceSportInferenceTest.kt`. | `REQ-FIL-030.3` / `TST-FIL-030.3` | `*BANALServiceSportInferenceTest`, `*BANALServiceLifecycleTest` |
| **3** | Wire `KinematicSportTypeDiscriminator` into `SpeedAndLocationDevice_ATTFused.kt` (`< 1,500` LoC) with anti-circular split `resolveCarriageSportContextHint()` vs full `resolveActiveBSportType()`, internal `handleSportTypeChanged()`, zero `SPORT_TYPE_CHANGED_BY_USER_INTENT`, and `resetOriginAndEkf()` cleanup; extend `SpeedAndLocationDevice_ATTFusedTest.kt` & `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest.kt`. | `REQ-FIL-030.2` / `TST-FIL-030.2` | `*SpeedAndLocationDevice_ATTFusedTest`, `*SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest`, `*SmartphoneImuCadenceEstimatorTest` |
| **4** | Implement `resolveEffectiveSpeedForIdentity`, `resolveSnapshotSportTypeId`, and moving-speed overloads in `EquipmentAndSportTypeDiscoveryManager.kt`; add 1-Hz `MovingSpeedHistogram` fallback in `LiveWorkoutSession.java`; wire zero-growth `BanalFinalizationSnapshot` & `finalizeLiveSession()` in `TrackerService.java` (`<= 3,556` LoC); add `EquipmentAndSportTypeDiscoveryManagerMovingSpeedTest.kt` & extend `TrackerServiceFinalizationTest.kt`. | `REQ-FIL-030.4` / `TST-FIL-030.4` | `*EquipmentAndSportTypeDiscoveryManagerMovingSpeedTest`, `*EquipmentDiscoveryTest`, `*TrackerServiceFinalizationTest` |
| **5** | Sync `docs/architecture.md` (`Rule 8.8`), `docs/architecture/detailed_design/banal_service.md` (`REQ-PRO-114`), `docs/system/` (`sysreq_live_tracking.md`, `system_architecture.md`, `system_software_traceability.md` per `REQ-PRO-131.4`), and living docs (`[Implemented]`); verify monolith LoC bounds, contract tests & `verify_requirement_governance.py`. | `REQ-FIL-030.5` / `TST-FIL-030.5` | `*ArchitectureConformanceContractTest`, `*DetailedDesignParityContractTest`, `*SystemLiveTrackingRequirementsContractTest`, `*SystemArchitectureDesignContractTest`, `*SourceTextReadingAuditContractTest`, `verify_requirement_governance.py` |

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted Tier-1 `./gradlew :app:testDebugUnitTest --tests ...` under `python3 tools/parallel_sprint_lock.py` across all 14 listed unit/integration/contract suites + `python3 tools/verify_requirement_governance.py --base-ref refs/heads/sprint/2026-11-19_01_Früh`.
* **Rollback**: Isolated on `feature/ATT-8891` (base `a3a5942a`); zero DB schema migrations; clean branch revert if needed.
