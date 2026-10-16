# Sprint Review & Retrospective — Sprint 2026-42.6

**Date**: 2026-10-16  
**Sprint**: `2026-42.6` (`sprint/2026-42.6`)  
**Target Release**: `V4.9.40`

---

## 1. Sprint Review Summary

### 1.1 Accepted Tickets (`FixVersion: V4.9.40`)
1. **ATT-3690**: *OutOfMemoryError in Google Maps `Polyline.setPoints` (`m140.dsa.b`) due to 1Hz `MapRoute`/`MapSegment` reallocation and unthrottled polyline updates* — **Accepted**
2. **ATT-3776**: *Replace preview zone `FilterChip`s in `ZonesSettingsActivity` with compact zone-tinted bordered pills to prevent Z1-Z7 line wrapping* — **Accepted**
3. **ATT-3777**: *Remove status badge, reset action, last-updated row, and athlete weight row from `ZonesSettingsActivity` header* — **Accepted**
4. **ATT-3778**: *Use growing 1..29s rolling window during initial 30s warm-up for live Normalized Power, Intensity Factor, and TSS* — **Accepted**
5. **ATT-3771**: *Prevent line wrap on `Histogramm` segmented button in Zone Card Display Mode selector and Zone Distribution cards* — **Accepted**
6. **ATT-3772**: *Remove detailed per-zone time breakdown list when Zone Distribution card is in Stacked Bar mode* — **Accepted**
7. **ATT-3773**: *Configure default Zone Card Display Mode independently for Workout Summary and Workout Details* — **Accepted**
8. **ATT-3774**: *Unify arrowhead style and size across all 8 turn cue vector icons and fix Turn Left arrowhead centering* — **Accepted**
9. **ATT-3708**: *Scale live `ZoneGaugeBar` linearly with 10% boundary clipping, center active zone label beneath active segment, and use primary-blue needle* — **Accepted**
10. **ATT-3704**: *Add dedicated Athlete Profile screen with timestamped history and time-series plots for Weight, Resting HR, FTP, and W/kg* — **Accepted** (Follow-ups: `ATT-3850`, `ATT-3851`).
11. **ATT-3696**: *Make Zone 6 color (`TTColor.Zone6` / `R.color.zone_6`) distinctly browner and lighter for clear visual separation from Zone 7 (Black)* — **Accepted**
12. **ATT-3692**: *Remove dead sensor pairing code from `ControlTrackingScreen`, `ControlTrackingViewModel`, `TrackingTabsScreen`, and `MainActivityWithNavigation`* — **Accepted**
13. **ATT-3779**: *Render `ElevationProfile` empty state on tracking tabs when enabled before tracking starts or without active route* — **Accepted**

### 1.2 Bounced / Reverted Tickets
- **ATT-2859**: *Migrate legacy multi-density raster drawables to scalable Jetpack Compose vector assets* — **Reverted on `sprint/2026-42.6` (`fc89b8d4`)** and bounced back to `Zu erledigen` after discovering the original `.fig` (`xfig`), `.svg` (Inkscape), and `.xcf` (GIMP) master vector files in `/home/rainer/Dropbox/workspace/`.

---

## 2. Key Retrospective Learnings & Root Cause Analysis

### Learning 1: System Navigation Bar Inset Clearance on Full-Screen Scrollable Containers (`ATT-3704` -> `ATT-3850`)
- **Observation**: On the new *Profil & Zonen* (`AthleteProfileScreen`) screen, when scrolling all the way down to the bottom of the screen (where the embedded Power Zones Configuration card resides), the very bottom of the card (the 7-Zone Preview Pills row) was partially obscured behind the Android system navigation bar.
- **Root Cause**: `AthleteProfileScreen` uses `Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp))` hosted inside an edge-to-edge Activity (`MainActivityWithNavigation`) without applying `Modifier.navigationBarsPadding()` or `WindowInsets.navigationBars` bottom padding.
- **Countermeasure (Rule 41 / `docs/design_guidelines.md` Sec 5.6)**:
  - Codified **Rule 41**: Every full-screen scrollable container (`Column(Modifier.verticalScroll(...))` or `LazyColumn`) hosted inside an edge-to-edge Activity **MUST** account for the bottom Android system navigation bar insets (via `Modifier.navigationBarsPadding()` after `verticalScroll`, or `WindowInsets.navigationBars.asPaddingValues()` in `LazyColumn(contentPadding = ...)`).
  - Created follow-up ticket `ATT-3850` with on-device screenshot evidence attached (`ATT-3704_bottom_navbar_overlap.png`) and ranked it at the top of the backlog.

### Learning 2: Authoritative Vector Master Files in `/home/rainer/Dropbox/workspace/` (`ATT-2859`)
- **Observation**: Even when constructed from geometric primitives (`tools/generate_all_vectors.py`), hand-measured approximations of the complex multi-layered sport and sensor icons (`bsport_bike`, `bsport_run`, `logo_ant`, etc.) showed subtle geometric differences compared to the original raster PNGs when compared side-by-side. During the review, the user located the original `.fig` (`xfig`), `.svg` (Inkscape), and `.xcf` (GIMP) vector source files in `/home/rainer/Dropbox/workspace/` (`aTrainingTracker_Images/`, `Images/`, `Logos/`, `featureGraphic/`).
- **Countermeasure (Updated Rule 37 / `docs/design_guidelines.md` Sec 5.5)**:
  - Documented `/home/rainer/Dropbox/workspace/` in `docs/design_guidelines.md` §5.5 and on `ATT-2859` so that any icon vectorization directly converts the original `.svg` / `.fig` master geometry rather than approximating by hand.

### Learning 3: Navigation Drawer Information Architecture (`ATT-3704` -> `ATT-3851`)
- **Observation**: With the addition of new top-level screens (*Profil & Zonen*, *Ausrüstung*, *Sensoren*, *Karten*, *Segmente*, etc.), the main Navigation Drawer is becoming full and requires structural grouping/decluttering.
- **Action**: Created follow-up ticket `ATT-3851` (*Restructure and declutter Navigation Drawer items and sections*) ranked at the top of the backlog.
