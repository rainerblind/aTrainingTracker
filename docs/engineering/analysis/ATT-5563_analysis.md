# Stage 1 Analysis: ATT-5563 - Steigungsverteilung: Sichtbarkeit, Nomenklatur & Schwellenwert-Labels

**Ticket**: [ATT-5563](https://rainerblind.atlassian.net/browse/ATT-5563)  
**Sub-task**: [ATT-5583](https://rainerblind.atlassian.net/browse/ATT-5583) (`[Analysis]`)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.8`  
**Branch**: `feature/ATT-5563`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
Following the Sprint Review of `ATT-5487`, three refinements were identified for the Slope / Grade distribution feature:
1. **Visibility Configuration**: Users require independent toggles in Expert Settings (`WorkoutMasksAndCardsSection`) to control `GradeZoneDistributionCard` display in *Workout Details* (default: enabled / `true`) and *Workout Summary* list cards (default: disabled / `false`) to avoid cluttering compact workout summaries.
2. **Terminology**: Slope is physical terrain rather than physiological exertion; terms must be changed from *Steigungszonen* / *Grade Zones* to *Steigungsverteilung* / *Slope Distribution* across all 9 supported locales.
3. **Category Labels**: Replace generic `Z1`..`Z7` badges with unambiguous mathematical threshold notation (`<2%`, `≥2%`, `≥5%`, `≥10%`, `≥15%`, `≥20%`, `≥25%`) in both Column Chart (`ZoneDistributionColumnChart`) and Stacked Bar (`ZoneDistributionStackedBar`), while preserving `Z1`..`Z5`/`Z7` for Heart Rate and Power.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Architectural Gap**:
  1. `WorkoutCardSectionPreferences` & `WorkoutDetailPreferences` lack a `showGradeDistribution` preference flag and DataStore keys.
  2. `WorkoutMasksAndCardsSection.kt` lacks a subordinate switch under Zone Analysis for both domains.
  3. `ZoneTimeEntry` hardcodes `"Z${entry.zoneIndex}"` rendering in `ZoneDistributionColumnChart.kt` (L134) and `ZoneDistributionStackedBar.kt` (L272) without support for domain-specific category strings.
* **Independent Peer Review & Verification (ATT-3534 / Rules 69 & 70)**:
  - Audited `WorkoutMasksAndCardsSection.kt`: File is currently at 383 lines. Adding the two subordinate toggle rows must remain concise to strictly respect the < 400 lines ceiling enforced by `WorkoutMasksAndCardsLayoutTest.testFileSizeConstraint_strictlyUnder400Lines` (`TST-UI-377.5`).
  - Audited label layout in `ZoneDistributionColumnChart.kt`: At 7 equal-width columns (~50dp on mobile), the longest label `≥25%` (4 chars, ~25dp text + 11dp swatch/spacer = ~36dp) fits cleanly within column bounds without text overflow or truncation.
  - Audited `TrackOnMapScreen.kt` (L215): Guarding `hasZonesData` with `|| (gradeZoneDistribution != null && activeDetailPrefs.showGradeDistribution)` ensures altitude workouts without HR/Power sensors do not display an empty Zonen tab when grade distribution is turned off.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `showGradeDistribution` to `WorkoutCardSectionPreferences` (default: `false`) and `WorkoutDetailPreferences` (default: `true`) in `MyPreferenceManager`.
  2. Add `SubordinateTuningToggleItem` in `WorkoutMasksAndCardsSection.kt` under Zone Analysis in both Summary and Details blocks.
  3. Guard `GradeZoneDistributionCard` rendering with `showGradeDistribution` in `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`, and `TrackOnMapScreen.kt`.
  4. Decouple badge text in `ZoneTimeEntry` via `customBadgeLabel: String?` with fallback `val badgeLabel: String get() = customBadgeLabel ?: "Z$zoneIndex"`, providing `<2%`, `≥2%`, `≥5%`, `≥10%`, `≥15%`, `≥20%`, `≥25%` for grade distribution.
  5. Update `aftermath_grade_zones_title` and add `settings_workout_card_grade_distribution` across all 9 locales.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No changes to HR (`Z1`..`Z5`) or Power (`Z1`..`Z7`) category labels or thresholds.
  2. No modifications to `ElevationProfile.kt` color coding or canvas layout.
  3. No SQLite database schema migrations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-453` (Grade / Slope Distribution Card & 1% Telemetry Histogram).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-45.7 via `ATT-5487` (commit `36e00b64ef28827de43f2f04399de442ca9a6898`).
* **Root Reason for Existing Formulation**: Reused existing generic `ZoneDistributionData` and `Z1`..`Z7` strings to achieve fast initial integration in ATT-5487 without adding preference schema overhead.
* **Preservation of Core Invariants**: Retains canonical 7-zone color palette (`TTColor.Zone1..Zone7`) matching `ElevationProfile.kt`, preserves HR/Power zone badges, and ensures DataStore backward compatibility via safe fallback defaults (`true` in Details, `false` in Summary).

---

## 5. Architectural Strategy & High-Level Solution
* **Preferences**: Add DataStore keys `WORKOUT_CARD_SHOW_GRADE_DISTRIBUTION` (default: `false`) and `WORKOUT_DETAIL_SHOW_GRADE_DISTRIBUTION` (default: `true`).
* **Settings UI**: Integrate `SubordinateTuningToggleItem` within `if (workoutCardPrefs.showZoneAnalysis)` and `if (workoutDetailPrefs.showZoneAnalysis)` in `WorkoutMasksAndCardsSection.kt`.
* **Category Badges**: Pass `GRADE_CATEGORY_LABELS` in `ZoneDistributionCalculator.calculateGradeDistribution`. Render `entry.badgeLabel` in `ZoneDistributionColumnChart` and `ZoneDistributionStackedBar`.
* **Localization**: Update `aftermath_grade_zones_title` to *Slope Distribution* / *Steigungsverteilung* and add setting title across all 9 language directories with 100% parity verified by `TranslationParityTest`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. HR and Power zone cards strictly retain `Z1`..`Z5` and `Z1`..`Z7` badges.
  2. `WorkoutMasksAndCardsSection.kt` line count strictly < 400 lines (`TST-UI-377.5`).
  3. Default visibility: `true` in Workout Details, `false` in Workout Summary list.
  4. 100% translation parity across all 9 supported locales.
* **Risk Rating**: **LOW** (Targeted preference properties, localized strings, and badge label decoupling; zero database or concurrency risk).
