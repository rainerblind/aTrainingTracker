# Stage 2: Requirement & Test Specification - ATT-6290: SupporterScreen PioneerStatusBanner Neutral Copy

**Ticket**: [ATT-6290](https://rainerblind.atlassian.net/browse/ATT-6290)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-46.1` | **Branch**: `feature/ATT-6290`  
**Requirement Mapping**: `REQ-UI-502` (`docs/requirements/req_ui_design_system.md`)  
**Test Spec ID**: `TST-UI-502` (`docs/tests/tst_ui_design_system.md`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-09  

---

## 1. Requirement Specification (REQ-UI-502)

### 1.1 Problem Statement & Rationale
`PioneerStatusBanner` in `SupporterComponents.kt` displays `R.string.pioneer_thank_you_body`. Across all 9 locales, the historical copy specifically mentions "the early ANT+ days", alienating early adopters who tracked workouts prior to V5.0.0 with BLE, GPS, or internal sensors. The copy SHALL express neutral gratitude for tracking with an earlier version of `aTrainingTracker`.

### 1.2 Functional & Architectural Requirements
* **REQ-UI-502.1** (*Neutral Pioneer Appreciation Copy*): `pioneer_thank_you_body` SHALL express appreciation for recording workouts with an earlier version of `aTrainingTracker`, confirm permanent unlocking of unlimited sensors and future features, and contain zero references to "ANT+" across all 9 locales.
* **REQ-UI-502.2** (*Prohibition of Decorative Inline Emojis*): Pursuant to `ATT-5400`, the string SHALL NOT contain inline Unicode emojis.
* **REQ-UI-502.3** (*9-Language Localization Parity*): The string SHALL maintain 100% localization parity across all 9 locales without format specifiers (`%s`, `%d`).

### 1.3 9-Language Localization Specification Table
| Locale | File Path | Localized Copy (`pioneer_thank_you_body`) |
| :--- | :--- | :--- |
| `EN` (default) | `values/strings.xml` | `Thank you for recording your workouts with an earlier version of aTrainingTracker! Unlimited sensors and all future features are permanently unlocked for you.` |
| `DE` | `values-de/strings.xml` | `Danke, dass du bereits mit einer früheren Version von aTrainingTracker deine Trainings aufgezeichnet hast! Unbegrenzte Sensoren und alle zukünftigen Funktionen sind für dich dauerhaft freigeschaltet.` |
| `ES` | `values-es/strings.xml` | `¡Gracias por registrar tus entrenamientos con una versión anterior de aTrainingTracker! Sensores ilimitados y todas las funciones futuras están desbloqueados permanentemente para ti.` |
| `FR` | `values-fr/strings.xml` | `Merci d\'avoir enregistré vos entraînements avec une version antérieure d\'aTrainingTracker ! Les capteurs illimités et toutes les futures fonctionnalités sont débloqués en permanence pour vous.` |
| `IT` | `values-it/strings.xml` | `Grazie per aver registrato i tuoi allenamenti con una versione precedente di aTrainingTracker! Sensori illimitati e tutte le funzionalità future sono sbloccati per sempre per te.` |
| `JA` | `values-ja/strings.xml` | `以前のバージョンのaTrainingTrackerでワークアウトを記録していただきありがとうございます！無制限のセンサー接続と今後の全機能が永続的にアンロックされています。` |
| `NL` | `values-nl/strings.xml` | `Bedankt voor het vastleggen van je trainingen met een eerdere versie van aTrainingTracker! Onbeperkte sensoren en alle toekomstige functies zijn permanent voor jou ontgrendeld.` |
| `PL` | `values-pl/strings.xml` | `Dziękujemy za rejestrowanie treningów we wcześniejszej wersji aTrainingTracker! Nieograniczona liczba czujników i wszystkie przyszłe funkcje są dla Ciebie trwale odblokowane.` |
| `PT` | `values-pt/strings.xml` | `Obrigado por registrar seus treinos com uma versão anterior do aTrainingTracker! Sensores ilimitados e todos os recursos futuros estão permanentemente desbloqueados para você.` |

### 1.4 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* an athlete with Pioneer status opening `SupporterScreen`, *When* `PioneerStatusBanner` is displayed, *Then* `pioneer_thank_you_body` expresses appreciation for recording workouts with an earlier version and contains zero references to "ANT+" and zero emojis.
* *Invariants*: Pioneer grandfathered Pro privileges (`isProActive() == true`), dynamic since-year formatting (`sinceYear`), 36dp tier emblem, and 9-locale parity are 100% preserved.

---

## 2. Test Specification (TST-UI-502)

### Test Case 1: Pioneer Copy Contract & ANT+ Elimination (`TST-UI-502.1`)
* **Scope**: Automated Unit Contract Test (`SupporterCopyContractTest.kt`)
* **Assertion**: Verify across all 9 locales that `pioneer_thank_you_body` exists, has no format arguments (`%s`, `%d`), has no emojis, contains zero occurrences of "ANT+" (case-insensitive), and matches exact DE/EN copy.

### Test Case 2: 9-Language Localization Parity (`TST-UI-502.2`)
* **Scope**: Resource Parity Test (`TranslationParityTest.kt`)
* **Assertion**: Verify `pioneer_thank_you_body` is defined in all 9 supported locales with 100% parity.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-UI-502.3`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.supporter.*" --tests "com.atrainingtracker.trainingtracker.ui.branding.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-502.1` | Contract | `SupporterCopyContractTest.kt` | `REQ-UI-502.1`, `REQ-UI-502.2` | Specified |
| `TST-UI-502.2` | Localization | `strings.xml` (all 9 locales) | `REQ-UI-502.3` | Specified |
| `TST-UI-502.3` | Regression | `ui.settings.supporter.*`, `ui.branding.*`, `build.*` | `REQ-PRO-001` | Specified |
