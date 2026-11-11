# Stage 1 Analysis: ATT-7159 - Reject Altitude Spikes, Validate Workout Climbs via DEM & Clean Climbs.db

**Ticket**: [ATT-7159](https://rainerblind.atlassian.net/browse/ATT-7159)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3`  
**Branch**: `bugfix/ATT-7159` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
On-device inspection revealed impossible climbs persisted in `Climbs.db` (e.g. `541m @ 104.6%` with `max_grade = 367.4%` on a flat residential street in Schönaich) and dozens of phantom climbs generated from 2018–2019 winter commute rides where cold-air barometer thermal drift (`+50m` to `+70m` over 1–2 km) converted flat or downhill roads (`DEM -9.6m`) into stored 4–8% climbs. These phantom ascents pollute free-ride ClimbPro in the `Höhe` cockpit.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * `ClimbDetector.detectClimbs()` lacks vertical jump and physical plausibility guards: it integrates single-step GPS jumps (`+154m` / `367%`) and calibration snap cliffs (`-688m` drop immediately following summit).
  * `WorkoutRepository.extractAndPersistClimbsFromWorkout()` directly trusts raw 1-Hz barometric/GPS `ALTITUDE` samples without DEM ground-truth cross-checking.
  * Existing `Climbs.db` instances populated by `ATT-6691` (`climbs_workout_backfill_v1`) contain corrupted `route_id IS NULL` rows that persist until purged.
* **Independent Peer Review & Verification**:
  * Audited `ClimbDetector.kt` (lines 79–142): Verified zero upper gradient bounds, zero single-step $\Delta\text{alt}$ clamp, and zero post-summit drop check.
  * Audited `WorkoutRepository.kt` (lines 3979–3999): Confirmed `extractAndPersistClimbsFromWorkout` bypasses `ElevationCacheDatabaseManager`.
  * Verified: `ElevationCacheDatabaseManager.getElevation(lat, lon)` is available on `Dispatchers.IO` for point DEM queries.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Goals**:
  * Add step-jump guards (`|deltaAlt| > 25m` or gradient `> 45%` over `d >= 10m`), plausible limits (`avgGrade <= 25%`, `maxGrade <= 45%`), and post-peak calibration snap rejection (`> 35m` drop in `< 50m`) to `ClimbDetector.detectClimbs()`.
  * Add DEM ground-truth cross-validation in `WorkoutRepository.kt` for workout climbs (`routeId == null`): reject if DEM gain `< minGain * 0.75` / DEM grade `< 2.2%`, or if barometric drift exceeds `max(demGain * 1.6 + 18.0, demGain + 35.0)`.
  * Implement idempotent cleanup migration `PREF_CLIMBS_WORKOUT_CLEANUP_V2` in `WorkoutRepository` purging invalid `route_id IS NULL` rows in `Climbs.db` and refreshing `LiveClimbsRepository`.
  * Add unit tests covering spike rejection, thermal drift filtering, and `v2` cleanup.
* **Out-of-Scope Non-Goals**:
  * Altering route climb detection for planned GPX routes (which already use DEM elevations).
  * Altering `Climbs.db` table schema or index definitions.
  * Modifying cockpit sheet UI or chart renderers.
* **Complexity Audit**: Scope is bounded to 3 files within Climbs/Aftermath domain. Estimated at 3 SP (Standard Complex, split not required).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-MAP-064` (*Workout Climb Ingestion*) & `REQ-MAP-066` (*Historical Climb Backfill*), targeting `ClimbDetector.kt` and `WorkoutRepository.kt`.
* **Historical Origin & Commit Trace**: `ATT-2766` (`c7438e78`, workout climb ingestion), `ATT-6691` (`8d42cf31`, backfill v1).
* **Root Reason for Existing Formulation**: Assumed phone barometers and GPS altitude were continuous and drift-free, ignoring cold-air thermal drift and GPS cold-start altitude acquisition leaps.
* **Preservation of Core Invariants**: Climb detection formula ($\text{Score} = \text{length} \times \text{gradient}$), category thresholds (`HC`..`CAT_4`), and 50m spatial deduplication remain strictly intact.

---

## 5. Architectural Strategy & High-Level Solution
1. **Spike Filtering in `ClimbDetector.kt`**: Split ascents on non-physical discontinuities; drop candidates with `avgGrade > 25%`, `maxGrade > 45%`, or post-summit drop `> 35m` within `< 50m`.
2. **DEM Ground-Truth Validation in `WorkoutRepository.kt`**: Query `ElevationCacheDatabaseManager.getElevation()` at start, mid, summit. Filter phantom drift climbs.
3. **Idempotent Self-Healing Migration in `WorkoutRepository.kt`**: Run `cleanupCorruptedWorkoutClimbs()` on startup gating on `PREF_CLIMBS_WORKOUT_CLEANUP_V2`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Real climbs are never dropped; GPX route climbs remain unaffected; `Climbs.db` schema remains intact; single-thread SQLite dispatchers preserved.
* **Risk Rating**: **MEDIUM** (Modifies mathematical climb detector and touches persisted `Climbs.db` entries; mitigated by targeted DEM checks and idempotent migration).
