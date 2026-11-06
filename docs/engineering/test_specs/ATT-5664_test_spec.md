# Stage 2: Requirement & Test Specification - ATT-5664: Explicitly label new favorite route creation

**Ticket**: [ATT-5664](https://rainerblind.atlassian.net/browse/ATT-5664)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5664`  
**Requirement Mapping**: `REQ-UI-123` | **Test Spec ID**: `TST-UI-078`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-UI-123`)

### 1.1 Problem Statement & Rationale
In `ClusterNamingDialog` (`ImportBackupTabsScreen.kt` & `WorkoutClusterComponents.kt`), the route assignment section ambiguously asked for a generic "new name", conflicting with the preceding workout name input field (`cluster_naming__workout_name_label`). The prompts must explicitly contrast selecting an existing favorite route (*Lieblingsstrecke*) vs. creating a new favorite route.

### 1.2 Functional Requirements & 9-Locale Translation Matrix
The system SHALL update the following 3 string resources across all 9 supported locales:

| Locale | `cluster_naming__assignment_label` | `cluster_naming__new_name_prompt` | `cluster_naming__new_route_name_label` |
| :--- | :--- | :--- | :--- |
| **EN** | Select existing favorite route: | Or create a new favorite route: | New Favorite Route Name |
| **DE** | Bestehende Lieblingsstrecke auswählen: | Oder neue Lieblingsstrecke anlegen: | Name der neuen Lieblingsstrecke |
| **ES** | Seleccionar ruta favorita existente: | O crear una nueva ruta favorita: | Nombre de la nueva ruta favorita |
| **FR** | Sélectionner un itinéraire favori existant : | Ou créer un nouvel itinéraire favori : | Nom du nouvel itinéraire favori |
| **IT** | Seleziona percorso preferito esistente: | O crea un nuovo percorso preferito: | Nome del nuovo percorso preferito |
| **JA** | 既存のお気に入りルートを選択: | または新しいお気に入りルートを作成: | 新しいお気に入りルート名 |
| **NL** | Bestaande favoriete route selecteren: | Of een nieuwe favoriete route maken: | Naam van nieuwe favoriete route |
| **PL** | Wybierz istniejącą ulubioną trasę: | LUB utwórz nową ulubioną trasę: | Nazwa nowej ulubionej trasy |
| **PT** | Selecionar rota favorita existente: | Ou criar uma nova rota favorita: | Nome da nova rota favorita |

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (Explicit Labels)**: *Given* `ClusterNamingDialog` is shown, *When* `selectedCluster == null`, *Then* the upper section displays `cluster_naming__assignment_label`, the creation section displays `cluster_naming__new_name_prompt`, and the input field displays `cluster_naming__new_route_name_label`.
* **AC-2 (100% Parity)**: *Given* all 9 locale directories, *When* `TranslationParityTest` runs, *Then* 100% string presence and matching specifiers are verified.
* **Invariants**: Resource keys unchanged; zero Compose layout or database schema changes; no emojis (`ATT-5400`).

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-123` (*Localized Cluster Naming Interface*), targeting `strings.xml`.
* `Historical Origin & Commit Trace`: Introduced in `ATT-494` / `ATT-565`; updated in `ATT-5560` (workout name addition).
* `Root Reason for Existing Formulation`: Generic labels were sufficient before the separate workout name field was introduced.
* `Preservation of Core Invariants`: Key names preserved; 100% 9-language translation parity maintained.

---

## 2. Test Specification (`TST-UI-078`)

### Test Case 1: 9-Language Translation Parity Audit (`TST-UI-078.1`)
* **Scope**: Localization Unit Test (`app/src/test/.../TranslationParityTest.kt`)
* **Preconditions**: All 9 locale `strings.xml` files updated.
* **Action**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.TranslationParityTest"`
* **Expected Result**: 100% parity across all 9 locales, zero missing entries.

### Test Case 2: Tier 1 Targeted Package Regression (`TST-UI-078.2`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
* **Expected Result**: 100% PASS across build and theme tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-078.1` | Localization | `strings.xml` (9 locales) | `REQ-UI-123`, `REQ-UI-106` | Specified |
| `TST-UI-078.2` | Regression (Tier 1) | Theme & Build suites | `REQ-PRO-001`, `REQ-UI-123` | Specified |
