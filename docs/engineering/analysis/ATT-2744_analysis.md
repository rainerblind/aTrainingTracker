# Stage 1 Analysis: ATT-2744 - Improve tracking tab configuration toggles layout and eliminate text truncation

**Ticket**: [ATT-2744](https://atrainingtracker.atlassian.net/browse/ATT-2744)  
**Sub-task**: [ATT-2812](https://atrainingtracker.atlassian.net/browse/ATT-2812) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Branch**: `improvement/ATT-2744`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-41.3 review on a physical Google Pixel 10 (ATT-2620 verification), three critical ergonomics and layout issues were observed in tracking tab configuration mode (`ScreenMode.CONFIGURATION` in `SensorGridScreen.kt` and `TrackingTabsScreen.kt`):

1. **Severe Ellipsis Text Truncation**:
   The spatial toggle cards placed side-by-side in 2-column rows ("Karte" vs "Höhenprofil", and "Live-Segmente" vs "Live-Anstiege") suffer severe text truncation. With the icon (20 dp), title, and status badge pill ("Aktiv" / "Ausgeblendet", ~85 dp), the available text width in half-screen columns shrinks to ~35–43 dp on portrait mobile viewports. This forces titles into unreadable ellipses: "K...", "H...", "Live Se...", "Live-An...".
2. **Arbitrary Grey Card Wrapping**:
   "Live-Segmente", "Live-Anstiege", and "Lap Knopf" are wrapped inside an enclosed, elevated grey `Surface` container (`color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, shape = RoundedCornerShape(12.dp)`), while "Karte" and "Höhenprofil" sit in bare rows outside. This arbitrary grouping creates visual inconsistency and user confusion ("Why are LiveSegments, LiveClimb, and Lap Button in one grey box?").
3. **Floating Lap Button Occlusion in Configuration Mode**:
   When entering configuration mode on a tab with the lap button enabled (e.g. the "Runden" tab), the large floating `LapButton` (`+ Runde`) in `TrackingTabsScreen.kt` (line 701) continues to render at `Alignment.BottomCenter`, completely floating over and occluding the lower configuration toggles.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Text Truncation & 2-Column Math
In `SensorGridScreen.kt` (lines 438–502):
- Display width on portrait Pixel 10 is ~392–412 dp.
- Screen horizontal padding is 16 dp * 2 = 32 dp, leaving ~360–380 dp.
- A 2-column `Row` splits this width: `(360 - 8) / 2 = 176 dp` per column.
- Inside each `SpatialCockpitToggleCard`, `Surface` padding is 12 dp * 2 = 24 dp, leaving 152 dp of content width.
- The status badge pill ("Ausgeblendet" = 12 chars in `labelSmall`) with 16 dp horizontal padding and 6 dp start padding consumes ~85 dp.
- The leading icon (20 dp) + 8 dp spacing consumes 28 dp.
- Remaining width for `title`: `152 - 85 - 28 = 39 dp`.
- In German, "Höhenprofil" (~90 dp), "Live-Segmente" (~95 dp), and "Live-Anstiege" (~95 dp) are mathematically guaranteed to truncate into "H...", "Live Se...", and "Live-An...". In French ("Profil d'altitude"), Spanish, and Polish, identical clipping occurs.

### Arbitrary Grey Dock Box
In sprint 2026-41.1 (`ATT-2360`) and 2026-41.3 (`ATT-2620`), the author conceptualized Live Segments, Climbs, and Lap Button as "dock features" (features appearing in bottom sheets or floating controls during tracking) and wrapped them in an elevated grey `Surface` container titled "Spatial WYSIWYG Dock". From the user's mental model, however, all 5 controls below the sensor grid are simply spatial feature toggles for that tab. Wrapping 3 of them in a nested grey box introduces gratuitous nesting, asymmetric padding, and arbitrary visual hierarchy.

### Floating Lap Button Occlusion
In `TrackingTabsScreen.kt` (line 701):
```kotlin
val shouldShowLapButton = currentViewInfo?.showLapButton == true
```
The condition only checked whether the current tab has `showLapButton == true`, omitting `screenMode != ScreenMode.CONFIGURATION`. As documented in `lap_button_edit_mode_screenshot.png`, the floating `LapButton` at `Alignment.BottomCenter` floats directly over the scrollable configuration canvas, blocking access to the toggles.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Eliminate 2-column side-by-side rows for spatial toggle cards in `SensorGridScreen.kt`. Render all spatial feature toggles (Map, Elevation Profile, Live Segments, Live Climbs, Lap Button) as full-width `SpatialCockpitToggleCard`s with uniform spacing and padding.
  2. Eliminate the nested elevated grey `Surface` dock box. Harmonize all 5 lower toggles into a single, cohesive vertical layout matching the top Navigation Hints toggle card.
  3. Suppress the floating `LapButton` in `TrackingTabsScreen.kt` whenever `screenMode == ScreenMode.CONFIGURATION`.
  4. Ensure 100% full-text readability across all 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero ellipsis truncation.
  5. Update contract tests in `TrackingTabWysiwygContractTest.kt` to enforce full-width toggle layout, absence of arbitrary nested dock `Surface`, and LapButton configuration suppression.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying the toggle persistence data model or SQLite database schema (`TrackingScreenState`, `TabToggleActions`).
  * Changing `ScreenMode.TRACKING` or `ScreenMode.PREVIEW` layouts or map weighting.
  * Altering the visual design or logic of `LapButton` in tracking mode.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Refines Clause 2 and Clause 4 of `REQ-UI-295` (*Unified Scrollable Container Architecture and Viewport Insets Slotting for Tracking Tab Configuration Mode (`ScreenMode.CONFIGURATION`)*).
* **Historical Origin & Commit Trace**: Ticket `ATT-2620`, Sprint `2026-41.3`, commit `e5033b5c`.
* **Root Reason for Existing Formulation**: In ATT-2620, Live Segments, Live Climbs, and Lap Button were conceptualized as bottom-dock controls and enclosed in an elevated grey `Surface` container, pairing items into 2-column rows to reduce vertical length. However, this caused severe label truncation on mobile viewports and created arbitrary visual clutter, while the floating `LapButton` was inadvertently left visible in edit mode.
* **Preservation of Core Invariants**:
  - Unified scrollable container (`verticalScroll(rememberScrollState()).navigationBarsPadding()`) remains strictly preserved.
  - Live Map and Elevation Profile exclusion in `CONFIGURATION` mode remains strictly preserved.
  - Runtime gating (`state.showMap`, `state.showElevationProfile`, `state.showLiveSegments`, `state.showLiveClimbs`, `state.showLapButton`, `state.showNavigationHints`) remains strictly preserved.
  - All existing string resources across all 9 locales remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Full-Width Spatial Toggle Architecture (`SensorGridScreen.kt`)**:
   - Replace 2-column `Row` constructs and the nested `Surface` with a unified vertical sequence of full-width `SpatialCockpitToggleCard`s below the sensor grid:
     - Map (`config_tracking__show_map`)
     - Elevation Profile (`config_tracking__showElevationProfile`)
     - Live Segments (`config_tracking__showLiveSegments`)
     - Live Climbs (`config_tracking__show_live_climbs`)
     - Lap Button (`config_tracking__showLapButton`)
   - Group them in a `Column` with `modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)` and `verticalArrangement = Arrangement.spacedBy(6.dp)`.
   - Each card receives `modifier = Modifier.fillMaxWidth()`, providing ~320 dp of usable horizontal text clearance—more than 3x the space needed for the longest localized label ("Profil d'altitude", "Live-Segmente", "Runden-Knopf").

2. **Floating Lap Button Configuration Gating (`TrackingTabsScreen.kt`)**:
   - Update `shouldShowLapButton` in `TrackingTabsScreen.kt`:
     ```kotlin
     val shouldShowLapButton = currentViewInfo?.showLapButton == true && screenMode != ScreenMode.CONFIGURATION
     ```

3. **Contract & Visual Testing (`TrackingTabWysiwygContractTest.kt`)**:
   - Verify that configuration mode contains zero 2-column `Row` arrangements for spatial toggles.
   - Verify that configuration mode contains no nested grey dock `Surface` with tonal elevation 2.dp.
   - Verify that `TrackingTabsScreen.kt` suppresses `LapButton` when `screenMode == ScreenMode.CONFIGURATION`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Full clean-room test suite pass rate (`./gradlew testDebugUnitTest`) MUST be 100%.
  2. Zero regression in TRACKING or PREVIEW modes.
  3. Zero regressions in tab persistence or toggle state dispatch (`TabToggleActions`).
  4. 100% 9-language localization parity without introducing or breaking strings.
* **Risk Assessment**:
  - Low risk: Pure layout restructuring and conditional visibility gating in Compose UI layer. No database, service, or background threading changes.
