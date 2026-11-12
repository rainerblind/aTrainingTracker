# Stage 3: Implementation Plan - ATT-7022: SensorStatus & SportingJourneySheet: Wire Supporter Emblem to 'My Sporting Journey' Trophy Vault

**Ticket**: [ATT-7022](https://rainerblind.atlassian.net/browse/ATT-7022)  
**Sub-task**: [ATT-7193](https://rainerblind.atlassian.net/browse/ATT-7193) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-67](https://rainerblind.atlassian.net/browse/ATT-67) (*Zeiträume / Periods & Stats*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3` | **Branch**: `feature/ATT-7022`  
**Requirement Mapping**: `REQ-SPT-001`–`REQ-SPT-004` | **Test Mapping**: `TST-SPT-001`–`TST-SPT-004`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
Wire the Supporter/Pioneer emblem tap in `SensorStatus` (`TrackingTabsScreen.kt`) and `SupporterScreen.kt` to "The Trophy Vault — My Sporting Journey" (`SportingJourneySheet.kt`). The engine calculates emotional, story-driven retrospectives across 14+ years (2012–2026) 100% offline from existing SQLite tables (`WorkoutSummaries`, `ExtremumValues`) on `Dispatchers.IO` with zero schema migrations, encompassing 4 pillars: Hero Totals (Equator/Everest scale), Hidden Gems (cranks, heartbeats, kWh power plant), Milestones, and an Achievement Room with a shareable Athlete Passport.

---

## 2. Traceability & Requirements Mapping
* `REQ-SPT-001` (*Cockpit Emblem & Entry Points*) $\rightarrow$ `TST-SPT-001` (`TrackingTabsScreenTest`, `SupporterScreenTest`)
* `REQ-SPT-002` (*Multi-Horizon & Sport Filtering Engine*) $\rightarrow$ `TST-SPT-002` (`SportingJourneyRepositoryTest`)
* `REQ-SPT-003` (*Hidden Gems & Achievement Logic*) $\rightarrow$ `TST-SPT-003` (`SportingJourneyCalculatorTest`)
* `REQ-SPT-004` (*Shareable Passport & 9-Locale Parity*) $\rightarrow$ `TST-SPT-004` (`TranslationParityTest`, `SportingJourneyShareTest`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Database Schema Migrations**: `DB_VERSION = 30` strictly unchanged; all aggregations query existing tables and indices.
2. **Thread Safety & UI Fluidity**: Heavy SQLite queries execute strictly on `Dispatchers.IO`; UI observes reactive `StateFlow`.
3. **Supporter Tier Consistency**: Free tier emblem remains hidden in cockpit; paid/pioneer tiers retain visual badge and tap action.
4. **Code Lock & Human Gate**: Production code edits locked until Gate 3; parent ticket terminal completion reserved for human.

---

## 4. Proposed Architectural Changes
* **Domain Layer**: `SportingJourneyModels.kt` (data contracts for `HeroTotals`, `HiddenGems`, `MilestoneItem`, `AchievementBadge`, `TimeHorizon`, `SportFilter`) & `SportingJourneyCalculator.kt` (pure mathematical calculations: Equator %, Everest multiples, crank turns, heartbeats, mechanical work in kWh, circadian DNA, and badge unlocks).
* **Data Layer**: `SportingJourneyRepository.kt` querying `WorkoutSummaries` (`finished = 1`) and `ExtremumValues` on `Dispatchers.IO`.
* **UI Layer**: `SportingJourneySheet.kt` (`AppModalBottomSheet` host with time/sport chips and 4 insight pillars) and `SportingJourneyComponents.kt`.
* **Host Reachability (Rule 81)**: Wire `onSupporterClick` in `TrackingTabsScreen.kt` (landscape and portrait) and add entry card in `SupporterScreen.kt`.
* **UI Consistency (Rule 23)**:
  * Reference Screen: `SupporterScreen.kt`, `PeriodSummaryScreen.kt`, and `FirstJubileeCelebrationDialog.kt`.
  * Reused Components: `AppModalBottomSheet`, `FilterChip`, `Card`, `MaterialTheme.colorScheme`, `TTColor`.
  * Theme Tokens: `RoundedCornerShape(12.dp)`, 8/12/16dp spacing, standard Material 3 typography tokens; zero one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Domain Calculator & SQLite Repository (`REQ-SPT-002`, `REQ-SPT-003`, `TST-SPT-002`, `TST-SPT-003`)
* Files: `SportingJourneyModels.kt`, `SportingJourneyCalculator.kt`, `SportingJourneyRepository.kt`
* Action: Implement domain models, pure mathematical calculator, and IO-dispatched SQLite queries for All-Time vs Year and All vs Bike vs Run.

### Step 2: Cockpit & Settings Host Reachability Wiring (`REQ-SPT-001`, `TST-SPT-001`)
* Files: `SensorStatus.kt`, `TrackingTabsScreen.kt`, `SupporterScreen.kt`
* Action: Provide `onSupporterClick` callback in both `SensorStatus` invocations in `TrackingTabsScreen.kt` to toggle sheet; add entry card in `SupporterScreen.kt`.

### Step 3: SportingJourneySheet UI & 4 Insight Pillars (`REQ-SPT-001`, `REQ-SPT-003`, `TST-SPT-001`, `TST-SPT-003`)
* Files: `SportingJourneySheet.kt`, `SportingJourneyComponents.kt`
* Action: Construct Material 3 bottom sheet featuring period/sport chips, Hero Totals card, Hidden Gems 4-grid, Milestones, and Achievement Room.

### Step 4: Shareable Athlete Passport via Android Intent (`REQ-SPT-004`, `TST-SPT-004`)
* Files: `SportingJourneyShareHelper.kt`, `SportingJourneySheet.kt`
* Action: Format summary passport text card stamped with active tier emblem and dispatch via `Intent.ACTION_SEND`.

### Step 5: 9-Language Localization & Unit Verification (`REQ-SPT-004`, `TST-SPT-004`)
* Files: `app/src/main/res/values*/strings_stats.xml` across all 9 locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`)
* Action: Author localization keys with 100% parity across all 9 languages. Verify with `TranslationParityTest` and targeted unit test suite.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.sportingjourney.*" --tests "com.atrainingtracker.trainingtracker.ui.TranslationParityTest"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback**: Worktree branch isolation (`feature/ATT-7022`) allows complete `git revert` without impacting `develop` or `sprint/2026-46.3`.
