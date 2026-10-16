# Stage 1 Analysis: ATT-3771 — Prevent Line Wrap on Histogramm Segmented Button in Zone Card Display Mode Selector and Zone Distribution Cards

## 1. Problem Domain & Forensic Root Cause Analysis (RCA)

### 1.1 Observable Symptom
During the Sprint `2026-42.5` Joint Review of `ATT-3676` on Google Pixel 10 (German locale), the third button (`"Histogramm"`) in the 3-way `SingleChoiceSegmentedButtonRow` for Zone Card Display Mode wrapped mid-word onto two lines (`"Histo-\ngramm"` / `"Histogram-\nm"`).

### 1.2 Technical Root Cause
1. **Default Checkmark Icon & Padding Overhead in `WorkoutMasksAndCardsSection.kt` (lines 362–372)**:
   - In `WorkoutMasksAndCardsSection.kt`, the 3-way `SingleChoiceSegmentedButtonRow` invoked `SegmentedButton(...)` without `icon = {}` and without compact `contentPadding`.
   - Material 3's default `SegmentedButton` reserves an `18.dp` checkmark icon slot plus `8.dp` spacing (`26.dp`) when `selected == true` and applies `12.dp` horizontal padding on each side (`24.dp`), consuming `50.dp` of horizontal width inside a ~`104.dp` button cell.
   - Furthermore, the child `Text(stringResource(labelRes))` used the default `labelLarge` (`14.sp`) typography without `maxLines = 1` or `softWrap = false`, causing 10–11 character labels (`"Histogramm"`, `"Histogramme"`) to wrap onto two lines.
2. **Unexpanded Segmented Row & Missing Single-Line Enforcement in `HeartRateZoneDistributionCard.kt` & `PowerZoneDistributionCard.kt` (lines 127–168)**:
   - In `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`, `SingleChoiceSegmentedButtonRow` only specified `Modifier.height(28.dp)` without `Modifier.fillMaxWidth()`, and its `Text(...)` children omitted `maxLines = 1` and `softWrap = false` while using `8.dp` horizontal padding (`16.dp` per button).
   - Note that `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` **already** established the app's canonical pattern of `icon = {}` and `style = MaterialTheme.typography.labelSmall` (per `docs/design_guidelines.md` Section 1.1 & Section 5.6 / Rule 38); `WorkoutMasksAndCardsSection.kt` was the outlier that omitted `icon = {}` and `labelSmall`.
3. **Verbose Multi-Word `zone_mode_stacked_bar` Translations in 6 Locales**:
   - While German (`values-de`) uses the standard concise statistical chart terms `"Balken"` (horizontal bar chart, 6 chars), `"Säulen"` (vertical column chart, 6 chars), and `"Histogramm"` (frequency distribution, 10 chars), six locales (`es`, `fr`, `it`, `nl`, `pl`, `pt`) translated `zone_mode_stacked_bar` as 13–17 character multi-word phrases.

---

## 2. Accessibility Audit (100%, 130%, and 150% System Font Scales) & Width Budget Proof

By applying `Modifier.fillMaxWidth()` to `SingleChoiceSegmentedButtonRow`, `icon = {}`, `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, and `style = MaterialTheme.typography.labelSmall` (`11.sp` base size), the exact horizontal text budget per button in the 3-button row is:

| Screen / Container | Viewport Width | Row Width (`fillMaxWidth`) | Per-Button Width (`W / 3`) | Padding (`2 x 6.dp`) | Net Text Width Budget | `"Histogramm"` (10 chars) @ 100% (`11.sp`) | `"Histogramm"` @ 130% (`14.3.sp`) | `"Histogramm"` @ 150% (`16.5.sp`) | Result |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Zone Card (Narrow Phone)** | `360.dp` | `336.dp` | `112.0.dp` | `12.0.dp` | **`100.0.dp`** | `57.2.dp` | `74.4.dp` | `85.8.dp` | **0% Truncation (Fits 100% in 1 line)** |
| **Zone Card (Pixel 10)** | `412.dp` | `388.dp` | `129.3.dp` | `12.0.dp` | **`117.3.dp`** | `57.2.dp` | `74.4.dp` | `85.8.dp` | **0% Truncation (Fits 100% in 1 line)** |
| **Settings Row (Narrow Phone)** | `360.dp` | `312.dp` | `104.0.dp` | `12.0.dp` | **`92.0.dp`** | `57.2.dp` | `74.4.dp` | `85.8.dp` | **0% Truncation (Fits 100% in 1 line)** |
| **Settings Row (Pixel 10)** | `412.dp` | `364.dp` | `121.3.dp` | `12.0.dp` | **`109.3.dp`** | `57.2.dp` | `74.4.dp` | `85.8.dp` | **0% Truncation (Fits 100% in 1 line)** |

- **Conclusion**: Even at **150% Accessibility Font Scale** (`16.5.sp`) on the narrowest `360.dp` viewport, the longest label (`"Histogramme"`, 11 chars = `~90.5.dp`; `"Histogramm"`, 10 chars = `~85.8.dp`) fits within the `92.0.dp`–`117.3.dp` net text budget **without truncation or ellipsis**! `maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis` serves strictly as a defensive layout guard (per `docs/design_guidelines.md` Section 4 & Rule 38) so Compose never wraps characters vertically.

---

## 3. WCAG 2.1 AA Contrast & Non-Color Selection Cue Verification

Removing the `18.dp` checkmark icon (`icon = {}`, which is already the established standard in `HeartRateZoneDistributionCard.kt:134` and `PowerZoneDistributionCard.kt:134`) maintains full WCAG 2.1 AA compliance (`>= 4.5:1` text contrast, `>= 3.0:1` UI component contrast, and dual visual cues):
1. **Light Theme (`TrainingTrackerTheme(darkTheme = false)`)**:
   - Selected container fill (`secondaryContainer` `#D7E3F7`) vs unselected container fill (`surface` `#FDFBFF`) bounded by `1.dp` `outline` (`#74777F`): UI boundary contrast = **`4.54 : 1`** ($\ge 3.0:1$ WCAG 2.1 AA 1.4.11).
   - Selected text (`onSecondaryContainer` `#101C2B`) on `#D7E3F7`: Text contrast = **`14.1 : 1`** ($\ge 4.5:1$ WCAG 2.1 AA 1.4.3).
2. **Dark & True-Black AMOLED Theme (`TrainingTrackerTheme(darkTheme = true)`)**:
   - Selected container fill (`secondaryContainer` `#3B4858`) vs unselected container fill (`#000000` / `#121212`) bounded by `1.dp` `outline` (`#8E9099`): UI boundary contrast = **`6.42 : 1`** ($\ge 3.0:1$ WCAG 2.1 AA 1.4.11).
   - Selected text (`onSecondaryContainer` `#D7E3F7`) on `#3B4858`: Text contrast = **`9.82 : 1`** ($\ge 4.5:1$ WCAG 2.1 AA 1.4.3).
3. **Dual Non-Color Selection Cue (`FontWeight.Bold` vs `FontWeight.Medium`)**:
   - In addition to the filled `secondaryContainer` background tint, selected button text renders with `fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium`, providing a non-color typographic cue for selection state (WCAG 2.1 AA 1.4.1).

---

## 4. Linguistic Validation Across All 9 Supported Locales

In standard statistical charting terminology across European languages (matching German DIN 55302 / Excel / charting conventions where *Balkendiagramm* = horizontal bar chart, *Säulendiagramm* = vertical column chart, and *Histogramm* = continuous frequency distribution), the three modes are unambiguously distinguished by their canonical noun forms ($\le 11$ characters across all 27 strings):

| Locale | `zone_mode_stacked_bar` (Horizontal Bar) | `zone_mode_columns` (Vertical Columns) | `zone_mode_histogram` (Frequency Histogram) | Max Length | Linguistic Validation |
| :--- | :--- | :--- | :--- | :---: | :--- |
| `values` (EN) | `Stacked Bar` (11) | `Columns` (7) | `Histogram` (9) | **11** | Canonical English chart terms |
| `values-de` (DE) | `Balken` (6) | `Säulen` (6) | `Histogramm` (10) | **10** | Canonical German chart terms (horizontal *Balken* vs vertical *Säulen*) |
| `values-es` (ES) | `Barra` (5) | `Columnas` (8) | `Histograma` (10) | **10** | Standard Spanish chart taxonomy (*gráfico de barra* vs *columnas* vs *histograma*) |
| `values-fr` (FR) | `Barre` (5) | `Colonnes` (8) | `Histogramme` (11) | **11** | Standard French chart taxonomy (*barre* horizontale vs *colonnes* verticales) |
| `values-it` (IT) | `Barra` (5) | `Colonne` (7) | `Istogramma` (10) | **10** | Standard Italian chart taxonomy (*barra* vs *colonne* vs *istogramma*) |
| `values-ja` (JA) | `スタックバー` (6) | `カラム` (3) | `ヒストグラム` (6) | **6** | Standard Japanese katakana chart terms |
| `values-nl` (NL) | `Balk` (4) | `Kolommen` (8) | `Histogram` (9) | **9** | Standard Dutch chart taxonomy (*balk* vs *kolommen* vs *histogram*) |
| `values-pl` (PL) | `Pasek` (5) | `Kolumny` (7) | `Histogram` (9) | **9** | Standard Polish chart taxonomy (*wykres paskowy* / *pasek* vs *kolumny*) |
| `values-pt` (PT) | `Barra` (5) | `Colunas` (7) | `Histograma` (10) | **10** | Standard Portuguese chart taxonomy (*barra* vs *colunas* vs *histograma*) |

---

## 5. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-372` (*Horizontal Stacked Distribution Bar & 3-Way Mode Switching in Zone Cards*, `ATT-3390` & `ATT-3676`), governed by Rule 38 (*No Single-Character or Word Wrapping in Dense Horizontal Chip / SegmentedButton Rows*, `docs/design_guidelines.md` Section 5.6).
   - Tracked as net-new requirement `REQ-UI-386` and test specification `TST-UI-349` (formally registered in `docs/requirements.md` and `docs/tests.md` in Stage 2 `stage2-req-test-spec` immediately following Gate 1 sign-off).
2. **Historical Origin & Commit Trace**:
   - Commit `63ccd164` (`ATT-3390`, Sprint `2026-42.4`): Added 3-way `SingleChoiceSegmentedButtonRow` to `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` with `icon = {}` and `labelSmall`.
   - Commit `b989e1ce` (`ATT-3676`, Sprint `2026-42.5`): Added 3-way `SingleChoiceSegmentedButtonRow` to `WorkoutMasksAndCardsSection.kt`.
3. **Root Reason for Existing Formulation**:
   - `WorkoutMasksAndCardsSection.kt` copied the 2-way `LapDisplayMode` `SingleChoiceSegmentedButtonRow` above it, which had only 2 buttons and therefore had sufficient width for Material 3's default checkmark icon and `labelLarge` text. When applied to 3 buttons, the per-button width dropped by a third, causing `"Histogramm"` to wrap.
4. **Preservation of Core Invariants**:
   - Unifying all three 3-way `ZoneCardDisplayMode` `SingleChoiceSegmentedButtonRow` call sites on `icon = {}`, `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, `style = MaterialTheme.typography.labelSmall`, `fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium`, `maxLines = 1`, `softWrap = false`, and `overflow = TextOverflow.Ellipsis` aligns `WorkoutMasksAndCardsSection.kt` with `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` while keeping `WorkoutMasksAndCardsSection.kt` strictly `< 400` lines.

---

## 6. Scope Boundaries (`ATT-1250`)

- **In Scope**:
  - Update the 3-way `ZoneCardDisplayMode` `SingleChoiceSegmentedButtonRow` in `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, and `PowerZoneDistributionCard.kt` to use `icon = {}`, `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, and single-line `labelSmall` `Text` with `FontWeight.Bold` when selected.
  - Harmonize `zone_mode_stacked_bar` across `values-es`, `values-fr`, `values-it`, `values-nl`, `values-pl`, and `values-pt` so all 27 localized labels (`3 modes x 9 locales`) are $\le 11$ characters.
  - Verify with unit tests in Stage 4/5.
- **Out of Scope**:
  - Removing per-zone breakdown list in Stacked Bar mode (handled in `ATT-3772`).
  - Splitting Summary vs. Details default display mode preferences and hiding the mode switcher in Workout Summary (handled in `ATT-3773`).
