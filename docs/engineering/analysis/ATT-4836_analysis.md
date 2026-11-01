# Stage 1: Problem Domain & Root Cause Analysis - ATT-4836: Own section for auto pause in settings

**Ticket**: [ATT-4836](https://rainerblind.atlassian.net/browse/ATT-4836)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Active Sprint**: `2026-44.1`  
**Target Release**: `V4.9.41`  
**Author**: AI Agent 1 (Analyst)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Historical Archaeology (Chesterton's Fence)

Auto-Pause controls were originally introduced in ATT-45 as experimental threshold sliders nested inside the *Sensors & GPS Filter* accordion section of *Advanced Tuning* (`SensorsGpsFilterSection.kt` / `AdvancedTuningScreen.kt`).
As sport-specific thresholds (Bike, Run, Other) were introduced with independent speed triggers and debounce intervals, keeping 9 distinct controls embedded inside an advanced developer/filter accordion created severe usability and architectural defects:
1. **Low Discoverability**: Everyday athletes expect Auto-Pause to be a first-class settings category in the Navigation Drawer alongside *Sport Types* and *Training Zones*, not hidden under *Advanced Tuning* > *Sensors & GPS*.
2. **Settings Clutter & Visual Competition**: `SensorsGpsFilterSection` grew to over 260 lines, mixing raw GPS accuracy / altimeter smoothing with high-level sport behavioral automation.
3. **Foundation for Upcoming Auto-Pause Enhancements**: Upcoming sprint tickets (`ATT-4839` split detection/resume intervals, `ATT-4840` start auto-paused, `ATT-4856` visual pause cues) require a dedicated, extensible settings cockpit.

---

## 2. Forensic Scope Bounding & Invariants

### In-Scope Deliverables
1. **Navigation Drawer & Routes**: Add `drawer_auto_pause` entry in the Navigation Drawer (`AppNavigationDrawer.kt`, `ids.xml`, `NavRoutes.kt`) directly below *Sport Types*, routed to `NavRoutes.AUTO_PAUSE`.
2. **Dedicated Tabbed Settings Cockpit (`AutoPauseTabsScreen.kt` & `AutoPauseViewModel.kt`)**:
   - Tabbed Material 3 interface with 3 tabs: *Cycling* (`BSportType.BIKE`), *Running* (`BSportType.RUN`), and *Other Sports* (`BSportType.UNKNOWN`).
   - Standard collapsing top bar with drawer hamburger navigation and a sport-scoped *Reset to Defaults* action (`R.string.reset_to_defaults`).
   - Sport-specific controls per tab: Enable toggle (`TuningToggleItem`), Pause Speed Threshold slider (`TuningSliderItem`), and Debounce Delay slider (`TuningSliderItem`).
   - Reactive StateFlow backing with immediate DataStore persistence via `TuningPreferencesDataStore`.
3. **Legacy Clean-up (Single Unambiguous Surface per Design Guidelines 3.2)**:
   - Remove Auto-Pause toggles and sliders from `SensorsGpsFilterSection.kt`, `AdvancedTuningScreen.kt`, and `AdvancedTuningDialog.kt`.
4. **9-Language Localization & Unit Test Verification**:
   - Provide complete 9-language strings for new drawer/screen labels.
   - Author navigation and ViewModel contract tests.

### Non-Functional Invariants
- `TuningPreferencesDataStore` keys and default values (`DEFAULT_AUTO_PAUSE_*`) remain 100% backward-compatible.
- `TrackerService` auto-pause evaluation logic is unchanged.
