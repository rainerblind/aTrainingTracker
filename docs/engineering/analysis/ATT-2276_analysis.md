# Stage 1 Analysis: ATT-2276 - Implement In-App Feature Discovery and Tips Guide

**Ticket**: [ATT-2276](https://rainerblind.atlassian.net/browse/ATT-2276)  
**Sub-task**: Pending creation (`[Analysis]`)  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `sprint/2026-43.9` | **Branch**: `feature/ATT-2276`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-10-23  

---

## 1. Problem Statement & Motivation
aTrainingTracker features powerful telemetry capabilities developed over 15+ years (multi-protocol failover, route clustering / Lieblingsstrecken, Strava live segments, automated gear tracking, and Coggan/Skiba physiological metrics). However, athletes currently lack an in-app discovery surface:
1. *Undiscovered Flagship Features*: Features run quietly in the background without discoverability, leaving athletes unaware of route auto-clustering or sensor failover.
2. *Need for Non-Intrusive Guidance*: Athletes exploring on-device require opt-in, glanceable guidance without disruptive onboarding popups or blocking tutorials.
3. *Actionable Deep-Links*: Tips must provide direct 1-tap shortcuts into relevant app screens ("Sensoren koppeln", "Lieblingsstrecken ansehen", "Cockpit anpassen").

---

## 2. Root Cause Analysis & Independent Peer Review (ATT-3534)
* **Live Code Audit**:
  * *Navigation Drawer Hubs*: `AppNavigationDrawer.kt:182-237` defines 5 groups with 22 items. `drawer__settings` is collapsible (`collapsible = true`), making it the ideal placement for `R.id.drawer_tips_guide`.
  * *Compose Navigation*: `ATrainingTrackerApp.kt:340-560` uses `NavHost` with type-safe `NavRoutes.kt`. Adding `NavRoutes.TIPS_GUIDE` fits the single-activity architecture cleanly.
  * *Deep-Link Infrastructure*: `MainActivityWithNavigation.kt:735-760` resolves drawer IDs to routes. Action buttons within tips can trigger `navController.navigate(...)` cleanly.
* **Independent Hypothesis Peer Review**:
  * *UI Layout (Accordion vs. Carousel)*: Ticket suggests carousel or accordion. Audit confirms a vertical scrollable column of expandable cards (`TipsGuideScreen.kt`) avoids horizontal swipe gesture conflicts with drawer gestures and provides instant scanning across phone and landscape orientations.
  * *Asset Footprint*: The ticket mentions screenshots/animations. To prevent multi-megabyte APK bloat, the implementation will use theme-aware Material vector iconography and styled cards (Light, Dark, and AMOLED) with zero bitmap footprint.
  * *Drawer Density Invariant*: Adding `drawer_tips_guide` expands Settings items ($7 \to 8$) and total destinations ($22 \to 23$) while preserving the 9 default visible rows (`groups.filter { !it.collapsible }.sumOf { it.items.size } == 9`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Dedicated "Tips & Tricks" drawer entry (`R.id.drawer_tips_guide`) in `drawer__settings` with `ic_tips_and_updates` icon.
  * Full-screen Compose destination `TipsGuideScreen.kt` at `NavRoutes.TIPS_GUIDE = "tips_guide"` with TopAppBar, Back navigation, and scrollable expandable card accordion.
  * 7 curated power tips: (1) Favorite Routes / Clustering, (2) Sensor Failover & Pairing, (3) Cockpit Layouts & Bike Bell, (4) Strava Live Segments & Scrubbing, (5) Gear & Equipment Tracking, (6) Training Zones & Physiology, (7) Data Sovereignty & Offline Backup.
  * Action shortcuts on cards deep-linking to corresponding app destinations (`LOCATIONS`, `SENSORS`, `SPORT_TYPES`, `SEGMENTS`, `EQUIPMENT`, `trainingZones`, `BACKUP_RESTORE`).
  * 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  * Updating `NavigationDrawerStateTest.kt` and `SingleActivityNavigationTest.kt` for 23 total destinations.
* **Out-of-Scope Non-Goals**:
  * Aggressive auto-prompting modal dialogs or blocking walkthrough overlays.
  * External web manual or video hosting (handled under ATT-2274 and ATT-2275).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-422` (*Drawer Structure & Settings Relocation*) and `REQ-UI-159` (*Single-Activity Navigation*); introduces `REQ-UI-431` (*In-App Feature Discovery & Tips Guide*).
* **Historical Origin & Commit Trace**: `ATT-3851` (commit `04b6b158`, 5-group drawer), `ATT-3928` (commit `4623bb6f`, collapsible groups), and `ATT-4289` (commit `30954b03`, 22 destinations).
* **Root Reason for Existing Formulation**: `REQ-UI-422` capped destination count to 22 when Sport Types was relocated to Settings.
* **Preservation of Core Invariants**: 5 category hubs, non-collapsible 9 visible rows, `drawer_advanced_tuning` as the terminal Settings item, deep-link routing backward compatibility, and 100% test pass rate strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. *Navigation Layer*:
   - Add `R.id.drawer_tips_guide` in `ids.xml` and `AppNavigationDrawer.kt:createDrawerGroups` inside `drawer__settings` (before `drawer_privacy_policy`).
   - Define `NavRoutes.TIPS_GUIDE = "tips_guide"` and map in `fromDrawerItemId` / `toDrawerItemId`.
2. *Compose Screen*:
   - Create `com.atrainingtracker.trainingtracker.ui.help.TipsGuideScreen.kt` with `Scaffold`, `TopAppBar`, and `LazyColumn`.
   - Implement `TipCard` composable: Material 3 `ElevatedCard` (`RoundedCornerShape(12.dp)`), expandable body with explanatory bullets, highlight badge, and `Button` / `OutlinedButton` deep link.
3. *Deep-Linking*:
   - Wire action buttons directly to navigation callbacks invoking `navController.navigate(...)`.
4. *Localization*:
   - Define all tip titles, summaries, and action labels in `values*/strings.xml` across all 9 locales.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero disruption of active tracking; drawer collapses properly; non-collapsible density remains 9; 100% clean-room test pass rate (`NavigationDrawerStateTest`, `SingleActivityNavigationTest`).
* **Risk Rating**: **LOW** (Isolated UI screen and navigation route addition; zero database or background service modifications).
