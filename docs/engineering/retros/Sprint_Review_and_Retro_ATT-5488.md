# Sprint Review & Retrospective — Sprint 2026-45.7 (`ATT-5488`)

**Ticket**: [ATT-5488](https://rainerblind.atlassian.net/browse/ATT-5488)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Sprint**: `2026-45.7`  
**Target Release**: `V5.0.0`  
**Branch**: `sprint/2026-45.7` $\rightarrow$ `develop`  
**Date**: 2026-11-06  

---

## 1. Sprint Velocity & Capacity Summary (`REQ-PRO-029`)

| Metric | Planned at Sprint Start | Completed & Accepted (`i.O.`) | Unstarted Carry-Over (User-Requested Early Sprint End) | Returned to `Zu erledigen` (`n.i.O.`) |
| :--- | :---: | :---: | :---: | :---: |
| **Engineering Tickets** | 23 | **13** (`ATT-5431`, `ATT-5477`, `ATT-5489`, `ATT-5487`, `ATT-5486`, `ATT-5485`, `ATT-5483`, `ATT-5482`, `ATT-5481`, `ATT-5479`, `ATT-5480`, `ATT-5478`, `ATT-5475`) | **10** (`ATT-5474`, `ATT-5473`, `ATT-5472`, `ATT-5471`, `ATT-5469`, `ATT-5470`, `ATT-5468`, `ATT-5467`, `ATT-5491`, `ATT-5490`) | **0** |
| **Review & Retro Ticket** | 1 (`ATT-5488`) | **1** | 0 | 0 |
| **Story Points (`customfield_10016`)** | **44 SP** | **30 SP** | **14 SP** | **0 SP** |

* **Capacity Envelope Note (`Rule 1` Documented Deviation)**: Phase 2 was concluded at the user's request after completing 13 tickets (**30 SP**), which matches the exact upper bound of the canonical 24–30 SP sprint capacity envelope (`REQ-PRO-029`). All 13 completed tickets were accepted (`i.O.`) during Ceremony 2; the remaining 10 unstarted tickets (`14 SP`) carry over cleanly to the next sprint.
* **Tier 2 Sprint Integration Gate**: `4,487 tests completed, 100% pass rate` (`docs/engineering/test_results/index.html`) and `python3 tools/verify_requirement_governance.py` (`0 errors`, 100% living doc counter parity across all 9 shards).

---

## 2. Sprint Review Summary (13 Completed Engineering Tickets + 1 Retro Ticket)

| # | Key | SP | Summary | Commit | Review Outcome | Follow-Up Tickets / Notes |
| :-: | :--- | :-: | :--- | :---: | :--- | :--- |
| 1 | `ATT-5431` | 3 | ASPICE Audit Hardening (SWE.1/2/4/6): Synchronize REQ/TST Verified status & counts, modernize `docs/architecture.md`, and enforce universal image/doc/SWE.2 rules | `a3f5bdb7` | **Accepted (`i.O.`)** (`V5.0.0`) | 100% shard-to-master counter parity & automated `AspiceSweAuditContractTest` |
| 2 | `ATT-5477` | 2 | `ActivityThread.throwRemoteServiceException` | `8fa4ffd4` | **Accepted (`i.O.`)** (`V5.0.0`) | Dedicated 24dp monochrome `ic_stat_notify_logo.xml`, 6/6 notification builders migrated, AAB density split disabled |
| 3 | `ATT-5489` | 3 | `MyBTLEDevice`: Paired BLE sensors with Random Static MAC addresses fail to connect (`addr_type=public`) + configurable BLE search timeout in Expert Settings | `22d8c54d` | **Accepted (`i.O.`)** (`V5.0.0`) | `ADDRESS_TYPE_RANDOM` on API 33+, filtered scan assist, configurable 3..15s timeout slider (default 5s) |
| 4 | `ATT-5487` | 3 | Workout Details (Zonen / Histogram Tab): Add Grade / Slope Distribution Card (Balken, Säulen & Feingranulares Steigungs-Histogramm) | `36e00b64` | **Accepted (`i.O.`)** (`V5.0.0`) | Follow-up **`ATT-5563`** created for Expert Settings visibility toggle (default off in Summary), renaming away from "Zonen", and `%` category labels instead of `Z1..Z7` |
| 5 | `ATT-5486` | 1 | `ElevationProfile`: Use Fixed `TTColor.Zone1` Green for Marginal Altitude Histogram Instead of First Segment Grade Color | `7ecad186` | **Accepted (`i.O.`)** (`V5.0.0`) | Uniform `TTColor.Zone1` green in Landscape marginal altitude distribution |
| 6 | `ATT-5485` | 2 | Lieblingsstrecke (`WorkoutClusterHeatmapScreen`): Large central map with compact 2-line header, bottom Detail/Trend tabs, and removal of trend workout banner | `39a51b63` | **Accepted (`i.O.`)** (`V5.0.0`) | Follow-up **`ATT-5564`** created to add the missing top drag-handle indicator bar to the workout peek popup |
| 7 | `ATT-5483` | 2 | Workout Details (Runden & Segmente Tab): Clicking a Lap Does Not Open `LapEditBottomSheet` | `09175f91` | **Accepted (`i.O.`)** (`V5.0.0`) | `LapEditBottomSheet` wired at `TrackOnMapScreen` root with boundary move handoff |
| 8 | `ATT-5482` | 3 | Aftermath: Delete / Merge Lap (Remove Lap Boundary and Merge Adjacent Laps) | `25aa20ab` | **Accepted (`i.O.`)** (`V5.0.0`) | Transactional SQLite lap merge with Option A direction toggle for interior laps & live preview |
| 9 | `ATT-5481` | 1 | `ClockDevice`: Eliminate 1-Hz `Calendar.getInstance()` allocation in `ClockTimeTask` (`ATT-5476` follow-up) | `4e17c52d` | **Accepted (`i.O.`)** (`V5.0.0`) | Zero-allocation reusable `Date` mutation per 1-Hz tick |
| 10 | `ATT-5479` | 2 | Workout Photos: Reject `(0.0, 0.0)` Null-Island EXIF GPS Coordinates and Exclude Invalid Coordinates from Map Markers & Bounds | `4f7b8736` | **Accepted (`i.O.`)** (`V5.0.0`) | `GeoUtils.isValidGpsCoordinate`, track timestamp fallback & SQLite `selfHealPhotos` |
| 11 | `ATT-5480` | 2 | `CreateLapOnMapSheet` & `MoveLapBoundaryOnMapSheet`: Blue Track (`TrackType.BEST`), Scissors Icon for Lap Split, and Dedicated Map Pin Icons | `bcfed94d` | **Accepted (`i.O.`)** (`V5.0.0`) | `ic_lap_split_scissors.xml`, `ic_lap_move_boundary.xml`, and `TrackType.BEST` blue polyline |
| 12 | `ATT-5478` | 2 | Aftermath Detail View: Prevent Repeated Map Reloads and Tab Jumping When `WorkoutData` Updates | `253795b0` | **Accepted (`i.O.`)** (`V5.0.0`) | `loadedWorkoutId` guard preserves tracks and active tab during in-place updates |
| 13 | `ATT-5475` | 3 | Workout Photo Picker: Pre-Filter Photos Taken During Workout Time Window with 'Show All Photos' Button | `c82c8248` | **Accepted (`i.O.`)** (`V5.0.0`) | `WorkoutTimePhotoPickerSheet` (`\pm 5\text{ min}` MediaStore query) with `Alle Bilder anzeigen` fallback |

---

## 3. Retrospective Topics & Permanent Countermeasures (`Rule 74`)

### Topic 1: Expert Settings Visibility Defaults (Summary Off by Default) & Domain-Authentic Category Labels (`ATT-5487` $\rightarrow$ `ATT-5563`)
* **Observation**: `GradeZoneDistributionCard` was wired into both Workout Details and `WorkoutSummary` without Expert Settings toggles (making it always visible in `WorkoutSummary`, where secondary distribution cards should default to `false`), and used physiological `"Zonen"` / `"Z1..Z7"` labels instead of `"Steigungsverteilung"` and percentage ranges (`<2%`, `2–5%`, ..., `>25%`).
* **Countermeasure (`Rule 74` — `docs/design_guidelines.md` Sec 5.17)**:
  * Every new card added to `WorkoutSummary` or `WorkoutDetailTabbedContent` **MUST** include visibility toggles in Expert Settings (`WorkoutMasksAndCardsSection.kt`), defaulting to `false` in `WorkoutSummary` and `true` in `WorkoutDetailTabbedContent` unless explicitly requested otherwise.
  * Reserve `"Zonen"` / `"Z1..ZN"` labels strictly for physiological zones (Heart Rate, Power); for physical/environmental distributions (Grade/Slope), use `"Verteilung"` (*Steigungsverteilung*) and display the actual category bounds (`<2%`, `2–5%`, ..., `>25%`) on bar/column axes.

### Topic 2: Mandatory Drag-Handle Indicator Bar on Expandable Peek Bottom Sheets (`ATT-5485` $\rightarrow$ `ATT-5564`)
* **Observation**: The workout peek bottom sheet in `WorkoutClusterHeatmapScreen` lacked a top drag-handle indicator bar (`BottomSheetDefaults.DragHandle()`), hiding the affordance that the sheet can be pulled upwards.
* **Countermeasure (`Rule 74` — `docs/design_guidelines.md` Sec 5.15)**:
  * Every expandable or draggable peek bottom sheet (`BottomSheetScaffold`, `ModalBottomSheet`, or custom peek overlay) **MUST** render a visible top drag-handle indicator pill (`BottomSheetDefaults.DragHandle()` or `32.dp × 4.dp` rounded bar) so users immediately see that the sheet can be dragged up.

### Topic 3: State Reset Ordering Before Completion Event Emission (`ATT-5565`)
* **Observation**: In `WorkoutRepository.deleteWorkout(id)` on `Dispatchers.IO`, `deleteFinishedEvent.postValue(Pair(id, false))` ran inside `try/catch` before `_deletionProgress.postValue(DeletionProgress.Idle)` in `finally`, causing a 1-in-4,487 thread scheduling race under heavy CPU load when an observer awakened by `deleteFinishedEvent` asserted `deletionProgress == Idle`.
* **Countermeasure (`Rule 74` — `docs/coding_standards.md` Sec 5.5)**:
  * Always reset in-progress/loading state flows (`_deletionProgress`, `_isLoading`) to `Idle`/`false` **before** emitting terminal completion events (`deleteFinishedEvent`, `saveFinishedEvent`) so observers triggered by completion never read a stale `InProgress` state.
