# Stage 1 Analysis: ATT-6681 - DisplaySettingsDialog: Condense verbose 3-line display option subtitles into concise single-line descriptions

**Ticket**: [ATT-6681](https://rainerblind.atlassian.net/browse/ATT-6681)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6681`  
**Author**: AI Agent 1 (Implementer - Pro)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
In `ATT-6282`, descriptive subtitles (`forcePortraitSummary`, `prefsKeepScreenOnSummary`, `prefsNoUnlockingSummary`) were added to the top three switches in `DisplaySettingsDialog`.
On standard mobile viewports (e.g., Pixel 10), German and other localized strings wrap to 2–3 lines per switch with orphaned words (e.g., *"Querformat"*, *"nötig ist"*), rendering the modal bottom sheet excessively text-heavy and pushing the battery-saver brightness sliders off-screen into a scroll.
Additionally, `cockpit_theme_description` spans over 120 characters across 2–3 lines.

**Target**: Condense `forcePortraitSummary`, `prefsKeepScreenOnSummary`, `prefsNoUnlockingSummary`, and `cockpit_theme_description` across all 9 locales into crisp, single-line descriptions ($\le 55$ characters).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * **Call-Site Audit**:
    * `DisplaySettingsDialog.kt`: Uses `stringResource(R.string.forcePortraitSummary)`, `prefsKeepScreenOnSummary`, `prefsNoUnlockingSummary`, and `cockpit_theme_description`.
    * `values*/strings.xml`: German strings currently range from 75 to 127 characters, causing aggressive multi-line wraps in standard 360-412dp window widths.
    * `DisplaySettingsTest.kt`: Lines 426–451 hardcode exact string assertions for German and English summaries.
  * **Hypothesis Validated**: Shortening strings to crisp ~35–50 char single-line copy resolves wrapping and eliminates vertical bloat while preserving clarity and semantics.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  * Shorten `forcePortraitSummary`, `prefsKeepScreenOnSummary`, `prefsNoUnlockingSummary`, and `cockpit_theme_description` across all 9 locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
  * Enforce a max length bound ($\le 55$ characters for toggles, $\le 65$ characters for cockpit theme) in `DisplaySettingsTest.kt`.
  * Update `DisplaySettingsTest.kt` assertions to match the new concise German and English copy.
* **Out-of-Scope Non-Goals**:
  * Modifying toggle preference keys or `TrainingApplication` storage mechanics.
  * Structural layout changes to `DisplaySettingsDialog.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-503` (*Display Settings Toggle Subtitles & Capitalization Parity*, `ATT-6282`). Refined by **`REQ-UI-514`**.
* **Historical Origin & Commit Trace**: Commit `6753b825` (`ATT-6282`).
* **Root Reason for Existing Formulation**: Subtitles were authored descriptively to explain the background behavior, but excessive sentence length degraded visual hierarchy on mobile viewports.
* **Preservation of Core Invariants**: Behavioral clarity is preserved; all 9 locales maintain exact semantic meaning without formatting specifiers or emojis (`ATT-5400`).

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**:
  * Preference keys (`forcePortrait`, `keepScreenOn`, `noUnlocking`, `volumeKeysSwitchCockpitTabs`) MUST NOT change.
  * 9-language localization parity MUST be strictly preserved.
* **Risk Rating**: **LOW** (pure string resource and unit test refinement, zero behavioral or architectural changes).
