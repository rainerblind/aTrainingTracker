# Implementation Plan - SCRUM-127: Prevent track accumulation while paused

Suspend map track and elevation profile updates when the session is in a PAUSED state.

## 1. Requirements Mapping
- **Requirement**: `REQ-PRO-004` (Prevent track and profile accumulation while paused)
- **Test ID**: `TST-UI-039` (Pause Movement Isolation)

## 2. Impact Analysis
- **Core Repository**: `BANALServiceRepository.kt`.
- **Logic**: Currently uses `TrainingApplication.isTracking()` which is true during pause.
- **Side Effects**: None. This aligns the visual representation with the accumulated distance logic.

## 3. Proposed Changes

### 3.1 Refine Update Gates (`BANALServiceRepository.kt`)
- In `startObservingBANALService()`, replace `TrainingApplication.isTracking()` with a check for `TrainingApplication.getTrackingMode() == TrackingMode.TRACKING`.
- This affects two locations:
    1. The block that appends to `_currentTrack` (Map).
    2. The block that appends to `_currentPathPoints` (Elevation Profile).

## 4. Verification Plan
- **Build**: Ensure successful compilation.
- **Visual Audit**:
    1. Start workout.
    2. Pause workout.
    3. Verify that `_currentTrack` and `_currentPathPoints` stop accumulating new data points even if sensor values (GPS/Altitude) change.
