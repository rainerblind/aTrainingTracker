# Implementation Plan: ATT-1431

**Ticket**: [ATT-1431](https://atrainingtracker.atlassian.net/browse/ATT-1431): `[Verbesserung] Two more textsizes (even larger)`  
**Sprint**: `2026-39.3`  
**FixVersion**: `V4.9.38`  
**Status**: Stage 3 Implementation Plan  
**Author**: Agent 1 (Pair Programming Assistant)  
**Date**: 2026-09-28  

---

## 1. Overview & Architecture

This implementation plan details the file-by-file changes required to add two ultra-large text sizes (`XXHUGE` and `XXXHUGE`) to the cockpit sensor field sizing scale. The design ensures seamless mathematical scaling, visual hierarchy preservation (`FontWeight.SemiBold`), automatic configuration UI exposure, database serialization stability, 9-language translation parity, and full unit test coverage.

---

## 2. File-by-File Implementation Steps

### Step 1: Localization Resources (9 Locales)
Add string resources for `view_size_xxhuge` and `view_size_xxxhuge` to all 9 supported language XML files:

1. **`app/src/main/res/values/strings.xml`** (English):
   ```xml
   <string name="view_size_xxhuge">XXHuge</string>
   <string name="view_size_xxxhuge">XXXHuge</string>
   ```
2. **`app/src/main/res/values-de/strings.xml`** (German):
   ```xml
   <string name="view_size_xxhuge">XX-Riesig</string>
   <string name="view_size_xxxhuge">XXX-Riesig</string>
   ```
3. **`app/src/main/res/values-es/strings.xml`** (Spanish):
   ```xml
   <string name="view_size_xxhuge">XXGigante</string>
   <string name="view_size_xxxhuge">XXXGigante</string>
   ```
4. **`app/src/main/res/values-fr/strings.xml`** (French):
   ```xml
   <string name="view_size_xxhuge">XXGéant</string>
   <string name="view_size_xxxhuge">XXXGéant</string>
   ```
5. **`app/src/main/res/values-it/strings.xml`** (Italian):
   ```xml
   <string name="view_size_xxhuge">XXGigante</string>
   <string name="view_size_xxxhuge">XXXGigante</string>
   ```
6. **`app/src/main/res/values-ja/strings.xml`** (Japanese):
   ```xml
   <string name="view_size_xxhuge">超特大</string>
   <string name="view_size_xxxhuge">極大</string>
   ```
7. **`app/src/main/res/values-nl/strings.xml`** (Dutch):
   ```xml
   <string name="view_size_xxhuge">XXGigantisch</string>
   <string name="view_size_xxxhuge">XXXGigantisch</string>
   ```
8. **`app/src/main/res/values-pl/strings.xml`** (Polish):
   ```xml
   <string name="view_size_xxhuge">XXGigantyczny</string>
   <string name="view_size_xxxhuge">XXXGigantyczny</string>
   ```
9. **`app/src/main/res/values-pt/strings.xml`** (Portuguese):
   ```xml
   <string name="view_size_xxhuge">XXGigante</string>
   <string name="view_size_xxxhuge">XXXGigante</string>
   ```

### Step 2: Enum & Typography Extension ([`SensorFieldView.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt))

1. **Extend `ViewSize` enum**:
   ```kotlin
   enum class ViewSize {
       XSMALL, SMALL, NORMAL, LARGE, XLARGE, HUGE, XHUGE, XXHUGE, XXXHUGE
   }
   ```
2. **Update `ViewSize.getDisplayName(context: Context)`**:
   ```kotlin
   ViewSize.XXHUGE -> R.string.view_size_xxhuge
   ViewSize.XXXHUGE -> R.string.view_size_xxxhuge
   ```
3. **Update `getSensorValueTextStyle(viewSize: ViewSize, typography: Typography)`**:
   ```kotlin
   ViewSize.XXHUGE -> typography.displayLarge.copy(fontSize = 140.sp)
   ViewSize.XXXHUGE -> typography.displayLarge.copy(fontSize = 180.sp)
   ```
   (Base style automatically wrapped in `.copy(fontWeight = FontWeight.SemiBold)`).
4. **Update `getSensorUnitTextStyle(viewSize: ViewSize, typography: Typography)`**:
   ```kotlin
   ViewSize.XXHUGE -> typography.headlineLarge.copy(fontSize = 56.sp)
   ViewSize.XXXHUGE -> typography.headlineLarge.copy(fontSize = 64.sp)
   ```
5. **Update `labelStyle`**:
   ```kotlin
   ViewSize.XXHUGE -> MaterialTheme.typography.headlineMedium.copy(fontSize = 36.sp)
   ViewSize.XXXHUGE -> MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp)
   ```
6. **Update `filterStyle`**:
   ```kotlin
   ViewSize.XXHUGE -> MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)
   ViewSize.XXXHUGE -> MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp)
   ```

### Step 3: Typography & Hierarchy Unit Tests ([`SensorFieldTypographyTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldTypographyTest.kt))

1. In `testExplicitFontSizes_forCustomViewSizes()`:
   Add explicit assertions for `XXHUGE` (140.sp value / 56.sp unit) and `XXXHUGE` (180.sp value / 64.sp unit).
2. Existing tests `testAllSensorValueTextStyles_enforceSemiBoldFontWeight()` and `testAllSensorUnitTextStyles_doNotEnforceBoldFontWeight()` automatically iterate `ViewSize.values()`, testing all 9 sizes.

### Step 4: Configuration UI & ViewModel Tests ([`EditSensorFieldViewModelTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModelTest.kt))

Create unit tests verifying:
1. `EditDialogUiState.availableViewSizes` contains all 9 sizes in `ViewSize.values()`.
2. `onViewSizeChanged(ViewSize.XXHUGE)` and `onViewSizeChanged(ViewSize.XXXHUGE)` mutate `selectedViewSize` correctly.

### Step 5: Localization Parity Verification ([`TranslationParityTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt))

Execute `TranslationParityTest` to confirm 0 missing translations across all 9 locales.

---

## 3. Preserved Invariants & Safety Audit

1. **Visual Hierarchy**: Primary readings always use `FontWeight.SemiBold`; units never use `FontWeight.Bold`.
2. **Existing ViewSize Stability**: Sizes `XSMALL`, `SMALL`, `NORMAL`, `LARGE`, `XLARGE`, `HUGE`, `XHUGE` remain completely unaltered.
3. **Database Schema Stability**: No schema bump; SQLite column `VIEW_SIZE` stores string representation with fallback to `ViewSize.NORMAL`.
4. **9-Language Parity**: All 9 supported locales have complete string resources.
5. **Physical Device State**: Pixel 10 maintained strictly in Light Mode (`Night mode: no`).

---

## 4. Verification & Testing Steps

1. `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldTypographyTest`
2. `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.localization.TranslationParityTest`
3. `./gradlew testDebugUnitTest` (full clean-room suite).
4. Physical Pixel 10 end-to-end verification.

---

## 5. Risk Rating & Recommendation

- **Risk Level**: **LOW**
- **Recommendation**: **RECOMMEND PASS** for Stage 3 Implementation Plan. Ready for Gate 3 audit.
