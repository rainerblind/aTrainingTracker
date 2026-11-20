# Stage 2: Requirement & Test Specification — ATT-9150

**Ticket**: [ATT-9150](https://rainerblind.atlassian.net/browse/ATT-9150) (`1 SP`, Medium) | **Release**: `V5.0.0`  
**Branch**: `feature/ATT-9150` | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-20  
**Requirement Mapping**: `REQ-MIG-054` (`[Planned]`, `SYSREQ-DEX-004`)  
**Test Mapping**: `TST-MIG-052` (`[Planned]`)  

---

## 1. Requirement Attributes & Gate 1 Advisories
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **Localization Parity**: `N/A - no UI strings` (`0` XML resource edits).
- **`SYS.2` / `SYS.3` Impact (`REQ-PRO-131.4`)**: None (`SYSREQ-DEX-004` already covers import Strava opt-out persistence; zero hardware/HAL/external API changes).
- **Gate 1 Advisories Incorporated**:
  1. **Tri-State `UPLOAD_TO_STRAVA` Guard (`cursor.isNull(0)` + `-1` only)**: `WorkoutSummariesDatabaseManager.applyInferredIdentity()` (`lines 322-344`) SHALL query `UPLOAD_TO_STRAVA` using `try (Cursor ...)` (for `lint_cursor_safety.py` compliance) and update `UPLOAD_TO_STRAVA` ONLY when the queried row exists AND the column is SQL `NULL` (`cursor.isNull(0)`) or `-1` (schema `DEFAULT -1`). Explicit `0` (opt-out) and `1` (opt-in) MUST never be overwritten. If `workoutId` does not exist (`!cursor.moveToFirst()`), `applyInferredIdentity()` MUST execute cleanly as a safe no-op without throwing.
  2. **LoC Ceiling Preservation**: Keep `WorkoutSummariesDatabaseManager.java <= 3,148` lines (`ArchitectureConformanceContractTest.kt:120` ceiling = `3,148`, `0` net lines added).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: Net-new `REQ-MIG-054` & `TST-MIG-052` hardening existing `[Verified]` `REQ-MIG-028` (`TST-MIG-025`) and `REQ-SET-029`/`030` (`WorkoutSummariesDatabaseManager.java:334-338`).
- **Historical Origin & Commit Trace**: `e0392e39` (`SCRUM-200`) introduced `applyInferredIdentity()` overwriting `UPLOAD_TO_STRAVA`; `b58ca19b` (`ATT-1116`) guarded `currentUpload != 1` to protect `1` without distinguishing explicit `0` opt-out from undecided `-1` (`DEFAULT -1` at `lines 2554, 2857`) or SQL `NULL` masked to `0` by `getInt()`.
- **Root Reason for Existing Formulation**: `ATT-1116` assumed non-`1` meant undecided; in reality, `-1` and `NULL` mean undecided while `0` means explicit "no" (`(-1: check preferences, 0: no, 1: yes)`).
- **Preservation of Core Invariants**: Live workouts (`DEFAULT -1`) still receive `identity.getUploadToStrava()` (`1` or `0` per `REQ-EXP-006`); explicit `1` opt-ins remain protected; `getInt()` stays untouched for all other callers; `WorkoutSummariesDatabaseManager.java <= 3,148` LoC.

---

## 3. Acceptance Criteria & Test Cases (`Given-When-Then`)

### `REQ-MIG-054`: Preserve Explicit Strava Upload Opt-Out During Inferred Identity Application (`TST-MIG-052`)
1. **Empty-DB TCX Import Opt-Out (`TcxImportStravaToggleTest`, removes cluster-seed workaround)**:
   - **Given** an empty `RouteClusters.db` (`0` clusters) and Strava connected with a valid Strava sport mapping,
   - **When** `LegacyImportEngine.importFromTcxResult(..., uploadToStrava = false)` runs and `refreshClusters()` triggers self-healing `migrateHistory()` -> `assignClusterToWorkout()` -> `applyInferredIdentity()`,
   - **Then** `WorkoutSummaries.UPLOAD_TO_STRAVA` SHALL remain `0`, and `ExportManager.exportWorkoutTo(..., STRAVA)` SHALL have `0` invocations.
2. **Live Workout `DEFAULT -1` Inference (`WorkoutSummariesDatabaseManagerApplyIdentityTest`)**:
   - **Given** a newly tracked workout row with `UPLOAD_TO_STRAVA = -1` (schema default),
   - **When** `applyInferredIdentity(workoutId, identity)` is invoked with `uploadToStrava = 1` (and separately `uploadToStrava = 0` for `REQ-EXP-006`),
   - **Then** `UPLOAD_TO_STRAVA` SHALL update to `1` (or `0` respectively) along with `SPORT_ID`, `B_SPORT`, and `EQUIPMENT_ID`.
3. **SQL `NULL` Row Inference**:
   - **Given** a legacy/synthetic workout row whose `UPLOAD_TO_STRAVA` cell is SQL `NULL`,
   - **When** `applyInferredIdentity(workoutId, identity)` runs with `uploadToStrava = 1`,
   - **Then** `cursor.isNull(0)` detects SQL `NULL` (avoiding `getInt()`'s `NULL -> 0` coercion) and updates `UPLOAD_TO_STRAVA` to `1`.
4. **Explicit `0` and Explicit `1` Preservation**:
   - **Given** rows with `UPLOAD_TO_STRAVA = 0` (explicit opt-out) or `UPLOAD_TO_STRAVA = 1` (explicit opt-in),
   - **When** `applyInferredIdentity(workoutId, identity)` runs with opposing `identity.getUploadToStrava()` (`1` vs `0`),
   - **Then** `UPLOAD_TO_STRAVA = 0` stays `0` and `UPLOAD_TO_STRAVA = 1` stays `1`, while `SPORT_ID`/`B_SPORT`/`EQUIPMENT_ID` still update cleanly.
5. **Non-Existent `workoutId` Safe No-Op**:
   - **Given** a `workoutId` absent from `WorkoutSummaries`,
   - **When** `applyInferredIdentity(999999L, identity)` is invoked,
   - **Then** no exception is thrown and `0` rows are modified.

---

## 4. Bidirectional Traceability & Governance
| Requirement ID | Status | Priority | Type | Verification Method | System Req | Linked Test Spec | Target Test File(s) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `REQ-MIG-054` | `[Planned]` | High | Functional | Unit | `SYSREQ-DEX-004` | `TST-MIG-052` | `TcxImportStravaToggleTest.kt`, `WorkoutSummariesDatabaseManagerApplyIdentityTest.kt` |
