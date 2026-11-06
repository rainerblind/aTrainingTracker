# Sprint Review & Retrospective: Sprint 2026-45.8 (`ATT-5566`)

**Ticket**: [ATT-5566](https://rainerblind.atlassian.net/browse/ATT-5566) (`Review & Retro`)  
**Sprint**: `Sprint 2026-45.8` (Jira Sprint ID `400`, Branch `sprint/2026-45.8`)  
**Target Release**: `V5.0.0`  
**Date**: 2026-11-06  

---

## 1. Sprint Velocity & Delivery Summary (`REQ-PRO-029`)

| Metric | Planned (Ceremony 1) | Completed & Accepted (Ceremony 2) | Deferred (Documented Deviation, Rule 1) | Completion Rate |
| :--- | :---: | :---: | :---: | :---: |
| **Work Tickets** | 19 | **17** | 2 (`ATT-5504`, `ATT-5492`) | **89.5%** |
| **Story Points (SP)** | 27 SP | **25 SP** | 2 SP (2 × 1 SP) | **92.6%** |
| **Accepted (`i.O.`)** | — | **17 / 17** (`100%` of completed) | — | **100%** |
| **Follow-Up Refinements Logged** | — | 7 (`ATT-5654`..`ATT-5660`) | — | — |

> **Documented Deviation (`Rule 1 — Pflicht zum Widerspruch & Dokumentierte Abweichungserlaubnis`)**: While Ticket 17 (`ATT-5559`) was completing Stage 5, the user requested ending Phase 2 immediately after `ATT-5559` to start the Sprint Review. After constructive resistance pointing out the two remaining 1-SP tickets (`ATT-5504`, `ATT-5492`), the user confirmed concluding Phase 2 at 17 tickets (25 SP). Both deferred tickets remain unstarted for the next sprint.

---

## 2. Ticket-by-Ticket Review Outcomes (Backlog Rank Order)

| # | Key | SP | Epic | Summary | Verdict | Follow-Up Ticket(s) |
| :-: | :--- | :-: | :--- | :--- | :---: | :--- |
| 1 | [ATT-5561](https://rainerblind.atlassian.net/browse/ATT-5561) | 1 | `ATT-235` | Restore `<uses-library android:name="org.apache.http.legacy" />` in `AndroidManifest.xml` | **Accepted (`i.O.`)** | — |
| 2 | [ATT-5565](https://rainerblind.atlassian.net/browse/ATT-5565) | 1 | `ATT-235` | Fix race condition between `deleteFinishedEvent` and `_deletionProgress` reset in `WorkoutRepository` | **Accepted (`i.O.`)** | — |
| 3 | [ATT-5496](https://rainerblind.atlassian.net/browse/ATT-5496) | 2 | `ATT-355` | Replace Bronze/Silver/Gold Emblems (`B5`, `S4-A`, `G6`), Variant C layout & `PRO` / `PRO+ SUPPORTER` | **Accepted (`i.O.`)** | — |
| 4 | [ATT-5559](https://rainerblind.atlassian.net/browse/ATT-5559) | 1 | `ATT-176` | User-configurable cluster assignment score threshold (default `1.2`) in `ClusterTuningContent` | **Accepted (`i.O.`)** | — |
| 5 | [ATT-5563](https://rainerblind.atlassian.net/browse/ATT-5563) | 2 | `ATT-826` | `GradeZoneDistributionCard`: Expert Settings visibility switch, rename & `%`-category labels | **Accepted (`i.O.`)** | [ATT-5654](https://rainerblind.atlassian.net/browse/ATT-5654) |
| 6 | [ATT-5474](https://rainerblind.atlassian.net/browse/ATT-5474) | 2 | `ATT-355` | Workout Photos in Expert Settings (`WorkoutMasksAndCardsSection`): Show Photos, Add-Button & Size | **Accepted (`i.O.`)** | [ATT-5655](https://rainerblind.atlassian.net/browse/ATT-5655) |
| 7 | [ATT-5473](https://rainerblind.atlassian.net/browse/ATT-5473) | 2 | `ATT-355` | `WorkoutPhotoStrip` & Preview UI: Neutral styling, unclipped tile text & stacked preview buttons | **Accepted (`i.O.`)** | — |
| 8 | [ATT-5472](https://rainerblind.atlassian.net/browse/ATT-5472) | 2 | `ATT-355` | `PhotoTrackOverlayComposer` & Share Footer: Symbol-only badge, portrait proportions & stacked buttons | **Accepted (`i.O.`)** | [ATT-5656](https://rainerblind.atlassian.net/browse/ATT-5656) |
| 9 | [ATT-5471](https://rainerblind.atlassian.net/browse/ATT-5471) | 2 | — | `StravaCsvParser`: Language-independent column detection instead of localized header names | **Accepted (`i.O.`)** | — |
| 10 | [ATT-5469](https://rainerblind.atlassian.net/browse/ATT-5469) | 2 | — | Subscription Downgrade Sensor Quota: Trigger reconciliation globally on App Start and Tracking Screen | **Accepted (`i.O.`)** | — |
| 11 | [ATT-5470](https://rainerblind.atlassian.net/browse/ATT-5470) | 1 | `ATT-355` | `SwapOrUpgradeDialog`: Fix 3-button row wrapping via stacked full-width buttons | **Accepted (`i.O.`)** | [ATT-5657](https://rainerblind.atlassian.net/browse/ATT-5657) |
| 12 | [ATT-5468](https://rainerblind.atlassian.net/browse/ATT-5468) | 1 | `ATT-355` | `GarageUpgradeNudgeDialog`: Full-width stacked button layout | **Accepted (`i.O.`)** | — |
| 13 | [ATT-5467](https://rainerblind.atlassian.net/browse/ATT-5467) | 1 | — | `FirstJubileeCelebrationDialog`: Transitional prompt text before Google Play and Email buttons | **Accepted (`i.O.`)** | [ATT-5658](https://rainerblind.atlassian.net/browse/ATT-5658), [ATT-5659](https://rainerblind.atlassian.net/browse/ATT-5659) |
| 14 | [ATT-5491](https://rainerblind.atlassian.net/browse/ATT-5491) | 1 | — | Location sensors: Prioritize `Google Fused` over GPS in `MySensorManager` & default pair on fresh install | **Accepted (`i.O.`)** | — |
| 15 | [ATT-5490](https://rainerblind.atlassian.net/browse/ATT-5490) | 1 | — | `KeepTwoSensorsBottomSheet`: Preselect `Google Fused` and most recently active remote sensor | **Accepted (`i.O.`)** | [ATT-5660](https://rainerblind.atlassian.net/browse/ATT-5660) |
| 16 | [ATT-5564](https://rainerblind.atlassian.net/browse/ATT-5564) | 1 | `ATT-176` | `WorkoutClusterHeatmapScreen`: Visible drag-handle bar in workout peek bottom sheet | **Accepted (`i.O.`)** | — |
| 17 | [ATT-5560](https://rainerblind.atlassian.net/browse/ATT-5560) | 2 | `ATT-176` | `ClusterNamingDialog`: Convert to `AppModalBottomSheet` and display/edit imported Workout Name | **Accepted (`i.O.`)** | — |

---

## 3. Root Cause Analysis of Review Findings & Tier 2 Gate Anomalies

1. **First-Class Section Integration in Expert Settings & 1:1 Detail Tab Rendering (`ATT-5563` $\rightarrow$ `ATT-5654`, `ATT-5474` $\rightarrow$ `ATT-5655`)**:
   * *Observation*: In `ATT-5563`, the visibility toggle for *Steigungsverteilung* was implemented as a standalone `Switch` tucked beneath the *Zonenkarten-Anzeigemodus* selector instead of being a sortable checkbox row inside the *Summary-Karten* and *Detail-Tabs* lists. In `ATT-5474`, `WorkoutDetailTabbedContent.kt` contained a legacy fallback block (`if (!anyTabHasPhotos && selectedWorkout.photos.isNotEmpty())`) inside `WorkoutDetailSectionType.DETAILS` that continued rendering photos even when disabled in Expert Settings, and `WorkoutPhotoThumbnailSize` used fixed `dp` tiles (`76/104/136.dp`) instead of full/half row widths for `LARGE` (*Groß*) and `MEDIUM` (*Mittel*).
   * *Countermeasure (`Rule 75`, `docs/design_guidelines.md` Sec 5.17)*: Every new summary or detail section must be wired as a sortable `WorkoutSectionType` / `WorkoutDetailSectionType` checkbox row in `WorkoutMasksAndCardsSection`, and `WorkoutDetailTabbedContent` must render sections strictly 1:1 from `WorkoutDetailTabConfig` without fallback blocks in `DETAILS`.

2. **Essential Sensor Protection (`Google Fused`) & Stable Sensor List Ordering (`ATT-5470` $\rightarrow$ `ATT-5657`, `ATT-5490` $\rightarrow$ `ATT-5660`)**:
   * *Observation*: While testing `SwapOrUpgradeDialog`, `SensorQuotaReconciliationManager.determineRecommendedUnpairDeviceId` recommended `Google Fused` for unpairing when it happened to have the oldest `lastActive` timestamp. In `DeviceListScreen`, toggling sensor pairing checkboxes caused some rows to jump vertically because `DeviceListViewModel.getFilteredDevices` sorted by `isConnected` inside the secondary comparator.
   * *Countermeasure (`Rule 75`, `docs/design_guidelines.md` Sec 5.18)*: Never recommend `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` (or the sole paired location sensor) for unpairing, and never re-sort sensor lists on `isConnected` state changes.

3. **3-Tier Photo Pre-Selection in Share Sheet & Grounded Milestone/Supporter Copy (`ATT-5472` $\rightarrow$ `ATT-5656`, `ATT-5467` $\rightarrow$ `ATT-5658`, `ATT-5659`)**:
   * *Observation*: When sharing a workout via *"Foto mit Track & Daten"*, showing all device photos immediately requires hunting for the workout's photos (`ATT-5656` specifies a 3-stage filter: workout photos $\rightarrow$ photos taken during the workout time window $\rightarrow$ all photos). In `FirstJubileeCelebrationDialog` and `SupporterScreen`, copy should stay modest and grounded (simple support for app development without *"15+ years"*, *"indie"*, or *"privacy"* slogans, and ordering feedback as *Email $\rightarrow$ Play Store $\rightarrow$ Financial Support at bottom*, including Pioneers).

4. **Tier 2 Unit Test Failures from Unchecked `TrainingApplication.cSharedPreferences` & 10-Arg `suggestCluster` Mocks (`ATT-5559`, `ATT-5560`)**:
   * *Observation*: Because Phase 2 ended immediately after `ATT-5559` per user request, the Tier 2 full unit test suite ran at the start of Ceremony 2 and caught 20 NPEs in `TrainingApplication.getClusterScoreThreshold()` (`cSharedPreferences == null` in JVM ViewModel tests) and 3 MockK stub mismatches in `LegacyImportEngineWorkoutNameTest` and `TcxImportPeriodAndClusterIntegrationTest` where `suggestCluster` gained a 10th default parameter (`scoreThreshold`).
   * *Countermeasure (`Rule 75`, `docs/coding_standards.md` Sec 5.5)*: Guard all `TrainingApplication` static `SharedPreferences` getters/setters with `cSharedPreferences != null` and grep all `app/src/test/` call sites whenever adding a default parameter to a mocked method. Fixed in commit `23aa1975`.

5. **Governance, Skill & Template Alignment (`ATT-5566` Audit Comment)**:
   * *Countermeasure*: Synchronized `.cursorrules` with `.agents/rules/aspice_governance.md` (Strategy A branching, one squash commit per ticket, `submit-stage`/`gate-verdict`, Two-Tier testing), updated `.agents/skills/{jira-workflow,stage1-analysis,stage5-verification,sprint-planner}/SKILL.md` and `templates/{walkthrough_template,test_spec_template}.md`, added `regression-bisect` and `release-manager` to `tools/verify_skills.py` (all 11 skills verified), and removed 5 stray screenshots from `.agents/skills/sprint-planner/`.
