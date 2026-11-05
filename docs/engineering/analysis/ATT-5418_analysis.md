# Stage 1 Analysis: ATT-5418 - Online User Guide 2-Spalten-Layout, Du-Anrede & Factual Corrections

**Ticket**: [[ATT-5418]](https://rainerblind.atlassian.net/browse/ATT-5418)  
**Sub-task**: [[ATT-5462]](https://rainerblind.atlassian.net/browse/ATT-5462) (`[Analysis]`)  
**Parent Epic**: [[ATT-2273]](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5418`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-05  

---

## 1. Problem Statement & Motivation
Online User Guide (`ATT-2275`) in `docs/guide/` (Ch. 1–9, `de` & `en`) suffers from 3 defects:
1. **Layout**: Full-width 9:20 portrait screenshots (~1,400 px height) severely disconnect text and visuals.
2. **Screenshots**: 12 images in `docs/guide/img/` are mismatched, broken, private, or developer boards (e.g. Auto-Pause instead of Bikes/Shoes, internal icon board `ATT-5201`, 4.2 MB car ride `Ins Erzgebirge`, private file manager with `FRITZ!NAS`, status-bar overlap, scrolled-off activity grid).
3. **Factual Errors**: Texts describe non-existent features (`ACTIVITY_RECOGNITION`, `1x/2x/3x` tile widths, TTS voice cues, OSM offline vector maps, bike tare weight on gear, TrainingPeaks upload, generic green grid) while German text uses formal "Sie".

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **RCA**: Initial generation in `ATT-2275` lacked responsive layout constraints (`<table>` side-by-side) and visual inspection (`read_file`), accepting mock/placeholder screenshots and AI hallucinated feature descriptions.
- **Independent Hypothesis Verification**: Verified all 24 PNGs in `docs/guide/img/` via `read_file` (Rule 71). Confirmed:
  - `02_sensor_pairing.png`: Shows crash recovery dialog (`Training unterbrochen`).
  - `02_dual_protocol.png`: Shows `ATT-5201` developer icon design board.
  - `03_equipment_bikes.png` & `03_equipment_shoes.png`: Show Auto-Pause settings for Bike & Run.
  - `04_cockpit_tuning.png`: Shows `Tipps & Tricks` screen, not cockpit settings.
  - `05_gpx_routes.png`: Shows Start tab instead of route selection/elevation profile.
  - `06_cluster_expanded.png`: 4.2 MB file showing a 590 km car ride.
  - `07_*.png`: All 3 show single workout aftermath instead of `Profil & Zonen` / PMC.
  - `08_periods_cards.png`: Status bar `04:08` overlaps `Dauer` tab header.
  - `08_yearly_activity_grid.png`: Contribution grid scrolled past and completely invisible.
  - `09_fit_export.png`: System file manager showing private `FRITZ!NAS` icons.
  - `OnlineUserGuideContractTest.kt`: Regex `!\[.*?\]\((.*?)\)` fails on HTML `<img>` tags; test must be extended.

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope**:
  - Convert Chapters 1–9 (`de` and `en`) to 2-column layout (`width="65%"` text, `width="35%"` screenshot with `width="240"`, clickable `<a>` wrapper).
  - Adopt uniform "Du"-Anrede across `docs/guide/de/` (`index.md`, `01`–`09.md`).
  - Replace/correct all 12 invalid/misplaced images in `docs/guide/img/` (and optimize 4.2 MB cluster asset).
  - Correct all factual inaccuracies against actual code across Chapters 1–9.
  - Extend `OnlineUserGuideContractTest.kt` to support `<img>` tags and verify anchor/file parity.
- **Out-of-Scope**: Runtime app changes, Jekyll theme overhaul, translation to 7 other languages.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- **Original Requirement & Target**: Amends `REQ-PRO-033` (*Comprehensive Online User Guide on GitHub Pages*, Sprint `2026-45.4`, `ATT-2275`).
- **Historical Origin & Commit Trace**: Created via commit `1fbb551625` (`ATT-2275`).
- **Root Reason for Existing Formulation**: Established initial manual structure and build contract.
- **Preservation of Core Invariants**: 18-file chapter parity, Jekyll exclusion hygiene, and anchor parity (`<a id="...">`) for in-app deep-linking remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution
- **Markdown Layout Pattern**: Standardized Jekyll- and GitHub-compatible HTML table:
  `<td width="65%" valign="top" markdown="1">` (left) and `<td width="35%" valign="top" align="center">` (right, with `<a href="../img/..."><img src="../img/..." width="240" .../></a><br/><sub><em>...</em></sub>`).
- **Asset Pipeline**: Capture clean authentic screenshots from connected Pixel device (`66020DLCR002FL`) / existing verified walkthrough assets for missing screens.

---

## 6. System Invariants & Risk Assessment
- **Core Invariants**:
  1. `OnlineUserGuideContractTest` passes with 100% coverage across all 18 markdown files.
  2. Anchor parity `<a id="...">` preserved between DE and EN across all sections.
  3. Zero production APK code changes (`app/src/main/...` untouched).
- **Risk Rating**: **LOW** — Pure documentation, image asset, and build test contract refinement.
