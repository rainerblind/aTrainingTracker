# Stage 3: Implementation Plan - ATT-5371: SupporterScreen UX, Dynamic Pioneer Year & Localization

**Ticket**: [ATT-5371](https://rainerblind.atlassian.net/browse/ATT-5371)  
**Sub-task**: [ATT-5384](https://rainerblind.atlassian.net/browse/ATT-5384) (`[Impl-Plan]`) | **Parent Epic**: [ATT-51](https://rainerblind.atlassian.net/browse/ATT-51) (*Monetize*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.5` | **Branch**: `feature/ATT-5371`  
**Requirement Mapping**: `REQ-SET-085`, `REQ-SET-086` | **Test Mapping**: `TST-SET-085`, `TST-SET-086`, `TST-LOC-001`  
**Author**: AI Agent 1 (`subagent_pro`) | **Risk**: **LOW** | **Date**: 2026-11-04  

---

## 1. Architecture Decomposition (SWE.2) & UI Consistency
* **Database & Repository Layer**: `WorkoutSummariesDatabaseManager.java` queries `SELECT MIN(timeStart)` via `getEarliestWorkoutYear(): Integer?`.
* **State & Domain Layer**: `SettingsDataStore.kt` persists `pioneerSinceYear: Flow<Int?>`. `ProFeatureManager.kt` initializes and exposes `pioneerSinceYear: StateFlow<Int?>` to UI layers.
* **UI Layer**:
  * `SupporterComponents.kt`: Restructure `SupporterPlanCard` to position `Empfehlung` badge vertically above tier title; enforce `maxLines = 1, softWrap = false` on action buttons (`Rule 38`). Remove `HardwareVotingSection`. Update `PioneerStatusBanner` and `ActiveTierBanner` with dynamic year and localized strings.
  * `SupporterScreen.kt`: Remove `HardwareVotingSection` invocation and state. Update footer card copy.
  * `AppNavigationDrawer.kt`: Dynamically format Pioneer drawer badge with year / fallback and localize all drawer tier badges.
* **UI Consistency (Rule 23)**: Reuses `MaterialTheme.colorScheme` (`primaryContainer`, `onPrimaryContainer`, `secondaryContainer`, `surfaceVariant`), `RoundedCornerShape(8.dp/12.dp)`, and centralized typography. Zero new hardcoded hex colors outside `Color.kt`.

---

## 2. Invariant Protection & System Safety
1. **Pioneer Lifetime Invariant**: Pioneer Pro privileges remain irrevocable and 100% free forever.
2. **Core Feature Invariant**: Tracking, offline maps, sensor telemetry, and exports remain 100% free and unmonetized.
3. **No-Wrap Invariant (Rule 38)**: Zero single-character mid-word wrapping on buttons (`maxLines = 1, softWrap = false`).
4. **9-Language Parity**: 100% resource coverage across all 9 locales with zero missing format specifiers.

---

## 3. Atomic Implementation Steps (Stage 4 Construction)

### Step 1: Database Earliest Workout Year & DataStore Caching (`REQ-SET-085.3`)
* **Files**: `WorkoutSummariesDatabaseManager.java`, `SettingsDataStore.kt`, `ProFeatureManager.kt`.
* Add `getEarliestWorkoutYear(): Integer?` querying `SELECT MIN(timeStart)`. Persist in `SettingsDataStore` (`KEY_PIONEER_SINCE_YEAR`) and expose `pioneerSinceYear: StateFlow<Int?>` in `ProFeatureManager`.
* Add unit test coverage in `WorkoutSummariesDatabaseManagerTest.kt` and `ProFeatureManagerTest.kt`.

### Step 2: Remove Hardware Voting & Refactor SupporterPlanCard (`REQ-SET-086.2`, `REQ-SET-086.4`)
* **Files**: `SupporterScreen.kt`, `SupporterComponents.kt`, `SupporterTier.kt`.
* Remove `HardwareVotingSection` composable, enum references, and caller invocation in `SupporterScreen`.
* In `SupporterPlanCard`, position recommendation badge vertically above the tier title. Apply `Modifier.weight(1f, fill = false)` and `maxLines = 1, softWrap = false` to action buttons (`Rule 38`).

### Step 3: Localization Parity & Typo Fix Across 9 Locales (`REQ-SET-086.5`, `TST-LOC-001`)
* **Files**: `app/src/main/res/values*/strings.xml` (all 9 locales), `SupporterComponents.kt`, `SupporterScreen.kt`, `AppNavigationDrawer.kt`.
* Correct `100%%` $\rightarrow$ `100%` in `supporter_screen_subtitle` and replace sensor purchase copy with indie app sustainability copy across all 9 locales.
* Extract all hardcoded English strings into `@StringRes` in all 9 locales (`pioneer_badge_label_with_year`, `pioneer_badge_label`, `pioneer_thank_you_body`, `supporter_active_tier_prefix`, `supporter_active_tier_thank_you`, `supporter_plan_button_active`, `supporter_plan_button_support`, `supporter_period_month`, `supporter_period_year`, `supporter_price_per_period`, `supporter_core_invariant_title`, `supporter_core_invariant_body`, and `drawer_tier_*`).

### Step 4: Verification Suite & Regression Defense (`TST-SET-085`, `TST-SET-086`, `TST-LOC-001`)
* Run `TranslationParityTest`, `ProFeatureManagerTest`, `WorkoutSummariesDatabaseManagerTest`, and `ThemeColorCentralizationAuditTest`. Verify 100% pass rate.

---

## 4. Verification & Rollback Plan
* **Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.monetization.ProFeatureManagerTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManagerTest"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback Safety**: Working tree isolated to `feature/ATT-5371`; clean revert without DB migration schema bump.
