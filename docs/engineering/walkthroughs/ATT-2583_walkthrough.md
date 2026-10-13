# Stage 5 Walkthrough: ATT-2583

## 1. Ticket & Scope Traceability
- **Parent Ticket**: [ATT-2583](https://atrainingtracker.atlassian.net/browse/ATT-2583) — *[Verbesserung] Match and List Starred Segments along Routes in Route Details*
- **Subtask**: [ATT-2769](https://atrainingtracker.atlassian.net/browse/ATT-2769) (`[Verification]`)
- **Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)
- **Sprint**: `2026-41.3`
- **Feature Branch**: `feature/ATT-2583`
- **Requirement**: `REQ-UI-302`
- **Test Specification**: `TST-UI-262`

---

## 2. Feature & Architecture Overview
This enhancement matches saved and starred Strava/local segments along planned routes and surfaces them in a dedicated route breakdown section beneath the elevation profile, giving athletes pre-ride tactical insight into sprint and climb segments.

### Key Capabilities Delivered:
1. **`RouteSegmentMatcher.kt` Geodesic Overlap Engine**:
   - Evaluates candidate segments on `Dispatchers.Default` using pure spherical trigonometry (`GeoUtils`).
   - Rejects sport mismatches (e.g. RUN vs. BIKE) while preserving UNKNOWN sport interoperability.
   - Enforces 25 m corridor proximity for segment start and finish points.
   - Enforces travel bearing vector alignment ($\Delta \theta \le 45^\circ$, forward dot product $> 0$) to eliminate reverse and perpendicular crossing segments.
   - Enforces forward route progression ($D_{\text{end}} > D_{\text{start}}$) and path length consistency ($|(D_{\text{end}} - D_{\text{start}}) - \text{segment.distance}| \le \max(100.0, \text{segment.distance} \times 0.25)$).
   - Validates intermediate midpoints ($\le 50\text{ m}$) for multi-point segments to reject horseshoe divergences.
   - Sorts matched segments ascending by start distance along the route.

2. **`RouteSegmentsBreakdownSection.kt` Composable**:
   - ElevatedCard presentation styled consistently with Material 3 design and `RouteClimbsBreakdownSection`.
   - Displays sport icon, segment name, UCI climb category badge (`ClimbCategoryChip`), and athlete PR badge (`routes_segment_pr`).
   - Displays metrics row: route start marker (`routes_segment_start_at`), segment length, and average gradient.

3. **`RouteOnMapScreen.kt` & `RoutesScreen.kt` Integration**:
   - Asynchronous matching via `produceState` preventing UI thread stalls.
   - Coexistence synergy: Material 3 `FilterChip` toggle row (`[ Anstiege (N) ]  [ Segmente (M) ]`) when both climbs and segments exist on a route, with selection state preserved across screen rotation via `rememberSaveable`.
   - Direct fallback when only climbs or only segments exist.
   - Interactive synergy: Tapping a segment card highlights its polyline on the upper map and sets `externalScrubDistance` in `MapDetailLayout` to scrub the elevation profile directly to the segment's route starting position.

---

## 3. Localization Parity (100% across 9 Locales)
All segment breakdown string resources externalized across all 9 application locales without XML entity regressions:
- `routes_segments_section_title`: `%d Segmente` (DE) / `Segments (%d)` (EN) / `%d segmentos` (ES, PT) / `%d segments` (FR) / `%d segmenti` (IT) / `%dセグメント` (JA) / `%d segmenten` (NL) / `%d segmentów` (PL)
- `routes_segments_tab_title`: `Segmente (%d)` (DE) / `Segments (%d)` (EN) / `Segmentos (%d)` (ES, PT) / `Segments (%d)` (FR) / `Segmenti (%d)` (IT) / `セグメント (%d)` (JA) / `Segmenten (%d)` (NL) / `Segmenty (%d)` (PL)
- `routes_segment_start_at`: `Start bei %s` (DE) / `Start at %s` (EN) / `Inicio en %s` (ES) / `Départ à %s` (FR) / `Inizio a %s` (IT) / `開始地点 %s` (JA) / `Start bij %s` (NL) / `Start na %s` (PL) / `Início em %s` (PT)
- `routes_segment_pr`: `PR %s`

---

## 4. Verification & Clean-Room Regression Evidence
1. **Targeted Unit & Architectural Tests**:
   - `RouteSegmentMatcherTest.kt`: 7 exhaustive unit test scenarios verifying forward alignment, reverse rejection, perpendicular crossing rejection, sport type rejection, diverging terminal rejection, horseshoe midpoint divergence rejection, and ascending sort order.
   - `RouteSegmentsBreakdownContractTest.kt`: Verified composable signatures, parameter declarations, ElevatedCard structure, PR badge rendering, `RouteBreakdownTab` enum, `rememberSaveable` state persistence, and `FilterChip` toggle logic.
   - `RouteSegmentsLocalizationTest.kt`: 100% 9-language XML parity verification across all 9 `strings.xml` files.
2. **Full Clean-Room Regression Suite**:
   - Command: `./gradlew testDebugUnitTest`
   - Result: 100% pass rate across entire application test suite with 0 regressions.
