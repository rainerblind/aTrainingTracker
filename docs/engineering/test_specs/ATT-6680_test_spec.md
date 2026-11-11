# Stage 2: Requirement & Test Specification - ATT-6680: SupporterScreen (PioneerStatusBanner): Add transition sentence inviting voluntary financial support below the Pioneer unlock notice

**Ticket**: [ATT-6680](https://rainerblind.atlassian.net/browse/ATT-6680)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-UI-516` (*Pioneer Status Banner Voluntary Support Transition Copy Parity*)  
**Test Spec ID**: `TST-UI-516` | **Branch**: `feature/ATT-6680`  
**Author**: AI Agent 1 (Implementer - Flash) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-UI-516)

### 1.1 Problem Statement & Rationale
In `ATT-6290` (`REQ-UI-505`), `pioneer_thank_you_body` on `PioneerStatusBanner` (`SupporterScreen.kt`) was updated to confirm permanent feature and sensor unlock for Pioneer athletes. However, displaying the Supporter subscription plan cards (`Bronze`, `Silver`, `Gold`) directly beneath this banner without explanation causes user confusion as to why subscription tiers are shown when their app is already unlocked. The system SHALL append a respectful transition sentence inviting voluntary financial support for ongoing development to `pioneer_thank_you_body` across all 9 supported locales.

### 1.2 Functional & Architectural Requirements
1. **Voluntary Support Transition Sentence (`REQ-UI-516.1`)**: `pioneer_thank_you_body` SHALL append a polite, non-coercive transition sentence clarifying that voluntary financial contributions for ongoing development remain welcome despite all features being unlocked.
2. **9-Language Localization Parity (`REQ-UI-516.2`)**: The updated copy SHALL be localized across all 9 supported language directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
3. **Hygiene & Quality Standards (`REQ-UI-516.3`)**:
   - Pursuant to `ATT-5400`, `pioneer_thank_you_body` SHALL NOT contain decorative inline Unicode emojis.
   - The copy SHALL NOT contain formatting specifiers (`%s`, `%d`) or references to "ANT+".
4. **Contract Test Validation (`REQ-UI-516.4`)**: `SupporterCopyContractTest.kt` SHALL validate that `pioneer_thank_you_body` exists in all 9 locales, contains zero emojis/specifiers/"ANT+", and matches the exact German and English canonical copy.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* an athlete with Pioneer status viewing `SupporterScreen`,
* *When* `PioneerStatusBanner` is rendered above the Supporter plan cards,
* *Then* `pioneer_thank_you_body` includes the transition sentence inviting voluntary support, contains zero emojis, and has zero format specifiers across all 9 locales.
* *Given* `SupporterCopyContractTest.kt`,
* *When* executed,
* *Then* exact copy assertions for German and English pass with 100%.

### 1.4 System Invariants & Requirement Archaeology (Chesterton's Fence)
* **Original Requirement ID & Target**: Amends `REQ-UI-505` (*Supporter Screen Pioneer Thank You Banner Copy Parity*, `ATT-6290`). Targets `app/src/main/res/values*/strings.xml` and `SupporterCopyContractTest.kt`.
* **Historical Origin & Commit Trace**: `ATT-6290` (`69e96e70`, Sprint 2026-45.6) neutralized legacy "ANT+" references.
* **Root Reason for Existing Formulation**: The prior update focused purely on removing protocol bias and thanking users, leaving the visual juxtaposition with paid supporter tiers below unexplained.
* **Preservation of Core Invariants**: Unconditional Pioneer feature and sensor unlock is 100% preserved; support remains strictly voluntary; zero inline emojis (`ATT-5400`) and zero format specifiers.

---

## 2. Test Specification (TST-UI-516)

### Test Case 1: 9-Language Presence & Non-Blank Audit (`TST-UI-516.1`)
* **Scope**: JVM Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/supporter/SupporterCopyContractTest.kt`)
* **Assertion**: Verify `pioneer_thank_you_body` exists and is non-blank across all 9 locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

### Test Case 2: Hygiene Constraints Audit (`TST-UI-516.2`)
* **Scope**: JVM Unit Test (`SupporterCopyContractTest.kt`)
* **Assertion**: Verify `pioneer_thank_you_body` contains zero format arguments (`%s`, `%d`), zero decorative emojis (`ATT-5400`), and zero references to "ANT+" (case-insensitive) across all 9 locales.

### Test Case 3: Exact Copy Match Test for German & English (`TST-UI-516.3`)
* **Scope**: JVM Unit Test (`SupporterCopyContractTest.kt`)
* **Assertion**:
  - German (`de`):
    `"Danke, dass du bereits mit einer früheren Version von aTrainingTracker deine Trainings aufgezeichnet hast! Unbegrenzte Sensoren und alle zukünftigen Funktionen sind für dich dauerhaft freigeschaltet. Über eine freiwillige finanzielle Unterstützung für die Weiterentwicklung würden wir uns trotzdem sehr freuen."`
  - English (`values/`):
    `"Thank you for recording your workouts with an earlier version of aTrainingTracker! Unlimited sensors and all future features are permanently unlocked for you. Nevertheless, we would still be very happy about voluntary financial support for ongoing development."`

### Test Case 4: Tier 1 Targeted Unit Test Regression Suite (`TST-UI-516.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.supporter.SupporterCopyContractTest"`
* **Assertion**: 100% pass rate.

---

## 3. 9-Language Localization Reference Table

| Locale | File Path | Localized Copy (`pioneer_thank_you_body`) |
|:---|:---|:---|
| `de` | `values-de/strings.xml` | `Danke, dass du bereits mit einer früheren Version von aTrainingTracker deine Trainings aufgezeichnet hast! Unbegrenzte Sensoren und alle zukünftigen Funktionen sind für dich dauerhaft freigeschaltet. Über eine freiwillige finanzielle Unterstützung für die Weiterentwicklung würden wir uns trotzdem sehr freuen.` |
| `en` | `values/strings.xml` | `Thank you for recording your workouts with an earlier version of aTrainingTracker! Unlimited sensors and all future features are permanently unlocked for you. Nevertheless, we would still be very happy about voluntary financial support for ongoing development.` |
| `es` | `values-es/strings.xml` | `¡Gracias por registrar tus entrenamientos con una versión anterior de aTrainingTracker! Sensores ilimitados y todas las funciones futuras están desbloqueados permanentemente para ti. Sin embargo, agradeceríamos enormemente cualquier apoyo financiero voluntario para el desarrollo continuo.` |
| `fr` | `values-fr/strings.xml` | `Merci d\'avoir enregistré vos entraînements avec une version antérieure d\'aTrainingTracker ! Les capteurs illimités et toutes les futures fonctionnalités sont débloqués en permanence pour vous. Néanmoins, nous serions très heureux de recevoir un soutien financier volontaire pour le développement continu.` |
| `it` | `values-it/strings.xml` | `Grazie per aver registrato i tuoi allenamenti con una versione precedente di aTrainingTracker! Sensori illimitati e tutte le funzionalità future sono sbloccati per sempre per te. Tuttavia, saremmo comunque molto grati per un supporto finanziario volontario allo sviluppo continuo.` |
| `ja` | `values-ja/strings.xml` | `以前のバージョンのaTrainingTrackerでワークアウトを記録していただきありがとうございます！無制限のセンサー接続と今後の全機能が永続的にアンロックされています。それでも、今後の継続的な開発に向けた自発的なご支援をいただければ大変嬉しく存じます。` |
| `nl` | `values-nl/strings.xml` | `Bedankt voor het vastleggen van je trainingen met een eerdere versie van aTrainingTracker! Onbeperkte sensoren en alle toekomstige functies zijn permanent voor jou ontgrendeld. Desondanks zouden we vrijwillige financiële steun voor de verdere ontwikkeling nog steeds enorm waarderen.` |
| `pl` | `values-pl/strings.xml` | `Dziękujemy za rejestrowanie treningów we wcześniejszej wersji aTrainingTracker! Nieograniczona liczba czujników i wszystkie przyszłe funkcje są dla Ciebie trwale odblokowane. Niemniej jednak będziemy bardzo wdzięczni za dobrowolne wsparcie finansowe dla dalszego rozwoju.` |
| `pt` | `values-pt/strings.xml` | `Obrigado por registrar seus treinos com uma versão anterior do aTrainingTracker! Sensores ilimitados e todos os recursos futuros estão permanentemente desbloqueados para você. Ainda assim, ficaríamos muito gratos por qualquer apoio financeiro voluntário para o desenvolvimento contínuo.` |

---

## 4. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
|:---|:---|:---|:---|:---|
| `TST-UI-516.1` | Presence & Non-Blank Audit | `SupporterCopyContractTest.kt` | `REQ-UI-516.2` | **Specified** |
| `TST-UI-516.2` | Hygiene Constraints (0 Emojis, 0 Specifiers, 0 ANT+) | `SupporterCopyContractTest.kt` | `REQ-UI-516.3` | **Specified** |
| `TST-UI-516.3` | Exact DE & EN Copy Match | `SupporterCopyContractTest.kt` | `REQ-UI-516.1` | **Specified** |
| `TST-UI-516.4` | Unit Test Regression Execution | `SupporterCopyContractTest.kt` | `REQ-UI-516.4` | **Specified** |
