# Stage 2: Requirement & Test Specification - ATT-5371: SupporterScreen UX & Pioneer Year

**Ticket**: [ATT-5371](https://rainerblind.atlassian.net/browse/ATT-5371) | **Parent Epic**: [ATT-51](https://rainerblind.atlassian.net/browse/ATT-51) (*Monetize*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-45.5` | **Requirements**: `REQ-SET-085`, `REQ-SET-086` | **Tests**: `TST-SET-085`, `TST-SET-086`, `TST-LOC-001`  
**Branch**: `feature/ATT-5371` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-SET-085 & REQ-SET-086)

### 1.1 Scope & Technical Requirements
On-device Pixel 10 review revealed hardcoded Pioneer year 2011, hardware device purchase claims, button text wrapping, `100%%` typo, and hardcoded English strings. Requirements are updated:
1. **Dynamic Pioneer Year (`REQ-SET-085.3`)**:
   - `WorkoutSummariesDatabaseManager` SHALL query `SELECT MIN(timeStart)` via `getEarliestWorkoutYear(): Integer?`.
   - `ProFeatureManager` SHALL cache `pioneerSinceYear: Flow<Int?>` in `SettingsDataStore`.
   - In both `SupporterScreen` (`PioneerStatusBanner`) and `AppNavigationDrawer` (`DrawerHeader`), display `"★ Pioneer (Seit %1$d)"` / `"★ Pioneer (Since %1$d)"` when a year is resolved, and fallback to `"★ Pioneer"` when no workouts exist.
2. **Retire Hardware Voting & Retune Value Proposition (`REQ-SET-086.2`)**:
   - Completely remove `HardwareVotingSection` and hardware acquisition claims from `SupporterScreen`, `SupporterComponents`, and strings, focusing copy on indie app sustainability.
3. **Card Layout & Wrap Prevention (`REQ-SET-086.4`)**:
   - In `SupporterPlanCard`, place `"Empfehlung"` / `"Recommended"` badge vertically above the tier title.
   - Enforce `maxLines = 1, softWrap = false` on action buttons to prevent single-character/mid-word wrapping (`Rule 38`).
4. **Typo Fix & Full 9-Language Localization Parity (`REQ-SET-086.5`, `TST-LOC-001`)**:
   - Replace literal `100%%` with `100%` in `supporter_screen_subtitle` across all affected locales.
   - Extract all hardcoded strings in `SupporterComponents.kt`, `SupporterScreen.kt`, and `AppNavigationDrawer.kt` into `res/values*/strings.xml`.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-SET-085` (Pioneer Recognition) and `REQ-SET-086` (Supporter Subscriptions).
* `Historical Origin & Commit Trace`: Commit `c899cda1` (`ATT-51`, Sprint 2026-45.4).
* `Root Reason for Existing Formulation`: 2011 was hardcoded as an initial placeholder; hardware voting was included as an exploratory user-intent signal.
* `Preservation of Core Invariants`: Lifetime Pioneer status remains permanently free; core tracking remains 100% unmonetized; Google Play Billing SKU definitions remain unchanged.

### 1.2 Acceptance Criteria & Invariants
* **Criterion 1 (Dynamic Pioneer Year)**: Given an athlete with historical workouts from 2018, when viewing the drawer or supporter banner, then `"★ Pioneer (Seit 2018)"` is displayed. If no workouts exist, `"★ Pioneer"` is displayed without a year.
* **Criterion 2 (Hardware Voting Removed)**: `SupporterScreen` renders cleanly without hardware voting selector or hardware promises.
* **Criterion 3 (Wrap Prevention)**: Recommendation badge sits above title; action button renders on 1 line without wrapping.
* **Criterion 4 (Localization)**: `100%` renders with single percent sign; zero hardcoded English text in UI.
* **Invariants**: Pioneer privileges irrevocable; tracking, sensors, and local exports 100% free forever.

---

## 2. Test Specification (TST-SET-085, TST-SET-086 & TST-LOC-001)

* **TST-SET-085.1 (Dynamic Earliest Workout Year Resolution)**: In `WorkoutSummariesDatabaseManagerTest`, verify `getEarliestWorkoutYear()` parses `MIN(timeStart)` correctly; test null fallback.
* **TST-SET-085.2 (Pioneer Badge Formatting Contract)**: Assert `PioneerStatusBanner` and `DrawerHeader` format dynamic year string or fallback.
* **TST-SET-086.1 (Layout & Wrap Contract)**: In Compose tests, assert `SupporterPlanCard` button has `maxLines = 1` and recommendation badge is positioned above title.
* **TST-SET-086.2 (Hardware Voting Retirement)**: Assert `HardwareVotingSection` composable and references are removed.
* **TST-LOC-001 (Monetization 9-Locale Parity)**: Run `TranslationParityTest`; assert 100% key parity across all 9 locales, single `%` in subtitle, and zero missing parameters.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-085.1` | Database/Manager | `WorkoutSummariesDatabaseManager.getEarliestWorkoutYear` | `REQ-SET-085.3` | Specified |
| `TST-SET-085.2` | UI Badge Format | `PioneerStatusBanner`, `DrawerHeader` | `REQ-SET-085.3` | Specified |
| `TST-SET-086.1` | UI Layout/Card | `SupporterPlanCard` | `REQ-SET-086.4` | Specified |
| `TST-SET-086.2` | UI Cleanup | `SupporterScreen` | `REQ-SET-086.2` | Specified |
| `TST-LOC-001` | Localization | `TranslationParityTest` | `REQ-SET-086.5` | Specified |
