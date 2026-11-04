# Stage 1 Analysis: ATT-2308 - Milestone Workout Auto-Naming and First Jubilee Celebration Review Prompt

**Ticket**: [ATT-2308](https://rainerblind.atlassian.net/browse/ATT-2308)  
**Sub-task**: [ATT-5335](https://rainerblind.atlassian.net/browse/ATT-5335) (`[Analysis]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Branch**: `feature/ATT-2308`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
* Reaching round milestones (100, 250, 500, 1000+ sessions) represents a major athletic achievement, yet aTrainingTracker currently assigns standard auto-names without recognizing milestone session counts.
* Post-workout completion of a major milestone is the optimal psychological moment (Peak-End Rule) to congratulate the athlete, solicit 5-star Google Play Store reviews, invite direct developer feedback, and introduce the Pro supporter tier.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Current State**: `WorkoutAutoNamingHelper` names workouts from favorite locations and `WorkoutClusterEngine` numbers recurring cluster routes (`#2`, `#3`), but lifetime sport totals are untracked in titles. `WorkoutSummariesDatabaseManager` provides aggregate `getFinishedWorkoutCount()` across all sports, but lacks sport-segregated queries (`sportId` / `BSportType`).
* **Independent Peer Review & Verification (ATT-3534)**:
  * In `TrackerService.java:1893-1956`, `FINISHED = 1` and `SPORT_ID` are committed prior to `finalizeLiveSession()`, enabling accurate synchronous sport-specific milestone detection on the finished session.
  * Milestone recurrence verified: `count == 100 || count == 250 || (count >= 500 && count % 500 == 0)`.
  * Pre-export persistence into `WorkoutSummaries.WORKOUT_NAME` ensures zero-touch propagation to Strava, TCX/FIT exports, and period summaries without altering export pipelines.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Sport-specific finished session count query in `WorkoutSummariesDatabaseManager`.
  * Milestone auto-naming prefix format: `[Count]. [SportName]: [Cluster/Route/Time Name]`.
  * Material 3 First-Jubilee Celebration Dialog with Google Play In-App Review (Play Store URI fallback), direct email feedback to Rainer, and free-tier supporter Pro appeal (`NavRoutes.SUPPORTER`).
  * Single-prompt invariant in `SettingsDataStore` (`has_shown_first_jubilee_prompt`).
  * `BuildConfig.DEBUG` preview trigger button in `SupporterDebugSection` for 1-tap Sprint Review testing.
  * 9-language localization parity (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No alterations to cluster geometric similarity or cluster assignment logic.
  * No re-prompting on subsequent milestones (250, 500, etc.) — strictly zero review spam.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement (`REQ-SET-088`), extending `REQ-SET-066` (*Sequential Auto-Naming Counter Ergonomics*) and `REQ-SET-087` (*Pro Feature Manager Architecture*). No existing requirements modified.

---

## 5. Architectural Strategy & High-Level Solution
* **Database & Naming**: Add `getFinishedWorkoutCountForSport(long sportId)` to `WorkoutSummariesDatabaseManager`. Implement `MilestoneAutoNamingManager` to detect milestones and format titles (`milestone_workout_name_format`).
* **Celebration Dialog**: Hoist `FirstJubileeCelebrationDialog` in `ATrainingTrackerApp` via `MainActivityWithNavigation.mTrackingStoppedReceiver`. When athlete hits first milestone ever and `!settingsDataStore.hasShownFirstJubileePrompt()`, show dialog and immediately persist `has_shown_first_jubilee_prompt = true`. Free tier (`!ProFeatureManager.isProActive`) includes supporter appeal navigating to `NavRoutes.SUPPORTER`.
* **Debug Support**: Add 1-tap "Preview Jubilee Celebration" button to `SupporterDebugSection` (`BuildConfig.DEBUG`).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. *AC-4 Single-Prompt Invariant*: `has_shown_first_jubilee_prompt` persisted immediately on first milestone; subsequent milestones update title without dialog.
  2. *Export Invariant*: Milestone titles propagate transparently to TCX, FIT, and Strava.
  3. *Thread Safety*: SQLite counting executed on background IO dispatcher.
* **Risk Rating**: **LOW** (Modular additions; decoupled from core sensor pipeline; guarded by feature flag/datastore).
