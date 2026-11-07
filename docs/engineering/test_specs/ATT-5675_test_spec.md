# Stage 2: Requirement & Test Specification - ATT-5675: Remove Garmin Trademark '(ClimbPro)'

**Ticket**: [ATT-5675](https://rainerblind.atlassian.net/browse/ATT-5675)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5675`  
**Requirement Mapping**: `REQ-UI-461` (refining `REQ-MAP-027` Clause 5, `REQ-UI-383`) | **Test Spec ID**: `TST-UI-461`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-UI-461`)

### 1.1 Problem Statement & Rationale
**ClimbPro™** is a registered trademark of Garmin Ltd. Utilizing third-party trademarks in user-visible UI strings creates legal trademark exposure and risks automated rejection by the Google Play Store under *Unauthorized Use of Third-Party Trademarks*. In `tuning_show_live_climbs_title`, the bracketed suffix `(ClimbPro)` across all 9 language resources is redundant because the subtitle `tuning_show_live_climbs_summary` clearly describes the live climb cockpit functionality. The system SHALL remove `(ClimbPro)` across all 9 locales and neutralize related comments in production and test code.

### 1.2 Functional & Architectural Requirements
1. **Tuning Title Neutralization (`REQ-UI-461.1`)**: Across all 9 locales (`values*/strings.xml`), `tuning_show_live_climbs_title` SHALL omit `(ClimbPro)` (e.g. EN: *"Live Climbs"*, DE: *"Live-Anstiege"*, ES: *"Subidas en vivo"*, FR: *"Montées en direct"*, IT: *"Salite in tempo reale"*, JA: *"ライブクライム"*, NL: *"Live beklimmingen"*, PL: *"Podjazdy na żywo"*, PT: *"Subidas ao vivo"*).
2. **Comment Hygiene in Code & Tests (`REQ-UI-461.2`)**:
   - `LiveClimbSheet.kt` (line 60 KDoc): Remove `(ClimbPro)`.
   - `TrackingViewsDatabaseManager.java` (line 1517 Javadoc): Replace `Live ClimbPro` with `Live Climb`.
   - `SensorGridLandscapeLayoutTest.kt` (line 187 KDoc): Replace `ClimbPro` with `Live Climb`.
3. **Localization Parity & Invariants (`REQ-UI-461.3`)**: `tuning_show_live_climbs_title` SHALL maintain 100% parity across all 9 locales with zero inline emojis (`ATT-5400`). Preference key `KEY_SHOW_LIVE_CLIMBS` and SQLite schema remain untouched.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (UI String Neutralization)**: *Given* an athlete inspecting Advanced Tuning in any of the 9 locales, *When* reading the live climbs toggle, *Then* the title displays the localized name without `(ClimbPro)`.
* **AC-2 (Comment Hygiene)**: *Given* source files `LiveClimbSheet.kt`, `TrackingViewsDatabaseManager.java`, and `SensorGridLandscapeLayoutTest.kt`, *When* audited for third-party branding, *Then* zero occurrences of `ClimbPro` remain.
* **AC-3 (9-Locale Parity)**: *Given* `TranslationParityTest`, *When* validating all 9 locales, *Then* `tuning_show_live_climbs_title` exists, is non-empty, and contains zero format specifiers or emojis.
* **Invariants**: DataStore key `KEY_SHOW_LIVE_CLIMBS`, database column `SHOW_LIVE_CLIMBS`, and climb detection/telemetry algorithms remain byte-identical.

---

## 2. Test Specification (`TST-UI-461`)

### Test Case 1: 9-Language Title Neutralization & Absence Audit (`TST-UI-461.1`)
* **Scope**: Localization Contract Test (`TranslationParityTest.kt`, new `LiveClimbsBrandingTest.kt`)
* **Action**: Verify that `tuning_show_live_climbs_title` across all 9 locales exists, is non-empty, contains zero inline emojis, and does NOT contain `ClimbPro` or `(ClimbPro)`.

### Test Case 2: Codebase Comment Trademark Audit (`TST-UI-461.2`)
* **Scope**: Source Code & Javadoc Hygiene Contract Test (`LiveClimbsBrandingTest.kt`)
* **Action**: Verify that `LiveClimbSheet.kt`, `TrackingViewsDatabaseManager.java`, and `SensorGridLandscapeLayoutTest.kt` do not contain `ClimbPro`.

### Test Case 3: DataStore & Database Contract Invariants (`TST-UI-461.3`)
* **Scope**: Database & Preferences Contract Test (`TrackingViewsDatabaseManagerTest.kt`)
* **Action**: Assert `TrackingViewsDbHelper.SHOW_LIVE_CLIMBS` ("show_live_climbs") and default column mapping are preserved.

### Test Case 4: Tier 1 Targeted Regression Suite (`TST-UI-461.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.*" --tests "com.atrainingtracker.trainingtracker.ui.climbs.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Action**: Confirm 100% test pass rate across all modified packages.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-461.1` | Localization | 9 `strings.xml` files, `TranslationParityTest` | `REQ-UI-461.1`, `REQ-UI-461.3` | Specified |
| `TST-UI-461.2` | Comment Audit | `LiveClimbSheet.kt`, `TrackingViewsDatabaseManager.java`, `SensorGridLandscapeLayoutTest.kt` | `REQ-UI-461.2` | Specified |
| `TST-UI-461.3` | Contract | `TrackingViewsDatabaseManager.java`, `TrackingViewsDbHelper` | `REQ-UI-461.3` | Specified |
| `TST-UI-461.4` | Regression | Affected Package Test Suites | `REQ-UI-461.1`..`REQ-UI-461.3` | Specified |
