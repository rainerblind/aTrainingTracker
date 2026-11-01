# Stage 3: Implementation Plan - ATT-4856: [Feature] Make clear when paused

**Ticket**: [ATT-4856](https://rainerblind.atlassian.net/browse/ATT-4856)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-44.1`  
**Author**: AI Agent 1 (Software Architect)  
**Date**: 2026-11-01  

---

## 1. Architectural Design & Layer Boundaries (SWE.2)

```
[TrainingApplication (TRACKING_STATE_CHANGED broadcast)]
                       │
                       ▼
         [SensorGridScreen (DisposableEffect)]
          ├── trackingMode: TrackingMode
          └── isAutoPaused: Boolean
                       │
         ┌─────────────┴─────────────┐
         ▼                           ▼
[SensorGridStatusResolver]   [SensorGridContent]
  ├── resolveCockpitStatus     └── graphicsLayer { alpha = resolveGridAlpha(...) }
  └── resolveGridAlpha
         │
         ▼
[NonRecordingStatusBanner]
  ├── NOT_STARTED (PlayCircle, "Not Started", "Start" -> REQUEST_START_TRACKING)
  ├── PAUSED (PauseCircle, "Paused", "Resume Now" -> REQUEST_RESUME_FROM_PAUSED)
  └── AUTO_PAUSED (PauseCircle, "Auto-Paused", "Resume Now" -> REQUEST_RESUME_FROM_PAUSED)
```

### Layer Responsibilities
- **Domain Engine / State Resolver (`SensorGridStatusResolver.kt`)**: Pure functions `resolveCockpitStatus` and `resolveGridAlpha` mapping runtime state to `CockpitStatus` enum and opacity float.
- **UI Components (`SensorGridScreen.kt`)**: Replaces `AutoPauseHudBanner` with enlarged `NonRecordingStatusBanner` and applies `alpha = 0.6f` to `SensorGridContent` when not recording.
- **Application Core (`TrainingApplication.java`)**: Dispatches `TRACKING_STATE_CHANGED` intents received by `SensorGridScreen`.

---

## 2. UI Consistency (Design Guidelines Section 5)

- **Reference Screen**: `SensorGridScreen.kt` (current HUD overlay).
- **Theme Tokens**: High-contrast warning palette (`DarkWarningContainer` / `LightWarningContainer`, `DarkOnWarningContainer` / `LightOnWarningContainer`, `DarkWarningAction` / `LightWarningAction`, `DarkWarning` / `LightWarningBorder`).
- **Typography & Dimensions**: `MaterialTheme.typography.titleSmall` with `FontWeight.Bold`, 22.dp icons, 36.dp action button height with `RoundedCornerShape(12.dp)`.

---

## 3. Atomic Implementation Steps

1. **Step 1: Status Resolution Engine & Pure Model**:
   - Create `com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridStatus.kt` defining `CockpitStatus` enum and `SensorGridStatusResolver` (`resolveCockpitStatus`, `resolveGridAlpha`).
2. **Step 2: 9-Language Localization**:
   - Add `cockpit_status_not_started` ("Not Started") and `cockpit_status_paused` ("Paused") across all 9 `strings.xml` files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
3. **Step 3: UI Enhancement in `SensorGridScreen.kt`**:
   - Update broadcast receiver in `SensorGridScreen` to track both `trackingMode` and `isAutoPaused`.
   - Implement `NonRecordingStatusBanner(status: CockpitStatus, onAction: () -> Unit, modifier: Modifier)`.
   - Update both dual-pane and single-pane layouts to render `NonRecordingStatusBanner` and apply `resolveGridAlpha` to `SensorGridContent`.
4. **Step 4: Unit Testing**:
   - Author `SensorGridStatusTest.kt` verifying `resolveCockpitStatus` and `resolveGridAlpha` across all permutations (`TST-TRK-045.1`, `TST-TRK-045.2`).
   - Run `TranslationParityTest` (`TST-TRK-045.3`) and `ThemeColorCentralizationAuditTest` (`TST-TRK-045.4`).

---

## 4. Invariants & Verification Checklist

1. **Code Lock**: Verify Gate 3 sign-off before modifying production code.
2. **Telemetry Invariant**: Sensor data in grid cards continues live streaming without pause.
3. **Zero Regressions**: Existing `SensorGridScreen` behavior (bike bell, bottom sheets, navigation hints) remains 100% operational.
