# Stage 1 Analysis: ATT-7909 - Java Core Migration: Modernize SQLite database managers via repository interfaces

**Ticket**: [ATT-7909](https://rainerblind.atlassian.net/browse/ATT-7909)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Branch**: `feature/ATT-7909`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
Under ADR-001 (`REQ-ARC-005`, Tier 4 Persistence), legacy SQLite database managers (`WorkoutSummariesDatabaseManager`, `WorkoutSamplesDatabaseManager`, `DevicesDatabaseManager` — 6,006 LOC) manage high-frequency telemetry and workout records. While retained in Java per PO decision (`ATT-7749`) to eliminate regression risk in 1-Hz sensor/GPS ingestion, their interfaces and callers require modernization:
- Eliminate direct SQLite manager instantiation and backdoor getters across ViewModel/UI layers (`DevicesTabbedViewModel`, `SportTypeViewModel`, `TrackingViewsRepository`).
- Encapsulate raw `Cursor` extractions into domain DTO mappers (`PairedRemoteDeviceDto`, `WorkoutTelemetrySample`, `WorkoutDataMapper`).
- Enforce `Dispatchers.IO` thread confinement for asynchronous queries, preventing main-thread disk I/O and SQLite lock contention.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * *Ticket Hypothesis*: States 5,421 LOC across the 3 managers, references `TryWithResourcesCursorAuditTest` and `RepositoryCleanlinessContractTest`.
  * *Codebase Reality*: 6,006 LOC (3,084 in `WorkoutSummariesDatabaseManager`, 1,304 in `WorkoutSamplesDatabaseManager`, 1,618 in `DevicesDatabaseManager`).
  * *Caller Audit*:
    1. `DevicesTabbedViewModel` directly calls `DevicesDatabaseManager.getInstance(getApplication()).getDeviceName(deviceId)` and `.getDeviceType(deviceId)` (lines 114, 124), bypassing `DeviceDataRepository` which already exposes these methods.
    2. `SportTypeViewModel` directly instantiates `WorkoutSummariesDatabaseManager.getInstance(application)` to query `getSportTypeStats` and `getSportTypeStatsForPeriod` (lines 60, 95, 137), which belong in `WorkoutRepository` on `Dispatchers.IO`.
    3. `TrackingViewsRepository` exposes `DevicesDatabaseManager` in public method parameters (`getAllFilterData`) and returns internal struct `DevicesDatabaseManager.DeviceIdAndNameLists`, coupling UI views to low-level DB managers.
    4. Cursors: `WorkoutSummariesDatabaseManager.getWorkoutCursor()`, `getCursorForAllWorkouts()`, and `DevicesDatabaseManager.getCursorForAllDevices()` are consumed in repositories, but `WorkoutRepository.kt:3604` and `PeriodsRepository.kt:215` have unshielded calls prior to `.use {}` that can NPE if null.
    5. Merged Features Integrity: Recent merges on `sprint/2026-46.8` introduced critical helpers that must remain untouched: `clearPolarSyncPending()` / `setPolarSyncPending()` (`ATT-8000`) and accumulator/lap recovery preservation (`ATT-7875`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Route `DevicesTabbedViewModel` device name/type queries through `DeviceDataRepository`, eliminating direct `DevicesDatabaseManager.getInstance` in this ViewModel.
  * Expose `getSportTypeStats` and `getSportTypeStatsForPeriod` via `WorkoutRepository` with `withContext(Dispatchers.IO)`, and migrate `SportTypeViewModel` away from direct `WorkoutSummariesDatabaseManager` instantiation.
  * Decouple `TrackingViewsRepository.getAllFilterData()` and `getDeviceLists()` from direct `DevicesDatabaseManager` parameterization, encapsulating them cleanly.
  * Null-harden cursor acquisition paths in `WorkoutRepository` and `PeriodsRepository` (`?.use {} ?: emptyList()`).
  * Add architectural contract assertions in `RepositoryCleanlinessContractTest.kt` verifying `DevicesTabbedViewModel` and `SportTypeViewModel` contain zero `DatabaseManager.getInstance` references.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do NOT rewrite the 6,006 LOC SQLite managers from Java to Kotlin (they remain `Keep` in production Java per ADR-001 §7.1 / `ATT-7749`).
  * Do NOT migrate to Room database library in this ticket (evaluative preparation only).
  * Do NOT alter SQLite schemas, table columns, PRAGMA settings, or `DB_VERSION` constants.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* Net-new requirement only (`REQ-ARC-009` & `TST-ARC-009`). No existing requirements modified.
* *Chesterton's Fence Forensics*: `REQ-ARC-005` (ADR-001) established Tier 4. `REQ-TRK-066` previously decoupled Phase C foreground services (`TrackerService`, `BANALService`, `DeviceManager`). `REQ-ARC-009` extends this architectural boundary to Phase D (ViewModels and UI-facing repositories: `DevicesTabbedViewModel`, `SportTypeViewModel`, `TrackingViewsRepository`), enforcing `Dispatchers.IO` and repository encapsulation without destabilizing the battle-tested Java SQLite engines.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new requirement `REQ-ARC-009` and test `TST-ARC-009` in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`.
* **Affected Architecture Elements**: `DevicesTabbedViewModel.kt`, `SportTypeViewModel.kt`, `TrackingViewsRepository.kt`, `WorkoutRepository.kt`, `DeviceDataRepository.kt`, `RepositoryCleanlinessContractTest.kt`.
* **Verification Impact & Test Surface**: `RepositoryCleanlinessContractTest`, `ArchitectureConformanceContractTest`, `TryWithResourcesCursorAuditTest`, `SportTypeViewModelRetiredTest`, `DevicesTabbedScreenContractTest`, `WorkoutRepositoryConcurrencyTest`.
* **Risk Assessment & Invariants**: Low risk. Refactoring strictly substitutes direct singleton access with existing repository methods. Invariants preserved: 100% test pass rate, zero schema modifications, `FINISHED = 1` transaction guarantees, `ATT-8000` Polar sync pending methods intact.

---

## 6. Architectural Strategy & High-Level Solution
* In `WorkoutRepository.kt`, expose `getSportTypeStats` and `getSportTypeStatsForPeriod` encapsulated on `Dispatchers.IO`.
* In `SportTypeViewModel.kt`, inject `WorkoutRepository` and replace direct `dbSummariesManager` calls.
* In `DevicesTabbedViewModel.kt`, replace `DevicesDatabaseManager.getInstance(...)` with `deviceDataRepository.getDeviceName(...)` and `deviceDataRepository.getDeviceType(...)`.
* In `TrackingViewsRepository.kt`, encapsulate `DevicesDatabaseManager` usage behind clean internal calls.
* In `RepositoryCleanlinessContractTest.kt`, add contract assertions enforcing zero database manager instantiation in `DevicesTabbedViewModel` and `SportTypeViewModel`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) Zero regression in existing features, telemetry recording, and unit tests; (2) Thread safety: all SQLite queries in ViewModels dispatched strictly on `Dispatchers.IO`; (3) Preservation of all merged `ATT-8000` (Polar sync pending) and `ATT-7875` (crash recovery accumulator preservation) methods; (4) Downward ratcheting in `ArchitectureConformanceContractTest.BASELINE_ALLOWED_UI_DB_CALLERS`.
* **Risk Rating**: **LOW** — Pure encapsulation and redirection through existing repositories; zero SQLite schema or query logic changes.
