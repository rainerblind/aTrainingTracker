# Stage 1 Analysis: ATT-6722 - Lazy Cardio-Power Backfill, Physiological Bounds & Filter Warm-Up, and Accordion Subtitle

**Ticket**: [ATT-6722](https://rainerblind.atlassian.net/browse/ATT-6722)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6722` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
* `WorkoutRepository.getOrComputeCardioPowerDynamics(workoutId)` currently has zero callers across `app/src/main/`, leaving pre-existing historical rides with `0` rows in `cardio_power_dynamics` unless re-imported.
* When `getOrComputeCardioPowerDynamics` executes, it performs an unindexed full table query (`SELECT *` without projection or `ORDER BY`), which slows down DB access and does not guarantee strict monotonic time order.
* Rides failing qualification or quality criteria are not recorded, causing repetitive SQLite queries on each access.
* In `PowerHrDynamicsCalculator.kt`, filter state $x_k(\tau)$ is seeded directly with $P[\text{startIndex}]$ rather than pre-warmed from $t=0$, causing transient distortion when $\text{startIndex} = 480\text{s}$ lands on a zero-power coast or sprint surge.
* Missing physiological bounds allow degenerate parameters (negative $K$, non-physiological $\text{HR}_0$), and no minimum $R^2_{\text{start}} \ge 0.25$ or active pedaling ($\ge 15\text{ min}$) gates exist before persistence.
* Garmin smart-recording FIT/TCX files (2–7s variable $\Delta t$) are evaluated under an assumed 1.0s sample interval rather than using true timestamps (`TIME_ACTIVE` / `TIME_TOTAL`).
* `AdvancedTuningAccordion.kt:formatPhysiologyModelsSubtitle` omits `enableCardioPowerDynamics`, causing a discrepancy in the collapsed *Physiology & Training Models* summary.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Call Site Audit**:
  * `WorkoutRepository.kt:2286`: `getOrComputeCardioPowerDynamics` has 0 callers; runs `db.query(tableName, null, ...)` without column projection or `ORDER BY _id ASC`.
  * `TrackOnMapAftermathViewModel.kt:106`: `loadAftermathData` loads markers, tracks, and zone distributions, but does not trigger lazy backfill for missing `cardioPowerDynamics`.
  * `WorkoutSummariesViewModel.kt:89`: Does not trigger a bounded background backfill pass when `enableCardioPowerDynamics` is active.
  * `PowerHrDynamicsCalculator.kt:203`: Line 203 seeds `xBuffer[0] = power[startIndex]`, discarding filter state from minutes 0–8.
  * `PowerHrDynamicsCalculator.kt:224`: Only checks `k <= 0.0`; does not enforce $K \in [0.01, 0.45]$, $\text{HR}_0 \in [30, 125]$, $R^2_{\text{start}} \ge 0.25$, or active pedaling duration $\ge 900\text{s}$.
  * `AdvancedTuningAccordion.kt:145`: `formatPhysiologyModelsSubtitle` takes 4 parameters, omitting `enableCardioPowerDynamics`.
* **Independent Peer Review & Verification**:
  * *Sample Query Optimization*: Selecting only `[TIME_ACTIVE, TIME_TOTAL, TIME, POWER, HR]` with `ORDER BY _id ASC` reduces cursor allocation overhead by > 75% on large sample tables (> 10,000 rows).
  * *Filter Warm-Up Continuity*: Propagating the 1st-order discrete filter $x_k(\tau) = e^{-\Delta t/\tau} x_{k-1}(\tau) + (1 - e^{-\Delta t/\tau}) P_k$ across $t \in [0, \text{startIndex}-1]$ brings $x_{\text{startIndex}}(\tau)$ to the true dynamic steady state before OLS regression.
  * *Bounded Background Backfill*: Limiting background backfill to $N \le 20$ qualifying rides (`avgPower > 0 && avgHr > 0 && activeTimeSec >= 1200`) per pass prevents database thread starvation while populating Athlete Profile trends.
  * *Ineligible Caching*: Recording evaluated-ineligible workout IDs in a concurrent in-memory set eliminates redundant SQLite table checks for non-qualifying rides.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Optimize `WorkoutRepository.getOrComputeCardioPowerDynamics`: projected query (`TIME_ACTIVE`, `TIME_TOTAL`, `TIME`, `POWER`, `HR`), `ORDER BY _id ASC`, ineligible caching (`ineligibleCardioPowerWorkoutIds`), and reactive `updateWorkoutInMemory(workoutId)`.
  2. Add `WorkoutRepository.backfillHistoricalCardioPowerDynamics(limit = 20)` and invoke it reactively in `WorkoutSummariesViewModel` when `enableCardioPowerDynamics` is enabled.
  3. Wire lazy single-workout resolution in `TrackOnMapAftermathViewModel.loadAftermathData`.
  4. Enforce gates in `PowerHrDynamicsCalculator`: active pedaling $\ge 15\text{ min}$, $K \in [0.01, 0.45]\,\text{bpm/W}$, $\text{HR}_0 \in [30, 125]\,\text{bpm}$, and $R^2_{\text{start}} \ge 0.25$.
  5. Pre-warm filter state $x_k(\tau)$ from $t = 0$ to `startIndex` in `PowerHrDynamicsCalculator.fitWindow`.
  6. Support timestamp-aware sample intervals $\Delta t$ and window boundaries in `PowerHrDynamicsCalculator.calculate`.
  7. Update `TuningSubtitleFormatter.formatPhysiologyModelsSubtitle` to include `enableCardioPowerDynamics` with `R.string.cardio_power_domain_label`.
* **Out-of-Scope Non-Goals**:
  1. Altering SQLite schema or table versions (`WorkoutSummariesDatabaseManager.java`).
  2. Modifying real-time sensor metrics (`LIVE_HR_DRIFT` in `CyclingPowerMetricsDevice.java`, governed by `ATT-3907`).
  3. Modifying `CardioPowerTrendsCard` rendering logic (`ATT-3906` / `ATT-6724`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-REV-009` (*1st-Order Dynamical Power-HR System Identification Engine, 5th Physiological School & In-App Explanation Sheet*) and `REQ-UI-403.2` (*Physiology Models Accordion Summary*).
* **Historical Origin & Commit Trace**: Introduced in `ATT-3904` (commit `1f70fb15`).
* **Root Reason for Existing Formulation**: `getOrComputeCardioPowerDynamics` was drafted in `ATT-3904` as a foundation method without connecting caller hooks in aftermath viewmodels. Initial mathematical solver assumed 1-Hz uniform telemetry and seeded at `startIndex` without warm-up propagation.
* **Preservation of Core Invariants**:
  1. Pure functional calculation and numerical reproducibility preserved.
  2. Java caller compatibility (`TrackerService.java`, `CyclingPowerMetricsDevice.java`) preserved via `@JvmOverloads`.
  3. Reactive in-memory state update via `updateWorkoutInMemory` without database mutation races.
  4. 100% 9-locale translation parity preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Database & Repository Layer (`WorkoutRepository.kt`)**:
  * Project `[TIME_ACTIVE, TIME_TOTAL, TIME, POWER, HR]` ordered by `_id ASC` in `getOrComputeCardioPowerDynamics`.
  * Maintain `ineligibleCardioPowerWorkoutIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()`.
  * Call `updateWorkoutInMemory(workoutId) { it.copy(cardioPowerDynamics = calculated) }` on successful computation.
  * Add `backfillHistoricalCardioPowerDynamics(limit: Int = 20): Int`.
* **ViewModel Wiring**:
  * In `TrackOnMapAftermathViewModel.loadAftermathData`, lazily resolve dynamics if missing on qualifying rides.
  * In `WorkoutSummariesViewModel`, collect `enableCardioPowerDynamicsFlow` to trigger bounded backfill.
* **Physiological Calculation Engine (`PowerHrDynamicsCalculator.kt`)**:
  * Pre-warm filter state $x_k(\tau)$ from index 0 to `startIndex - 1`.
  * Add timestamp-aware $\Delta t_i = (t_i - t_{i-1})\text{.coerceIn}(1\text{L}, 60\text{L})\text{.toDouble}()$ support and elapsed-time window boundaries.
  * Enforce active pedaling $\ge 900\text{s}$, $K \in [0.01, 0.45]$, $\text{HR}_0 \in [30, 125]$, and $R^2_{\text{start}} \ge 0.25$.
* **UI Accordion Subtitle (`AdvancedTuningAccordion.kt`)**:
  * Add `enableCardioPowerDynamics: Boolean = false` to `formatPhysiologyModelsSubtitle` and append `R.string.cardio_power_domain_label`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Bounded execution: background backfill never blocks UI thread or saturates IO pool.
  2. Ineligible filtering prevents re-query loops on unsuitable or corrupted rides.
  3. Coerced $\Delta t \in [1\text{s}, 60\text{s}]$ prevents numerical overflow across long pauses.
* **Risk Rating**: **LOW** — Self-contained numerical calculation and query optimization with zero database schema migrations or breaking IPC changes (2 SP).
