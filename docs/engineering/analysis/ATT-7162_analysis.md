# Stage 1 Analysis: ATT-7162 - Configurable FIETS Index & Strava/Garmin Climb Categorization

**Ticket**: [ATT-7162](https://rainerblind.atlassian.net/browse/ATT-7162)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3`  
**Branch**: `feature/ATT-7162` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Currently, climb categorization in `Climb.kt:28-45` and `ClimbDetector.kt:191-192` uses only the Strava/Garmin formula:
$\text{Score} = \text{distance} \times \text{avgGrade\%} = D \times (H / D \times 100) = 100 \times H$.
Because distance cancels out completely, steepness has **zero influence** on climb classification: a brutal 1.3 km @ 9.6% wall (e.g. Mur de Huy) or a steep 15% ramp receives the exact same score as a gentle 3% false-flat with the same vertical gain. The **FIETS Index** ($\frac{H^2}{10 \times D} + \max(0, \frac{\text{summitAlt} - 1000}{1000})$) correctly balances vertical gain, gradient, and high-altitude difficulty. Making FIETS the default while offering Strava/Garmin as an advanced option resolves this disparity while preserving Garmin/Strava parity for athletes who prefer it.

---

## 2. Root Cause Analysis & Code Grounding
* **RCA**:
  * `ClimbCategory.fromScore()` only implements Strava score thresholds (`80000 / 64000 / 32000 / 16000 / 8000`).
  * `ClimbDetector.kt:191-192` bakes in `fromScore(dist * avgGrade)` during detection.
  * `Climb` holds a static `category` field without formula-awareness or dynamic re-evaluation.
  * `TuningPreferencesDataStore` and `NavigationSection.kt` lack a climb scoring formula setting and selector.
* **Code Audit**:
  * Audited `Climb.kt` (lines 28–52, 81–95): Confirmed `ClimbCategory` companion only offers `fromScore` and `fromCode`.
  * Audited `ClimbDetector.kt` (lines 186–212): Confirmed `createClimb` computes `score = dist * avgGrade`.
  * Audited `TuningPreferencesDataStore.kt`: Climb keys exist (`showLiveClimbs`, `climbMinLengthMeters`, `climbMinGradientPercent`), but no formula enum or key is present.
  * Audited UI call sites (`LiveClimbSheet.kt:153`, `RouteOnMapScreen.kt:521`, `ElevationProfile.kt:837`, `ClimbOnMapScreen.kt:188`): All render `climb.category` directly via `ClimbCategoryChip` or category color maps.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Goals**:
  * Domain enum `ClimbScoringFormula { FIETS, STRAVA_GARMIN }` with default `FIETS`.
  * FIETS calculation `calculateFietsIndex()` & `ClimbCategory.fromFietsIndex()` with open thresholds (`HC >= 6.5`, `CAT_1 >= 5.0`, `CAT_2 >= 3.5`, `CAT_3 >= 2.0`, `CAT_4 >= 0.5`, `UNCATEGORIZED < 0.5`).
  * Dynamic `Climb.resolveCategory(formula: ClimbScoringFormula)` based on `distanceMeters`, `elevationGainMeters`, and summit altitude.
  * Persist `climbScoringFormula` in `TuningPreferencesDataStore` with reactive `StateFlow` and `resetToDefaults()`.
  * Expose `NavigationSection.kt` FilterChip selector with full 9-language localization parity (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  * Connect category resolution across `LiveClimbSheet`, `RouteOnMapScreen`, and `ElevationProfile`.
* **Out-of-Scope Non-Goals**:
  * Schema migrations or destructive data rebuilds of `Climbs.db` (dynamic resolution avoids re-imports).
  * Altering climb detection boundary algorithms or smoothing filters.
* **Complexity Audit**: 2 SP (Standard Minor, bounded to domain models, preferences, and Compose settings section; split not required).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID**: Refines `REQ-MAP-027` (*Climb Detection and Live ClimbPro*) and `REQ-UI-281` (*Navigation Tuning Category*).
* **Historical Origin**: `ATT-2565` originally adopted the Strava/Garmin formula for simplicity and parity.
* **Chesterton's Fence Invariant**: Strava/Garmin formula must remain available as a selectable option for athletes comparing ratings against Garmin Edge / Strava segments.

---

## 5. Architectural Strategy & High-Level Solution
1. **Domain Model (`Climb.kt`)**: Add `ClimbScoringFormula`, `fromFietsIndex()`, `calculateFietsIndex()`, and `Climb.resolveCategory()`.
2. **Settings DataStore (`TuningPreferencesDataStore.kt`)**: Add `climbScoringFormula` key, flow mapping, and reset logic.
3. **UI Selector (`NavigationSection.kt`)**: Add `FilterChip` pair under Navigation settings matching `AftermathAnalysisSection` pattern.
4. **Dynamic Presentation**: UI layers resolve category via active formula, enabling immediate reactive re-categorization without DB writes.

---

## 6. System Invariants & Risk Assessment
* **Invariants**: Zero DB schema changes; existing persisted climbs remain valid; 100% translation parity across all 9 languages; default formula is `FIETS`.
* **Risk Rating**: **LOW** (Purely algorithmic scoring refinement and additive UI setting; zero persistence risk).
