# Tasks - ATT-342 Refinement: Zoom-Adaptive Alpha & Precision Blending

- `[x]` Refactor `MapContentScope.kt`:
    - `[x]` Implement zoom-aware `trackAlpha` and `memberMarkerAlpha` logic in `Render`
    - `[x]` Refine heatmap parameters for better blending at high zoom
- `[x]` Refactor `MarkerLayer` in `MapLayers.kt` (if needed) to support dynamic alpha multipliers
- `[x]` Verify clean overview at low zoom (no pins)
- `[x]` Verify prominent traces at high zoom
- `[x]` Create walkthrough artifact
