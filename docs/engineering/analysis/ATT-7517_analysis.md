# Stage 1 Analysis: ATT-7517 - Equal-Width Toggle Tiles, Grouped Cloud Sync Card & Scope Bounding

**Ticket**: [ATT-7517](https://rainerblind.atlassian.net/browse/ATT-7517)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7517` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
In `EditWorkoutScreen.kt`, three checkbox controls (`Pendeln`, `Indoor Trainer`, `Wettkampf`) currently span two awkward rows (`2 + 1` layout), wasting ~52 dp of vertical space. Only the tiny 24x24dp checkbox squares are tappable, not the text labels. Bare checkboxes visually blur workout tags with external cloud sync options (Strava and Health Connect), where Health Connect incorrectly renders a generic blue `Icons.Default.Favorite` heart instead of the official `R.drawable.ic_health_connect` vector asset with non-clickable wrapping text.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Fragmented Tag Checkboxes & Restricted Tap Targets)**: In `EditWorkoutScreen.kt:290-313`, `commute`, `trainer`, and `race` use basic unweighted `Row`s and small `Checkbox` targets lacking full-tile surface styling and `Modifier.toggleable` semantics.
* **RCA 2 (Cloud Sync Container Conflation & Asset Mismatch)**: In `EditWorkoutScreen.kt:315-370`, Strava and Health Connect rows are loose rows without a grouping container. Health Connect uses `Icons.Default.Favorite` instead of brand `R.drawable.ic_health_connect`.
* **Independent Peer Review & Verification**:
  * Verified `EditWorkoutScreen.kt#L290-370`: Confirmed 2+1 layout, non-clickable labels, and uncontained cloud sync rows with generic heart icon.
  * Verified `EditWorkoutViewModel.kt#L389-399`: `updateIsCommute` and `updateIsTrainer` already enforce mutual exclusivity in StateFlow.
  * Verified Scope Bounding: Ticket description included RPE 1-10 row clipping in `WorkoutRpeFeelSection.kt`. However, per Ceremony 1 Q2 agreement, RPE 1-10 Borg CR10 slider + Feel 1-5 M3 vector cards are strictly deferred to ticket ATT-7515.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Replace 2 checkbox rows with 1 row of 3 equal-width toggle tiles (`Modifier.weight(1f)`, `RoundedCornerShape(8.dp)`, min height 42 dp) for Commute, Indoor, and Race (`Variant B`).
  2. Implement full-surface `Modifier.toggleable(role = Role.Checkbox)` with active/inactive container accents (Race active styled with `tertiaryContainer` / `tertiary` border matching `WorkoutHeader.kt`).
  3. Introduce `indoor_short` ("Indoor") across all 9 locales to prevent multi-line overflow.
  4. Group Strava and Health Connect into an outlined card container (`RoundedCornerShape(10.dp)`) with full-row toggleable targets, brand assets (`logo_square_strava`, `ic_health_connect`), and disabled alpha (`0.38f`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * RPE 1-10 slider & Feel 1-5 cards in `WorkoutRpeFeelSection.kt` (assigned to ATT-7515 per Ceremony 1 Q2).
  * SQLite database schema changes or background sync pipeline alterations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Refines `REQ-UI-239` (*Unconditional Edit Workout Form Presentation*) and supersedes `REQ-UI-497.3` (*Strava Checkbox Row Alignment*).
* **Historical Origin & Commit Trace**: `REQ-UI-239` in `ATT-2029` (Sprint 2026-40.12); `REQ-UI-497.3` in commit `5d17fba6` (`ATT-5838`, Sprint 2026-44.1).
* **Root Reason for Existing Formulation**: Stacked checkboxes were initial quick implementations; `REQ-UI-497.3` left-aligned Strava with tag checkboxes before a unified cloud sync container was introduced.
* **Preservation of Core Invariants**: Commute/Trainer/Race data fields and ViewModel exclusivity intact; Strava disabled state (`!stravaMappingAvailable`) with 0.38f alpha and Health Connect sync flow 100% preserved.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-UI-239`, supersedes `REQ-UI-497.3`, net-new `REQ-UI-505.1` & `REQ-UI-505.2`.
* **Affected Architecture Elements**: `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`, `res/values*/strings.xml` (9 locales).
* **Verification Impact & Test Surface**: `EditWorkoutFieldsLayoutTest.kt`, `EditWorkoutLayoutContractTest.kt`, `EditWorkoutViewModelTest.kt`, `TranslationParityTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Localized Compose UI presentation and string resources; zero database or background worker changes.

---

## 6. Architectural Strategy & High-Level Solution
1. **Toggle Tiles**: `Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp))` with 3 equal tiles using `Modifier.weight(1f).defaultMinSize(minHeight = 42.dp).toggleable(...)`. Icons: `Commute`/`DirectionsBike`, `Home`/`FitnessCenter`, `EmojiEvents`.
2. **Grouped Cloud Sync**: Subtle outlined `Surface` (shape 10.dp) hosting Strava and Health Connect rows with full-row toggleability, brand vector drawables, and trailing Checkboxes.
3. **Localization Parity**: Add `indoor_short` to all 9 `strings.xml` files.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Unconditional field presence (`REQ-UI-239`).
  2. ViewModel Commute/Indoor exclusivity preserved.
  3. Strava and Health Connect sync contracts preserved.
  4. 100% 9-language localization parity.
* **Risk Rating**: **LOW** (Strictly scoped UI modernization and ergonomic polish in `EditWorkoutScreen.kt`).
