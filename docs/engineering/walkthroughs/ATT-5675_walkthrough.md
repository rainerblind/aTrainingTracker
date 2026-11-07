# Stage 5: Walkthrough & Verification - ATT-5675: Remove Garmin Trademark '(ClimbPro)'

**Ticket**: [ATT-5675](https://rainerblind.atlassian.net/browse/ATT-5675)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5675`  
**Requirement Mapping**: `REQ-UI-461` (`REQ-UI-461.1`–`REQ-UI-461.3`) | **Test Mapping**: `TST-UI-461` (`TST-UI-461.1`–`TST-UI-461.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Executive Summary & Verification Overview
Eliminated third-party trademark compliance risks associated with Garmin Ltd.'s registered trademark **ClimbPro™** to safeguard Google Play Store compliance under *Unauthorized Use of Third-Party Trademarks*:
* **Tuning Title Neutralization (`tuning_show_live_climbs_title`)**: Stripped `(ClimbPro)` across all 9 supported application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) $\rightarrow$ harmonized with existing cockpit toggle phrasing `config_tracking__show_live_climbs` (EN: *"Live Climbs"*, DE: *"Live-Anstiege"*, ES: *"Subidas en vivo"*, FR: *"Montées en direct"*, IT: *"Salite in tempo reale"*, JA: *"ライブクライム"*, NL: *"Live beklimmingen"*, PL: *"Podjazdy na żywo"*, PT: *"Subidas ao vivo"*).
* **Code & Javadoc/KDoc Trademark Sanitization**: Sanitized legacy references in `LiveClimbSheet.kt` (line 60), `TrackingViewsDatabaseManager.java` (line 1517), and `SensorGridLandscapeLayoutTest.kt` (line 187).
* **Trademark Hygiene & Contract Suite**: Added `LiveClimbsBrandingTest.kt` asserting complete trademark absence, 100% 9-locale parity, and preference/schema stability.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-461.1` | `TST-UI-461.1` | `LiveClimbsBrandingTest`, `TranslationParityTest` | **PASSED** | `Verified` |
| `REQ-UI-461.2` | `TST-UI-461.2` | `LiveClimbsBrandingTest` (Code & KDoc Audit) | **PASSED** | `Verified` |
| `REQ-UI-461.3` | `TST-UI-461.3` | `TranslationParityTest`, Contract Invariant Tests | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Runtime: 7s)
* **Executed Scope**:
  * `--tests "com.atrainingtracker.trainingtracker.ui.climbs.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"`
  * `--tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
  * `--tests "com.atrainingtracker.trainingtracker.build.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridLandscapeLayoutTest"`
* **Result**: `BUILD SUCCESSFUL in 7s` (100% PASS across targeted climbs, tuning preferences, localization, and build smoke test suites).
* **Governance Check**: `python3 tools/verify_requirement_governance.py` -> `PASS: Living documentation master tables match all 9 domain shards with 100% parity.`

---

## 4. Key Implementation Highlights

```xml
<!-- app/src/main/res/values/strings.xml -->
<string name="tuning_show_live_climbs_title">Live Climbs</string>

<!-- app/src/main/res/values-de/strings.xml -->
<string name="tuning_show_live_climbs_title">Live-Anstiege</string>
```

```kotlin
// app/src/test/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbsBrandingTest.kt
assertFalse(titleText.contains("ClimbPro", ignoreCase = true))
assertFalse(titleText.contains("(") || titleText.contains(")"))
```

---

## 5. Invariants & Preserved Behavior
1. **Contract Invariant (`REQ-UI-461.3`)**: `KEY_SHOW_LIVE_CLIMBS` ("tuning_show_live_climbs") and database column `TrackingViewsDbHelper.SHOW_LIVE_CLIMBS` ("ShowLiveClimbs") remain byte-identical.
2. **Climb Telemetry & Cockpit UI**: `ClimbDetector.kt`, `LiveClimbsRepository.kt`, and `LiveClimbSheet.kt` logic and layouts remain 100% untouched.
3. **9-Language Parity & Zero Emojis (`ATT-5400`)**: Complete parity across all 9 locales with zero inline emojis.
