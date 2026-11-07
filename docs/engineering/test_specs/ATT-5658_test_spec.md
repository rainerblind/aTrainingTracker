# Stage 2: Requirement & Test Specification - ATT-5658: FirstJubileeCelebrationDialog Reordering & Grounded Copy

**Ticket**: [ATT-5658](https://rainerblind.atlassian.net/browse/ATT-5658)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5658`  
**Requirement Mapping**: `REQ-REV-003`, `REQ-REV-004`, `REQ-REV-005` | **Test Spec ID**: `TST-REV-003`, `TST-REV-005`, `TST-REV-006`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-REV-003`)

### 1.1 Problem Statement & Layout Reordering
`FirstJubileeCelebrationDialog` SHALL be restructured into a modest, authentic vertical hierarchy:
1. Grounded milestone message (`jubilee_dialog_message`) celebrating achievements without exaggeration.
2. Section 1 (Top): Personal Feedback via Email (`jubilee_email_feedback_prompt` + `jubilee_action_email_feedback`).
3. Section 2 (Middle): Google Play Store Review (`jubilee_review_prompt` + `jubilee_action_review`).
4. Section 3 (Bottom): Financial Support Card (`jubilee_supporter_appeal_message` + `jubilee_action_upgrade_pro`), visible for both `SupporterTier.FREE` AND `SupporterTier.PIONEER` (whenever no paid subscription is active).

### 1.2 9-Locale Translation Matrix (3 String Keys)

| Locale | `jubilee_dialog_message` | `jubilee_email_feedback_prompt` | `jubilee_review_prompt` |
| :--- | :--- | :--- | :--- |
| **EN** | You have completed %1$d %2$s sessions with aTrainingTracker – a great milestone! There will surely be more to come. | If you enjoy the app or have ideas on how to make it even better, I would love to hear your feedback: | Do you enjoy aTrainingTracker? Then I would especially appreciate a positive review on Google Play: |
| **DE** | Du hast %1$d %2$s-Einheiten mit aTrainingTracker absolviert – ein schöner Meilenstein! Da kommen sicherlich noch ein paar dazu. | Wenn dir die App gefällt oder du Ideen hast, wie sie noch besser werden kann, würde ich mich sehr über dein Feedback freuen: | Gefällt dir aTrainingTracker? Dann freue ich mich insbesondere über eine positive Bewertung bei Google Play: |
| **ES** | Has completado %1$d sesiones de %2$s con aTrainingTracker: ¡un gran hito! Seguro que vendrán muchas más. | Si te gusta la aplicación o tienes ideas para mejorarla, me encantaría recibir tus comentarios: | ¿Te gusta aTrainingTracker? Entonces agradecería especialmente una valoración positiva en Google Play: |
| **FR** | Vous avez effectué %1$d séances de %2$s avec aTrainingTracker : une belle étape ! D\'autres suivront certainement. | Si vous appréciez l\'application ou avez des idées pour l\'améliorer, vos retours sont les bienvenus : | Vous appréciez aTrainingTracker ? Un avis positif sur Google Play serait particulièrement apprécié : |
| **IT** | Hai completato %1$d sessioni di %2$s con aTrainingTracker: un bel traguardo! Ne arriveranno sicuramente altre. | Se l\'app ti piace o hai idee su come migliorarla, mi farebbe molto piacere ricevere il tuo feedback: | Ti piace aTrainingTracker? Allora apprezzerei particolarmente una recensione positiva su Google Play: |
| **JA** | aTrainingTracker で %1$d 回の%2$sセッションを達成しました。素晴らしい節目です！これからもぜひ続けていきましょう。 | アプリを気に入っていただけた場合や、改善のアイデアがありましたら、ぜひご意見をお寄せください： | aTrainingTracker を気に入っていただけましたか？Google Play での高評価をいただけると大変励みになります： |
| **NL** | Je hebt %1$d %2$s-sessies voltooid met aTrainingTracker – een mooie mijlpaal! Daar komen er vast nog meer bij. | Als de app je bevalt of als je ideeën hebt om hem nog beter te maken, hoor ik heel graag je feedback: | Bevalt aTrainingTracker je? Dan stel ik een positieve beoordeling op Google Play bijzonder op prijs: |
| **PL** | Ukończyłeś %1$d treningów w dyscyplinie %2$s z aTrainingTracker – to wspaniały kamień milowy! Na pewno będzie ich jeszcze więcej. | Jeśli podoba Ci się aplikacja lub masz pomysły, jak ją ulepszyć, z chęcią poznam Twoją opinię: | Podoba Ci się aTrainingTracker? W takim razie szczególnie ucieszy mnie pozytywna ocena w Google Play: |
| **PT** | Completaste %1$d sessões de %2$s com o aTrainingTracker – um grande marco! Certamente virão muitas mais. | Se gostas da aplicação ou tens ideias para a melhorar, adoraria receber o teu feedback: | Gostas do aTrainingTracker? Então agradecia imenso uma avaliação positiva no Google Play: |

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (Reordered Vertical Flow)**: *Given* `FirstJubileeCelebrationDialog`, *When* rendered, *Then* sections appear in strict sequence: Milestone message $\rightarrow$ Email Feedback $\rightarrow$ Play Store Review $\rightarrow$ Financial Support.
* **AC-2 (Free & Pioneer Gating)**: *Given* an athlete with `SupporterTier.FREE` or `SupporterTier.PIONEER`, *When* viewing the dialog, *Then* Section 3 is visible; *Given* an athlete with a paid subscription (`BRONZE`..`DIAMOND`), *Then* Section 3 is omitted.
* **AC-3 (Localization & Zero Emojis)**: *Given* all 9 locales, *When* validated, *Then* format specifiers `%1$d %2$s` match and zero decorative inline emojis are present (`ATT-5400`).

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Amends `REQ-REV-003` (Clauses 2, 3, 4, 5) in `req_aftermath_reviews.md`.
* `Historical Origin & Commit Trace`: `ATT-2308` (initial dialog), refined in `ATT-5467`, `ATT-5659`, and `ATT-5658`.
* `Root Reason for Existing Formulation`: Supporter card was previously top-placed and gated by `!isProActive`, excluding Pioneers (`isPro == true`). Text was previously overly grandiose.
* `Preservation of Core Invariants`: Single-prompt invariant (`REQ-REV-004`) preserved; ReviewManager flow and fallback preserved; zero decorative emojis preserved; Rule 73 stacked button layout preserved.

---

## 2. Test Specification (`TST-REV-003`, `TST-REV-005`, `TST-REV-006`)

### Test Case 1: Tier Gating & Section Reordering Contract Test (`TST-REV-003`)
* **Scope**: Unit & Contract Test (`FirstJubileeCelebrationDialogTest.kt`)
* **Action**: Assert Section 3 visibility for `FREE` and `PIONEER`, verify omission for paid tiers (`BRONZE`..`DIAMOND`), and assert top-to-bottom layout order.
* **Expected Result**: 100% PASS.

### Test Case 2: 9-Locale Translation Parity & Positional Format Specifiers (`TST-REV-005`, `TST-LOC-001`)
* **Scope**: Localization Unit Test (`TranslationParityTest.kt`)
* **Action**: Verify `jubilee_dialog_message`, `jubilee_email_feedback_prompt`, and `jubilee_review_prompt` exist with 100% parity across all 9 locales with exact `%1$d %2$s` specifiers and zero emojis.
* **Expected Result**: 100% PASS.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-REV-003` | Contract | `FirstJubileeCelebrationDialog.kt`, `FirstJubileeCelebrationDialogTest.kt` | `REQ-REV-003` | Specified |
| `TST-REV-005` | Localization | `strings.xml` (9 locales), `TranslationParityTest.kt` | `REQ-REV-003`, `REQ-LOC-001` | Specified |
| `TST-REV-006` | Contract | `SupporterDebugSection.kt`, `FirstJubileeCelebrationDialogTest.kt` | `REQ-REV-005` | Specified |
