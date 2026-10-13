# Stage 2: Requirement & Test Specification - ATT-3325: Replace persistent option with 10s option for navigation cue dismiss duration

**Ticket**: [ATT-3325](https://rainerblind.atlassian.net/browse/ATT-3325)  
**Sub-task**: [ATT-3368](https://rainerblind.atlassian.net/browse/ATT-3368) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-287` (amending Clauses 1, 2, 3)  
**Test Mapping**: `TST-UI-247` (amending Clauses 1, 2, 3)  
**Branch**: `feature/ATT-3325`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Formal Requirements Specification (`REQ-UI-287` Amendment)

The system SHALL amend `REQ-UI-287` Clauses 1, 2, and 3 to replace the indefinite persistent option (`0`) with a 10s option (`10`) for turn cue auto-dismiss duration:

### Clause 1: Preference DataStore Persistence & Options (`TuningPreferencesDataStore.kt`)
* The system SHALL define `TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS` as `listOf(2, 3, 4, 5, 8, 10)`.
* `KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC` (Int) SHALL continue to default to `4` seconds (`DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC`).
* When reading `KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC`, any legacy persisted value of `0` ("Dauerhaft") or any value not in `NAVIGATION_CUE_DISMISS_OPTIONS` SHALL safely fall back to `DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC` (`4` seconds).
* Mutator `updateNavigationCueDismissDurationSec(durationSec: Int)` SHALL persist values $\in \{2, 3, 4, 5, 8, 10\}$, falling back to `4` for invalid or legacy inputs.

### Clause 2: Expert Settings UI Simplification (`NavigationSection.kt`, `AdvancedTuningAccordion.kt`)
* In `NavigationSection.kt`, the "Navigation Cue Dismiss Duration" (*Anzeigedauer der Navigationshinweise*) section SHALL expose selectable filter chips corresponding strictly to `[2s, 3s, 4s, 5s, 8s, 10s]`.
* The special branch for `sec == 0` / "Dauerhaft" SHALL be excised. All chips and header value indicators SHALL render uniform `"${sec}s"` formatting.
* `TuningSubtitleFormatter.formatNavigationSubtitle()` SHALL format the navigation section summary subtitle incorporating the effective dismiss duration (e.g. `"10s, 1.0 km"` or `"4s, 1.0 km"`).

### Clause 3: Runtime In-Ride HUD Application (`TurnPromptBanner.kt`)
* In `TurnPromptBanner.kt`, `dismissDurationSec` SHALL evaluate strictly to a positive integer ($2, 3, 4, 5, 8, \text{ or } 10$).
* When approaching a cue or transitioning to `isTurnNow`, the banner SHALL delay for `dismissDurationSec * 1000L` milliseconds before smoothly animating out, guaranteeing that turn banners never permanently occlude the cockpit sensor grid and map viewport.

### Clause 4: 9-Language Localization Integrity
* The obsolete string resource `tuning_nav_cue_dismiss_persistent` SHALL be removed across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-UI-287` (*Navigation Cue Auto-Dismiss Duration, Countdown Distance & Expert Settings Configuration*), Clauses 1, 2, 3.
2. **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.2 (`ATT-2632`, commit `8e6bcfc5`), amended in Sprint 2026-42.1 (`ATT-3321` and `ATT-3322`).
3. **Root Reason for Existing Formulation**: In ATT-2632, the `0` ("Dauerhaft") option was included for users wanting turn banners to never disappear. However, in practice, turn banners permanently occlude the cockpit sensor grid and map viewport until the next turn instruction, hiding telemetry and distracting athletes. Athletes requesting extended visibility require up to 10 seconds, which gives ample reading time while guaranteeing clean auto-dismissal.
4. **Preservation of Core Invariants**:
   * Default dismiss duration remains `4`s (`DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC`).
   * Factory reset (`resetToDefaults()`) restores `4`s.
   * Legacy user profiles with `0` automatically coerce to `4`s without user intervention.
   * Overlays remain 100% solid opaque (`ATT-3322`).
   * Countdown distance tuning (`30m .. 300m`, `ATT-3321`), turn cue detection math, and 100% full-suite unit test pass rate remain strictly preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1: Dismiss Duration Chip Options Parity**:
  * *Given* the athlete opens Advanced Settings -> Navigation,
  * *When* viewing the "Anzeigedauer der Navigationshinweise" section,
  * *Then* the available chips SHALL be exactly `2s`, `3s`, `4s` (default), `5s`, `8s`, and `10s`, with NO "Dauerhaft" (Persistent) chip rendered.
* **Criterion 2: 10s Selection Persistence & In-Ride Execution**:
  * *Given* the athlete selects the `10s` chip,
  * *When* `TurnPromptBanner` appears during active navigation,
  * *Then* the banner SHALL remain visible for 10 seconds before auto-dismissing, and `10` SHALL be persisted to DataStore across app restarts.
* **Criterion 3: Legacy Profile Fallback**:
  * *Given* a user profile where `navigationCueDismissDurationSec` was previously stored as `0`,
  * *When* DataStore loads the configuration,
  * *Then* the value SHALL evaluate to `4` (`DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC`).
* **Criterion 4: Subtitle Formatting**:
  * *Given* `radiusKm = 1.0f` and `dismissSec = 10`,
  * *When* `TuningSubtitleFormatter.formatNavigationSubtitle` evaluates,
  * *Then* it SHALL return `"10s, 1.0 km"`.
* **Criterion 5: 9-Language Localization Audit**:
  * *Given* `TranslationParityTest`,
  * *When* executed across all 9 locales,
  * *Then* zero missing or mismatched strings SHALL occur.

---

## 4. Test Cases Specification (`TST-UI-247` Amendment)

### Test 1: Defaults and Options List Assertion (`TuningConfigTest.kt`)
* **Objective**: Validate that `NAVIGATION_CUE_DISMISS_OPTIONS` equals `listOf(2, 3, 4, 5, 8, 10)` and default duration is `4`.
* **Assertion**:
  ```kotlin
  assertEquals(listOf(2, 3, 4, 5, 8, 10), TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS)
  assertEquals(4, config.navigationCueDismissDurationSec)
  ```

### Test 2: Subtitle Formatter Assertion (`AdvancedTuningAccordionTest.kt`)
* **Objective**: Validate that `formatNavigationSubtitle` formats `"10s, 2.5 km"` and falls back to `"4s, 2.5 km"` if passed `0`.
* **Assertion**:
  ```kotlin
  val subtitle = TuningSubtitleFormatter.formatNavigationSubtitle(radiusKm = 2.5f, dismissSec = 10)
  assertEquals("10s, 2.5 km", subtitle)
  ```

### Test 3: DataStore Legacy 0 Fallback & 10s Persistence (`TuningPreferencesDataStoreDismissDurationTest.kt`)
* **Objective**: Validate that persisting `10` emits `10`, while attempting to read `0` falls back to `4`.
* **Assertion**:
  * `updateNavigationCueDismissDurationSec(10)` -> emits `10`.
  * `updateNavigationCueDismissDurationSec(0)` -> falls back to `4`.
  * `updateNavigationCueDismissDurationSec(99)` -> falls back to `4`.

### Test 4: 9-Language Translation Parity (`TranslationParityTest.kt`)
* **Objective**: Confirm that removal of `tuning_nav_cue_dismiss_persistent` is complete across all 9 locales with 100% parity.
* **Execution**: `./gradlew testDebugUnitTest --tests "*TranslationParityTest*"`

### Test 5: Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`)
* **Objective**: 100% clean-room test pass rate across all modules.

---

## 5. Traceability Matrix

| Test Case | Description | Maps to Requirement Clause | Expected Result |
| :--- | :--- | :--- | :--- |
| `TST-UI-247.1` | Options list & default duration verification (`TuningConfigTest.kt`) | `REQ-UI-287` Clause 1 | `[2, 3, 4, 5, 8, 10]`, default 4s |
| `TST-UI-247.2` | 10s persistence & legacy 0 fallback verification (`TuningPreferencesDataStoreDismissDurationTest.kt`) | `REQ-UI-287` Clause 1 | 10s persists, 0 falls back to 4s |
| `TST-UI-247.3` | Subtitle format verification (`AdvancedTuningAccordionTest.kt`) | `REQ-UI-287` Clause 2 | `"10s, 2.5 km"` |
| `TST-UI-247.4` | 9-Language Localization Audit (`TranslationParityTest.kt`) | `REQ-UI-287` Clause 4 | 100% parity across 9 locales |
| `TST-UI-247.5` | Full Clean-Room Regression Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | 100% pass rate (0 failures) |
