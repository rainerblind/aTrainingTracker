# Stage 3 Implementation Plan: ATT-6722 - Lazy Historical Cardio-Power Backfill, Plausibility Gates & Accordion Subtitle

**Ticket**: [ATT-6722](https://rainerblind.atlassian.net/browse/ATT-6722)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6722` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Architectural Strategy & High-Level Design
* **Physiology Engine (`PowerHrDynamicsCalculator.kt`)**: Pure numerical 1st-order OE system identification. Implements discrete filter pre-warming $x_k(\tau)$ from $t = 0$ to `startIndex - 1` to eliminate warm-up transient step bias. Enforces physiological gates ($K \in [0.01, 0.45]$, $\text{HR}_0 \in [30, 125]$, $R^2_{\text{start}} \ge 0.25$, active pedaling $\ge 900\text{s}$) and timestamp-aware variable $\Delta t$.
* **Repository & Data Access (`WorkoutRepository.kt`)**: Optimizes `getOrComputeCardioPowerDynamics` with column projection (`TIME_ACTIVE`, `TIME_TOTAL`, `TIME`, `POWER`, `HR`) and `ORDER BY _id ASC`. Maintains in-memory `ineligibleCardioPowerWorkoutIds` (`ConcurrentHashMap.newKeySet<Long>()`) to eliminate redundant queries, triggers reactive `updateWorkoutInMemory`, and exposes `backfillHistoricalCardioPowerDynamics(limit = 20)`.
* **ViewModel & Lifecycle Hooks**: `TrackOnMapAftermathViewModel.loadAftermathData` resolves missing dynamics on qualifying rides; `WorkoutSummariesViewModel` collects `enableCardioPowerDynamicsFlow` to run bounded background backfill.
* **Settings & UI Layer (`AdvancedTuningAccordion.kt`)**: Adds `enableCardioPowerDynamics: Boolean = false` to `formatPhysiologyModelsSubtitle`, appending `R.string.cardio_power_domain_label`.

---

## 2. Atomic Implementation Steps & Reachability Wiring
* **Step 1: Filter Pre-Warming, Plausibility Gates & Variable $\Delta t$ (`PowerHrDynamicsCalculator.kt`)**:
  * In `fitWindow`, propagate $x_k(\tau) = e^{-\Delta t/\tau} x_{k-1}(\tau) + (1 - e^{-\Delta t/\tau}) P_k$ across $0 \le t < \text{startIndex}$.
  * In `fitWindow`, enforce candidate constraints $K \in [0.01, 0.45]$ and $\text{HR}_0 \in [30.0, 125.0]$.
  * In `calculate`, enforce active pedaling $\sum \Delta t (P > 0) \ge 900\text{s}$ and $R^2_{\text{start}} \ge 0.25$; support `timestampsSec: List<Long>?`.
* **Step 2: Sample Query Optimization, Ineligible Cache & Backfill (`WorkoutRepository.kt`)**:
  * Project candidate columns `[TIME_ACTIVE, TIME_TOTAL, TIME, POWER, HR]` ordered by `_id ASC`.
  * Return early if `workoutId in ineligibleCardioPowerWorkoutIds`; populate set if calculation returns `null`.
  * Call `updateWorkoutInMemory(workoutId) { it.copy(cardioPowerDynamics = calculated) }` on success.
  * Implement `suspend fun backfillHistoricalCardioPowerDynamics(limit: Int = 20): Int`.
* **Step 3: Aftermath & Summaries ViewModel Wiring**:
  * In `TrackOnMapAftermathViewModel.loadAftermathData`, lazily invoke `getOrComputeCardioPowerDynamics` for qualifying rides with null dynamics.
  * In `WorkoutSummariesViewModel`, launch coroutine collecting `tuningDataStore.enableCardioPowerDynamicsFlow` to trigger `backfillHistoricalCardioPowerDynamics(20)`.
* **Step 4: Accordion Subtitle Formatting Parity (`AdvancedTuningAccordion.kt`)**:
  * Update `TuningSubtitleFormatter.formatPhysiologyModelsSubtitle` signature and append `context.getString(R.string.cardio_power_domain_label)`.
  * Pass `enableCardioPowerDynamics` in `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`.
* **Step 5: Unit Tests (`PowerHrDynamicsCalculatorTest`, `WorkoutRepositoryCardioPowerBackfillTest`, `AdvancedTuningAccordionTest`)**:
  * Add unit tests for pre-warm continuity, plausibility rejection, timestamp-aware identification, query projection, ineligible caching, and subtitle formatting.

---

## 3. Non-Negotiable Invariants & Rollback Safety
1. **Thread & Pool Safety**: Backfill is strictly dispatched on `Dispatchers.IO` and bounded to $\le 20$ workouts to prevent SQLite starvation.
2. **Java Compatibility**: `@JvmOverloads` on `PowerHrDynamicsCalculator.calculate` preserved for `TrackerService.java`.
3. **9-Locale Parity**: Reuses existing `cardio_power_domain_label` without adding unlocalized string tokens.

---

## 4. Test Verification & Tier 1 Commands
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculatorTest"`
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAccordionTest"`
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutRepositoryCardioPowerBackfillTest"`
