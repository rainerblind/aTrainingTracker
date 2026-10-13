# Stage 2: Requirement & Test Specification: ATT-3101

## Metadata
* **Ticket ID**: ATT-3101
* **Title**: Long clicking sensor field on tracking screen does not open sensor configuration dialog
* **Parent Epic**: ATT-355 (Good and consistent UI)
* **Author**: Antigravity (Pair Programming Assistant)
* **Date**: 2026-10-10
* **Requirement ID**: `REQ-UI-336`
* **Test Specification ID**: `TST-UI-296`
* **Status**: Specified

---

## 1. Requirement Specification (REQ-UI-336)

### 1.1 Summary
The system SHALL trigger the sensor field configuration dialog (`EditSensorFieldDialog`) when an athlete performs a long-click gesture on any sensor field tile during active workout tracking (`ScreenMode.TRACKING`):
1. **Tracking Mode Long-Click Delegation (`SensorGridScreen.kt`)**:
   - In `SensorGridScreen.kt`, within the sensor field rendering loop for tracking and preview modes, the `onEdit` callback parameter of `SensorFieldView` SHALL be wired to `gridActions.onEditField(fieldState)`.
   - In `SensorFieldView.kt`, performing a long-press gesture (`onLongClick`) when `screenMode == ScreenMode.TRACKING` SHALL invoke `onEdit()`, thereby dispatching `gridActions.onEditField(fieldState)` and displaying `EditSensorFieldDialog` for the targeted sensor field without interrupting workout tracking.
2. **Single-Tap Safety Invariant (Zero Accidental Triggers)**:
   - In `ScreenMode.TRACKING`, single-tap (`onClick`) SHALL remain an intentional no-op to strictly prevent accidental HUD dialog interruptions during vigorous activities (cycling, running).
3. **Tile Reordering & Deletion Gating Invariant**:
   - Drag-and-drop / Pick-and-Place reordering (`onStartMove = {}`) and tile deletion (`onDelete = {}`) SHALL remain strictly disabled in `ScreenMode.TRACKING` and `ScreenMode.PREVIEW`.
4. **Configuration Mode Intactness**:
   - In `ScreenMode.CONFIGURATION`, single-tap SHALL continue to trigger `onEdit()` (opening the configuration dialog) and long-press SHALL continue to trigger `onStartMove()` (initiating Pick-and-Place tile reordering per `REQ-UI-200`).
5. **System Invariants**:
   - Zero telemetry displacement, isolated map recomposition barrier, and 100% clean-room unit test pass rate MUST be strictly preserved.

### 1.2 Chesterton's Fence Audit
* **Historical Precedent**: `e5033b5c` (*ATT-2620, Sprint 2026-41.2*) isolated `ScreenMode.CONFIGURATION` for scrollable WYSIWYG editing. In doing so, empty lambdas `onStartMove = {}, onEdit = {}, onDelete = {}` were placed in the tracking branch under the assumption that all editing callbacks should be suppressed during tracking.
* **Root Reason for Existing Formulation**: The developer overlooked that `SensorFieldView` already internally distinguishes between single click and long click (`onClick` vs `onLongClick`), ensuring that single taps do nothing while long clicks safely trigger field editing. Passing `onEdit = {}` broke the long-press edit flow in live tracking.
* **Preservation of Intent**: Accidental taps during riding/running remain safely ignored, while deliberate long presses open the configuration dialog.

---

## 2. Test Specification (TST-UI-296)

### 2.1 Test Categories & Verification Scope

| Test ID | Category | Target File | Verification Scope |
| :--- | :--- | :--- | :--- |
| **TST-UI-296.1** | Structural & Wiring Contract | `SensorGridScreenContractTest.kt` | Verify that in `SensorGridScreen.kt`, during tracking mode, `onEdit` on `SensorFieldView` delegates to `gridActions.onEditField(fieldState)`. |
| **TST-UI-296.2** | Tile Move/Delete Safety Contract | `SensorGridScreenContractTest.kt` | Verify that `onStartMove` and `onDelete` remain no-ops in tracking and preview modes. |
| **TST-UI-296.3** | SensorFieldView Gesture Logic Contract | `SensorFieldViewGestureContractTest.kt` | Verify that in `ScreenMode.TRACKING`, single tap does not trigger edit, while long press triggers `onEdit()`. In `ScreenMode.CONFIGURATION`, single tap triggers `onEdit()` and long press triggers `onStartMove()`. |
| **TST-UI-296.4** | Clean-Room Full Suite Regression | Full Unit Test Suite | Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions. |

---

## 3. Acceptance Criteria (Given-When-Then)

### Scenario 1: Long-Press Sensor Field in Live Tracking
* **Given** an athlete on the active tracking screen (`ScreenMode.TRACKING`),
* **When** the athlete performs a long click on any sensor field tile,
* **Then** the sensor field configuration dialog (`EditSensorFieldDialog`) is displayed, allowing selection of sensor type, font/view size, and filtering.

### Scenario 2: Single Tap in Live Tracking (Accidental Tap Protection)
* **Given** an athlete on the active tracking screen (`ScreenMode.TRACKING`),
* **When** the athlete single-taps a sensor field tile,
* **Then** no dialog or action is triggered (single-tap remains an intentional no-op).

### Scenario 3: Gestures in Configuration Mode Intact
* **Given** the tracking screen in `ScreenMode.CONFIGURATION`,
* **When** clicking a sensor field tile,
* **Then** the configuration dialog opens (`onEdit`).
* **When** long-clicking a sensor field tile,
* **Then** Pick-and-Place move mode is activated (`onStartMove`).

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-296.1` | Contract | `SensorGridScreen.kt` | `REQ-UI-336` | Specified |
| `TST-UI-296.2` | Contract | `SensorGridScreen.kt` | `REQ-UI-336` | Specified |
| `TST-UI-296.3` | Contract | `SensorFieldView.kt` | `REQ-UI-336` | Specified |
| `TST-UI-296.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
