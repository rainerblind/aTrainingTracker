# Stage 3: Implementation Plan - ATT-5819: Friendly Advisory Notice

**Ticket**: [ATT-5819](https://rainerblind.atlassian.net/browse/ATT-5819)  
**Sub-task**: [ATT-5907](https://rainerblind.atlassian.net/browse/ATT-5907) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-222` (`REQ-UI-222.6`)  
**Test Mapping**: `TST-UI-176` (`TST-UI-176.5`)  
**Branch**: `feature/ATT-5819`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Problem Description & Scope
In Expert Settings, `TuningWarningNotice` displays an alarming red error card (`errorContainer`, `Icons.Default.Warning`) with copy focused narrowly on corrupted sensor heuristics. Per `REQ-UI-222.6`, this plan modernizes the banner into an informative advisory notice (`surfaceVariant` container, `Icons.Default.Tune`, `primary` icon tint, `onSurfaceVariant` typography) and updates copy across all 9 supported languages to be friendly, factual, and reassuring regarding layout, model, and sensor customizations and factory reset safety.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-222.6` (`REQ-UI-222.6a` Friendly Copy, `REQ-UI-222.6b` Advisory Container, `REQ-UI-222.6c` Permanent Banner Visibility, `REQ-UI-222.6d` 9-Language Parity)
* **Test Mapping**: `TST-UI-176.5` (`TST-UI-176.5a` Visual & Contract, `TST-UI-176.5b` Translation Parity, `TST-UI-176.5c` Regression)

---

## 3. System Invariants & Preserved Behavior
1. **Preserved Preference & Schema Invariants**: Zero DataStore key or value range changes; factory defaults reset logic remains unaltered.
2. **Layout & Placement Invariant**: `TuningWarningNotice` remains permanently visible at the top of `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`.
3. **100% 9-Language Localization Parity**: Clean XML escaping without raw entities, emojis, or missing translation keys.

---

## 4. Proposed Architectural Changes

### Component 1: `TuningFormControls.kt` (`TuningWarningNotice`)
* Replace `MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)` with `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)`.
* Replace `Icons.Default.Warning` tinted `error` with `Icons.Default.Tune` tinted `MaterialTheme.colorScheme.primary`.
* Replace `onErrorContainer` typography colors with `MaterialTheme.colorScheme.onSurfaceVariant`.

### Component 2: 9-Language Localization (`app/src/main/res/values*/strings.xml`)
* Update `advanced_tuning_warning_desc` across all 9 languages:
  * **EN**: "Customize advanced layout, model, and sensor parameters here. You can restore factory defaults at any time using the reset icon."
  * **DE**: "Hier können fortgeschrittene Layout-, Modell- und Sensor-Parameter angepasst werden. Über das Reset-Icon können die Einstellungen jederzeit auf die Standardwerte zurückgesetzt werden."
  * **ES**: "Personalice aquí los parámetros avanzados de diseño, modelos y sensores. Puede restablecer los valores predeterminados en cualquier momento mediante el icono de restablecimiento."
  * **FR**: "Personnalisez ici les paramètres avancés de mise en page, de modèles et de capteurs. Vous pouvez restaurer les valeurs par défaut à tout moment à l\'aide de l\'icône de réinitialisation."
  * **IT**: "Personalizza qui i parametri avanzati di layout, modelli e sensori. È possibile ripristinare i valori predefiniti in qualsiasi momento utilizzando l\'icona di ripristino."
  * **JA**: "ここでは高度なレイアウト、モデル、センサーのパラメータをカスタマイズできます。リセットアイコンを使用していつでも初期設定に戻すことができます。"
  * **NL**: "Pas hier geavanceerde lay-out-, model- en sensorparameters aan. U kunt te allen tijde de standaardwaarden herstellen met het herstelpictogram."
  * **PL**: "Dostosuj tutaj zaawansowane parametry układu, modeli i czujników. W każdej chwili możesz przywrócić ustawienia domyślne za pomocą ikony resetowania."
  * **PT**: "Personalize aqui parâmetros avançados de layout, modelos e sensores. Você pode restaurar os padrões de fábrica a qualquer momento usando o ícone de redefinição."

### UI Consistency (Rule 23)
* **Reference Component**: Advisory containers in `docs/design_guidelines.md` Section 5.4.
* **Reused Components**: `Card`, `Icon`, `Text`, `MaterialTheme.colorScheme`.
* **Theme Tokens**: `surfaceVariant` (alpha 0.5f), `onSurfaceVariant`, `primary`, `typography.titleSmall`, `typography.bodySmall`.
* **New One-off Styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language String Localization
* Files: `app/src/main/res/values*/strings.xml` (all 9 locales)
* Changes: Update `advanced_tuning_warning_desc` with friendly, factual advisory copy.

### Step 2: Modernize `TuningWarningNotice` Composable
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControls.kt`
* Changes: Transition container to `surfaceVariant.copy(alpha = 0.5f)`, icon to `Icons.Default.Tune` (primary), text to `onSurfaceVariant`.

### Step 3: Contract Test & Parity Verification
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningVisualContractTest.kt`
* Changes: Add `testTuningWarningNotice_advisoryContainerAndInformativeIcon()` asserting elimination of `errorContainer` and `Icons.Default.Warning`, presence of `Icons.Default.Tune`, and reference to `advanced_tuning_warning_desc`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted contract test `AdvancedTuningVisualContractTest`, localization parity test `TranslationParityTest`, and architectural hygiene tests pass with 100% success.
* **Rollback**: Branch isolation on `feature/ATT-5819` allows immediate revert without affecting `sprint/2026-45.12`.
