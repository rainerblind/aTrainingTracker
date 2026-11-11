# Stage 2: Requirement & Test Specification - ATT-5122: Append optional Radar Traffic & Close-Pass Summary to Strava upload description and workout share summary

**Ticket**: [ATT-5122](https://rainerblind.atlassian.net/browse/ATT-5122)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `v2026.46`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirements**: `REQ-RAD-007`  
**Test Spec IDs**: `TST-RAD-007`  
**Branch**: `feature/ATT-5122`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification

### 1.1 Scope & System Rationale
Athletes riding with cycling rearview radar frequently share overtaking statistics, close-pass occurrences, and maximum approaching speeds in Strava activity feeds and social shares (popularized by *MyBikeTraffic*). This feature introduces an opt-in toggle to automatically format and append a concise Radar Traffic & Close-Pass Summary to Strava upload descriptions and workout share texts without altering existing user workout notes.

### 1.2 Functional & Architectural Requirements
1. **Opt-In Preference & Configuration (`REQ-RAD-007.1`)**:
   - `TrainingApplication.java` SHALL provide `SP_UPLOAD_STRAVA_RADAR_SUMMARY` (`uploadStravaRadarSummary`, default `false`).
   - Exposed as a toggle switch in `prefs_strava.xml`, `StravaSettingsDialog.kt`, and `RadarSettingsSection.kt`.
2. **Standard & High-Resolution Summary Formatter (`REQ-RAD-007.2`)**:
   - `RadarTrafficSummaryFormatter.kt` SHALL format standard BLE V1 / ANT+ metrics:
     `📡 Radar: <N> vehicles passed (<density>/km) • 🚀 Max <max_veh_spd> km/h (+<max_approach_spd> km/h approach)`
   - When V2 high-resolution metrics are present (`closePassCount > 0` or `heavyVehicleCount > 0`), include:
     `⚠️ <M> close passes (<1.5m, min <min_dist>m)` and `🚛 <K> heavy vehicles`.
3. **Strava Metadata Integration & Non-Destructive Update (`REQ-RAD-007.3`)**:
   - `StravaUploader.kt` SHALL query `WorkoutRepository.getRadarTrafficSummary(fileBaseName)`.
   - When enabled and `totalVehicles > 0`, append the formatted string to `summaryData.description` with a double newline (`\n\n`).
   - Symmetrically protect against duplication: if description already contains the radar summary line, do not append again.
4. **Workout Share Sheet Integration (`REQ-RAD-007.4`)**:
   - `ShareUtils.kt` (`saveAndShare`) SHALL support attaching `Intent.EXTRA_TEXT`.
   - `WorkoutShareBottomSheet.kt` SHALL provide the formatted traffic summary in share intents.
5. **Localization Parity (`REQ-RAD-007.5`)**:
   - 100% string parity across all 9 languages (EN, DE, ES, FR, IT, JA, NL, PL, PT) in `strings_radar.xml`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Strava Append)**:
  * *Given* `uploadStravaRadarSummary` is enabled and a workout recorded 114 vehicle passes,
  * *When* uploaded to Strava,
  * *Then* `StravaUploader.doUpdate()` appends the localized summary line without overwriting existing notes.
* **Criterion 2 (Zero-Vehicle / Non-Radar Suppression)**:
  * *Given* a workout recorded without radar or with 0 vehicle passes,
  * *When* uploaded to Strava or shared,
  * *Then* zero radar summary text is appended.
* **Criterion 3 (Idempotent Update)**:
  * *Given* a workout already containing a radar summary line in notes,
  * *When* uploaded or updated again,
  * *Then* the summary line is not duplicated.

---

## 2. Test Specification

### Test Case 1: Radar Traffic Summary Formatter (`TST-RAD-007.1`)
* **Scope**: Unit Test (`RadarTrafficSummaryFormatterTest.kt`)
* **Action**: Pass synthetic `RadarTrafficSummary` models (standard V1/ANT+, V2 close-pass, V2 heavy-vehicle, 0 vehicles, null); assert string format matching specifications across locales.
* **Expected Result**: Exact single-line formatting; null on 0 vehicles; accurate singular/plural grammar.

### Test Case 2: Strava Uploader Non-Destructive Update (`TST-RAD-007.2`)
* **Scope**: Unit & Contract Test (`StravaUploaderRadarSummaryTest.kt`)
* **Action**: Simulate Strava `doUpdate` with and without existing notes, with preference enabled/disabled, and with duplicate runs; assert `formBuilder` description parameter.
* **Expected Result**: Clean appending separated by `\n\n`; existing notes preserved; duplicate append prevented; omitted when toggle disabled.

### Test Case 3: Workout Share Intent Integration (`TST-RAD-007.3`)
* **Scope**: Unit Test (`ShareUtilsTest.kt`)
* **Action**: Verify `saveAndShare` propagates formatted summary via `Intent.EXTRA_TEXT`.
* **Expected Result**: `Intent.EXTRA_TEXT` correctly set.

### Test Case 4: 9-Language Localization Parity (`TST-RAD-007.4`)
* **Scope**: Contract Test (`TranslationParityTest.kt`)
* **Action**: Verify `radar_summary_*` and `radar_strava_*` keys across all 9 `strings_radar.xml` files.
* **Expected Result**: 100% key parity with matching format argument specifiers.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Target Verification |
| :--- | :--- | :--- | :--- |
| `REQ-RAD-007.1` | Opt-in preference in Strava & Radar settings | `TST-RAD-007.2` | `StravaUploaderRadarSummaryTest.kt` |
| `REQ-RAD-007.2` | Standard & V2 Radar summary string formatting | `TST-RAD-007.1` | `RadarTrafficSummaryFormatterTest.kt` |
| `REQ-RAD-007.3` | Strava description append & idempotency | `TST-RAD-007.2` | `StravaUploaderRadarSummaryTest.kt` |
| `REQ-RAD-007.4` | Workout share sheet text attachment | `TST-RAD-007.3` | `ShareUtilsTest.kt` |
| `REQ-RAD-007.5` | 9-language localization parity | `TST-RAD-007.4` | `TranslationParityTest.kt` |
