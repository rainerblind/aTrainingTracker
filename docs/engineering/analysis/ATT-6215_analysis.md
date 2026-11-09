# Stage 1 Analysis: ATT-6215 - Fix duplicate bikes/shoes & auto-reconcile duplicates

**Ticket**: [ATT-6215](https://rainerblind.atlassian.net/browse/ATT-6215)  
**Sub-task**: [ATT-6259](https://rainerblind.atlassian.net/browse/ATT-6259) (`[Analysis]`)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile (Gear, ANT+/BLE & PMC)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Branch**: `feature/ATT-6215`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
In *Mein Profil -> Ausrüstung*, duplicate bikes/shoes appear (e.g. `'Endurance'`, `'endurance '`, `'endurance'`; `'2Danger'`). This degrades UX and fractures workout odometer tracking across duplicate equipment records.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
Three interacting defects across `EquipmentDbHelper.java`, `StravaBulkExportImporter.kt`, and `StravaEquipmentSynchronizeThread.java`:
1. **Case-Sensitivity & Trailing Whitespace**: `EquipmentDbHelper.getEquipmentId` (L601) and `StravaEquipmentSynchronizeThread` (L204, L244) query exact `NAME = ?` without `TRIM()` or `COLLATE NOCASE`, ignoring `STRAVA_NAME`. Remote `'endurance '` fails to match local `'Endurance'`, creating duplicate entries.
2. **`StravaId` Name Pollution**: `StravaBulkExportImporter.kt` (L326, L351, L374) passes `bike.name` / `shoe.name` into `addOrUpdateStravaGear` as `stravaId`. This stores plaintext names in `StravaId`, which later prevents Strava API sync from linking unlinked gear (`STRAVA_ID IS NULL OR STRAVA_ID=''`), spawning secondary duplicates.
3. **Local Name Overwrite**: `addOrUpdateStravaGear` unconditionally overwrites user-customized `NAME` with remote `name`.
* **Independent Peer Review & Verification**:
  * Verified live code: `getEquipmentId` strictly executes `NAME = ?`. `StravaBulkExportImporter` passes name to `stravaId`. `StravaEquipmentSynchronizeThread` duplicates gear insertion logic with un-trimmed case-sensitive matching.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Normalize lookups in `EquipmentDbHelper` (`getEquipmentId`, `addOrUpdateStravaGear`) and `StravaEquipmentSynchronizeThread` to `TRIM(Name) = TRIM(?) COLLATE NOCASE OR TRIM(StravaName) = TRIM(?) COLLATE NOCASE`.
  * Preserve local customized `NAME` during Strava gear updates (only update `STRAVA_ID`, `STRAVA_NAME`, `FRAME_TYPE`, `RETIRED`).
  * Fix `StravaBulkExportImporter` to keep `StravaId = null` (populating only `StravaName`).
  * Add automatic reconciliation/migration to deduplicate existing records in `Equipment.db`, migrate `LINKS` and `SportTypeEquipmentLink` entries, reassign `WorkoutSummaries.equipmentId`, and prune duplicates.
* **Out-of-Scope Non-Goals**:
  * Refactoring Strava OAuth or token refresh workflows.
  * UI layout changes to Compose equipment management screens.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-EXT-006` (Strava Equipment Sync Integrity), `REQ-EXT-009.4` (Reauthorization Deduplication), and `REQ-MIG-048.4` (Bulk Import Equipment Matching).
* **Historical Origin & Commit Trace**: `ATT-588` (commit `06990c88` - `REQ-EXT-006`), `ATT-4957` (commit `33856f04` - `REQ-MIG-048`).
* **Root Reason for Existing Formulation**: Early gear lookups assumed exact string parity and did not anticipate case/space differences or ZIP exports lacking real Strava IDs (`b...`/`g...`).
* **Preservation of Core Invariants**: Sensor link bindings (`LINKS`), sport type associations (`SportTypeEquipmentLink`), workout telemetry metrics, and Strava API compliance remain strictly intact.

---

## 5. Architectural Strategy & High-Level Solution
1. **Case-Insensitive & Trimmed Lookup**:
   * Update `EquipmentDbHelper.getEquipmentId` and `addOrUpdateStravaGear` to match on trimmed, case-insensitive `NAME` and `STRAVA_NAME`.
   * Unify `StravaEquipmentSynchronizeThread` to delegate to `EquipmentDbHelper.addOrUpdateStravaGear` (DRY).
2. **Null `StravaId` in Bulk Import**:
   * Overload or adapt `addOrUpdateStravaGear` to accept `@Nullable String stravaId`. `StravaBulkExportImporter` passes `null` for `stravaId`.
3. **Self-Healing Deduplication Migration**:
   * Introduce `EquipmentReconciliationHelper` (or `EquipmentDbHelper.reconcileDuplicates()`), triggered during schema upgrade (`Equipment.db` `DB_VERSION` 2 -> 3) and app startup/sync.
   * Merge duplicates by sport type and normalized name: preserve canonical capitalized `NAME`, real `StravaId`, and `RETIRED` state; re-link `LINKS` and `SportTypeEquipmentLink`; reassign `WorkoutSummaries.equipmentId`; delete duplicate rows.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. No workout data loss: all historical `WorkoutSummaries.equipmentId` links safely migrate to surviving equipment.
  2. Hardware pairings (`LINKS`) and sport assignments (`SportTypeEquipmentLink`) preserved without orphaned records.
  3. Parent ticket Human Decision Gate strictly enforced.
* **Risk Rating**: **MEDIUM** (Touches SQLite persistence across `Equipment.db` and `WorkoutSummaries.db`; mitigated by transactional migration and contract unit tests).
