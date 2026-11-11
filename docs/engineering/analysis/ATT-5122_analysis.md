# Stage 1 Analysis: ATT-5122 - [Feature] Append optional Radar Traffic & Close-Pass Summary to Strava upload description and workout share summary

**Ticket**: [ATT-5122](https://rainerblind.atlassian.net/browse/ATT-5122)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `v2026.46`  
**Active Sprint**: `sprint/2026-46.2`  
**Branch**: `feature/ATT-5122`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

Athletes riding with cycling rearview radar frequently share overtaking statistics, close-pass occurrences, and maximum approaching speeds in their Strava activity feeds and social shares to document road traffic safety and heighten motorist awareness (popularized by platforms such as *MyBikeTraffic*). 

Currently, while `ATT-5088` introduced post-workout persistence and local visualization (`RadarTrafficSummaryCard`, aftermath map hotspot markers), athletes must manually calculate and copy-paste traffic statistics into their Strava description or external share sheets. 

This feature adds an opt-in toggle to automatically append a concise, standardized Radar Traffic & Close-Pass Summary to the Strava upload description (`StravaUploader.kt`) and include it in workout share intents (`ShareUtils.kt`), localized across all 9 supported languages without altering existing user workout descriptions.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  * *Audit of Hypothesis against Codebase*: The ticket suggests integrating radar description enrichment into `StravaUploader.kt`, `WorkoutRepository.kt`, `RadarSettingsSection.kt`, and `StravaSettingsDialog.kt`.
  * *Live Codebase Findings*:
    1. `StravaUploader.kt` lines 420–562: Currently fetches `WorkoutSummaryUpdateData` via `WorkoutRepository.getWorkoutSummaryUpdateData(fileBaseName)` and executes `formBuilder.add("description", description)` if non-empty. If the athlete did not provide manual workout notes, `description` is omitted from the metadata update request.
    2. `WorkoutRepository.kt` lines 640–690: `computeRadarTrafficSummaryFromSamples` parses vehicle counts, peak speeds, and threats from `WorkoutSamples.db`. `WorkoutDataMapper.kt` lines 523–558 resolves `RadarTrafficSummary`. However, there is no direct query method on `WorkoutRepository` to retrieve `RadarTrafficSummary` by `fileBaseName` for uploaders without rebuilding entire UI models.
    3. `TrainingApplication.java` & `prefs_strava.xml`: Strava upload options (`uploadStravaGPS`, `uploadStravaHR`, etc.) use `SharedPreferences`. Adding `SP_UPLOAD_STRAVA_RADAR_SUMMARY` (`uploadStravaRadarSummary`) provides instant, synchronous access for background upload workers (`StravaUploader.kt`) as well as settings UI composables.
    4. `RadarSettingsSection.kt` & `StravaSettingsDialog.kt`: Users should be able to toggle the summary from either the dedicated Radar Settings or the Strava Settings dialog. Both surfaces should observe and toggle this shared preference.
    5. `ShareUtils.kt`: `saveAndShare()` creates `Intent(Intent.ACTION_SEND)`. It currently accepts only `bitmap` and `fileName`, omitting `Intent.EXTRA_TEXT`. Adding an optional `shareText: String? = null` parameter allows `WorkoutShareBottomSheet.kt` and `combineWorkoutAndShare()` to pass the formatted summary.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Opt-in preference `uploadStravaRadarSummary` (default: `false`) exposed in `TrainingApplication.java`, `prefs_strava.xml`, `StravaSettingsDialog.kt`, and `RadarSettingsSection.kt`.
  * Standard Radar Summary formatter supporting BLE V1 (`ATT-5083`) and ANT+ (`ATT-5084`):
    `📡 Radar: 114 vehicles passed (1.8/km) • 🚀 Max 104 km/h (+68 km/h approach)`
  * High-Resolution V2 Radar Summary formatter supporting RearVue 820 (`ATT-5095` / `ATT-5096`):
    `📡 Radar: 114 vehicles passed (1.8/km) • ⚠️ 3 close passes (<1.5m, min 1.12m) • 🚛 9 heavy vehicles • 🚀 Max 104 km/h`
  * Complete 9-language localization parity in `strings_radar.xml` (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  * Safe description composition in `StravaUploader.kt`: Preserve existing user notes, prevent duplicate appends on retry/duplicate update (idempotency check), and suppress summary when `RADAR_VEHICLE_COUNT == 0` or no radar connected.
  * Integration into `ShareUtils.kt` (`Intent.EXTRA_TEXT`) for workout share sheets.
  * Comprehensive unit tests in `StravaUploaderRadarSummaryTest.kt` and `RadarTrafficSummaryFormatterTest.kt`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to FIT file encoding (`FitFileWriter.java` already completed in `ATT-5088`).
  * No changes to database schema or SQLite migrations (`DB_VERSION` remains intact).
  * No external network calls to third-party APIs other than existing Strava upload endpoints.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-EXP-043` ("Radar Traffic & Close-Pass Summary in Strava Description & Share Sheet") complementing `REQ-EXP-008` (Smart Asymmetric Naming) and `REQ-RAD-005` (Workout Traffic Persistence & Aftermath Summary Card).
* **Historical Origin & Commit Trace**: `ATT-902` / `REQ-EXP-008` established the non-destructive Strava metadata update flow in `StravaUploader.kt`. `ATT-5088` introduced `RadarTrafficSummary`.
* **Root Reason for Existing Formulation**: `StravaUploader.kt` strictly preserves user-entered notes and duplicate activity titles.
* **Preservation of Core Invariants**: The radar summary must append to existing notes with a newline separator (`\n\n`), never overwrite existing text, and never append if the toggle is disabled or vehicle count is zero.

---

## 5. Architectural Strategy & High-Level Solution

```
[WorkoutSamples.db / Summaries] ──> WorkoutRepository.getRadarTrafficSummary(fileBaseName)
                                                   │
                                                   ▼
[TrainingApplication.uploadStravaRadarSummary()] ─> RadarTrafficSummaryFormatter.format(summary, context)
                                                   │
                ┌──────────────────────────────────┴──────────────────────────────────┐
                ▼                                                                     ▼
     StravaUploader.doUpdate()                                               ShareUtils.saveAndShare()
 (formBuilder.add("description", notes + summary))                      (intent.putExtra(EXTRA_TEXT, summary))
```

1. **Formatter (`RadarTrafficSummaryFormatter.kt`)**: Dedicated utility to build the localized summary string from `RadarTrafficSummary`, respecting 9 languages and V1 vs V2 metric availability.
2. **Repository Access (`WorkoutRepository.kt`)**: Expose `getRadarTrafficSummary(fileBaseName: String): RadarTrafficSummary?` using cached summary metrics or sample streaming.
3. **Uploader Integration (`StravaUploader.kt`)**: When `TrainingApplication.uploadStravaRadarSummary()` is true and summary is non-null with `totalVehicles > 0`, append the formatted line to `summaryData.description` (with idempotency guard).
4. **Share Sheet Integration (`ShareUtils.kt` / `WorkoutShareBottomSheet.kt`)**: Pass `formattedSummary` via `Intent.EXTRA_TEXT` when sharing.
5. **Settings UI (`StravaSettingsDialog.kt`, `RadarSettingsSection.kt`)**: Add reactive Switch toggle bound to `uploadStravaRadarSummary`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. *Zero Overwrite*: Existing workout notes in `WorkoutSummaries.DESCRIPTION` must never be altered or lost in SQLite or on Strava.
  2. *Zero Non-Radar Regression*: Non-radar workouts and rides with 0 vehicle passes must emit zero radar summary text.
  3. *Idempotency*: Re-running `doUpdate()` (e.g. duplicate reconciliation or retry) must never duplicate the radar summary line.
  4. *Localization Parity*: All 9 languages supported with zero missing string resources.
* **Risk Rating**: **LOW** (1 SP feature, isolated to string formatting, opt-in preference toggle, and metadata payload composition).
