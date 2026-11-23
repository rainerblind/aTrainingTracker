# Stage 2 Requirement & Test Specification — ATT-9602

- **Ticket**: `ATT-9602` (`3 SP`, `Bug`, `tier-a`, Epic `ATT-166`) | **Target Release**: `V5.0.0` | **Risk Rating**: `HIGH`
- **Reserved IDs**: `REQ-FIL-040` (`[Planned]`), `TST-FIL-040` (`[Planned]`)
- **System Traceability (`SYS.2`/`SYS.3`)**: `Derived (SW-only)` — internal barometer dispersion estimation, 3-state startup emission gating, and ERTS deceleration damping refining `REQ-FIL-026`, `REQ-FIL-022.3`, `REQ-FIL-033.1`, and `REQ-FIL-037.2` without external hardware, HAL, SQLite, or cloud API changes.
- **Living-Doc Shards**: `docs/requirements/req_tracking_cockpit_part3.md` (`v1.0.8`), `docs/tests/tst_tracking_cockpit_part2.md` (`v1.0.11`).
- **Localization Parity**: `N/A` — pure sensor-fusion bugfix with zero UI or `strings*.xml` edits.

## 1. Requirements Summary (`REQ-FIL-040` — Priority: `High`, Type: `Functional`, Verification: `Unit`)

| Sub-ID | Normative Contract (SHALL / MUST) |
|---|---|
| `REQ-FIL-040.1` | Pure-Kotlin `BarometricBucketDispersion` SHALL compute intra-bucket dispersion (`IQR` for `size >= 4`, `0.5 * span` for `size in 2..3`, `0.0` for `size < 2`) against `physicalSpan = max(2.5, 0.20 * max(0.0, speedMps))`. When `excessSpread == 0.0` (handlebar / calm pocket / `8–12%` climbs & alpine descents), `dispersionVarM2` MUST stay `0.0` and the exact 1-Hz bucket median SHALL be emitted with zero lag (passing `speedMps = speed` to `filterSlewOnly`). When `excessSpread > 0.0`, inflate `dispersionVarM2` immediately (`min(2500.0, 2.0 * excessSpread^2)`) with `0.65` decay retention, emit the interquartile trimmed mean over a bounded `3,000 ms` (`<= 128` samples) rolling raw window, keep `filterSlewOnly` at base slew (`speedMps = 0.0`), and pass `varBaro = mEffectiveRBaro + dispersionVarM2` to `ekf.updateBarometer()`. |
| `REQ-FIL-040.2` | `KinematicAltitudeSlopeEkf` SHALL track `lastBaroVarM2` (reset in `reset()`/`reanchorOnResume()`/`setMode()`) and apply `computeStepGatedRefVariance()` baro-vs-ref discrepancy gating ONLY when `lastBaroVarM2 <= DEM_BARO_TRUST_VAR_THRESHOLD` (`16.0 m²`), preventing corrupted pocket barometers from vetoing valid flat DEM/GPS updates while preserving 100% of `REQ-FIL-037.2` healthy-baro DEM jump gating. |
| `REQ-FIL-040.3` | `AltitudeAndSlopeDevice_ATTFused` SHALL record `mFirstBaroTimestampMs` (cleared in `shutDown()`) and defer emitting `mAltitudeSensor`/`mSlopeSensor`/`mVerticalSpeedSensor`/`mAscentSensor`/`mDescentSensor` for up to `STARTUP_REFERENCE_GRACE_MS = 5_000L` strictly when `ekf.is3StateMode && !ekf.hasReferenceAnchor` (`ekf.isInitialized` MUST still be `true` on sample #1). DEM/GPS lock within `5 s` emits calibrated altitude immediately (`0` cliff, `0.0 m` phantom ascent/descent); offline timeout `>= 5 s` falls back to baro cleanly (`REQ-FIL-022.3` preserved); 2-State & `reanchorOnResume()` publish immediately (`0 ms`). |
| `REQ-FIL-040.4` | `ExtendedRtsAltitudeSlopeSmoother` SHALL keep `LOW_SPEED_SLOPE_THRESHOLD_MPS = 1.2` and `lowSpeedSlopeGainFactor()` untouched (`TST-FIL-033.1`), while detecting braking (`dv/dt <= -0.25 m/s²`, `4.0 s` hold below `2.5 m/s`): inflate `effectiveVarBaro` (`8.0×` with ref, `4.0×` without ref below `2.5 m/s`) and scale `qgVal` & baro `k1` by `(speedMps / 2.5)^2` below `2.5 m/s` so flat-road braking (`5.5 -> 0.6 m/s`) avoids `-11%` plunges without degrading steady climbs/descents or real downhill braking. |

## 2. Traceability Matrix & Verification Plan (`TST-FIL-040`)

| Test ID | Target Test Class(es) | Requirement(s) | Key Verification Procedure |
|---|---|---|---|
| `TST-FIL-040.1` | `BarometricBucketDispersionTest.kt`, `AltitudeAndSlopeDevice_ATTFusedTest.kt` | `REQ-FIL-040.1` | `~13 Hz` asymmetric `0.92 Hz` `70–88 m` pocket wave on flat `436–438 m` DEM + full-loop replay -> `|alt - true| <= 2.0 m`, no `+10%` hill, live ascent/descent within `<= 20%` of ERTS/true; calm handlebar `8–12%` climb & `-12%` `18 m/s` alpine descent -> `dispersionVarM2 == 0.0`, zero added lag. |
| `TST-FIL-040.2` | `KinematicAltitudeSlopeEkfTest.kt` | `REQ-FIL-040.2` | Biased baro with `varBaro = 1000.0` (`> 16.0`) does NOT veto flat DEM in `computeStepGatedRefVariance()`; healthy `varBaro <= 16.0` preserves all `REQ-FIL-037.2` DEM jump gating and `DEFAULT_VAR_BARO = 0.25` / 2-state `p00` invariants. |
| `TST-FIL-040.3` | `AltitudeAndSlopeDevice_ATTFusedTest.kt` | `REQ-FIL-040.3` | 3-State `+35 m` QNH startup with DEM at `t = 2 s` -> `ekf.isInitialized == true` at `t = 0 s`, no `470.9 -> 435.0 m` cliff, `0.0 m` phantom ascent/descent; offline `>= 5 s` fallback + late DEM `0.0 m` descent; 2-State & `reanchorOnResume()` immediate publish. |
| `TST-FIL-040.4` | `ExtendedRtsAltitudeSlopeSmootherTest.kt`, `ArchitectureConformanceContractTest.kt` | `REQ-FIL-040.4` | `15 s` flat braking (`5.5 -> 0.6 m/s`, `DEM = 438 m`, `-2.0 m` baro drop) -> `|SLOPE| <= 4%` (no `-11%` dip) & alt near `438 m`; real `-8%` downhill braking preserves smooth negative slope; `TST-FIL-033.1` & `<= 1,500` LoC PASS. |

## 3. Acceptance Criteria (Given-When-Then)

- **Given** `~13 Hz` pocket-compression baro waves (`70–88 m` p-p, `0.92 Hz`) on flat `436–438 m` DEM (`t = 25..65 s`) and across a full loop, **When** processed by `AltitudeAndSlopeDevice_ATTFused`, **Then** live `ALTITUDE` stays within `<= 2.0 m` of true elevation (no `+6.7 m` / `+10%` hill) and full-workout live `ASCENT`/`DESCENT` stay within `<= 20%` of ERTS/true gain.
- **Given** a handlebar/calm-pocket `8–12%` climb or fast alpine descent (`intra-bucket span <= 2 m`), **When** processed by `AltitudeAndSlopeDevice_ATTFused`, **Then** `varBaro` is NOT inflated and altitude/slope track the true grade promptly.
- **Given** a 3-State outdoor startup with `+35 m` QNH offset and DEM/GPS arriving at `t = 1–2 s`, **When** the first `5 s` are recorded, **Then** no `470.9 -> 435.0 m` cliff or phantom ascent/descent is emitted (while offline workouts fall back cleanly at `>= 5 s`).
- **Given** a `15 s` flat-road braking segment (`5.5 -> 0.6 m/s`, `DEM = 438 m`, `-2.0 m` baro drop), **When** smoothed by `ExtendedRtsAltitudeSlopeSmoother`, **Then** `SLOPE` remains within realistic flat bounds (`|SLOPE| <= 4%`, no `-11%` dip).

## 4. Chesterton's Fence & System Invariants

- **Original Requirement ID & Target**: `REQ-FIL-026.1` (`ATT-8881`), `REQ-FIL-022.3` (`ATT-8575`), `REQ-FIL-033.1` (`ATT-9063`), `REQ-FIL-037.2` (`ATT-9156`) in `docs/requirements/req_tracking_cockpit_part3.md`.
- **Root Reason for Existing Formulation**: `REQ-FIL-026.1` required immediate sample #1 publication to prevent bucket stalling; `REQ-FIL-037.2` assumed healthy barometer deltas; `REQ-FIL-033.1` only damped steady speeds `< 1.2 m/s`.
- **Preservation of Core Invariants**: `ekf.isInitialized == true` on sample #1; 2-State & `reanchorOnResume()` immediate publish; `5 s` offline fallback + `REQ-FIL-022.3` `0.0 m` bogus descent; `DEFAULT_VAR_BARO = 0.25` & 2-state `p00 = varBaro`; `REQ-FIL-037.2` unchanged for `varBaro <= 16.0`; `lowSpeedSlopeGainFactor()` unchanged; all files `<= 1,500` LoC; `0` UI/DB/`strings*.xml` edits.
