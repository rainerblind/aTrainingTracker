# Stage 5 Walkthrough - ATT-3264: Refactor LiveClimbSheet to reuse LiveSegments layout and shared ElevationProfile

**Ticket**: [ATT-3264](https://atrainingtracker.atlassian.net/browse/ATT-3264)  
**Sub-task**: [ATT-3278](https://atrainingtracker.atlassian.net/browse/ATT-3278) (`[Test]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*[Epic] Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-UI-352` (*LiveClimbSheet Design System Harmonization, Shared ElevationProfile Integration & Degenerate Geometry Safeguards*)  
**Test Spec Mapping**: `TST-UI-312`  
**Branch**: `feature/ATT-3264`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Executive Summary

During initial ClimbPro delivery (`ATT-1281`), `LiveClimbSheet.kt` was constructed rapidly to provide in-ride telemetry and visual gradient feedback for ascending route climbs. Several components were implemented in an ad-hoc manner:
1. `LiveClimbSheet.kt` contained a bespoke 170-line private composable `ClimbProfileCanvas` duplicating bounds calculation, coordinate projection, path building, and rider marker interpolation already maintained in the canonical `ElevationProfile.kt`.
2. `LiveClimbSheet.kt` introduced bespoke `ClimbCategoryChip` and `ClimbStatusBadge` composables wrapping raw `Surface` instead of using the standardized `MetricBadge.kt`.
3. Degenerate geometries (zero points, single point, flat altitude spans) lacked formal mathematical guards.

Under `ATT-3264`, we have:
- Extended `MetricBadge.kt` with an optional `shape: Shape = RoundedCornerShape(4.dp)` parameter, preserving 100% backward compatibility for all existing callers while enabling `ClimbCategoryChip` (`6.dp`) and `ClimbStatusBadge` (`12.dp`) to delegate cleanly.
- Extended the canonical `ElevationProfile.kt` with slope gradient color shading (`climbGradientShading = true`, `segmentColorProvider`), compact fixed height support (`fixedHeight = 68.dp`), and defensive boundary safeguards (`if (pathPoints.size < 2) return`, altitude range coerced to $\ge 1.0$).
- Extracted `getClimbGradeColor(gradePercent: Double): Color` as a shared public top-level utility mapping the 6 canonical grade bands.
- Refactored `LiveClimbSheet.kt` to delegate chips/badges to `MetricBadge` and replace `ClimbProfileCanvas` with `ElevationProfile`, completely excising the redundant 170-line canvas engine.

---

## 2. Component Refactoring & Code Walkthrough

### 2.1 Standardized MetricBadge Shape Parameter (`MetricBadge.kt`)
[MetricBadge.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/MetricBadge.kt#L38-L58):
```kotlin
@Composable
fun MetricBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    shape: Shape = RoundedCornerShape(4.dp)
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        modifier = modifier
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
```

### 2.2 Canonical ElevationProfile Slope Gradient Shading & Fixed Height (`ElevationProfile.kt`)
[ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt#L275-L390):
- **Degenerate Guard**: `if (pathPoints.size < 2) return`
- **Dynamic Segment Gradient Color**:
```kotlin
val color = if (climbGradientShading) {
    segmentColorProvider?.invoke(grade) ?: getClimbGradeColor(grade)
} else {
    when {
        grade < 2.0 -> TTColor.Zone1
        grade < 5.0 -> TTColor.Zone2
        grade < 10.0 -> TTColor.Zone3
        grade < 15.0 -> TTColor.Zone4
        grade < 20.0 -> TTColor.Zone5
        else -> Color.Black
    }
}
```
- **Height & Padding Adjustments**:
```kotlin
val topPadding = if (showZoomControls) 44.dp else 16.dp
val effectiveTopPadding = if (fixedHeight != null) 8.dp else topPadding
val bottomPadding = if (fixedHeight != null) 18.dp else 24.dp
val totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight
```

### 2.3 LiveClimbSheet Delegation & Canvas Elimination (`LiveClimbSheet.kt`)
[LiveClimbSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt#L188-L270):
- **ElevationProfile Invocation**:
```kotlin
val normalizedPoints = climb.normalizedPathPoints.ifEmpty { climb.pathPoints }
val currentDistance = if (liveClimb.status == LiveClimbStatus.ON_CLIMB || liveClimb.status == LiveClimbStatus.FINISHED) {
    (liveClimb.currentProgressFraction.coerceIn(0f, 1f) * climb.distanceMeters)
} else null

ElevationProfile(
    pathPoints = normalizedPoints,
    currentDistance = currentDistance,
    showZoomControls = false,
    climbGradientShading = true,
    fixedHeight = 68.dp,
    modifier = Modifier.fillMaxWidth()
)
```
- **Chip & Badge Delegation**:
```kotlin
@Composable
fun ClimbCategoryChip(category: ClimbCategory, modifier: Modifier = Modifier) {
    val (bgColor, textColor, labelRes) = getClimbCategoryColors(category)
    MetricBadge(
        text = stringResource(labelRes),
        containerColor = bgColor,
        contentColor = textColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    )
}
```
- **Excised Code**: Completely removed private `ClimbProfileCanvas` composable (-170 lines).

---

## 3. UI Consistency & Design Tokens (Rule 23)

- **Closest Reference Screen**: `LiveSegmentSheet.kt` (`ui/segments/`) and `SegmentHeader.kt` / `SegmentLiveDetails.kt`.
- **Reused Components**: `MetricBadge.kt` (`ui/components/MetricBadge.kt`) and canonical `ElevationProfile.kt` (`ui/map/ElevationProfile.kt`).
- **Theme Tokens**:
  - Shapes: `RoundedCornerShape(6.dp)` for category chips, `RoundedCornerShape(12.dp)` for status badge pills.
  - Spacing: `horizontal = 16.dp, vertical = 8.dp` container padding; `4.dp` vertical column item spacing per Design Guidelines §5.2.
  - Colors: `MaterialTheme.colorScheme.surface`, `primary`, `onSurfaceVariant`, `outlineVariant` (0.5f alpha divider).
  - Typography: `titleLarge` bold for climb title, `labelLarge` bold primary for counter, `headlineMedium` monospace bold for gradient per Design Guidelines §5.5.
- **Justification for Non-standard Styles**: None. This refactoring eliminates historical non-standard bespoke styles.

---

## 4. Verification & Clean-Room Regression Results

### 4.1 Targeted Test Suite Execution
Targeted unit tests verifying climb shading, badge shapes, and layout contracts:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.climbs.*" --tests "com.atrainingtracker.trainingtracker.ui.components.MetricBadge*" --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfile*"
```
- **Result**: `BUILD SUCCESSFUL in 12s` (64 tests executed, 0 failures, 100% pass rate).

### 4.2 Full Clean-Room Application Test Suite
Clean-room full application test suite:
```bash
./gradlew testDebugUnitTest
```
- **Result**: `BUILD SUCCESSFUL` (100% pass rate across the full application test suite).

---

## 5. Traceability Matrix

| Requirement | Test Spec | Implementation Deliverable | Test Deliverable | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-352` (clause 1) | `TST-UI-312.1` | `ElevationProfile.kt`, `LiveClimbSheet.kt` | `ElevationProfileClimbShadingTest.kt` | **Verified** |
| `REQ-UI-352` (clause 2) | `TST-UI-312.2` | `MetricBadge.kt`, `LiveClimbSheet.kt` | `MetricBadgeShapeTest.kt` | **Verified** |
| `REQ-UI-352` (clause 3) | `TST-UI-312.3` | `LiveClimbSheet.kt` | `LiveClimbSheetLayoutTest.kt` | **Verified** |
| `REQ-UI-352` (clause 4) | `TST-UI-312.1` | `ElevationProfile.kt` | `ElevationProfileClimbShadingTest.kt` | **Verified** |
| `REQ-UI-352` (clause 5) | `TST-UI-312.4` | `strings.xml` (all 9 locales) | `LiveClimbSheetLayoutTest.kt` | **Verified** |
