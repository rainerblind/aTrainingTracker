# Sprint Review & Retrospective — Sprint 2026-45.6 (`ATT-5402`)

**Ticket**: [ATT-5402](https://rainerblind.atlassian.net/browse/ATT-5402)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Sprint**: `2026-45.6`  
**Target Release**: `V5.0.0`  
**Branch**: `sprint/2026-45.6` $\rightarrow$ `develop`  
**Date**: 2026-11-05  

---

## 1. Sprint Velocity & Capacity Summary (`REQ-PRO-029`)

| Metric | Planned at Sprint Start | Mid-Sprint Additions (`Rule 65`) | Accepted (`i.O.`) | Deferred in Review (`Final Review (Human)`) | Returned to `Zu erledigen` (`n.i.O.`) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Engineering Tickets** | 10 (`ATT-5401..5397`, `ATT-4957`, `ATT-30`, `ATT-43`, `ATT-907`, `ATT-908`) | 2 (`ATT-5422`, `ATT-5418`) | **11** (`ATT-5401`, `ATT-5400`, `ATT-5399`, `ATT-5398`, `ATT-5397`, `ATT-4957`, `ATT-30`, `ATT-43`, `ATT-907`, `ATT-908`, `ATT-5422`) | **1** (`ATT-5418`) | **0** |
| **Review & Retro Ticket** | 1 (`ATT-5402`) | 0 | **1** | 0 | 0 |
| **Story Points (`customfield_10016`)** | **21 SP** | **4 SP** | **22 SP** | **3 SP** | **0 SP** |

* **Tier 2 Sprint Integration Gate**: `4,462 tests, 0 failures, 0 ignored (100% pass rate)` (`docs/engineering/test_results/index.html`) and `python3 tools/verify_requirement_governance.py` (`0 errors`).

---

## 2. Sprint Review Summary (12 Engineering Tickets + 1 Retro Ticket)

| # | Key | SP | Summary | Commit | Review Outcome | Follow-Up Tickets / Notes |
| :-: | :--- | :-: | :--- | :---: | :--- | :--- |
| 1 | `ATT-5401` | 1 | `FirstJubileeCelebrationDialog`: 'Bei Google Play bewerten' Button Does Nothing Due to Silent Play Core ReviewManager No-Op | `3118792d` | **Accepted (`i.O.`)** (`V5.0.0`) | Direct Play Store URI fallback when `ReviewManager` finishes in $<150\text{ ms}$ |
| 2 | `ATT-5400` | 1 | Remove Excessive Emojis from Gold and Diamond Garage Upgrade Nudge Texts Across All 9 Locales | `6c53901b` | **Accepted (`i.O.`)** (`V5.0.0`) | Clean vector iconography over inline Unicode emojis |
| 3 | `ATT-5399` | 2 | Replace Centered `AlertDialog`s with `AppModalBottomSheet` Bottom Popups and Codify in Design Guidelines | `ce6b99e3` | **Accepted (`i.O.`)** (`V5.0.0`) | Follow-ups `ATT-5468` & `ATT-5470` for stacked full-width CTA buttons in German |
| 4 | `ATT-5398` | 2 | Subscription Downgrade Sensor Quota Reconciliation: Keep-2-Sensors Selection Dialog when Paired Sensors > 2 in Free Tier | `d287b0f6` | **Accepted (`i.O.`)** (`V5.0.0`) | Follow-up `ATT-5471` for downgrade banner wording & dismissal |
| 5 | `ATT-5397` | 2 | Automatic Tier-Matched Launcher Icon and Removal of `SupporterQuickIconPickerDialog` | `8baf42c8` | **Accepted (`i.O.`)** (`V5.0.0`) | Automatic launcher alias synchronization with active subscription tier |
| 6 | `ATT-4957` | 3 | Import Strava bulk export ZIP archives (`activities.csv` metadata + `.fit`/`.tcx`/`.gpx.gz` streams + gear) | `33856f04` | **Accepted (`i.O.`)** (`V5.0.0`) | Streaming ZIP ingestion with gear reconciliation & duplicate prevention |
| 7 | `ATT-30` | 2 | Snapshot with track on picture | `50243b74` | **Accepted (`i.O.`)** (`V5.0.0`) | Follow-ups `ATT-5472` & `ATT-5473` for share overlay customization |
| 8 | `ATT-43` | 3 | Add photos taken during a workout | `5c9d9653` | **Accepted (`i.O.`)** (`V5.0.0`) | Follow-ups `ATT-5474`, `ATT-5476`, `ATT-5479` (neutral section tokens, `WorkoutMasksAndCardsSection` settings, `(0.0, 0.0)` EXIF GPS guard) |
| 9 | `ATT-907` | 3 | Aftermath: Create lap based on the map | `f1663727` | **Accepted (`i.O.`)** (`V5.0.0`) | Follow-ups `ATT-5478`, `ATT-5479`, `ATT-5480` (prevent `HorizontalPager` unmount on refresh, scissors icon, `TrackType.BEST` blue polyline) |
| 10 | `ATT-908` | 2 | Aftermath: Move lap based on the map | `2f4a555a` | **Accepted (`i.O.`)** (`V5.0.0`) | Follow-ups `ATT-5482` (delete/merge lap) & `ATT-5483` (open `LapEditBottomSheet` on lap row click) |
| 11 | `ATT-5422` | 1 | `SensorStatus` Top-Bar: 16 dp Randabstand (Option A) für Hamburger-Menü und Supporter-Emblem (Diamant) | `1408e928` | **Accepted (`i.O.`)** (`V5.0.0`) | Exact `16 dp` visual margin & `32 dp` symmetric touch targets |
| 12 | `ATT-5418` | 3 | Online User Guide (`docs/guide/`): 2-Spalten-Layout (`width=240`), Du-Anrede, Austausch von 12 falschen Screenshots & Korrektur erfundener Features (Kapitel 1-9) | `0bcdf317` | **Deferred in Review (`Final Review (Human)`)** | Kept in `Final Review (Human)` for live GitHub rendering inspection after `develop` push; follow-up `ATT-5484` created for 9-language translation |

*Additional Review Observation Tickets Created*: `ATT-5486` (Fixed `TTColor.Zone1` Green for `ElevationProfile` marginal altitude histogram) and `ATT-5487` (Grade / Slope Distribution Card in Workout Details Zonen/Histogramm Tab).

---

## 3. Retrospective Topics & Permanent Countermeasures (`Rule 73`)

### Topic 1: `AppModalBottomSheet` Horizontal Action Row Button Wrapping in German (`ATT-5399` & `ATT-5398`)
* **Observation**: Migrating `GarageUpgradeNudgeDialog` and `SwapOrUpgradeDialog` from `AlertDialog` to `AppModalBottomSheet` placed 3 action buttons or long German CTA labels directly inside `AppModalBottomSheet.actions` (`RowScope`), causing severe mid-word line wrapping (`"Abbreche\nn"`, `"Tausc\nhen &\nKoppel\nn"`).
* **Countermeasure (`Rule 73` — `docs/design_guidelines.md` Sec 5.3)**:
  * Whenever an `AppModalBottomSheet` has $\ge 3$ action buttons OR any button label exceeds ~15 characters across the 9 locales (especially German), primary and secondary CTAs **MUST** be stacked vertically as full-width buttons (`Modifier.fillMaxWidth()`) at the bottom of the sheet's `content` column (modeled on `FirstJubileeCelebrationDialog.kt`), leaving only a single full-width dismiss `TextButton` (or at most two short 1-word buttons) in `actions`.

### Topic 2: `WorkoutSummary` Visual Cohesion, Advanced Settings (`WorkoutMasksAndCardsSection`) Wiring & Stable Pager State Across Refreshes (`ATT-43`, `ATT-907`, `ATT-908`)
* **Observation**:
  1. In `ATT-43`, `WorkoutPhotoStrip` used `colorScheme.primary` (blue) for passive section icons and clipped `"Fotos hinzufügen"` inside a `76.dp` tile, whereas all neighboring sections in `WorkoutSummary.kt` use neutral `onSurfaceVariant` (dark grey) headers and `HorizontalDivider`s, and the new photo section was not wired into *Experten-Einstellungen -> Trainingsmasken & Karten* (`WorkoutMasksAndCardsSection.kt`).
  2. In `ATT-907`, `refreshWorkout(workoutId)` toggled `isDetailLoading = true` and `ExportWorkoutBroadcast` fired 5 times (once per export format), unmounting `WorkoutDetailTabbedContent` (`HorizontalPager`) and jumping repeatedly back to Tab 0 (*Karte & Diagramme*). Furthermore, a photo with EXIF `(0.0, 0.0)` Null-Island coordinates zoomed the map out to a 5,000 km view where the workout track became sub-pixel invisible.
* **Countermeasure (`Rule 73` — `docs/design_guidelines.md` Sec 5.17, `docs/coding_standards.md` Sec 5.9)**:
  * New sections in `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt` must use neutral `onSurfaceVariant` section headers, standard dividers, and mandatory visibility/sub-option wiring in `WorkoutMasksAndCardsSection.kt`.
  * In-place workout refreshes (`refreshWorkout(workoutId)`) must never unmount `WorkoutDetailTabbedContent` (`HorizontalPager`) when a workout is already displayed.
  * Always reject `(0.0, 0.0)` Null-Island coordinates (`abs(lat) < 1e-5 && abs(lng) < 1e-5`) when parsing EXIF GPS tags and when computing map markers or `LatLngBounds`.

### Topic 3: Multimodal `read_file` Verification of `docs/guide/img/*.png` & Code-Grounded User Documentation (`ATT-2275` $\rightarrow$ `ATT-5418`)
* **Observation**: In `ATT-2275`, relying solely on `File.exists()` unit tests without opening `.png` files via `read_file` allowed 12 mismatched screenshots and speculative features from early brainstorming notes to pass Gate 5.
* **Countermeasure (`Rule 73` — `stage5-verification/SKILL.md`, `docs/coding_standards.md` Sec 5.9)**:
  * Both `agent1` and `agent2` must visually inspect every image in `docs/guide/img/*.png` via `read_file` and verify all documented user guide features against `app/src/main/`.
