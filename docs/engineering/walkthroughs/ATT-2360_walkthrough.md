# Stage 5: Walkthrough & Verification - ATT-2360: Unified WYSIWYG tracking tab configuration with spatial overlays and per-tab popup toggles

**Ticket**: [ATT-2360](https://rainerblind.atlassian.net/browse/ATT-2360)  
**Sub-task**: [ATT-2514](https://rainerblind.atlassian.net/browse/ATT-2514) (`[Test]`)  
**Parent Epic**: [ATT-1191](https://rainerblind.atlassian.net/browse/ATT-1191) (*[Epic] Tracking Tabs Enhancement*)  
**Active Sprint**: `Sprint 2026-41.1`  
**Requirement Mapping**: `REQ-UI-275` (*Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles*)  
**Test Mapping**: `TST-UI-235` (*Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles Verification*)  
**Branch**: `feature/ATT-2360`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Executive Summary & Verification Overview

Previously, tracking tab feature toggles (`showMap`, `showElevationProfile`, `showLiveSegments`, `showLapButton`) were presented as an MVP checkbox list inside `TrackingTabConfigHeader.kt`. This header consumed critical vertical space, visually detached configuration from spatial layout context, and crucially lacked per-tab configuration for recently introduced features: Turn-by-Turn Navigation Prompts (`ATT-1450`) and Strava Live Climbs (`ATT-1281`). Athletes in races or specific training modes could not disable navigation popups or climb sheets on specific data-focused cockpit tabs without turning them off globally in app settings.

ATT-2360 remediates this architecture by providing:
1. **SQLite Database Schema v12**: Extended `VIEWS_TABLE` in `TrackingViewsDatabaseManager.java` with `ShowLiveClimbs int DEFAULT 1` and `ShowNavigationHints int DEFAULT 1`. Defensive migrations guarantee that existing tabs upgrade without data loss or feature regression.
2. **Repository & ViewModel Parity**: `TrackingViewsRepository.kt`, `TrackingViewModel.kt`, and `TrackingTabsViewModel.kt` now expose, propagate, and persist the new `showLiveClimbs` and `showNavigationHints` flags.
3. **Streamlined Tab Header**: Removed checkbox controls from `TrackingTabConfigHeader.kt`, restoring screen real estate for direct cockpit interaction while preserving tab renaming, relative tab insertion (`Add Before`/`Add After`), tab deletion, and Done confirmation.
4. **Spatial WYSIWYG Cockpit Editor**: In `ScreenMode.CONFIGURATION`, `SensorGridScreen.kt` renders direct spatial cards:
   - *Top HUD*: Turn-by-Turn Navigation Prompts toggle card (`showNavigationHints`).
   - *Center Section (below sensor grid)*: Embedded Map (`showMap`) and Elevation Profile (`showElevationProfile`) spatial toggle cards.
   - *Bottom Dock*: Live Segments (`showLiveSegments`), Live Climbs (`showLiveClimbs`), and Lap Button (`showLapButton`) toggle cards.
   Each card displays active vs. inactive visual styling (using MD3 tokens and semantic route green border for navigation hints) with an interactive badge indicator.
5. **Runtime Cockpit Gating**: In `ScreenMode.TRACKING`, `TurnPromptBanner` is gated by `state.showNavigationHints && tuningConfig.turnPromptsEnabled`, and `LiveClimbSheet` is gated by `state.showLiveClimbs && tuningConfig.showLiveClimbs`.
6. **100% 9-Language Localization**: Full localization across EN, DE, ES, FR, IT, JA, NL, PL, and PT for all new configuration strings.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-275` (1: SQLite Schema v12) | `TST-UI-235.1` | `TrackingViewsDatabaseManagerTest.kt` | **PASSED** (7/7) | `Verified` |
| `REQ-UI-275` (2: Repository Parity) | `TST-UI-235.2` | `TrackingViewsRepositoryTest.kt` | **PASSED** (7/7) | `Verified` |
| `REQ-UI-275` (3-6: WYSIWYG & Gating) | `TST-UI-235.3` | `TrackingTabWysiwygContractTest.kt` | **PASSED** (2/2) | `Verified` |
| `REQ-UI-275` (7: Localization Parity) | `TST-UI-235.4` | `TrackingTabLocalizationTest.kt` | **PASSED** (1/1, 9/9 locales) | `Verified` |
| `REQ-PRO-001` (Clean-Room Full Suite) | `TST-UI-235.5` | `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Contract Tests (17/17 Passed)
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManagerTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepositoryTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingTabWysiwygContractTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingTabLocalizationTest"
```
**Output**:
```text
BUILD SUCCESSFUL in 1m 8s
32 actionable tasks: 4 executed, 28 up-to-date
17 tests, 0 failures, 100% successful
```
- `TrackingViewsDatabaseManagerTest`: Verified v12 table creation, column defaults (1), upgrade from v11 to v12, and SQLite toggle persistence.
- `TrackingViewsRepositoryTest`: Verified `TrackingViewInfo` cursor extraction, default values, coroutine main-dispatcher execution, and update methods.
- `TrackingTabWysiwygContractTest`: Verified removal of checkboxes from header, presence of spatial cockpit cards in configuration mode, and runtime gating logic.
- `TrackingTabLocalizationTest`: Verified complete 9-language presence and non-emptiness for all new keys (`config_tracking__show_navigation_hints`, `config_tracking__show_live_climbs`, `config_tracking__wysiwyg_active`, `config_tracking__wysiwyg_hidden`).

---

## 4. Invariant & Governance Verification

1. **Zero Production Regressions**: Full clean-room unit test suite executed with 100% pass rate.
2. **Database Backward Compatibility**: Schema v12 defaults all newly introduced per-tab flags to `1` (true), ensuring zero disruption to existing athlete setups.
3. **Living Documentation Synchronized**: Status in `docs/requirements.md` (`REQ-UI-275`) and `docs/tests.md` (`TST-UI-235`) updated to `Verified`.
4. **Subtask Completion**: Stage 5 subtask `ATT-2514` transitioned to `Erledigt` via `freigabe`.
5. **Parent Ticket Final Review**: Parent ticket `ATT-2360` transitioned to `Final Review (Human)` and assigned to `human`.
