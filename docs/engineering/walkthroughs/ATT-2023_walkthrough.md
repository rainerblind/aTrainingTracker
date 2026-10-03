# Stage 5 Walkthrough - ATT-2023: Workouts Imported Twice (Revision 2: Option 2)

**Ticket**: [ATT-2023](https://rainerblind.atlassian.net/browse/ATT-2023)  
**Sub-task**: [ATT-2146](https://rainerblind.atlassian.net/browse/ATT-2146) (`[Test]`)  
**Parent Epic**: [ATT-529](https://rainerblind.atlassian.net/browse/ATT-529) (*Import TCX Files*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2023`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Domain & Forensic Root Cause (Revision 2)

During Sprint Review testing of Revision 1, duplicate imports were still observed on the user's test device (e.g. workout *"boneshaking in the morning"* on 2015-06-09 at 06:57 and 06:58).

### Physical Evidence:
Forensic analysis of the user's Dropbox storage at `/home/rainer/Dropbox/apps/Workouts/TCX` confirmed the presence of two separate files:
* `2015-06-09_065716.tcx`: Start `04:57:16Z` (06:57:16 local), sport `Biking`.
* `2015-06-09_065821.tcx`: Start `04:58:21Z` (06:58:21 local), sport `Biking`.
* Offset: 65 seconds (recorded simultaneously on two devices).

### Root Cause of Revision 1 Miss:
Revision 1 configured a start-time delta threshold of $\pm 30\text{ seconds}$ (`<= 30`). Because the start time difference between the two devices was 65 seconds, Revision 1 evaluated the second file as a separate workout and imported both.

### User Decision:
The user selected **Option 2: Extended Start-Time Tolerance Window ($\pm 3$ minutes / 180 seconds)** for workouts of the same sport.

---

## 2. Changes Made

### 2.1 Extended Same-Sport Tolerance Window in `LegacyImportEngine.kt`
- Updated `isWorkoutExisting`:
  - Added optional parameter `bSportType: BSportType? = null`.
  - Expanded epoch window from $\pm 30\text{s}$ to $\pm 180\text{s}$ ($3\text{ minutes}$).
  - Gated the window with `(${WorkoutSummaries.B_SPORT} = ? AND ...)` when `bSportType` is specified, ensuring activities of different sports within 3 minutes are not falsely suppressed.
- Hoisted `bSportType` and `sportId` resolution in `importFromTcxInternal` and `importFromGpxInternal` before mutex acquisition.
- Included `B_SPORT` in the initial `summaryValues` inserted into `WorkoutSummaries.TABLE`.

### 2.2 Unit Test Coverage in `LegacyImportEngineDeduplicationTest.kt`
- Updated query structure assertions to verify `<= 180`.
- Added unit test `testDeduplicationWithTimeStartAndSport_constructsSportAwareQuery` verifying that `BSportType.BIKE` is bound as argument and the sport clause is correctly assembled.
- Verified pre-dispatch queue deduplication and concurrent worker mutex synchronization.

---

## 3. Verification Results

| Test Target | Verification Type | Result |
| :--- | :--- | :--- |
| `LegacyImportEngineDeduplicationTest` | Targeted Unit & Concurrency Tests | **100% Passed (6/6 tests)** |
| Full Test Suite (`./gradlew testDebugUnitTest`) | Clean-Room Suite Regression | **100% Passed** |
| Dual-Phone Offset Simulation (65s) | Tolerance Logic | **Verified (65s $\le$ 180s)** |
| Invariant Preservation | Non-Sport / Different Sport Safety | **Verified** |
