# Stage 2: Requirement & Test Specification - ATT-1733: [Lieblingsorte] Make Starts and Strecken badges on KnownLocationCard much more subtle

**Ticket**: [ATT-1733](https://rainerblind.atlassian.net/browse/ATT-1733)  
**Sub-task**: [ATT-1786](https://rainerblind.atlassian.net/browse/ATT-1786) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1396](https://rainerblind.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Requirement Mapping**: `REQ-UI-207` (*Lieblingsorte: Subtle Low-Contrast Ghost Badges for Starts and Strecken on KnownLocationCard*)  
**Test Spec ID**: `TST-UI-161`  
**Branch**: `feature/ATT-1733`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-207)

### 1.1 Problem Statement & Rationale
In Sprint Review 2026-40.5 (ATT-1643), the athlete user commended the dedicated row placement for Starts and Strecken (Routes) badges, but noted that their current styling (`primaryContainer.copy(alpha = 0.5f)` background, bold primary blue text, and saturated icons) is far too visually aggressive and dominant in a list of favorite locations (`screenshot_known_locations.png`). Rather than presenting secondary drill-down metadata, the badges visually overpower the card title and primary elevation metric. This requirement establishes understated, low-contrast ghost badge styling with refined typography and subtle affordances.

### 1.2 Functional & Architectural Requirements

1. **Subtle Ghost Container Styling (`KnownLocationsScreen.kt`)**:
   - Both Starts-Badge and Strecken-Badge (`Surface`) on `KnownLocationCard` SHALL utilize understated ghost container styling.
   - The container background color SHALL use `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)`, completely replacing the saturated `primaryContainer.copy(alpha = 0.5f)` pill background.
   - The container SHALL incorporate a subtle boundary stroke `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))` to gently ground the touch target on surface cards without creating harsh outlines.
   - Corner radius SHALL remain `RoundedCornerShape(8.dp)` with compact internal content padding `horizontal = 8.dp, vertical = 4.dp`.

2. **Understated Content & Typography Hierarchy (`KnownLocationsScreen.kt`)**:
   - The container `contentColor`, text color, and leading icon tint SHALL use `MaterialTheme.colorScheme.onSurfaceVariant`.
   - Typography SHALL use `MaterialTheme.typography.labelMedium` (12sp) with `FontWeight.Medium`, eliminating the heavy `labelLarge` (14sp) bold typography.
   - Leading icons (`Icons.Default.Place`, `ic_favorite_route`) SHALL be scaled to `13.dp` and tinted with `MaterialTheme.colorScheme.onSurfaceVariant`.
   - The trailing drill-down chevron (`Icons.AutoMirrored.Filled.ArrowForward`) SHALL be scaled to `11.dp` and tinted with `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)`, subtly indicating drill-down capability without drawing unwarranted attention.

3. **Preservation of Layout Hierarchy & Invariants**:
   - The dedicated row placement (`FlowRow`) beneath the altitude metric (`REQ-UI-195`) SHALL remain strictly intact.
   - The altitude metric (`ic_ascent` + elevation text) SHALL remain the sole primary-accented metric on the card.
   - Interactive clickability (`onShowWorkouts` navigating to filtered workouts, `onShowRoutes` navigating to filtered routes) SHALL remain 100% operational.
   - Test tags `location_starts_badge_${item.id}` and `location_routes_badge_${item.id}` SHALL be preserved on the respective `Surface` components.
   - The universal top-left long-press delete context menu (`REQ-UI-061`) SHALL remain intact.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-195` (item 2: *Refined Compact Badge Styling*), targeting `KnownLocationCard` in `KnownLocationsScreen.kt`.
* **Historical Origin & Commit Trace**: Introduced in sprint `2026-40.5` (`ATT-1643`) via commit `073df267`.
* **Root Reason for Existing Formulation**: `ATT-1643` resolved the multi-line wrapping defect by decoupling badges into a dedicated row, but carried forward legacy saturated primary container colors from `ATT-1401` and `ATT-1594`. In a repetitive multi-card list, this preserved color scheme created unintended visual dominance.
* **Preservation of Core Invariants**: Dedicated row progression, touch target accessibility, drill-down click navigation (`onShowWorkouts`, `onShowRoutes`), test tags (`location_starts_badge_${item.id}`, `location_routes_badge_${item.id}`), universal delete context menu (`REQ-UI-061`), and 9-language localization parity are 100% strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Subtle Ghost Container)**:
  * *Given* an athlete viewing the "Lieblingsorte" management list (`KnownLocationsScreen`),
  * *When* inspecting `KnownLocationCard`,
  * *Then* the Starts-Badge and Strecken-Badge SHALL render with a soft ghost background `surfaceVariant.copy(alpha = 0.35f)` and subtle boundary stroke `outlineVariant.copy(alpha = 0.25f)`, and SHALL NOT display the saturated `primaryContainer` blue pill.

* **Criterion 2 (Understated Content Hierarchy)**:
  * *Given* `KnownLocationCard` rendered in Light or Dark theme,
  * *When* viewing badge text and icons,
  * *Then* the text and leading icons SHALL be rendered in `onSurfaceVariant` with `labelMedium` (12sp) `FontWeight.Medium`, and the trailing chevron SHALL be scaled to 11dp with `TTAlpha.Medium`.

* **Criterion 3 (Interactive Navigation Parity)**:
  * *Given* an athlete viewing `KnownLocationCard`,
  * *When* tapping the Starts badge,
  * *Then* the app SHALL invoke `onShowWorkouts` navigating to filtered workouts starting at that location.
  * *When* tapping the Strecken badge,
  * *Then* the app SHALL invoke `onShowRoutes` navigating to filtered routes linked to that location.

* **Criterion 4 (Preservation of Test Tags & Layout Structure)**:
  * *Given* automated test suites executing layout contract checks,
  * *When* scanning the AST of `KnownLocationsScreen.kt`,
  * *Then* test tags `location_starts_badge_${item.id}` and `location_routes_badge_${item.id}` SHALL be present, and the altitude metric SHALL precede the dedicated badges row.

---

## 2. Test Specification (TST-UI-161)

### Test Case 1: Badge Styling & Low-Contrast Tokens Contract (`TST-UI-161.1`)
* **Scope**: Unit / Static Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationCardLayoutTest.kt`
* **Assertion**:
  - `content.contains("color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)")` or subtle ghost color.
  - `content.contains("contentColor = MaterialTheme.colorScheme.onSurfaceVariant")`.
  - `content.contains("style = MaterialTheme.typography.labelMedium")`.
  - `content.contains("fontWeight = FontWeight.Medium")`.
  - `content.contains("border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))")`.
  - `!content.contains("primaryContainer.copy(alpha = 0.5f)")`.

### Test Case 2: Layout Invariants & Test Tag Preservation (`TST-UI-161.2`)
* **Scope**: Unit / Static Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationCardLayoutTest.kt`
* **Assertion**:
  - Altitude metric precedes the dedicated badges row.
  - `location_starts_badge_\${item.id}` is preserved.
  - `location_routes_badge_\${item.id}` is preserved.

### Test Case 3: 9-Language Localization Plurals Audit (`TST-UI-161.3`)
* **Scope**: Localization Parity Test
* **Assertion**:
  - `R.plurals.known_locations_starts` and `R.plurals.known_locations_routes` exist across all 9 application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with identical format specifiers (`%d`).

### Test Case 4: Clean-Room Full Suite Regression Execution (`TST-UI-161.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Assertion**: 100% pass rate with zero test failures across all test modules.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Verification | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-161.1` | Unit | Ghost badge styling & low-contrast tokens | `REQ-UI-207.1`, `REQ-UI-207.2` | Specified |
| `TST-UI-161.2` | Unit | Layout structure & test tag preservation | `REQ-UI-207.3`, `REQ-UI-195` | Specified |
| `TST-UI-161.3` | Localization | Plurals translation parity across 9 locales | `REQ-UI-207.3`, `REQ-UI-106` | Specified |
| `TST-UI-161.4` | Regression | Full-suite clean-room unit test execution | `REQ-PRO-001` | Specified |
