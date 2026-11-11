# Stage 3: Implementation Plan - ATT-5122: Append optional Radar Traffic & Close-Pass Summary to Strava upload description and workout share summary

**Ticket**: [ATT-5122](https://rainerblind.atlassian.net/browse/ATT-5122)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `v2026.46`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-RAD-007`  
**Test Mapping**: `TST-RAD-007`  
**Branch**: `feature/ATT-5122`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

Cyclists using rearview radar want to document road safety by sharing overtaking statistics, close passes, and peak vehicle speeds on Strava and social networks. Currently, this requires tedious manual copying after rides. 

This feature adds an opt-in preference (`uploadStravaRadarSummary`, default: `false`) to automatically format and append a clean, single-line Radar Traffic & Close-Pass Summary to the Strava upload description and include it in workout share intents, localized across all 9 languages without altering existing user workout notes.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-RAD-007` (*Radar Traffic & Close-Pass Summary in Strava Description & Share Sheet*)
* **Test Mapping**: `TST-RAD-007` (*Radar Traffic & Close-Pass Strava Description & Share Summary Test*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Workout Note Overwriting**: Existing user-entered workout descriptions in `WorkoutSummaries.DESCRIPTION` must never be altered in SQLite or overwritten on Strava.
2. **Zero Non-Radar / Zero-Vehicle Overhead**: Workouts without radar telemetry or with `0` vehicle overtakes must emit zero summary text with zero performance penalty.
3. **Idempotency**: Retrying uploads or running duplicate reconciliation must never duplicate the `📡 Radar:` summary line.
4. **9-Language String Parity**: 100% localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
5. **No DB Schema Changes**: SQLite schema version remains completely unchanged (`DB_VERSION` intact).

---

## 4. Proposed Architectural Changes

### Component 1: Shared Preference & Application Layer (`TrainingApplication.java`, `prefs_strava.xml`)
- Add preference key `SP_UPLOAD_STRAVA_RADAR_SUMMARY = "uploadStravaRadarSummary"` (default: `false`).
- Provide accessors `TrainingApplication.uploadStravaRadarSummary()` and `TrainingApplication.setUploadStravaRadarSummary(boolean)`.
- Declare `SwitchPreferenceCompat` in `prefs_strava.xml`.

### Component 2: Domain Model & Formatter (`RadarTrafficSummary.kt`, `RadarTrafficSummaryFormatter.kt`)
- Extend `RadarTrafficSummary` with optional V2 metrics (`closePassCount: Int = 0`, `minPassingDistanceM: Double? = null`, `heavyVehicleCount: Int = 0`).
- Create `RadarTrafficSummaryFormatter.kt` to assemble the localized single-line summary:
  - Standard V1/ANT+: `📡 Radar: %1$d vehicles passed (%2$s/km) • 🚀 Max %3$s km/h (+%4$s km/h approach)`
  - High-Resolution V2: `📡 Radar: %1$d vehicles passed (%2$s/km) • ⚠️ %3$d close passes (<1.5m, min %4$sm) • 🚛 %5$d heavy vehicles • 🚀 Max %6$s km/h`
  - Returns `null` if `summary == null` or `totalVehicles <= 0`.

### Component 3: Data Access Layer (`WorkoutRepository.kt`)
- Expose strongly-typed getter: `fun getRadarTrafficSummary(fileBaseName: String): RadarTrafficSummary?`
- Leverages existing `computeRadarTrafficSummaryFromSamples` via `WorkoutExportHeader` distance and duration.

### Component 4: Strava Uploader Integration (`StravaUploader.kt`)
- In `doUpdate()`: If `TrainingApplication.uploadStravaRadarSummary()` is true, fetch `getRadarTrafficSummary(fileBaseName)` and format it.
- If non-null, append to description:
  - If existing description is blank: use formatted summary directly.
  - If existing description is present and doesn't contain `"📡 Radar:"`: append `\n\n` + formatted summary.
  - If already contains `"📡 Radar:"`: preserve existing description as-is.

### Component 5: Social Sharing Integration (`ShareUtils.kt`, `WorkoutShareBottomSheet.kt`)
- Extend `ShareUtils.saveAndShare()` with optional `shareText: String? = null`, setting `Intent.EXTRA_TEXT` when provided.
- In `WorkoutShareBottomSheet.kt`, pass formatted summary to `saveAndShare()` when `uploadStravaRadarSummary()` is enabled and radar data is present.

### Component 6: Settings UI Integration (`StravaSettingsDialog.kt`, `RadarSettingsSection.kt`)
- In `StravaSettingsDialog.kt`: Add `SelectiveUploadToggleRow` for `uploadStravaRadarSummary`.
- In `RadarSettingsSection.kt`: Add an opt-in toggle switch card for Strava & Share summary.

### UI Consistency (Rule 23)
* **Reference screen**: `StravaSettingsDialog.kt` (Selective upload toggle list) and `RadarSettingsSection.kt` (ElevatedCard toggle sections).
* **Reused components**: `SelectiveUploadToggleRow`, `ElevatedCard`, `MaterialTheme.typography`, `MaterialTheme.colorScheme`.
* **Theme tokens**: Standard M3 container colors, corner radius `12.dp`, vertical spacing `8.dp` / `12.dp`.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Preference Configuration & Domain Model
* Files: `TrainingApplication.java`, `prefs_strava.xml`, `RadarTrafficSummary.kt`
* Implement `SP_UPLOAD_STRAVA_RADAR_SUMMARY`, accessors, XML preference, and V2 model fields.

### Step 2: Formatter Implementation
* File: `RadarTrafficSummaryFormatter.kt`
* Implement `format(summary: RadarTrafficSummary?, context: Context): String?` with 9-language resource lookups, singular/plural handling, and V1 vs V2 differentiation.

### Step 3: Repository Query Method
* File: `WorkoutRepository.kt`
* Implement `getRadarTrafficSummary(fileBaseName: String): RadarTrafficSummary?`.

### Step 4: Strava Uploader Metadata Payload
* File: `StravaUploader.kt`
* Update `doUpdate()` to conditionally append the formatted summary with idempotency protection.

### Step 5: Sharing Intent Text Enrichment
* Files: `ShareUtils.kt`, `WorkoutShareBottomSheet.kt`
* Add `shareText` support to `saveAndShare()`, pass formatted text in `WorkoutShareBottomSheet.kt`.

### Step 6: Settings UI Integration
* Files: `StravaSettingsDialog.kt`, `RadarSettingsSection.kt`
* Mount switch toggles connected to `uploadStravaRadarSummary`.

### Step 7: Unit & Contract Tests
* Files: `RadarTrafficSummaryFormatterTest.kt`, `StravaUploaderRadarSummaryTest.kt`, `ShareUtilsTest.kt`
* Verify formatting, uploader append, idempotency, suppression, and share intents.
* Test execution command:
  ```bash
  python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.uploader.StravaUploaderRadarSummaryTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.RadarTrafficSummaryFormatterTest" --tests "com.atrainingtracker.trainingtracker.helpers.ShareUtilsTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit tests under `parallel_sprint_lock.py`, run `verify_requirement_governance.py`, and run clean-room full test suite in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-5122`; revertible without affecting `sprint/2026-46.2`.
