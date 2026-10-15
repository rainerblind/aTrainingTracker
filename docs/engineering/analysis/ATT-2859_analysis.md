# Stage 1 Analysis: ATT-2859 - Migrate Legacy Multi-Density Raster Drawables to Faithful Scalable Vector Assets

**Ticket**: [ATT-2859](https://atrainingtracker.atlassian.net/browse/ATT-2859)  
**Sub-task**: [ATT-3725](https://atrainingtracker.atlassian.net/browse/ATT-3725) (`[Analysis]`)  
**Parent Epic**: [ATT-431](https://atrainingtracker.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Branch**: `feature/ATT-2859`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Statement & Motivation

The application maintains dozens of legacy multi-density PNG raster drawables originating from the app's early Android architecture (dating back to 2011–2017). These include sport type icons (`bsport_bike`, `bsport_run`, `bsport_other`), ANT+ sensor badges (`hr`, `bike_spd`, `bike_cad`, `bike_speed_and_cadence`, `bike_pwr`, `run_spd`, `temp`), BLE sensor badges (`bt_hr`, `bt_bike_spd`, `bt_bike_cad`, `bt_bike_speed_and_cadence`, `bt_bike_pwr`, `bt_run`), and duplicate grayed-out assets (`bsport_*_gray.png`).

This legacy raster architecture incurs severe disadvantages:
1. **Density Split Fragility**: Raster images distributed across separate folders (`drawable-mdpi`, `-hdpi`, `-xhdpi`, `-xxhdpi`, `-xxxhdpi`) introduce vulnerability to `Resources$NotFoundException` crashes when Play Store density splits or non-standard device densities are encountered.
2. **Visual Scalability & Rendering Quality**: Bitmaps do not scale crisply on modern high-DPI displays (QHD+, 4K, foldables) compared to mathematical vector curves.
3. **APK Footprint & Redundancy**: Maintaining 5 density variants for each icon plus separate greyed-out bitmaps adds unnecessary APK footprint (~103 PNG files).

### Sprint 2026-42.4 Joint Review Rejection & Forensic Post-Mortem
During the Sprint 2026-42.4 Joint Review, the previous attempt to vectorize these assets (merge commit `8e218312`) was **rejected (n.i.O.) and reverted** because:
- The previous implementation mistakenly replaced the iconic multi-color assets with generic, monochrome Material Design vector icons (e.g. replacing the multi-color cyclist with a generic single-color bike icon).
- This degraded the app's visual identity and looked unacceptable on physical devices.

**Official Human Mandate (Sprint 2026-42.4 Review)**:
> *"The goal of ATT-2859 is to faithfully redraw the existing icons as vector graphics (preserving their exact visual design, multi-color geometry, and badge layout — e.g., `bsport_bike` with black wheels, red frame, and blue rider; black ANT+ rounded-square sensor badges; blue Bluetooth rounded-square sensor badges), NOT to replace them with generic monochrome Material Design icons."*

---

## 2. Forensic Investigation & Asset Catalog

A comprehensive inspection of the existing raster PNGs across `res/drawable*` reveals the exact visual geometry, color palette, and composition required for each asset:

### 2.1 Category A: Sport Type Indicators (Multi-Color Vector Assets)
1. **`bsport_bike`** (192×192 px canvas in xxxhdpi):
   - **Wheels**: Two thick black (`#000000`) circular rings (rear wheel centered at $(60, 116)$, front wheel centered at $(144, 116)$, radius $\approx 36$ px, inner cutout radius $\approx 18$ px).
   - **Bicycle Frame**: Bold red (`#FF0000`) tubular geometry forming a rear triangle (chainstay, seatstay), top tube, down tube, seat tube, and front fork.
   - **Saddle & Seatpost**: Black (`#000000`) horizontal saddle and vertical post at $(64, 72)$.
   - **Rider**: Pure blue (`#0000FF`) aerodynamic time-trial cyclist posture:
     - Head: Solid blue circle centered at $(146, 44)$, radius $\approx 14$ px.
     - Torso / Back: Curved ergonomic back arching from $(70, 64)$ to $(136, 68)$.
     - Arms: Forearm resting forward on aero-extension bars at $(162, 86)$.
     - Legs: Thigh and calf bent in active pedaling stroke down toward bottom bracket.
   - **Background**: Fully transparent (`#00000000`).

2. **`bsport_run`** (192×192 px canvas in xxxhdpi):
   - **Runner**: Pure blue (`#0000FF`) silhouette of a stick-figure athlete running dynamically toward the right:
     - Head: Solid blue circle centered at $(114, 30)$, radius $\approx 16$ px.
     - Torso: Forward-leaning diagonal torso.
     - Arms: Left arm bent backward, right arm pumping forward.
     - Legs: Right leg bent high at knee in forward stride; left leg extending backward in push-off phase.
   - **Background**: Fully transparent (`#00000000`).

3. **`bsport_other`** (192×192 px canvas in xxxhdpi):
   - **Stopwatch Ring**: Rich gold/yellow (`#FFC700`) circular ring body (outer radius $\approx 68$ px, ring width $\approx 14$ px) with top neck and side button brackets.
   - **Top Center Button / Crown**: Rectangular push-button in pure red (`#FF0000`).
   - **Right Button**: Angled push-button in pure green (`#008F00`) tilted at $\approx +30^\circ$.
   - **Left Button**: Angled push-button in dark blue (`#00008F`) tilted at $\approx -30^\circ$.
   - **Dial Face**: Transparent interior with subtle neutral fill.

### 2.2 Category B: ANT+ Sensor Badges (Black Rounded Square Badges)
Canvas: 96×96 px (xhdpi).
- **Badge Container**: Solid black (`#000000`) rounded square with corner radius $r \approx 12$ px and fully transparent outer corners (eliminating the legacy `#FAFAFA` bounding box glitch from non-alpha RGB bitmaps).
- **Foreground Graphics**: Pure white (`#FFFFFF`).
- **Composition**: Upper region ($y \in [8..66]$) features the device glyph; lower region ($y \in [72..90]$) features bold uppercase white lettering.

| Asset | Glyph Description | Bottom Label |
| :--- | :--- | :--- |
| `hr.xml` | White heart outline ($w \approx 64$ px, $h \approx 54$ px, stroke $\approx 7$ px) | **`HR`** |
| `bike_spd.xml` | White cyclist silhouette (head, arms, torso, frame, two hollow wheels) | **`SPD`** |
| `bike_cad.xml` | Identical white cyclist silhouette | **`CAD`** |
| `bike_speed_and_cadence.xml` | Identical white cyclist silhouette | **`S & C`** |
| `bike_pwr.xml` | Identical white cyclist silhouette | **`PWR`** |
| `run_spd.xml` | White footprint outline (sole) with 5 solid circular toes angled diagonally | **`SPD`** |
| `temp.xml` | White thermometer outline (bulb, stem, 5 horizontal tick marks, bulb highlight) | **`TEMP`** |

### 2.3 Category C: Bluetooth LE Sensor Badges (Blue Rounded Square Badges)
Canvas: 96×96 px (xhdpi).
- **Badge Container**: Solid official Bluetooth Blue (`#0A3D91`) rounded square with corner radius $r \approx 12$ px and transparent outer corners.
- **Foreground Graphics**: Pure white (`#FFFFFF`).

| Asset | Glyph Description | Bottom Label |
| :--- | :--- | :--- |
| `bt_hr.xml` | Large white heart outline filling the badge body | *(None - full heart)* |
| `bt_bike_spd.xml` | White time-trial cyclist silhouette (matching `bsport_bike` shape) | **`speed`** (lowercase bold) |
| `bt_bike_cad.xml` | Same white time-trial cyclist silhouette | **`cadence`** (lowercase bold) |
| `bt_bike_speed_and_cadence.xml` | Same white time-trial cyclist silhouette | **`s & c`** (lowercase bold) |
| `bt_bike_pwr.xml` | Same white time-trial cyclist silhouette | **`power`** (lowercase bold) |
| `bt_run.xml` | Same white runner silhouette (matching `bsport_run` shape) filling badge | *(None - full runner)* |

### 2.4 Category D: Obsolete / Dead Assets to Permanently Delete
- `bsport_bike_gray.png`, `bsport_run_gray.png`, `bsport_other_gray.png`: Duplicate grayed-out assets from legacy Android days. Audited across codebase: **0 production code references**. Compose dynamic tinting or enabled states handle disabled styling directly.
- `btle_hr.png`: Orphaned duplicate of `bt_hr.png`. Audited across codebase: **0 production code references**.

---

## 3. Chesterton's Fence Requirement Archaeology (`REQ-UI-312`)

1. **Original Requirement ID & Target**: Amends `REQ-UI-312` (*Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture*). Targets `app/src/main/res/drawable/`, `SportTypeSelectorContractTest.kt`, and `DrawableDensityFallbackAuditTest.kt`.
2. **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.5 via ticket `ATT-2858` (Epic `ATT-235`: *No crashs*).
3. **Root Reason for Existing Formulation**: `ATT-2858` copied raster PNG files into root `res/drawable/` as an emergency defensive measure against density-split APK crashes on Google Play, and instituted `DrawableDensityFallbackAuditTest.kt` to ensure every asset in a density folder has a root counterpart.
4. **Preservation of Core Invariants**:
   - `DrawableDensityFallbackAuditTest.kt` checks that any drawable name in a density folder has a matching file in root `res/drawable/`. When density-specific PNGs are deleted and replaced by a single authoritative XML VectorDrawable in root `res/drawable/`, root fallback is inherently 100% satisfied.
   - Resource identifiers (`R.drawable.bsport_bike`, `R.drawable.hr`, etc.) remain completely unchanged.
   - Replacing raster PNGs with faithfully redrawn XML vector drawables eliminates density-split crashes permanently while preserving the app's visual identity.

---

## 4. Scope Bounding

### 4.1 In Scope
1. **Creation of 16 Faithful XML VectorDrawables** in root `app/src/main/res/drawable/`:
   - Sport icons (3): `bsport_bike.xml`, `bsport_run.xml`, `bsport_other.xml`
   - ANT+ badges (7): `hr.xml`, `bike_spd.xml`, `bike_cad.xml`, `bike_speed_and_cadence.xml`, `bike_pwr.xml`, `run_spd.xml`, `temp.xml`
   - BLE badges (6): `bt_hr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`, `bt_bike_speed_and_cadence.xml`, `bt_bike_pwr.xml`, `bt_run.xml`
2. **Purge of Obsolete Density Raster Files**:
   - Remove the 16 migrated `.png` files across all density folders (`drawable-ldpi/`, `drawable-mdpi/`, `drawable-hdpi/`, `drawable-xhdpi/`, `drawable-xxhdpi/`, `drawable-xxxhdpi/`, and root `drawable/`).
   - Remove all 18 instances of `bsport_*_gray.png` and all instances of `btle_hr.png`.
3. **Contract Test Modernization**:
   - Update `SportTypeSelectorContractTest.kt` to assert presence of vector drawables (`.xml`) rather than legacy `.png` or dead `_gray` assets.
   - Verify `DrawableDensityFallbackAuditTest.kt` passes with 100% compliance.
   - Add `VectorAssetResolutionContractTest.kt` to verify inflation, viewport dimensions, and rendering.

### 4.2 Out of Scope
- Third-party brand assets (`connected_with_*.png`, Strava, RunKeeper, Dropbox) — governed by external sync tickets.
- Map POI markers (`arrowhead.png`, `start_logo_map.png`) — governed by map styling tickets.
- Battery drawables (`stat_sys_battery_*.png`) — governed by telemetry UI tickets.

---

## 5. Architectural & Implementation Strategy

1. **Exact Path Geometry Extraction**:
   - Trace vector paths directly from the high-resolution xxxhdpi/xhdpi reference bitmaps using mathematical vector primitives (circles, bezier curves, polygon paths).
   - Use viewport dimensions matching canvas proportions (e.g. 96×96 or 192×192 or normalized 24×24 dp).
2. **Multi-Color `<vector>` Composition**:
   - Use multiple `<path>` elements within each vector drawable to preserve multi-color geometry:
     - `bsport_bike.xml`: Path 1 (black wheels), Path 2 (black saddle), Path 3 (red frame), Path 4 (blue rider).
     - `bsport_other.xml`: Path 1 (yellow body), Path 2 (red crown), Path 3 (green button), Path 4 (blue button).
   - ANT+ badges: Path 1 (black rounded square background), Path 2 (white glyph), Path 3 (white text).
   - BLE badges: Path 1 (blue rounded square background), Path 2 (white glyph), Path 3 (white text where applicable).
3. **Zero Bounding Box Glitch**:
   - Transparent vector canvas replaces the legacy `#FAFAFA` RGB corners.

---

## 6. Next Steps
1. Advance `ATT-3725` to `in_review` and execute Gate 1 audit via `review_agent.py`.
2. Transition parent `ATT-2859` to Stage 2 (`Req & Test Spec`).
3. Author formal requirement `REQ-UI-375` and test spec `TST-UI-338`.
