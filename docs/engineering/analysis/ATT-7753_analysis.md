# Stage 1 Analysis: ATT-7753 - Per-bike Front/Rear wheel circumference time-series history & GPS-free dual-wheel iTPMS puncture detection

**Ticket**: [ATT-7753](https://rainerblind.atlassian.net/browse/ATT-7753)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Branch**: `feat/ATT-7753`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Currently, `BANALServiceRepository.kt` connects `IndirectTpmsEstimator.kt` (ATT-4880) using `activeDevices?.firstOrNull` for a single wheel speed sensor evaluated against GNSS ground displacement without persisting circumference history across workouts. This presents three severe limitations:
1. **Single-Wheel Blind Spot**: Punctures on the unmonitored wheel (e.g. rear when only front is instrumented) are completely undetected.
2. **GNSS Noise & Canopy Dropout**: Gating on GNSS distance requires 1.5–2.0 km of open-sky riding and fails in tunnels or deep forest canopy.
3. **No Longitudinal Degradation History**: Athletes cannot track tire pressure diffusion or wear trends over weeks of rides.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Dual-Wheel Revolution Ratio ($\rho$) Elimination of GNSS**: When a bike is equipped with both `FRONT` and `REAR` speed sensors (enabled by ATT-7752 mounting positions), the revolution ratio $\rho_k = \frac{\Delta N_{rear,k}}{\Delta N_{front,k}} = \frac{C_{front}}{C_{rear}}$ is completely independent of GNSS. Gating strictly on low yaw-rate ($|\omega_{ENU,z}| < 5^\circ/\text{s}$) excludes cornering curvature in hairpins, enabling fast CUSUM detection of slow punctures within 150–250 m ($\sim 15\text{--}25\text{ s}$) in any environment while pinpointing the specific deflating tire.
* **Dual Architecture (Ratio + Absolute GNSS)**: If both tires lose pressure equally in storage, $\rho$ remains constant. Therefore, independent per-sensor GNSS estimators (`IndirectTpmsEstimator`) must run concurrently for each active wheel sensor to trigger multi-day storage pump reminders.
* **Circumference History Database**: Post-workout converged circumferences from `WheelCircumferenceCalibrator` / `IndirectTpmsEstimator` must be persisted in SQLite table `WheelCircumferenceHistory` in `Equipment.db` and rendered as a dual-curve time series (Front vs. Rear) per bike with a *"Reifen aufgepumpt"* baseline reset action (`Rule 84`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. `DualWheelTpmsEstimator`: GPS-free dual-wheel revolution ratio CUSUM detector with yaw-rate gating ($|\omega_{ENU,z}| < 5^\circ/\text{s}$), locking $\rho_{start}$ in first 300 m and alerting on $\ge 0.8\%$ deviation within 150–250 m with wheel specificity (*Vorderrad* vs. *Hinterrad*).
  2. Multi-sensor `BANALServiceRepository` telemetry dispatcher mapping all active wheel speed sensors to individual GNSS estimators in parallel.
  3. `WheelCircumferenceHistory`: SQLite table in `Equipment.db`, repository query APIs, and post-workout persistence.
  4. Time-series chart in Bike Equipment UI showing Front and Rear circumference history with baseline reset action (`Rule 84`).
  5. 9-language localization parity across all new TPMS alert and UI strings.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Real-time tire temperature modeling or external physical direct TPMS Bluetooth valve cap sensor protocols.
  2. Accelerometer-based road surface roughness estimation.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-SEN-030`, extending `REQ-TRK-050` (*iTPMS Single-Sensor GNSS*, `ATT-4880`) and `REQ-SEN-029` (*Wheel Mounting Positions*, `ATT-7752`).
* **Historical Origin & Commit Trace**: `ATT-4880` (Sprint 2026-44.2, commit `8d400192`, single-wheel iTPMS) and `ATT-7752` (Sprint 2026-46.6, commit `4edcda86`, front/rear position tracking).
* **Root Reason for Existing Formulation**: Baseline only supported one wheel sensor due to absence of mounting position metadata in `Equipment.db`.
* **Preservation of Core Invariants**: 100% backward compatibility for single-wheel sensor bikes; single-sensor GNSS alerts continue to operate identically; 100% clean-room test pass rate.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-SEN-030` (`docs/requirements/req_tracking_cockpit.md`), `TST-SEN-030` (`docs/tests/tst_tracking_cockpit.md`).
* **Affected Architecture Elements**: `DualWheelTpmsEstimator.kt`, `IndirectTpmsEstimator.kt`, `BANALServiceRepository.kt`, `EquipmentDbHelper.java`, `EquipmentRepository.kt`, `EquipmentViewModel.kt`, `WheelCircumferenceHistoryChart.kt`, `strings.xml` (9 locales).
* **Verification Impact & Test Surface**: Dual-wheel ratio simulation tests (front puncture, rear puncture, hairpin gating), multi-sensor telemetry routing tests, SQLite circumference history tests, Compose chart rendering tests, translation parity.
* **Risk Assessment & Invariants**: Low risk. Mathematical ratio detector is decoupled from GPS pipelines; database table is additive; fallback to single-wheel iTPMS is seamless.

---

## 6. Architectural Strategy & High-Level Solution
1. **Core Math Engine**: Implement `DualWheelTpmsEstimator` tracking $\rho_k = \frac{\Delta N_{rear,k}}{\Delta N_{front,k}}$. Gated on $|\omega_{ENU,z}| < 0.087\text{ rad/s}$ ($5^\circ/\text{s}$) and $v \ge 3.0\text{ m/s}$. Lock $\rho_{start}$ after 300 m. Alert with `CRITICAL` severity when deviation $|\delta| \ge 0.008$.
2. **Repository Wiring**: In `BANALServiceRepository.kt`, maintain `activeWheelEstimators: Map<Long, IndirectTpmsEstimator>`. When both `FRONT` and `REAR` sensors are present on active bike, feed samples to `DualWheelTpmsEstimator`.
3. **Database & UI**: In `EquipmentDbHelper.java` (`DB_VERSION = 5`), add table `WheelCircumferenceHistory`. Persist post-workout results. In Compose UI, render `WheelCircumferenceHistoryChart` displaying Front and Rear curves over time.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Single-wheel bikes maintain exact baseline behavior of `REQ-TRK-050` with zero regression.
  2. Hairpin turns and sharp corners produce 0.0% false puncture alerts.
  3. All UI strings verified with 100% 9-language localization parity and zero text clipping (`Rule 84`).
* **Risk Rating**: **MEDIUM** — Core mathematical ratio detector is pure Kotlin unit-testable; UI chart and SQLite persistence follow standard proven patterns.
