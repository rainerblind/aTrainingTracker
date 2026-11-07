# Living Test Specification: Jubilee Milestones & App Reviews

**Domain**: `aftermath_reviews`  
**Prefixes**: `REV`  
**Scope**: Milestone workout auto-naming, jubilee achievements, Google Play in-app reviews, athlete feedback, and supporter tier promotions.  
**Total Tests**: 6

---

### TST-REV-001: **Sport-Specific Milestone Workout Counter Unit Test** [Verified]
- **Requirement(s)**: `REQ-REV-001`
- **Component**: `WorkoutSummariesDatabaseManager.java`, `MilestoneAutoNamingManager.kt`
- **Jira**: `ATT-2308`
- **Test Description**: 1. Insert 99 finished Bike workouts and 99 finished Run workouts. 2. Verify `getFinishedWorkoutCountForSport` returns 99 for each sport. 3. Insert 1 finished Bike workout (hitting 100). 4. Verify Bike counter reaches 100 while Run remains 99. 5. Verify milestone threshold evaluator returns true for 100, 250, 500, 1000, 1500 and false for non-milestone counts.

### TST-REV-002: **Dynamic Milestone Auto-Naming Title Formatting & Export Propagation Contract Test** [Verified]
- **Requirement(s)**: `REQ-REV-002`
- **Component**: `MilestoneAutoNamingManager.kt`, `WorkoutAutoNamingHelper.kt`
- **Jira**: `ATT-2308`
- **Test Description**: 1. Format title for 100th Bike workout with base cluster name "Hausrunde". 2. Verify formatted title matches localized string pattern "[Count]. [SportName]: [ClusterName]" (e.g. "100. Radfahrt: Hausrunde"). 3. Verify title is persisted in `WorkoutSummaries.WORKOUT_NAME` prior to export.

### TST-REV-003: **First-Jubilee Celebration Dialog Tier Gating, Reordering & Intent Dispatch Contract Test** [Verified]
- **Requirement(s)**: `REQ-REV-003`
- **Component**: `FirstJubileeCelebrationDialog.kt`
- **Jira**: `ATT-2308`, `ATT-5401`, `ATT-5399`, `ATT-5467`, `ATT-5659`, `ATT-5658`
- **Test Description**: 1. Launch dialog in Free and Pioneer tier modes. 2. Verify top-to-bottom ordering: milestone message, email feedback prompt and button, Play Store review prompt and button, and financial development support card at the bottom. 3. Launch dialog with active paid Pro tier (Bronze, Silver, Gold, Diamond). 4. Verify financial support card is cleanly hidden, while email and Play Store sections remain visible. 5. Trigger review action and verify visible outcome: verify `Context.findActivity()` resolution, and ensure that in debug mode, or if `launchReviewFlow` terminates immediately (< 300ms) or without Activity, `openPlayStorePage(context)` fallback is immediately launched.

### TST-REV-004: **Single-Prompt Invariant & Zero-Spam Re-prompt Suppression Test** [Verified]
- **Requirement(s)**: `REQ-REV-004`
- **Component**: `SettingsDataStore.kt`, `MilestoneAutoNamingManager.kt`
- **Jira**: `ATT-2308`
- **Test Description**: 1. Verify initial `has_shown_first_jubilee_prompt` is false. 2. Trigger first jubilee celebration dialog and verify flag is persisted as true. 3. Trigger subsequent milestone (e.g. 250th Bike or 100th Run). 4. Verify title is formatted with milestone prefix but celebration dialog is never triggered again.

### TST-REV-005: **9-Locale Localization Parity & String Resource Verification** [Verified]
- **Requirement(s)**: `REQ-REV-002`, `REQ-REV-003`
- **Component**: `res/values/strings.xml`, `TranslationParityTest.kt`
- **Jira**: `ATT-2308`, `ATT-5467`, `ATT-5659`, `ATT-5658`
- **Test Description**: Run `TranslationParityTest` (`TST-LOC-001`) asserting that all milestone title templates, dialog headers, review button labels, email body strings, transitional feedback prompts (`jubilee_email_feedback_prompt`, `jubilee_review_prompt`), and supporter appeal texts exist with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, and PT with valid literal linebreaks, matching format specifiers, and zero decorative inline Unicode emojis.

### TST-REV-006: **SupporterDebugSection Jubilee Preview Trigger Contract Test** [Verified]
- **Requirement(s)**: `REQ-REV-005`
- **Component**: `SupporterDebugSection.kt`
- **Jira**: `ATT-2308`
- **Test Description**: 1. Verify preview button is present when `BuildConfig.DEBUG == true`. 2. Tap preview button and verify mock celebration dialog opens. 3. Confirm `has_shown_first_jubilee_prompt` remains unmutated in DataStore.
