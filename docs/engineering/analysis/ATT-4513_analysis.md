# Stage 1 Analysis: ATT-4513 - [Verbesserung] Use a lighter checked track color for subordinate 'Show in Workout Summary' switches in Light Mode

**Ticket**: [ATT-4513](https://rainerblind.atlassian.net/browse/ATT-4513)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4513`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Statement & Motivation

In `ATT-4464`, `SubordinateTuningToggleItem` in `TuningFormControls.kt` was downscaled to `Modifier.scale(0.6f)` and styled with `checkedTrackColor = MaterialTheme.colorScheme.secondary` and `checkedThumbColor = MaterialTheme.colorScheme.onSecondary` to establish subordinate visual hierarchy under master model toggles (`TuningToggleItem`).

However, in Light Mode, `MaterialTheme.colorScheme.secondary` resolves to `LightSecondary = Color(0xFF0000CD)` (a deep, dark navy blue), whereas `primary` is `LightPrimary = Color(0xFF1464F4)`. Consequently, the checked track on the subordinate switch appears significantly darker, higher-contrast, and visually heavier than the primary switch directly above it, contradicting Rule 59 and `docs/design_guidelines.md` Section 3.3.

---

## 2. Root Cause Analysis & Call Site Audit

### 2.1 Theme Token Asymmetry
In `Color.kt` and `Theme.kt`, `LightSecondary` is `#0000CD` (relative luminance ~0.07), while `LightPrimary` is `#1464F4` (relative luminance ~0.16). Using `MaterialTheme.colorScheme.secondary` inadvertently created a higher visual weight in Light Mode than the master toggle's `primary` color. In Dark Mode, `secondary` is `DarkSecondary` (`#C2C5DD`), but in Light Mode it is aggressively dark.

### 2.2 Call Site & Blast Radius Audit

| Component | File | Current Behavior & Required Adaptation |
| :--- | :--- | :--- |
| **Form Controls** | `TuningFormControls.kt` | Update `SubordinateTuningToggleItem` `SwitchDefaults.colors`: set `checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)` and `checkedThumbColor = MaterialTheme.colorScheme.primary`. |
| **Model Accordion** | `PhysiologyModelsSection.kt` | Consumes `SubordinateTuningToggleItem` at 3 sites (lines 91, 118, 135); signature and behavior preserved with zero changes. |
| **Contract Tests** | `TuningFormControlsTest.kt` | Update test assertion checking `checkedTrackColor` / `checkedThumbColor` tokens. |
| **Contract Tests** | `PhysiologyModelsVisibilityContractTest.kt` | Update line 324 asserting subordinate switch track color token. |
| **Living Documentation** | `docs/requirements.md`, `docs/tests.md`, `docs/design_guidelines.md` | Update `REQ-UI-424.1`, `TST-UI-388.3`, and Section 3.3 to reflect lighter checked track token. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Update `SubordinateTuningToggleItem` in `TuningFormControls.kt` to render a noticeably lighter checked track in Light Mode via `checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)` with contrasting `checkedThumbColor = MaterialTheme.colorScheme.primary`.
  2. Maintain dark mode legibility, compact dimensions (`scale(0.6f)`, `height(24.dp)`), and typography (`bodySmall`, `onSurfaceVariant`).
  3. Update unit tests in `TuningFormControlsTest.kt` and `PhysiologyModelsVisibilityContractTest.kt`.
  4. Update `REQ-UI-424.1`, `TST-UI-388.3`, and `docs/design_guidelines.md` Section 3.3.
* **Out-of-Scope Non-Goals**:
  1. No changes to parent `TuningToggleItem` or other settings toggles.
  2. No changes to DataStore persistence keys, defaults, or view models.
  3. No changes to `WorkoutDetails.kt` summary gating or live cockpit tiles.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Refines `REQ-UI-424.1` (*Show in Workout Summary Toggles for Physiology Models in Advanced Settings, Rule 59 Subordinate Toggle Hierarchy*).
* **Historical Origin & Commit Trace**: `ATT-4369` (`157684c0`, summary toggles added), `ATT-4432` (`e7b34e7e`, `SubordinateTuningToggleItem` created), and `ATT-4464` (`0deaf91a`, downscaled to 0.6f with `secondary` colors).
* **Root Reason for Existing Formulation**: `ATT-4464` intended to soften the subordinate switch using semantic `secondary` tokens without anticipating that `LightSecondary` was `#0000CD`, creating an overly dark track in Light Mode.
* **Preservation of Core Invariants**: Component signature, 0.6f scale, 24.dp height, modularity limits (< 400 lines in `TuningFormControls.kt`), and 100% test pass rate are strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

Use `SwitchDefaults.colors()` with dynamic primary alpha:
```kotlin
colors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.primary,
    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
)
```
* **Light Mode**: Renders a soft pastel blue track (`#95B9FA` on white) with solid primary blue thumb (`#1464F4`), achieving distinctly lighter visual weight than the master switch.
* **Dark Mode**: Renders a muted cyan-blue track (`#55647F` on dark surface) with bright sky-blue thumb (`#A6C8FF`), preserving clear legibility.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero functional regression in summary toggle preference dispatching or persistence.
  2. File line count in `TuningFormControls.kt` remains strictly `< 400` lines (currently 280 lines).
  3. Zero breaking API changes to `SubordinateTuningToggleItem` parameter list.
* **Risk Rating**: **LOW** — Self-contained Compose color styling change affecting only subordinate switch visual tokens, verified by automated contract tests and screenshot inspection; zero database, lifecycle, or concurrency risk.
