# Project Verification & Testing Plan

This document defines the verification procedures for all project requirements. This project follows a **Test-Driven Development (TDD)** approach, where tests are defined before implementation.

## 1. Automated Unit Tests (SWE.4)

*These tests verify the core logic of individual software units without requiring a physical device.*

| Test ID | Component | Requirement ID(s) | Test Description | Status |
|:---|:---|:---|:---|:---|
| **TST-UNT-001** | `NumericalEncodingUtils` | `REQ-DAT-004` | Verify that encoding and decoding LatLng/Double lists result in zero data loss. | Verified |
| **TST-UNT-002** | `TimeFormatter` | `REQ-FIL-003`, `REQ-UI-002` | Verify Removal of leading zeros and correct localized string generation for s, m:s, and h:m:s formats. | Verified |
| **TST-UNT-003** | `EquipmentDiscovery` | `REQ-TRK-004`, `REQ-SET-003` | Verify sport-type resolution logic based on varied active sensor ID sets and speed. | Verified |
| **TST-UNT-004** | `MovingAverageFilter` | `REQ-FIL-001` | Verify correct averaging of a sequence of numbers (Numbered and Timed variants). | Proposed |
| **TST-UNT-005** | `ExponentialSmoothing` | `REQ-FIL-002` | Verify the recursive smoothing formula: `y[k+1] = α m[k] + (1-α) y[k]`. | Proposed |
| **TST-UNT-006** | `SportTypeMapping` | `REQ-EXP-006` | Verify that `SportTypeDatabaseManager` correctly handles a `null` Strava mapping as a persistent state. | Proposed |
| **TST-UNT-007** | `EditWorkoutViewModel` | `REQ-EXP-006` | Verify that selecting a sport type with a `null` Strava mapping automatically sets `uploadToStrava` to `0`. | Proposed |
| **TST-UNT-008** | `Accumulator` | `REQ-PRO-003` | Verify that hardware deltas received during a pause are discarded, while deltas received while active are accumulated correctly. | Verified |

## 2. Regression & Manual Verification (SWE.5 / SWE.6)

These procedures ensure high-level system integrity and UI consistency.

| Test ID | Category | Requirement ID(s) | Procedure | Expected Result |
|:---|:---|:---|:---|:---|
| **TST-REG-001** | **Tracking** | `REQ-TRK-001`, `REQ-TRK-003`, `REQ-TRK-007`, `REQ-MAP-003` | Start workout, background app for 10 min, check bearing jitter. | Persistent 1Hz sampling, active notification, filtered map camera. |
| **TST-REG-002** | **Branding** | `REQ-EXT-001`, `REQ-EXT-002`, `REQ-MAP-005` | Navigate to Strava Segments list and Detail map. | Correct orange/white logos visible in headers and context rows. |
| **TST-REG-003** | **Cloud** | `REQ-EXP-001`, `REQ-EXP-002` | Complete workout with Strava enabled. | File export success UI and successful upload to third-party API. |
| **TST-INT-002** | **UI/Logic** | `REQ-EXP-006` | In Sport Type Editor, select "- No upload -". Then create/edit workout with this type. | Strava checkbox is unchecked and disabled in workout editor. |
| **TST-INT-003** | **Mapping** | `REQ-EXP-007` | In Sport Type Editor, select a new Strava type (e.g., Gravel Ride). Save and reopen. | The new type is persisted and correctly displayed in the editor and summary views. |
| **TST-INT-004** | **UI/Logic** | `REQ-UI-009` | In Tracking Tab Configuration, toggle "Show Elevation Profile". | Elevation profile is displayed below the map on the tracking screen. |
| **TST-INT-005** | **UI Layout** | `REQ-UI-010` | Open the Tab Configuration on a narrow device or split-screen. | Checkboxes wrap into multiple rows instead of overlapping. Labels remain fully visible. |
| **TST-INT-006** | **UI Layout** | `REQ-UI-010` | Audit the checkbox sequence in the tab configuration header. | Checkboxes appear in the exact order: Map, Elevation Profile, Live Segments, Lap Button. |
| **TST-MAN-001** | **General** | All other `REQ` | Ad-hoc functional testing on a physical device. | Feature performs according to the rationale defined in requirements. |

## 3. Structural & Compliance Checks

| Test ID | Focus | Requirement ID(s) | Method |
|:---|:---|:---|:---|
| **TST-STR-001** | **Stability** | `REQ-CON-005`, `REQ-TRK-005`, `REQ-DAT-005`, `REQ-UI-008` | Static analysis and architectural audit. |
| **TST-STR-002** | **Thread Safety** | `REQ-PRO-002` | Static analysis of `synchronized` blocks to ensure no nested locks or I/O within locks. |

## 4. Release Verification Workflow

Whenever a file is modified, the following workflow is triggered:

1.  **Change Detection**: The agent identifies which files were touched.
2.  **Requirement Mapping**: The agent cross-references the files with `docs/requirements.md` (Implementation Column) to identify affected features.
3.  **Test Selection**: The agent selects the relevant **Verification ID** from the requirements table.
4.  **Co-Execution**: The user and agent perform the verification. The agent updates the `Status` column in `docs/requirements.md` upon success.
