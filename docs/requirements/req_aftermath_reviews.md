# Living Requirements: Jubilee Milestones & App Reviews

**Domain**: `aftermath_reviews`  
**Prefixes**: `REV`  
**Scope**: Milestone workout auto-naming, jubilee achievements, Google Play in-app reviews, athlete feedback, and supporter tier promotions.  
**Total Requirements**: 5

---

### REQ-REV-001: Sport-Specific Finished Workout Milestone Counting & In-Memory Detection [Verified]
- **Tests**: `TST-REV-001` | **Tickets**: `ATT-2308`
- **Implementation**: `WorkoutSummariesDatabaseManager.java`, `MilestoneAutoNamingManager.kt`
- **Rationale**: Reaching round training milestones (100, 250, 500, 1000, 1500, 2000...) represents a major personal athletic achievement. The system SHALL maintain independent, accurate session counters segregated per primary sport type in SQLite rather than conflating all activities into an aggregate sum.
- **Description**:
  1. `WorkoutSummariesDatabaseManager` SHALL provide `getFinishedWorkoutCountForSport(long sportId): int` querying `SELECT COUNT(*) FROM WorkoutSummaries WHERE finished = 1 AND sportId = ?`.
  2. The system SHALL evaluate milestone workout counts according to the exact progression rule: `count == 100 || count == 250 || (count >= 500 && count % 500 == 0)`.
  3. Milestone evaluation SHALL execute on a background IO dispatcher (`Dispatchers.IO`) immediately following session finalization.

### REQ-REV-002: Dynamic Milestone Workout Auto-Naming & Export Title Propagation [Verified]
- **Tests**: `TST-REV-002` | **Tickets**: `ATT-2308`
- **Implementation**: `MilestoneAutoNamingManager.kt`, `WorkoutAutoNamingHelper.kt`, `TrackerService.java`
- **Rationale**: Milestone sessions should be visibly celebrated across the athlete's entire ecosystem (session history, cloud sync, Strava, and TCX/FIT exports).
- **Description**:
  1. When a finished workout matches a milestone count for its sport type, the system SHALL format the workout title using the localized pattern: `[Count]. [SportName]: [GeneratedName]` (e.g. `100. Radfahrt: Hausrunde` / `100th Ride: Home Loop`).
  2. The milestone-augmented title SHALL be persisted into `WorkoutSummaries.WORKOUT_NAME` prior to external cloud sync or TCX/FIT export, ensuring zero-touch propagation without modifying export codecs.
  3. Non-milestone sessions SHALL retain standard naming behavior without counter prefixing.

### REQ-REV-003: First-Jubilee Celebration Dialog & Multi-Channel Engagement [Verified]
- **Tests**: `TST-REV-003` | **Tickets**: `ATT-2308`, `ATT-5401`, `ATT-5399`
- **Implementation**: `FirstJubileeCelebrationDialog.kt`, `MainActivityWithNavigation.kt`
- **Rationale**: Reaching the very first milestone across any sport is an athlete's peak emotional moment. The app SHALL present a celebratory Material 3 dialog offering frictionless Google Play review, direct email feedback to Rainer, and an authentic supporter appeal.
- **Description**:
  1. Upon saving the session that achieves the athlete's first milestone ever across any sport, if `!settingsDataStore.hasShownFirstJubileePrompt`, the app SHALL display `FirstJubileeCelebrationDialog`.
  2. The dialog SHALL provide an **In-App Review Action** requesting rating via Google Play's `ReviewManager`. To prevent silent no-ops, clicking 'Bei Google Play bewerten' SHALL guarantee a visible outcome: in `BuildConfig.DEBUG` or when `launchReviewFlow` completes in `< 300ms` without displaying UI (or if `Context` cannot be unwrapped to an `Activity`), the system SHALL immediately fallback to opening the Play Store listing via `market://details?id=com.atrainingtracker` (with HTTPS web fallback).
  3. The dialog SHALL provide a **Direct Feedback Action** launching an email draft to `rainer.blind@gmail.com` with pre-filled subject and diagnostic app version info.
  4. If the athlete is on the Free tier (`!ProFeatureManager.isProActive.value`), the dialog SHALL display a charming indie developer note from Rainer inviting the athlete to upgrade to Pro (`NavRoutes.SUPPORTER`). If already Pro/Pioneer, the upgrade prompt SHALL be cleanly omitted.

### REQ-REV-004: Single-Prompt Invariant Persistence & Subsequent Jubilee Non-Intrusiveness [Verified]
- **Tests**: `TST-REV-004` | **Tickets**: `ATT-2308`
- **Implementation**: `SettingsDataStore.kt`, `MilestoneAutoNamingManager.kt`
- **Rationale**: To eliminate review spam and maintain professional user experience, celebration and review prompting MUST occur strictly once in the app lifetime.
- **Description**:
  1. `SettingsDataStore` SHALL maintain `KEY_HAS_SHOWN_FIRST_JUBILEE_PROMPT = booleanPreferencesKey("has_shown_first_jubilee_prompt")`, defaulting to `false`.
  2. When the first celebration dialog is displayed, the system SHALL persist `has_shown_first_jubilee_prompt = true`.
  3. For all subsequent milestone events (e.g., 250, 500, or when a second sport hits 100), the system SHALL apply milestone auto-naming (`REQ-REV-002`), but SHALL NEVER display the celebration or review dialog again.

### REQ-REV-005: Diagnostic Jubilee Dialog Debug Preview Trigger [Verified]
- **Tests**: `TST-REV-006` | **Tickets**: `ATT-2308`
- **Implementation**: `SupporterDebugSection.kt`
- **Rationale**: On-device verification and sprint review testing of the celebration dialog must be accessible without manually completing 100 workouts.
- **Description**:
  1. In `SupporterDebugSection.kt`, gated strictly under `BuildConfig.DEBUG`, the UI SHALL provide a "Preview First-Jubilee Dialog" button.
  2. Tapping this button SHALL trigger display of `FirstJubileeCelebrationDialog` with mock milestone data without mutating `SettingsDataStore.has_shown_first_jubilee_prompt`.
