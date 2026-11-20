# Stage 2: Requirement & Test Specification - ATT-9156: Live ATT Fused Altitude/Slope EKF DEM Grid-Step Jump Gating & Variance Retuning

**Ticket**: `ATT-9156` (`2 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-9156` (base `bf7eed5e`)  
**Requirement Mapping**: `REQ-FIL-037` (`REQ-FIL-037.1`–`.3`, refining `REQ-FIL-016.1`/`.2`, `REQ-FIL-020.1`/`.2`, `REQ-FIL-022`, `REQ-FIL-023.2`/`.3`, `REQ-FIL-026`, mirroring offline `REQ-FIL-033`)  
**Test Spec ID**: `TST-FIL-037` (`TST-FIL-037.1`–`.3`) | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-20

---

## 1. Requirement Specification (`REQ-FIL-037`)

### 1.1 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-008`
- **Implementation**: `KinematicAltitudeSlopeEkf.kt`, `AltitudeAndSlopeDevice_ATTFused.kt`

### 1.2 Functional & Architectural Requirements
1. **Coherent DEM/GPS Variance Retuning, Baro/Standstill Slope Decoupling & 3×3 Joseph SPD Update (`REQ-FIL-037.1`)**:
   - `KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM` **SHALL** be retuned from `4.0 m²` to **`25.0 m²`** (`±5 m` 1σ SRTM/DEM vertical uncertainty), and `DEFAULT_VAR_GPS` **SHALL** be retuned coherently from `16.0 m²` to **`64.0 m²`** (`±8 m` 1σ vertical GPS uncertainty, strictly `> DEFAULT_VAR_DEM` preserving `REQ-FIL-020.1` DEM > GPS accuracy hierarchy and `testThreeStateGpsBeforeDem` `< 1.0 m` recovery).
   - In `KinematicAltitudeSlopeEkf.updateReferenceAltitude()`, `k1Eff` **SHALL** be decoupled to `0.0` whenever `hasBarometerSample || isStandstill` (preserving `k1Eff = (p10 / s) * gainScale` strictly for barometer-free DEM/GPS-only slope tracking).
   - The 3-state reference covariance update in `updateReferenceAltitude()` **SHALL** use the exact symmetric positive-definite 3×3 Joseph form `P^+ = (I - KH)P(I - KH)^T + K R K^T` (`H = [1, 0, 0]`, `R = effectiveVarRef`), preventing covariance asymmetry or SPD loss when `k1Eff` is zeroed or damped.
2. **Post-Anchor Baro-vs-Reference Step-Discrepancy Jump Gating, Reference Tracker Advancement, Quadratic Huber Variance Inflation & High-Precision Guard (`REQ-FIL-037.2`)**:
   - `KinematicAltitudeSlopeEkf.updateReferenceAltitude()` **SHALL** accept `@JvmOverloads enableStepGating: Boolean = (varRef > 1.0)` so high-precision known-location or manual calibration anchors (`varRef <= 1.0 m²`) bypass DEM step gating completely.
   - Strictly for **post-anchor** updates (`hasReferenceAnchor == true`, preserving `REQ-FIL-022` initial `Baro-before-Ref` / `Ref-before-Baro` `+130.4 m` weather-offset snaps and `resetAscentDescentAnchor()`), when `enableStepGating && hasBarometerSample`:
     (a) Compare `Δref = refAltM - lastRefAltM` against recent barometric change `Δbaro = lastBaroAltM - lastRefBaroAltM`; whenever `stepDiscrepancy = abs(Δref - Δbaro)` exceeds `DEM_STEP_GATE_THRESHOLD_M` (`3.0 m`), inflate `stepVarRef` quadratically (`varRef * (1.0 + (stepDiscrepancy / 1.0)^2)` or stronger for abrupt `>= 8.0 m` jumps while `|Δbaro| < 1.0 m`) so `10–21 m` 1–2 s DEM cell jumps produce negligible altitude pull (`|Δalt/dt| << 0.5 m/s`) and `0.0 m` false ascent across `2.0 m` hysteresis.
     (b) **Reference Tracker Advancement**: Even when step-discrepancy variance inflation triggers, `lastRefAltM` and `lastRefBaroAltM` **SHALL** still advance to `(refAltM, lastBaroAltM)` so legitimate persistent step corrections (e.g., coarse GPS `453 m` -> persistent DEM `438 m` in `testThreeStateGpsBeforeDem`) see `stepDiscrepancy ≈ 0` on ticks `2..10` and converge cleanly (`< 1.0 m` error, accurate `baroBias`), while transient DEM bounces (`440 -> 427 -> 440 m`) trigger massive inflation on BOTH `-13 m` and `+13 m` edges (`0.0 m` false ascent).
     (c) **Quadratic Innovation-Based Huber Variance Inflation**: On top of `stepVarRef`, compute normalized innovation `normRes = abs(y) / sqrt(max(1e-9, p00 + stepVarRef))`; whenever `normRes > huberK` (`3.0`), inflate `effectiveVarRef = stepVarRef * (normRes / huberK)^2` (preserving `p00` against covariance collapse on multi-tick outliers while keeping normal GPS->DEM recovery `|y|/sigma = 15 / sqrt(64 + 25) = 1.59 < 3.0` completely uninflated).
   - `reset()` and `setMode()` **SHALL** clear all step-gating reference/baro tracking state cleanly.
3. **Live Loop Ascent/Descent Accuracy, Slope Plausibility & MSL Loop-Closure Anchoring (`REQ-FIL-037.3`)**:
   - `AltitudeAndSlopeDevice_ATTFused` (`handleReferenceAltitude()` and `checkReferenceAltitudeFromSensors()`) **SHALL** invoke `ekf.updateReferenceAltitude()` using the retuned `DEFAULT_VAR_DEM` (`25.0 m²`) and `DEFAULT_VAR_GPS` (`64.0 m²`), preserving `REQ-FIL-020.1` DEM precedence, `REQ-FIL-020.2` `2.0 m` hysteresis, `REQ-FIL-022.3` re-anchor hysteresis reset, and `REQ-FIL-026` 1-Hz barometric aggregation.
   - Across a synthetic replay of the Pixel 10 `Kurze Runde var 2 #53` loop (`~60 m` true elevation gain/loss with repeated `13–21 m` DEM grid-cell jumps and slow barometric weather drift), live `ASCENT` and `DESCENT` **SHALL** stay within `55–75 m` (eliminating `167 m` inflation), live `SLOPE` **SHALL** remain physically plausible (no `-35%/+34%` DEM-induced spikes), and loop closure **SHALL** stay anchored near true MSL altitude without barometric drift runaway.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (`REQ-FIL-037.1`, `REQ-FIL-037.2`)**: *Given* a live 3-state workout with barometer + DEM enabled on `AltitudeAndSlopeDevice_ATTFused` where DEM jumps `440 -> 427 -> 440 m` (`-13 m` / `+13 m`) or `±21 m` in `1–2 s` while barometer changes `< 0.5 m`, *When* `KinematicAltitudeSlopeEkf.updateReferenceAltitude()` processes the DEM updates, *Then* step-discrepancy + Huber variance inflation and `k1 = 0.0` decoupling hold live `|Δalt/dt| < 0.3 m/s`, `|SLOPE| < 3%` on flat terrain, `ASCENT`/`DESCENT = 0.0 m`, and symmetric SPD 3×3 covariance (`p00, p11, p22 > 0`, `det(P) > 0`).
* **AC-2 (`REQ-FIL-037.1`, `REQ-FIL-037.2`)**: *Given* (a) coarse GPS initial anchor (`453 m`, `DEFAULT_VAR_GPS = 64.0 m²`) followed by persistent DEM fixes at `438 m` (`DEFAULT_VAR_DEM = 25.0 m²`), (b) gradual thermal/weather barometric drift over a loop, (c) a high-precision manual/known-location anchor (`varRef = 0.5 m² <= 1.0`), and (d) barometer-free DEM-only climbing (`!hasBarometerSample`), *When* `KinematicAltitudeSlopeEkf` processes each scenario, *Then* (a) altitude & `baroBias` converge to `438.0 ± 1.0 m` / `130.4 ± 1.0 m`, (b) weather drift is absorbed into `baroBias` keeping MSL loop closure anchored, (c) high-precision anchor updates unimpeded (`enableStepGating == false`), and (d) barometer-free DEM-only mode still estimates non-zero positive climb slope.
* **AC-3 (`REQ-FIL-037.3`)**: *Given* a synthetic replay of the Pixel 10 `Kurze Runde var 2 #53` loop (`~60 m` true climb/descent, `2.0 m/s` running, repeated `13–21 m` DEM grid jumps, gradual barometric drift) through `AltitudeAndSlopeDevice_ATTFused`, *When* live `ASCENT`, `DESCENT`, `SLOPE`, `VERTICAL_SPEED`, and closing `ALTITUDE` are evaluated, *Then* live `ASCENT` and `DESCENT` stay in `55–75 m` (not `167 m`), `|SLOPE|` remains physically plausible (no `-35%/+34%` artifacts), and loop closure remains anchored near true MSL altitude.

### 1.4 Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. *Original Requirement ID & Target*: Net-new `REQ-FIL-037` refining `REQ-FIL-016.1`/`.2`, `REQ-FIL-020.1`/`.2`, `REQ-FIL-022`, `REQ-FIL-023.2`/`.3`, and `REQ-FIL-026` (live counterpart to offline `REQ-FIL-033`) across `KinematicAltitudeSlopeEkf.kt` and `AltitudeAndSlopeDevice_ATTFused.kt`.
2. *Historical Origin & Commit Trace*: `75c1497e` & `33e60295` (`ATT-6279`), `8b0eba41` (`ATT-8081`), `23317f58` & `e9766625` (`ATT-8575`), `eba66320` (`ATT-8576`), `60472a32` (`ATT-8881`), `b6a1eaea` (`ATT-9063`).
3. *Root Reason for Existing Formulation*: `ATT-6279` assumed `±2 m` smooth DEM (`DEFAULT_VAR_DEM = 4.0`) and coupled DEM innovations directly into `slope` without barometric step-discrepancy gating or Joseph SPD updates; `ATT-9063` fixed offline ERTS (`ExtendedRtsAltitudeSlopeSmoother.kt`) without modifying the live EKF.
4. *Preservation of Core Invariants*: `DEFAULT_VAR_BARO = 0.25` and 2-state `p00 = varBaro` unchanged; `REQ-FIL-022` `+130.4 m` initial weather-offset anchoring & `resetAscentDescentAnchor()` preserved; `REQ-FIL-020.1`/`.2` DEM precedence & `2.0 m` hysteresis preserved; `REQ-FIL-023.2`/`.3` standstill slope clearing & low-speed damping preserved; `REQ-FIL-026` 1-Hz baro bucketing & adaptive jitter preserved; `KinematicAltitudeSlopeEkf.kt` & `AltitudeAndSlopeDevice_ATTFused.kt <= 1,500` LoC (`0` protected monolith edits); `0` DB schema bumps; `0` new `src/main` reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

---

## 2. Test Specification (`TST-FIL-037`)

* **`TST-FIL-037.1` (Coherent Variance Retuning, Baro/Standstill Slope Decoupling & 3×3 Joseph SPD Suite — `KinematicAltitudeSlopeEkfTest.kt`)**:
  - Assert `DEFAULT_VAR_DEM == 25.0`, `DEFAULT_VAR_GPS == 64.0` (`DEFAULT_VAR_GPS > DEFAULT_VAR_DEM`), and `DEFAULT_VAR_BARO == 0.25`.
  - Verify `13–21 m` DEM grid jumps while barometer is active & flat leave `ekf.slope` decoupled (`|slopePercent| < 1%`), `|Δaltitude|` strongly damped, and 3×3 covariance symmetric positive-definite (`p00, p11, p22 > 0`, `pij == pji`, positive principal minors / determinant).
  - Verify barometer-free DEM-only climb (`!hasBarometerSample`) still updates `ekf.slope > 0` cleanly.
* **`TST-FIL-037.2` (Baro-vs-DEM Step-Discrepancy Gating, Tracker Advancement, Huber Variance Inflation & High-Precision Anchor Guard Suite — `KinematicAltitudeSlopeEkfTest.kt`)**:
  - Verify transient `440 -> 427 -> 440 m` and `±21 m` DEM bounces with flat barometer (`|Δbaro| < 0.5 m`) inflate `effectiveVarRef` on both edges (`|Δalt| < 0.5 m`, `0` slope spike) without collapsing `p00`.
  - Verify persistent coarse-GPS (`453 m`) -> DEM (`438 m`) recovery (`testThreeStateGpsBeforeDem`) still converges within `±1.0 m` via `lastRefAltM`/`lastRefBaroAltM` advancement on ticks `2..10`, gradual weather drift still absorbs into `baroBias`, and high-precision reference anchors (`varRef = 0.5 <= 1.0`, `enableStepGating = false`) apply unimpeded.
* **`TST-FIL-037.3` (End-to-End Pixel 10 Loop Replay & Contract Suite — `AltitudeAndSlopeDevice_ATTFusedTest.kt`, Contracts)**:
  - Replay a synthetic Pixel 10 `Kurze Runde var 2 #53` loop (`~60 m` true climb/descent at `2.0 m/s` with `13–21 m` DEM grid-cell bounces + gradual baro drift) through `AltitudeAndSlopeDevice_ATTFused`: assert live `ASCENT` and `DESCENT` in `55–75 m` (not `167 m`), live `|SLOPE|` plausible (no `-35%/+34%` artifacts), `|Δalt/dt|` smooth, and closing `ALTITUDE` anchored near true MSL.
  - Verify `ArchitectureConformanceContractTest` (`0` protected monolith edits), `DeadCodeEliminationContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SourceTextReadingAuditContractTest`, and `Localization Parity: N/A - no UI strings`.

---

## 3. Traceability Matrix

| Test Case | Scope | Test Suite(s) | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-FIL-037.1` | Coherent `DEFAULT_VAR_DEM`/`GPS`, baro/standstill slope decoupling & 3×3 Joseph SPD | `KinematicAltitudeSlopeEkfTest` | `REQ-FIL-037.1` | Planned |
| `TST-FIL-037.2` | Step-discrepancy gating, tracker advancement, Huber variance inflation & `varRef <= 1.0` guard | `KinematicAltitudeSlopeEkfTest` | `REQ-FIL-037.2` | Planned |
| `TST-FIL-037.3` | Synthetic Pixel 10 loop live ascent/descent/slope/MSL closure & governance contracts | `AltitudeAndSlopeDevice_ATTFusedTest`, `ArchitectureConformanceContractTest`, `DeadCodeEliminationContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SourceTextReadingAuditContractTest` | `REQ-FIL-037.3` | Planned |
