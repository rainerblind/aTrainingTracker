# Stage 1 Analysis: ATT-5371 - SupporterScreen UX, Dynamic Pioneer Year & Localization

**Ticket**: [ATT-5371](https://rainerblind.atlassian.net/browse/ATT-5371)  
**Sub-task**: [ATT-5382](https://rainerblind.atlassian.net/browse/ATT-5382) (`[Analysis]`)  
**Parent Epic**: [ATT-51](https://rainerblind.atlassian.net/browse/ATT-51) (*Monetize*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.5` | **Branch**: `feature/ATT-5371`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
Sprint 2026-45.4 on-device review of `SupporterScreen` and Navigation Drawer on Pixel 10 revealed 4 UX, layout, and localization defects:
1. Pioneer badges hardcode 2011 (`"SINCE 2011"`), misrepresenting athletes who joined in later pre-cutoff years.
2. Hardware voting section and copy promise user-funded hardware test devices, creating scope liability.
3. Recommended card wraps action button into `Suppor\nt` (Rule 38 single-character mid-word wrapping).
4. Subtitle contains literal `100%%` typo; multiple user-facing strings are hardcoded in English.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Item 1 (Pioneer Year RCA)**: `AppNavigationDrawer.kt:380` and `pioneer_badge_label` hardcode 2011. `WorkoutSummariesDatabaseManager` contains `timeStart` (`DATETIME`) but lacks `getEarliestWorkoutYear()`. `SettingsDataStore` lacks a cached year preference, forcing static strings.
* **Item 2 (Hardware Voting RCA)**: `SupporterScreen.kt:152` embeds `HardwareVotingSection`, claiming subscriptions fund specific sensor hardware. Removing this section and updating copy grounds the value proposition in continuous indie maintenance.
* **Item 3 (Button Wrapping RCA)**: `SupporterPlanCard` places the tier title, `Empfehlung` pill badge, and `Support` button on the same horizontal row. German string lengths starve the button width, forcing Compose mid-word wrap. Moving the badge above the title and setting `maxLines = 1, softWrap = false` resolves this.
* **Item 4 (Double Percent & Localization RCA)**: `supporter_screen_subtitle` in 5 locales contains `100%%` in a non-formatted resource. `SupporterComponents.kt`, `SupporterScreen.kt`, and `AppNavigationDrawer.kt` contain 8+ hardcoded English string literals without `@StringRes` references.
* **Independent Peer Review & Verification**:
  * Verified `WorkoutSummaries.TIME_START` format in SQLite: `MIN(timeStart)` reliably extracts earliest workout date string (`YYYY-...`).
  * Audited 9-locale strings: all 9 locales currently lack dynamic year format specifiers and localized drawer tier badges.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Add `getEarliestWorkoutYear(): Integer?` to `WorkoutSummariesDatabaseManager.java`.
  * Cache `pioneerSinceYear: Flow<Int?>` in `SettingsDataStore.kt` via `ProFeatureManager.kt`.
  * Format dynamic Pioneer badges (`"★ Pioneer (Seit %1$d)"` / fallback `"★ Pioneer"`) in Drawer & Banner.
  * Remove `HardwareVotingSection` and update introductory/footer copy.
  * Move `Empfehlung` badge above title in `SupporterPlanCard`; enforce `maxLines = 1, softWrap = false`.
  * Correct `100%%` $\rightarrow$ `100%` and achieve 100% 9-locale parity for all supporter strings.
* **Out-of-Scope Non-Goals**:
  * Modifying Google Play Billing subscription SKUs or pricing rules (`REQ-SET-086.1`).
  * Altering the 2-device free pairing limit or quota intercept logic (`REQ-SET-083`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirements Modified**: `REQ-SET-085` (Pioneer Recognition) and `REQ-SET-086` (Supporter Subscriptions).
* **Historical Origin**: Commit `c899cda1` (`ATT-51`, Sprint 2026-45.4).
* **Root Reason for Existing Formulation**: 2011 was hardcoded as a placeholder founding year; hardware voting was added as an exploratory intent signal.
* **Preservation of Core Invariants**: Pioneer lifetime Pro status, authentic Cervélo branding, and 100% free core workout recording remain strictly preserved and unaffected.

---

## 5. Architectural Strategy & High-Level Solution
* **Database & DataStore**: Add `getEarliestWorkoutYear()` in `WorkoutSummariesDatabaseManager.java`. `ProFeatureManager` extracts and persists `pioneerSinceYear` in `SettingsDataStore.kt`.
* **UI Refactoring**: Update `AppNavigationDrawer.DrawerHeader` and `PioneerStatusBanner` to consume `pioneerSinceYear`. Remove `HardwareVotingSection` from `SupporterScreen.kt`. Restructure `SupporterPlanCard` to place `Empfehlung` badge vertically above the title row.
* **Localization**: Add parameterized strings and localize all hardcoded text across all 9 `values*/strings.xml` files.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Lifetime Pioneer status remains irrevocable and permanently free.
  2. Core tracking, local file exports, and sensors remain 100% free and unmonetized forever.
  3. Zero text clipping or mid-word line wrapping on action buttons across all screen densities.
* **Risk Rating**: **LOW** (UI layout, string localization, and read-only SQLite aggregation query; decoupled from sensor telemetry and billing transactions).
