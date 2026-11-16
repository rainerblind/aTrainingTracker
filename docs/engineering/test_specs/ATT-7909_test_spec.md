# Stage 2: Requirement & Test Specification - ATT-7909: Java Core Migration: Modernize SQLite database managers via repository interfaces

**Ticket**: [ATT-7909](https://rainerblind.atlassian.net/browse/ATT-7909) | **Sub-task**: [ATT-8280](https://rainerblind.atlassian.net/browse/ATT-8280) (`[Req & Test Spec]`)
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`
**Requirement Mapping**: `REQ-ARC-009` (*Modernize SQLite Database Managers via Repository Interfaces*)
**Test Spec ID**: `TST-ARC-009` | **Branch**: `feature/ATT-7909` | **Author**: AI Agent 2 (Specification) | **Date**: 2026-11-16

---

## 1. Requirement Specification (REQ-ARC-009)

### 1.1 Attributes & Scope
- **Priority**: Medium | **Type**: Non-Functional | **Verification Method**: Unit / Integration
- **Implementation**: `DevicesTabbedViewModel`, `SportTypeViewModel`, `TrackingViewsRepository`, `WorkoutRepository`, `PeriodsRepository`, `DeviceDataRepository`.
- **Scope**: Tier 4 ADR-001 persistence modernization. Keep underlying SQLite engines in Java (6,006 LOC) per ATT-7749. Encapsulate access behind repositories on `Dispatchers.IO`, null-harden cursor lifecycles (`?.use {}`), and eliminate UI singleton access.

### 1.2 Functional & Architectural Requirements
- **REQ-ARC-009.1 (Presentation Encapsulation)**: ViewModels SHALL NOT instantiate SQLite managers. `DevicesTabbedViewModel` SHALL query device name/type via `DeviceDataRepository`. `SportTypeViewModel` SHALL query stats via `WorkoutRepository`. `TrackingViewsRepository` SHALL NOT expose `DevicesDatabaseManager` in public signatures.
- **REQ-ARC-009.2 (Thread Concurrency)**: All asynchronous repository query operations and cursor extractions SHALL execute confined to `Dispatchers.IO`.
- **REQ-ARC-009.3 (Null-Hardened Cursor Lifecycle)**: Raw `Cursor` consumption in `WorkoutRepository` and `PeriodsRepository` SHALL utilize `?.use {}` to safely guard against null cursors without throwing `NullPointerException`.
- **REQ-ARC-009.4 (Preservation of Invariants & Merged Helpers)**: Zero schema bumps, table alterations, or PRAGMA changes. Merged `setPolarSyncPending()` / `clearPolarSyncPending()` (ATT-8000/ATT-7919/ATT-7924) and accumulator restoration helpers (ATT-7875) SHALL remain intact.

### 1.3 Acceptance Criteria (Given-When-Then)
- *Criterion 1*: Given `DevicesTabbedViewModel`, When resolving device metadata on discovery, Then queries route via `DeviceDataRepository` with zero `DevicesDatabaseManager.getInstance` calls.
- *Criterion 2*: Given `SportTypeViewModel`, When loading sport types or period stats, Then queries execute via `WorkoutRepository` on `Dispatchers.IO` with zero `WorkoutSummariesDatabaseManager.getInstance` calls.
- *Criterion 3*: Given `TrackingViewsRepository`, When invoking public methods, Then zero database manager instances or internal structs leak into public method signatures.
- *Criterion 4*: Given `WorkoutRepository` and `PeriodsRepository`, When queries return null cursors, Then execution safely returns without `NullPointerException`.
- *Criterion 5*: Given `RepositoryCleanlinessContractTest` and `TryWithResourcesCursorAuditTest`, When executed, Then 100% pass rate is maintained.

---

## 2. Test Specification (TST-ARC-009)

### 2.1 Test Procedures
- **TST-ARC-009.1 (Architecture & Encapsulation Contract)**: `RepositoryCleanlinessContractTest` verifies zero disallowed database manager instantiations in `DevicesTabbedViewModel` and `SportTypeViewModel`, and checks `TrackingViewsRepository` public signatures.
- **TST-ARC-009.2 (DevicesTabbedViewModel Device Resolution)**: `DevicesTabbedViewModelTest` verifies `getDeviceName` and `getDeviceType` execute asynchronously through `DeviceDataRepository`.
- **TST-ARC-009.3 (SportTypeViewModel Stats Resolution)**: `SportTypeViewModelRetiredTest` / `SportTypeViewModelTest` verifies sport type stats query routing via `WorkoutRepository` on `Dispatchers.IO`.
- **TST-ARC-009.4 (Null-Hardened Cursor & Resource Safety)**: Unit tests verify `WorkoutRepository.loadAllWorkouts()` and `PeriodsRepository.startMigration()` null-safety under simulated null cursor; `TryWithResourcesCursorAuditTest` confirms safe cursor resource cleanup.
- **TST-ARC-009.5 (Merged Features Regression Parity)**: Unit tests verify `dismissPolarSyncPending()`, `setPolarSyncPending()`, `clearPolarSyncPending()`, and accumulator recovery helpers function with zero regression.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-ARC-009.1]` | Contract | `RepositoryCleanlinessContractTest` | `REQ-ARC-009.1`, `REQ-ARC-009.4` | Specified |
| `[TST-ARC-009.2]` | Unit | `DevicesTabbedViewModel.initNewDeviceListener` | `REQ-ARC-009.1`, `REQ-ARC-009.2` | Specified |
| `[TST-ARC-009.3]` | Unit | `SportTypeViewModel.loadSportTypes` | `REQ-ARC-009.1`, `REQ-ARC-009.2` | Specified |
| `[TST-ARC-009.4]` | Unit | `WorkoutRepository.loadAllWorkouts`, `PeriodsRepository` | `REQ-ARC-009.3` | Specified |
| `[TST-ARC-009.5]` | Unit | `WorkoutRepository.dismissPolarSyncPending` | `REQ-ARC-009.4` | Specified |
