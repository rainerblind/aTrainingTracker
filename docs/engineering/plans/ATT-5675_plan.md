# Stage 3: Implementation Plan - ATT-5675: Remove Garmin Trademark '(ClimbPro)'

**Ticket**: [ATT-5675](https://rainerblind.atlassian.net/browse/ATT-5675)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5675`  
**Requirement Mapping**: `REQ-UI-461` (`REQ-UI-461.1`–`REQ-UI-461.3`) | **Test Mapping**: `TST-UI-461` (`TST-UI-461.1`–`TST-UI-461.4`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Description & Background
To eliminate trademark compliance exposure with Garmin Ltd.'s registered trademark **ClimbPro™** and avoid automated Google Play Store metadata rejections under *Unauthorized Use of Third-Party Trademarks*, `tuning_show_live_climbs_title` must be stripped of `(ClimbPro)` across all 9 supported application locales. In addition, legacy `ClimbPro` references across KDoc/Javadoc comments in production and test files must be sanitized, while preserving all existing DataStore preference keys, database schemas, and live climb detection/cockpit algorithms.

---

## 2. Invariants & Preserved Behavior
1. **Contract Invariant (`REQ-UI-461.3`)**: `KEY_SHOW_LIVE_CLIMBS` ("tuning_show_live_climbs") in `TuningPreferencesDataStore.kt` and SQLite column `SHOW_LIVE_CLIMBS` in `TrackingViewsDbHelper` remain byte-identical.
2. **Detection & Cockpit Invariant (`REQ-MAP-027`, `REQ-UI-383`)**: `ClimbDetector.kt`, `LiveClimbsRepository.kt`, and `LiveClimbSheet.kt` logic and layout remain 100% intact.
3. **9-Language Parity (`REQ-UI-461.3`)**: `tuning_show_live_climbs_title` exists and is non-empty across all 9 locales, matching `config_tracking__show_live_climbs`.
4. **Emoji Prohibition (`ATT-5400`)**: Zero decorative inline emojis in string resources.

---

## 3. UI Consistency & Architectural Strategy
* **Reference Screens**: `SensorGridScreen.kt` (Cockpit Bottom Sheet), `AdvancedTuningDialog.kt` (Tuning Section).
* **Theme Tokens**: Standard Material 3 typography and colors (`MaterialTheme.colorScheme.*`).
* **New One-Off Styles**: None. Pure string resource updates and comment hygiene.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Base English String Resource (`REQ-UI-461.1`)
* **File**: `app/src/main/res/values/strings.xml`
* **Changes**: Update `tuning_show_live_climbs_title` from `"Live Climbs (ClimbPro)"` to `"Live Climbs"`.

### Step 2: 8 Localized String Resources Parity (`REQ-UI-461.1`, `REQ-UI-461.3`)
* **Files**: `app/src/main/res/values-{de,es,fr,it,ja,nl,pl,pt}/strings.xml`
* **Changes**: Strip ` (ClimbPro)` suffix:
  - `de`: `"Live-Anstiege"` | `es`: `"Subidas en vivo"` | `fr`: `"Montées en direct"`
  - `it`: `"Salite in tempo reale"` | `ja`: `"ライブクライム"` | `nl`: `"Live beklimmingen"`
  - `pl`: `"Podjazdy na żywo"` | `pt`: `"Subidas ao vivo"`

### Step 3: Production Code Javadoc/KDoc Trademark Sanitization (`REQ-UI-461.2`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt` (line 60): Remove `(ClimbPro)`.
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java` (line 1517): Replace `Live ClimbPro` with `Live Climb`.

### Step 4: Test Suite Comment & Branding Absence Verification (`REQ-UI-461.2`, `TST-UI-461.1`–`TST-UI-461.3`)
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridLandscapeLayoutTest.kt` (line 187): Replace `ClimbPro` with `Live Climb`.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbsBrandingTest.kt`: Author contract test asserting absence of `ClimbPro` across all 9 `strings.xml` and production comments.

---

## 5. Verification Commands (`TST-UI-461.4`)
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.*" --tests "com.atrainingtracker.trainingtracker.ui.climbs.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* `python3 tools/verify_requirement_governance.py`
