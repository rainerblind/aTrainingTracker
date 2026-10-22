# Stage 1 Analysis: ATT-4026 - Move hardcoded user-visible strings, units and content descriptions to resources

**Ticket**: [ATT-4026](https://rainerblind.atlassian.net/browse/ATT-4026)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4026`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22

---

## 1. Problem Statement & Root Cause Analysis (RCA)
- **Problem**: UI contains hardcoded strings, German labels, English `contentDescription` literals, missing icon accessibility, and hardcoded metric unit assumptions (`km`, `km/h`, `m`) that ignore `TrainingApplication.getUnit() == MyUnits.IMPERIAL`.
- **Root Cause**: Fast iterative Compose prototyping left ad-hoc string literals, unlocalized labels in `CropWorkoutScreen` ("Distanz:", "Dauer:", "Ø Geschwindigkeit:"), missing unit conversion branches in `WorkoutFilterBottomSheet`, and unhandled TalkBack semantics across icon buttons and canvas charts.
- **Impact**: Mixed-language display across the 9 supported locales (DE, EN, ES, FR, IT, JA, NL, PL, PT), TalkBack accessibility barriers, and incorrect imperial workout filtering (miles treated as km).

---

## 2. Call Site Audit & Forensic Verification
| Category | File & Location | Current Finding / Status | Proposed Remediation |
|---|---|---|---|
| **Text Literals** | `ImportBackupTabsScreen.kt:999,1008,1016,1041,1049,1060` | "Map Data for Import", "Sport Types", "Equipment", "None", "Unknown" | Extract to `R.string.*` with 9-locale parity |
| **German Labels & Metric Units** | `CropWorkoutScreen.kt:231,236,272,276,286,300,304` | "Distanz:", "Dauer:", "Ø Geschwindigkeit:", "Start: +", "End: -", hardcoded "km", "km/h" | Localize labels; apply unit-aware formatting (`DistanceFormatter`, `SpeedFormatter`) |
| **Filter Unit Assumptions** | `WorkoutFilterBottomSheet.kt:583-633,654-704,725-776` | Distance/duration/elevation chips & text fields hardcode km/m/min and `* 1000.0` | Support Imperial units (`MyUnits.IMPERIAL` -> mi / ft) and localize chip labels |
| **Toasts & Fallback Strings** | `MainActivityWithNavigation.kt:790,977,990` | "setting a new content destination not yet implemented", fallback `"Sensor"` | Extract to `R.string.destination_not_yet_implemented`, `R.string.sensor` |
| **Toasts (Already Fixed)** | `GpxImportActivity.kt:156` | "Route Imported!" | **Resolved** in ATT-4049 (`R.string.route_saved_success`) |
| **Unit Suffixes** | `ZoneCalculatorDialog.kt:253,300,325` | Hardcoded `"W"`, `"bpm"` text | Use `R.string.unit_watt` and `R.string.units_heart_rate` |
| **Lap Template** | `LapEditBottomSheet.kt:482` | Hardcoded template `"Lap $lapDisplayIndex"` | Extract `R.string.lap_placeholder_format` (`%1$d`) |
| **Period Empty State** | `PeriodSummaryCard.kt:539` | Hardcoded `"No GPS data for this period"` | Extract `R.string.period_no_gps_data` |
| **Marker Titles** | `TrackOnMapScreen.kt:272,386`, `ClimbOnMapScreen.kt:117,127`, `RouteOnMapScreen.kt:310,318` | Hardcoded "Lap Start", "Start", "End", "Summit" | Use `R.string.marker_*` resources |
| **English ContentDesc** | `LapSplitVisualizer.kt:154`, `WorkoutListActions.kt:86`, `ConnectWithStravaButton.kt:48`, `ElevationProfile.kt:976`, `SensorGridScreen.kt:1342,1353`, `TrackingTabConfigHeader.kt:116-133` | 13+ hardcoded English `contentDescription` literals | Extract to string resources with 9-locale translations |
| **Accessible Icon Buttons** | `ClusterTuningScreen.kt:71`, `ManualClusterScreen.kt:98`, `WorkoutClustersScreen.kt:192`, `WorkoutClusterHeatmapScreen.kt:656`, `LapEditBottomSheet.kt:489`, `ImportBackupTabsScreen.kt:930` | `IconButton` with `contentDescription = null` lacking TalkBack description | Add localized action descriptions (`back`, `close`, `clear`, `select_route`) |
| **Dead Debug Code** | `TrainingApplication.java:1598` | `todo(Context, String)` helper | Zero callers in codebase; mark deprecated or prune |

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. Extract identified hardcoded user-visible strings, German labels, marker titles, toasts, and content descriptions to `res/values/strings.xml` and maintain parity across all 9 locales (DE, ES, FR, IT, JA, NL, PL, PT) verified by `TranslationParityTest`.
  2. Implement unit-aware formatting in `CropWorkoutScreen` and imperial/metric unit conversion in `WorkoutFilterBottomSheet` based on `TrainingApplication.getUnit()`.
  3. Supply accessible content descriptions to interactive `IconButton` elements lacking labels and semantic descriptions for charts.
- **Out-of-Scope Non-Goals**:
  1. Full redesign or architectural rewrite of `WorkoutFilterBottomSheet` or `CropWorkoutScreen`.
  2. Modifying SQLite schema, data models, or network APIs.
  3. Replacing existing decorative `contentDescription = null` icons that reside inside already-labeled parent buttons.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Requirement ID & Target**: Net-new requirement `REQ-UI-429` (*Global User Interface Localization Parity, Unit Adaptability, and Accessibility Semantics*), extending `REQ-LOC-001` (*Translation Parity*), `REQ-UI-122` (*Positional Placeholder Parity*), and `REQ-UI-157` (*Workout Filter Criteria*).
- **Historical Origin & Commit Trace**: Discovered during whole-app architecture review (branch `feature/ATT-3921` @ `0234bfc0`). Review flagged UI literal debt accumulated during high-speed Compose migrations.
- **Root Reason for Existing Formulation**: Literals were added as immediate placeholders during rapid feature delivery. Metric conversions in filter chips assumed standard SI units without evaluating user imperial preference.
- **Preservation of Core Invariants**: Existing string keys, fallback mechanisms, database queries, and metric/imperial calculation accuracy remain strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Resource Extraction & Localization Parity**:
   - Add new string keys to `res/values/strings.xml` and all 8 localized variants (`values-{de,es,fr,it,ja,nl,pl,pt}/strings.xml`) adhering to `REQ-LOC-001` and `REQ-UI-122` positional argument rules (`%1$s`).
   - Run `TranslationParityTest` (`TST-STR-018`) to ensure 100% parity and zero missing keys or mismatched argument specifiers.
2. **Unit Adaptability in Compose Screens**:
   - Query `TrainingApplication.getUnit() == MyUnits.IMPERIAL`.
   - In `CropWorkoutScreen`, format distance and speed using `DistanceFormatter` and `SpeedFormatter` (or `LocalMetricFormatter.current`).
   - In `WorkoutFilterBottomSheet`, adapt distance presets (mi vs. km), elevation presets (ft vs. m), text field labels, and parsing math (`meters = miles * BANALService.METER_PER_MILE`).
3. **Accessibility Hardening**:
   - Assign localized `contentDescription` to action `IconButton` instances.
   - Add TalkBack semantics (`Modifier.semantics { contentDescription = ... }`) to interactive Canvas charts.

---

## 6. System Invariants & Risk Assessment
- **Core Invariants**:
  1. 100% translation parity across all 9 locales without broken format specifiers (`TranslationParityTest` PASS).
  2. Stored metric values in database (meters, seconds, mps) remain untouched; conversions occur strictly at the UI boundary.
  3. Parent ticket Human Decision Gate strictly enforced; zero regressions in existing unit test suites.
- **Risk Rating**: **LOW** — Clean presentation-layer refactoring and string resource extraction with established unit formatters and automated parity test verification.
