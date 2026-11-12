# Stage 1 Analysis: ATT-7022 - SensorStatus & SportingJourneySheet: Wire Supporter Emblem tap to 'My Sporting Journey' Trophy Vault

**Ticket**: [ATT-7022](https://rainerblind.atlassian.net/browse/ATT-7022)  
**Sub-task**: [ATT-7191](https://rainerblind.atlassian.net/browse/ATT-7191) (`[Analysis]`)  
**Parent Epic**: [ATT-67](https://rainerblind.atlassian.net/browse/ATT-67) (*Zeiträume / Periods & Stats*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3`  
**Branch**: `feature/ATT-7022` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
- `SensorStatus.kt` renders the Supporter/Pioneer emblem on the far right of the cockpit status bar and exposes `onSupporterClick: (() -> Unit)? = null`, but `TrackingTabsScreen.kt` leaves it unwired (tapping the badge does nothing).
- While `PeriodsScreen.kt` provides analytical period summaries, `aTrainingTracker` lacks an emotional, story-driven retrospective celebrating the athlete's accumulated training history across 14+ years (2012–2026).
- Athletes need "The Trophy Vault — My Sporting Journey": an interactive retrospective sheet accessible via the cockpit emblem (and `SupporterScreen.kt`), revealing lifetime/annual wrapped totals (Earth Equator %, Everest multiples), offline Hidden Gems (cranks, heartbeats, kWh power plant), milestones, and auto-unlocked trophies with a shareable Athlete Passport card.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  - **SensorStatus Callback Verified**: `SensorStatus.kt:64,143,215` defines `onSupporterClick: (() -> Unit)? = null` and disables the button if null. `TrackingTabsScreen.kt:744,787` invokes `SensorStatus` in compact landscape and portrait modes without providing `onSupporterClick`. Passing `{ showSportingJourney = true }` directly activates the emblem.
  - **Database Query Feasibility Verified**: `WorkoutSummaries` (`finished = 1`) contains `_id`, `distanceTotal_m`, `timeActive_s`, `ascending`, `clusterId`, `Sport`/`sportId`, and `timeStart`. `ExtremumValues` contains `(workoutID, sensorType, extremumType, value)` with existing composite indexes `idx_extrema_sensor_extrema` and `idx_extrema_workout_sensor_extrema`. Aggregations (cranks, heartbeats, mechanical work) run in $\approx 20\text{--}40\,\text{ms}$ on `Dispatchers.IO` with zero schema migrations.
  - **Milestone Navigation Verified**: `WorkoutNavigationEvents.navigateToWorkout(workoutId)` safely triggers `MainActivityWithNavigation` drawer navigation and `WorkoutSummariesTabbedScreen` selection without new intents or fragments.
  - **Prototype Parity Verified**: `docs/attachments/trophy_vault_prototype.html` validates the 4 pillars (Hero Totals, Hidden Gems, Milestones, Achievement Room) and Athlete Passport card design.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Wire `onSupporterClick` in `TrackingTabsScreen.kt` (landscape and portrait) to open `SportingJourneySheet`.
  2. Add discoverability entry point in `SupporterScreen.kt` (under active tier banner) and `FirstJubileeCelebrationDialog.kt`.
  3. Implement `SportingJourneyRepository` (IO-dispatched SQLite queries for All-Time vs Year, All Sports vs Bike vs Run).
  4. Implement `SportingJourneySheet` Composable using `AppModalBottomSheet` (4 pillars: Hero Totals, Hidden Gems, Milestones, Achievement Room).
  5. Implement shareable "Athlete Passport" text/card sharing via `Intent.ACTION_SEND`.
  6. Complete 9-language localization parity (`values`, `de`, `es`, `fr`, `it`, `nl`, `pl`, `pt`, `sv`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Zero database schema migrations (`DB_VERSION = 30` strictly unchanged).
  - No changes to real-time sensor recording algorithms or foreground tracking services.
  - No external social network SDKs or cloud sync (native Android share sheet only).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PER-039` (*The Trophy Vault & Sporting Journey Retrospective Sheet*), companion to `REQ-SET-086` (*Supporter Tiers & Emblems*) and `REQ-PER-007` (*Period-Workout Analytics*).
* **Historical Origin & Commit Trace**: `REQ-SET-086` introduced in `ATT-4773` (Sprint 2026-42.6) placed the emblem in `SensorStatus` as a visual tier indicator without an attached click handler.
* **Root Reason for Existing Formulation**: Cockpit sensor bar prioritized hardware telemetry indicators; emblem click handling was deferred until a dedicated retrospective surface was defined.
* **Preservation of Core Invariants**: Free tier emblem remains hidden in cockpit; paid/pioneer tiers gain tap access. All SQLite queries are strictly read-only on `Dispatchers.IO` and do not alter period caches or tracking pipelines.

---

## 5. Architectural Strategy & High-Level Solution
- **Data Layer**:
  - `SportingJourneyRepository.kt`: Executes optimized SQLite queries via `WorkoutSummariesDatabaseManager.getInstance(context).database` on `Dispatchers.IO`. Computes hero totals, biological/mechanical equivalents, circadian DNA, milestones, and badge statuses.
- **Domain Layer**:
  - `SportingJourneyModels.kt`: Data models for `SportingJourneySnapshot`, `HeroTotals`, `HiddenGems`, `MilestoneItem`, and `AchievementBadge`.
- **UI & Presentation Layer**:
  - `SportingJourneySheet.kt`: Material 3 modal bottom sheet (`AppModalBottomSheet`) featuring period selector (All-Time, 2026, Peak Year), sport filter chips (All, Bike, Run), and the 4 insight pillars.
  - `TrackingTabsScreen.kt`: Provides `onSupporterClick` callback to `SensorStatus` invocations to toggle `showSportingJourney`.
  - `SupporterScreen.kt`: Adds a "My Sporting Journey" action card to open the vault.
  - Native Share Action: Generates shareable summary text/passport card via `Intent.ACTION_SEND`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero Database Migrations: `DB_VERSION = 30` remains strictly unchanged; zero schema alterations.
  2. Thread Safety & UI Fluidity: All SQLite calculations run strictly on `Dispatchers.IO`; UI observes reactive `StateFlow`.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  - Justification: Purely additive UI and read-only database query feature. Zero changes to sensor pipeline, background tracking service, or database schema.
