# Stage 2: Requirement & Test Specification - ATT-5400: Remove Excessive Emojis from Gold and Diamond Garage Upgrade Nudge Texts

**Ticket**: [ATT-5400](https://rainerblind.atlassian.net/browse/ATT-5400)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5400`  
**Requirement Mapping**: `REQ-SET-088.5` (*Humorous N+1 Dialog & Pre-Selected Navigation*)  
**Test Spec ID**: `TST-SET-088`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-SET-088.5)

### 1.1 Problem Statement & Rationale
Post-Sprint 2026-45.5 review user feedback identified visual clutter in Gold and Diamond N+1 garage dialogs. Inline Unicode emojis (`🚴‍♂️`, `💎`, `✨`, `😉`) in `garage_detector_dialog_*` string resources duplicate the dedicated 32.dp Material vector icon slot (`DirectionsBike` / `Diamond`) rendered by `GarageUpgradeNudgeDialog.kt`, violating Material 3 guidelines and causing design redundancy.

### 1.2 Functional & Architectural Requirements
1. **Emoji Stripping Across All 9 Locales (`REQ-SET-088.5`)**: All 4 garage upgrade nudge string resources (`garage_detector_dialog_title_gold`, `garage_detector_dialog_title_diamond`, `garage_detector_dialog_message_gold`, `garage_detector_dialog_message_diamond`) SHALL be stripped of inline Unicode emojis (`🚴‍♂️`, `💎`, `✨`, `😉`) and trailing/adjacent redundant whitespace across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
2. **Positional Specifier & Phrasing Invariants**: The witty cyclist/runner phrasing and strict positional format specifiers (`%1$d`, `%2$d`, `%3$d`, `%4$d`, `%5$s`) SHALL remain 100% intact across all 9 locales.
3. **Design Guidelines Codification**: `docs/design_guidelines.md` (Sections 4 & 5.5) SHALL codify that UI dialogs and bottom sheets rely on vector header icons rather than peppering localized string resources with decorative inline Unicode emojis.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-088.5` (*Humorous N+1 Dialog & Pre-Selected Navigation*), targeting `res/values*/strings.xml` and `GarageUpgradeNudgeDialog.kt`.
* **Historical Origin & Commit Trace**: Commit `3b4e0a20` (Sprint `2026-45.4`, `ATT-5299`).
* **Root Reason for Existing Formulation**: Emojis were introduced informally during copy drafting to convey a playful cycling tone before the 32.dp Material vector header icon slot was implemented.
* **Preservation of Core Invariants**: Stripping emojis retains the witty cyclist/runner tone, dynamic fleet counts, and navigation contracts while eliminating visual distraction and aligning with Material 3.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Clean Title Strings)**: *Given* any supported locale, *When* `garage_detector_dialog_title_gold` or `_diamond` is loaded, *Then* it contains zero Unicode emojis and matches the clean title baseline.
* **Criterion 2 (Clean Body Messages & Specifiers)**: *Given* fleet count arguments (bikes, shoes, sensors, power meters, tier), *When* formatted into `garage_detector_dialog_message_gold` or `_diamond`, *Then* all 5 format tokens are substituted correctly with zero emojis.

---

## 2. Test Specification (TST-SET-088)

### Test Case 1: 9-Locale Emoji Audit & Specifier Parity (`TST-SET-088.7`)
* **Scope**: Contract / Localization Test in `GarageUpgradeNudgeStringsAuditTest.kt`
* **Target Files**: `app/src/main/res/values*/strings.xml`
* **Action**: Parse `garage_detector_dialog_*` across all 9 locales and assert zero Unicode emoji code points (e.g., `\uD83C-\uDBFF\uDC00-\uDFFF`, `\u2728`, `\u2705`, `\uD83D`) and 100% presence of positional specifiers `%1$d`..`%5$s`.
* **Expected Result**: 100% PASS with 0 emojis detected across all 36 entries (4 strings x 9 locales).

### Test Case 2: Clean 9-Language Localization Table
| Locale | Clean Gold Title | Clean Diamond Title |
| :--- | :--- | :--- |
| `values` (EN) | Gold-League Garage Detected! | World-Class Diamond Garage! |
| `values-de` (DE) | Gold-Liga Fuhrpark entdeckt! | Weltklasse Diamant-Fuhrpark! |
| `values-es` (ES) | ¡Garaje de liga Oro detectado! | ¡Garaje Diamante de clase mundial! |
| `values-fr` (FR) | Garage Ligue Or détecté ! | Garage Diamant de classe mondiale ! |
| `values-it` (IT) | Garage di Lega Oro rilevato! | Garage Diamante di classe mondiale! |
| `values-ja` (JA) | ゴールドリーグの機材を発見！ | ワールドクラスのダイヤモンド機材！ |
| `values-nl` (NL) | Goud-klasse garage gedetecteerd! | Diamant-klasse wereldgarage! |
| `values-pl` (PL) | Wykryto garaż ligi złotej! | Światowej klasy garaż diamentowy! |
| `values-pt` (PT) | Garagem de nível Ouro detetada! | Garagem Diamante de nível mundial! |

### Test Case 3: Regression Suite (`TST-SET-088.REG`)
* **Scope**: Targeted unit tests (`monetization.*`, `build.*`, `ThemeColorCentralizationAuditTest`, `TranslationParityTest`)
* **Expected Result**: 100% test pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-088.7` | Localization / Contract | `GarageUpgradeNudgeStringsAuditTest.kt` | `REQ-SET-088.5` | Specified |
| `TST-LOC-001` | Parity | `TranslationParityTest.kt` | `REQ-SET-088.5` | Specified |
| `TST-SET-088.REG` | Regression | Full targeted unit test suite | `REQ-PRO-001`, `REQ-SET-088` | Specified |
