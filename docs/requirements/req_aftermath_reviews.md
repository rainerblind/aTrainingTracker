# Living Requirements: Jubilee Milestones & App Reviews

**Domain**: `aftermath_reviews`  
**Prefixes**: `REV`  
**Scope**: Milestone workout auto-naming, jubilee achievements, Google Play in-app reviews, athlete feedback, and supporter tier promotions.  
**Total Requirements**: 8

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
- **Tests**: `TST-REV-003`, `TST-REV-005` | **Tickets**: `ATT-2308`, `ATT-5401`, `ATT-5399`, `ATT-5467`, `ATT-5659`, `ATT-5658`
- **Implementation**: `FirstJubileeCelebrationDialog.kt`, `MainActivityWithNavigation.kt`
- **Rationale**: Reaching the very first milestone across any sport is an athlete's peak emotional moment. The app SHALL present a celebratory Material 3 dialog offering frictionless personal email feedback, Google Play review, and an authentic supporter appeal.
- **Description**:
  1. Upon saving the session that achieves the athlete's first milestone ever across any sport, if `!settingsDataStore.hasShownFirstJubileePrompt`, the app SHALL display `FirstJubileeCelebrationDialog`.
  2. The dialog SHALL display a grounded, motivating milestone achievement message (`jubilee_dialog_message`) celebrating the first milestone with down-to-earth copy (*"ein schöner Meilenstein! Da kommen sicherlich noch ein paar dazu"* / *"a great milestone! There will surely be more to come"*).
  3. The dialog layout SHALL arrange engagement options in strict top-to-bottom order:
     a. **Personal Email Feedback Action (Top)**: Preceded by transitional prompt `jubilee_email_feedback_prompt`, providing button `jubilee_action_email_feedback` launching an email draft to `rainer.blind@gmail.com` with pre-filled subject and diagnostic app version info.
     b. **Google Play Store Review Action (Middle)**: Preceded by transitional prompt `jubilee_review_prompt`, providing button `jubilee_action_review` requesting rating via Google Play's `ReviewManager`. To prevent silent no-ops, clicking 'Bei Google Play bewerten' SHALL guarantee a visible outcome: in `BuildConfig.DEBUG` or when `launchReviewFlow` completes in `< 300ms` without displaying UI (or if `Context` cannot be unwrapped to an `Activity`), the system SHALL immediately fallback to opening the Play Store listing via `market://details?id=com.atrainingtracker` (with HTTPS web fallback).
     c. **Financial Support Card (Bottom)**: Displayed conditionally at the very bottom of the content column for both `SupporterTier.FREE` and `SupporterTier.PIONEER` tiers (whenever the athlete has no active paid recurring subscription). The card SHALL display `jubilee_supporter_appeal_message` and navigation button `jubilee_action_upgrade_pro` routing to `NavRoutes.SUPPORTER`. For athletes with active paid subscriptions (`BRONZE`, `SILVER`, `GOLD`, `DIAMOND`), the card SHALL be cleanly omitted.
  4. The dialog SHALL provide a dismiss action (`jubilee_action_dismiss`) in the sheet's `actions` slot marking the milestone as acknowledged.
  5. All user-facing strings SHALL maintain 100% localization parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero decorative inline Unicode emojis (`ATT-5400`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-REV-003` (Clauses 2, 3, 4, 5), targeting `FirstJubileeCelebrationDialog.kt` and `strings.xml`.
2. *Historical Origin & Commit Trace*: `ATT-2308` (initial jubilee celebration dialog), refined in `ATT-5467`, `ATT-5659`, and `ATT-5658` (grounded wording, top-to-bottom reordering: Email -> Play Store -> Financial Support, and financial support visibility for Pioneers).
3. *Root Reason for Existing Formulation*: Historical copy positioned the financial supporter card at the top and tied its visibility to `!isProActive`. Because `SupporterTier.PIONEER.isPro == true`, Pioneers were treated as paid subscribers and excluded from seeing the voluntary development support option. Furthermore, the celebratory text was previously overly grandiose (*"Das ist ein beeindruckender Meilenstein"*).
4. *Preservation of Core Invariants*: Single-prompt invariant (`REQ-REV-004`) preserved; ReviewManager flow and fallback preserved; zero decorative emojis preserved; Rule 73 stacked button layout preserved.

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

### REQ-REV-006: Map Snapshot Share Pager Stability, Duplicate Header Elimination & Caller State Restoration [Verified]
- **Tests**: `TST-REV-007` | **Tickets**: `ATT-6322`
- **Implementation**: `WorkoutDetailTabbedContent.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`, `WorkoutSummariesTabbedScreen.kt`
- **Rationale**: Triggering 'Karten- & Analyse-Snapshot' in `WorkoutShareBottomSheet` must execute seamlessly without freezing the HorizontalPager at 50%, rendering duplicate visible headers under the tab row, or stranding the athlete on the wrong tab or screen after sharing.
- **Description**:
  1. *Pager Scroll Key Invariant & Programmatic Navigation*: `WorkoutDetailTabbedContent` SHALL NOT include `pagerState.currentPage` in the `LaunchedEffect` key list for programmatic snapshot tab switching (`pendingAutoShare`). The system SHALL use `pagerState.scrollToPage(mapTabIndex)` (instant transition without intermediate animation frames) so the pager coroutine is never cancelled mid-animation or frozen at the 50% midpoint (`Rule 80` / `docs/coding_standards.md` §1.11).
  2. *Origin Tab & Caller Screen Restoration*:
     a. In `WorkoutDetailTabbedContent`, initiating an automated snapshot share SHALL capture `originPageIndex = pagerState.currentPage`. Upon `onSnapshotComplete`, the pager SHALL automatically scroll back to `originPageIndex`.
     b. When opened with `initialAutoShareSnapshot = true` from workout list containers (`WorkoutSummariesTabbedScreen`, `WorkoutSummariesListFragment`, `WorkoutClustersScreen`), `onSnapshotComplete` SHALL be propagated via `TrackOnMapScreen` to automatically dismiss/pop the detail screen, returning the athlete to the origin list view.
  3. *MapDetailLayout Header Slot Offscreen Recording & Duplicate Prevention*: `MapDetailLayout` SHALL accept `showHeader: Boolean = true`. When `showHeader == false`, `MapDetailLayout` SHALL measure and record `header()` into `headerLayer` offscreen without rendering a second visible `WorkoutHeader` in the tab's upper layout column. `WorkoutDetailTabbedContent` SHALL pass `showHeader = false` to prevent duplicate visible header rendering below the tab row.
  4. *Preservation of Bitmap Output & Standalone Layout Invariants*: `headerLayer` in `MapDetailLayout` MUST continue producing a full-fidelity software bitmap for `combineWorkoutAndShare`. All standalone invocations of `MapDetailLayout` with `showHeader = true` MUST retain 100% visual layout parity.

### REQ-REV-007: Workout Cluster Reassignment Centroid Integrity & Sport Frequency Synchronization [Verified]
- **Tests**: `TST-REV-008` | **Tickets**: `ATT-6159`
- **Implementation**: `WorkoutClusterEngine.kt`
- **Rationale**: When an athlete moves a workout between route clusters in `moveWorkoutToCluster`, sequential updates must not overwrite calculated cluster centroids with stale snapshots, and sport frequency updates must be calculated after SQLite database reassignments.
- **Description**:
  1. *Centroid Preservation*: In `WorkoutClusterEngine.moveWorkoutToCluster`, updated centroid coordinates (`startLat`, `startLng`, `endLat`, `endLng`, `maxDispLat`, `maxDispLng`, `refDistance`) SHALL be calculated using pre-move hit counts and persisted to `dbManager`. Subsequent mutations SHALL NEVER overwrite updated centroids with a stale pre-calculation snapshot.
  2. *Database Reassignment Sequence*: `assignClusterToWorkout(context, workoutId, newClusterId)` SHALL be invoked before evaluating cluster sport frequency, ensuring `WorkoutSummaries.CLUSTER_ID` is updated in SQLite and cluster hit counts are reconciled.
  3. *Post-Move Sport Frequency Recalculation*: `summariesManager.getMostFrequentSportIdForCluster` SHALL be evaluated after `assignClusterToWorkout` for both source and target clusters. Updated sport properties (`probableSportId`, `bSportType`) SHALL be applied to freshly queried cluster records from `dbManager.getClusterById`.
  4. *Preservation of Core Invariants*: Centroid formulas, `WorkoutSummaries.CLUSTER_ID` authority, and cluster locking remain intact.
- **Acceptance Criteria**:
  • *Given* a workout moved from `currentClusterId` to `newClusterId`,
  • *When* `moveWorkoutToCluster` completes,
  • *Then* both source and target clusters retain their recomputed weighted centroids without being overwritten, and `probableSportId` reflects the post-reassignment workout distribution.

### REQ-REV-008: Generic Zone Distribution Card Architecture & Composable Wrapper Deduplication [Verified]
- **Tests**: `TST-REV-009` | **Tickets**: `ATT-6044`
- **Implementation**: `ZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `HeartRateZoneDistributionCard.kt`, `GradeZoneDistributionCard.kt`
- **Rationale**: The post-workout telemetry analysis screens feature three separate card components (`PowerZoneDistributionCard`, `HeartRateZoneDistributionCard`, `GradeZoneDistributionCard`) that share ~97% identical Compose layout, header styling, chart mode routing (`STACKED_BAR`, `COLUMNS`, `HISTOGRAM`), and segmented button controls (~600 lines total). Unifying these into a single parameterized `ZoneDistributionCard` eliminates ~440 lines of duplicate code while preserving public composable APIs, contract compatibility, and per-card state isolation.
- **Description**:
  1. *Generic Composable Architecture*: The system SHALL provide a unified, reusable `@Composable fun ZoneDistributionCard(...)` in `com.atrainingtracker.trainingtracker.ui.aftermath.zones` accepting:
     a. `distribution: ZoneDistributionData`
     b. `@DrawableRes iconRes: Int`
     c. `title: String`
     d. `unit: String`
     e. `modifier: Modifier = Modifier`
     f. `displayMode: ZoneCardDisplayMode? = null`
     g. `onDisplayModeChange: ((ZoneCardDisplayMode) -> Unit)? = null`
     h. `showModeSelector: Boolean = true`
     i. `defaultDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.COLUMNS`
  2. *Layout & Styling Invariants*:
     a. Card container SHALL maintain `ElevatedCard` with `RoundedCornerShape(12.dp)` and internal padding `12.dp`.
     b. Header Row SHALL display `iconRes` (size `18.dp`, tint `MaterialTheme.colorScheme.primary`), `title` (`titleSmall`, bold), flexible spacer, and formatted duration via `ZoneDistributionChartMath.formatZoneDuration(distribution.totalDurationSec)`.
     c. Chart Body SHALL switch on `effectiveDisplayMode`: `ZoneCardDisplayMode.STACKED_BAR` (`ZoneDistributionStackedBar` with `showLegend = false`), `ZoneCardDisplayMode.COLUMNS` / `ZoneCardDisplayMode.FIVE_ZONES` (`ZoneDistributionColumnChart`), and `ZoneCardDisplayMode.HISTOGRAM` (`TelemetryHistogramChart` with `unit = unit`).
     d. Mode Switcher SHALL render below the chart using `SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(28.dp))` with `buttonCount = if (hasHistogram) 3 else 2`, gated strictly on `showModeSelector && distribution.entries.isNotEmpty()`. Each `SegmentedButton` SHALL use `icon = {}`, `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, and single-line `labelSmall` text with `TextOverflow.Ellipsis`.
  3. *Wrapper Deduplication & Public API Backward Compatibility*:
     a. `PowerZoneDistributionCard.kt`, `HeartRateZoneDistributionCard.kt`, and `GradeZoneDistributionCard.kt` SHALL remain public composable entry points delegating to `ZoneDistributionCard`.
     b. Each wrapper composable SHALL encapsulate its own independent state (delegating `displayMode` and `defaultDisplayMode = ZoneCardDisplayMode.COLUMNS` to `ZoneDistributionCard`) ensuring display modes are not cross-contaminated across multiple cards on the same screen.
     c. Wrapper names and parameter contracts MUST remain 100% backward-compatible with existing callers across `TrackOnMapScreen`, `WorkoutDetailTabbedContent`, and `WorkoutSummary`.
- **Acceptance Criteria**:
  • *Given* workout telemetry with heart rate, cycling power, or slope grade distribution data,
  • *When* rendered in `TrackOnMapScreen`, `WorkoutDetailTabbedContent`, or `WorkoutSummary`,
  • *Then* `HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`, and `GradeZoneDistributionCard` render identical visual layouts delegating to `ZoneDistributionCard`, supporting independent 2-way/3-way display mode toggles and preserving `showModeSelector` gating.
