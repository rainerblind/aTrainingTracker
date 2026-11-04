# Sprint Review & Retrospective — Sprint 2026-45.5 (`ATT-5375`)

**Ticket**: [ATT-5375](https://rainerblind.atlassian.net/browse/ATT-5375)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Sprint**: `2026-45.5`  
**Target Release**: `V5.0.0`  
**Branch**: `sprint/2026-45.5` $\rightarrow$ `develop`  
**Date**: 2026-11-04  

---

## 1. Sprint Velocity & Capacity Summary (`REQ-PRO-029`)

| Metric | Planned at Sprint Start | Mid-Sprint Additions (`Rule 65`) | Accepted (`i.O.`) | Deferred in Review | Returned to `Zu erledigen` (`n.i.O.`) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Engineering Tickets** | 8 (7 active + 1 consolidated `ATT-5372`) | 0 | **6** (`ATT-1482`, `ATT-1406`, `ATT-5372`, `ATT-5373`, `ATT-5371`, `ATT-5370`) | **1** (`ATT-4853`) | **1** (`ATT-2274`) |
| **Review & Retro Ticket** | 1 (`ATT-5375`) | 0 | **1** | 0 | 0 |
| **Story Points (`customfield_10016`)** | **16 SP** | **0 SP** | **12 SP** | **2 SP** | **2 SP** |

* **Tier 2 Sprint Integration Gate**: `4,361+ tests, 0 failures, 0 ignored (100% pass rate)` (`docs/engineering/test_results/index.html`) and `python3 tools/verify_requirement_governance.py` (`0 errors`).

---

## 2. Sprint Review Summary (8 Engineering Tickets + 1 Retro Ticket)

| # | Key | SP | Summary | Commit | Review Outcome | Notes / Deviations |
| :-: | :--- | :-: | :--- | :---: | :--- | :--- |
| 1 | `ATT-1482` | 2 | App Icon Modernization: Android Adaptive Icon support & safe zone padding | `d1cb8224`, `e0c5b1c5` | **Accepted (`i.O.`)** (`V5.0.0`) | Centered at `cx=7080, cy=1280, scale=0.0086` for balanced runner leg clearance (`Rule 1` documented deviation) |
| 2 | `ATT-1406` *(+ `ATT-5372`)* | 3 + 1 | Pro Supporter Badges: Custom Launcher Icons, Cockpit Status Emblem & Quick Icon Picker Crash Fix | `ad89ef4d`, `e0c5b1c5` | **Accepted (`i.O.`)** (`V5.0.0`) | Crisp Inkscape-converted ribbon text, removed outer circle & top-right emblem per user review (`Rule 1` documented deviation) |
| 3 | `ATT-4853` | 2 | Optimize Play Store description (Title, Short, Full) across 9 locales and refresh screenshots | `df8ca3a5` | **Deferred in Review (`Final Review (Human)`)** | Kept in review for detailed human reading & screenshot refresh |
| 4 | `ATT-2274` | 2 | Produce Google Play Store Promo Video and Feature Highlights | `428f1a36` | **Returned to `Zu erledigen` (`n.i.O.`)** | Requires interactive session / human video capture |
| 5 | `ATT-5373` | 2 | `VerticalSpeedAndSlopeDevice`: Prevent initial DESCENT (`+22.5m`) and SLOPE (`-59%`) jump | `f3915a42` | **Accepted (`i.O.`)** (`V5.0.0`) | Barometer cold-start calibration settling & GPS accuracy gate |
| 6 | `ATT-5371` | 2 | `SupporterScreen`: Dynamic Pioneer year, remove hardware voting, fix button wrapping & localize strings | `31da409a`, `888c046f` | **Accepted (`i.O.`)** (`V5.0.0`) | Restored original single-line `DrawerHeader` with full-size (`1.81f`) supporter icon (`Rule 1` documented deviation) |
| 7 | `ATT-5370` | 2 | Workout Details Graphs: Add wider right-side marginal histograms to all graphs in graph color | `75485932` | **Accepted (`i.O.`)** (`V5.0.0`) | Universal `52.dp` portrait / `72/28` landscape series-colored marginal histograms |

---

## 3. Retrospective Topics & Permanent Countermeasures (`Rule 72`)

### Topic 1: Mandatory `read_file` Inspection of Reference Design Attachments (`docs/attachments/*.png`) & Inkscape Text-to-Path Conversion (`ATT-1406`)
* **Observation**: In `ATT-1406`, `Rule 71` mandated calling `read_file` on generated output images, which the subagents did—but they did not call `read_file` on the ticket's reference attachment (`docs/attachments/att_1406_cervelo_tiers_board.png`) and hand-coded stick-polygon letters for the supporter ribbons instead of converting true font glyphs via `inkscape --export-text-to-path`. Additionally, in `ATT-1482`, using `logo.fig`'s raw numeric bounding box center (`cx=6923, cy=920`) shifted the visual weight down-right so the runner's front leg sat right against the circular mask edge until re-centered to `cx=7080, cy=1280`.
* **Countermeasure (`Rule 72` — `docs/design_guidelines.md` Sec 5.16, ` docs/engineering/rule_history_archive.md`)**:
  * Whenever a ticket references visual attachments in `docs/attachments/`, Stage 1 (`[Analysis]`), Stage 4 (`[Implementation]`), and Gate 5 (`[Test]`) **MUST** call `read_file` on the reference attachment(s) alongside the generated output to verify 1:1 visual fidelity.
  * Never hand-code geometric stick-letter polygons for vector text badges; convert true font glyphs (`DejaVu Sans Bold`) via `inkscape --export-text-to-path --export-plain-svg` into absolute `M/L/C/Z` `VectorDrawable` paths.

### Topic 2: Adaptive Icon (`108dp`) Safe-Zone Margin Compensation in In-App Compose Slots & Clean Navigation Drawer Header (`ATT-5371`)
* **Observation**: Android Adaptive Icon foregrounds (`ic_launcher_foreground_*.xml`) have a `108dp × 108dp` canvas where the visible artwork occupies only `~55%` (`59.27 / 108`) inside the central safe zone. Rendering `activeVariant.foregroundRes` unscaled inside `DrawerHeader`'s `Modifier.size(46.dp)` slot shrank the icon to `~25dp` compared to full-bleed `R.drawable.logo_512`. Furthermore, adding a `Surface` text pill underneath `"a Training Tracker"` in `DrawerHeader` wrapped across 2 lines in German (`◆ DIAMOND-SPONSOR • \n Unbegrenzte Sensoren`), obscured the `menu_header_background` landscape photo, and clipped at the bottom of the `92.dp` header.
* **Countermeasure (`Rule 72` — `docs/design_guidelines.md` Sec 5.16)**:
  * Preserve the original single-line `DrawerHeader` (`46.dp` icon + `10.dp` spacer + `"a Training Tracker"` title over `menu_header_background`, with zero text pills underneath).
  * Whenever rendering a `108dp` adaptive icon foreground (`ic_launcher_foreground_*.xml`) in an in-app UI slot expecting full-bleed `R.drawable.logo_512` sizing, compensate for the adaptive safe-zone margin (`graphicsLayer { scaleX = 1.81f; scaleY = 1.81f }` inside `Box(Modifier.size(46.dp))`).
