# Stage 3 Implementation Plan - ATT-7518: Vector Geometry Fix for bt_bike_radar.xml

## 1. Architecture & Component Decomposition
- **Asset Scope**: `app/src/main/res/drawable/bt_bike_radar.xml` (VectorDrawable).
  - Target sub-element: `<group android:name="mini_icon_radar">` in top-left quadrant (`scaleX=0.035`, `scaleY=0.035`).
  - Origin Dot Geometry: Replace broken $r=80$ two-arc snowman with single closed circle (`M 420,720 A 60,60 0 1,0 540,720 A 60,60 0 1,0 420,720 Z` or centered circle).
  - Concentric Arcs: Replace pinched non-concentric arcs with uniform radial arc bands ($dr = 80$ thickness, uniform angular width) radiating rearward (left-facing, oriented away from cyclist).
- **Test Scope**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt`.
  - Add analytical contract assertions: verify single origin circle (no duplicate center/snowman), constant radial thickness on wave bands, and bounding box separation ($\ge 7.0$ px clearance).

## 2. Atomic Implementation Steps
1. **Step 1 - Geometry Calculation & Vector Drafting**:
   - Compute coordinate points for 3 concentric filled wave bands and origin circle with constant wall thickness ($dr=80$ units in local 960x960 grid, giving $2.8\text{ dp}$ scaled) radiating toward negative X (rearward).
   - Verify local bounds map to viewport $[10.0, 45.0] \times [8.0, 40.0]$ with clearance $\ge 7.0\text{ dp}$ from cyclist body ($X=51.3$).
2. **Step 2 - Vector Asset Update**:
   - Update `app/src/main/res/drawable/bt_bike_radar.xml` `<group android:name="mini_icon_radar">` path data with clean geometry.
3. **Step 3 - Contract Test Enhancement**:
   - In `VectorAssetResolutionContractTest.kt`, implement `testBtBikeRadarMiniIconConcentricity()` verifying zero snowman duplicate vertices, constant radial delta, and bounding clearance $\ge 7.0$.
4. **Step 4 - Test Execution & Verification**:
   - Run unit test suite: `tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.resources.VectorAssetResolutionContractTest`.
   - Run requirement governance audit: `python3 tools/verify_requirement_governance.py --text-file docs/engineering/plans/ATT-7518_plan.md --base-ref sprint/2026-46.5`.

## 3. Invariant Protection & System Properties
- **Viewport Invariant**: Vector viewport remains `0 0 96 96`; bike cyclist silhouette, BLE badge, and canvas bounds untouched.
- **Visual Parity**: Visual design matches `bike_radar.xml` and `docs/design_guidelines.md` sensor status icon canon.
- **Zero Regression**: All existing assertions in `VectorAssetResolutionContractTest` remain satisfied.

## 4. Verification & Testing Strategy
- **Automated Tests**:
  - `VectorAssetResolutionContractTest.kt`: `testBtBikeRadarMiniIconBounds`, `testBtBikeRadarMiniIconConcentricity`.
  - Requirement Traceability: `TST-UI-514.5` (origin dot circularity), `TST-UI-514.6` (concentric arc uniformity).
- **Manual / Visual Checks**: VectorDrawable rendering verification across densities (`mdpi` to `xxxhdpi`).

## 5. UI Consistency & Design Guidelines Compliance
- Conforms to `docs/design_guidelines.md` §3 (Vector Asset Guidelines): 96x96 viewport, 2dp minimum stroke weight equivalent, top-left sensor badge quadrant $[10, 45] \times [8, 40]$ with $\ge 7\text{ dp}$ clearance.

## 6. Living Documentation Updates
- Update `docs/requirements/req_ui_design_system.md` (`REQ-UI-514.2`, `REQ-UI-514.4`) to mark implementation status as planned for Stage 4 construction.
