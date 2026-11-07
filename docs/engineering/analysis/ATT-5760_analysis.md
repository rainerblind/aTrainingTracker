# Stage 1: Problem Domain & Root Cause Analysis - ATT-5760

**Ticket**: [ATT-5760](https://rainerblind.atlassian.net/browse/ATT-5760)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355): Good and consistent UI | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5760`  
**Requirement Mapping**: `REQ-UI-414` (`REQ-UI-414.6`), `REQ-UI-457` (Dynamic Photo Decoding)  
**Test Mapping**: `TST-UI-377` (`TST-UI-377.1`), `TST-UI-457`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Domain & Background
In `WorkoutPhotoStrip.kt` (Sprint 2026-45.9, `ATT-5655`), photo cards scale responsively across three thumbnail size tiers:
* `LARGE`: 1 photo per row at full available width (~360–400dp on mobile, ~950–1080px in physical pixels).
* `MEDIUM`: 2 photos per row at half available width (~180dp on mobile, ~480–540px in physical pixels).
* `SMALL`: 3–4 photos per row at compact width (60–90dp on mobile, ~200–250px in physical pixels).

**Defect**: In `PhotoThumbnailCard`, bitmaps are decoded statically via `PhotoTrackOverlayComposer.decodeSampledBitmap(context, photo.uri, maxDim = 240)`. Displaying a 240px bitmap scaled up across a 1080px container causes severe magnification blur (~4.5x upscaling), rendering photos fuzzy and pixelated (verified in attachment `ATT-5655_followup_low_res_large_photo.png`). Furthermore, `LaunchedEffect(photo.filePath)` is not keyed on dimensions, preventing sharp reloads when changing sizes in settings.

### Independent Peer Review & Hypothesis Verification (Rule 69 & 70)
* **Codebase Audit**: Confirmed hardcoded `maxDim = 240` at `WorkoutPhotoStrip.kt:328` and unkeyed `LaunchedEffect(photo.filePath)`.
* **Memory & OOM Guardrail**: Unconditionally loading full-resolution 12MP/48MP camera images would cause `OutOfMemoryError`. Decoding must remain bounded by computing `targetMaxDim = (maxOf(dimensions.width, dimensions.height) * density).roundToInt().coerceIn(256, 1200)`.

---

## 2. Chesterton's Fence & Invariant Archaeology
* **Original Requirement ID & Target**: Refines `REQ-UI-414.6` (*Workout Photos Configuration & Responsive Sizing*), targeting `WorkoutPhotoStrip.kt` and `PhotoThumbnailSizeTest.kt`.
* **Historical Origin & Commit Trace**: `5c9d9653` (`ATT-43`, initial photo strip), `8aed20fc` (`ATT-5474`, photo size presets), `9baf915c` (`ATT-5655`, responsive full/half row layout).
* **Root Reason for Existing Formulation**: When thumbnail sizes were fixed at 56dp/76dp/104dp in `ATT-5474`, `maxDim = 240` was adequate. When `ATT-5655` expanded `LARGE` to full row width, the downsampling constant was overlooked.
* **Preservation of Core Invariants**:
  1. *OOM Safety*: Decoded bitmap resolution remains clamped to $\le 1200\text{px}$, guaranteeing memory footprint $< 4\text{MB}$ per thumbnail.
  2. *Background Decoding*: Bitmap decoding remains strictly offloaded to `Dispatchers.IO`.
  3. *Layout Contract Stability*: `calculatePhotoItemDimensions` and layout aspect ratios remain 100% unchanged.

---

## 3. Scope Bounding & Technical Architecture
* **In-Scope**:
  1. Introduce `calculatePhotoDecodeMaxDim(dimensions: PhotoItemDimensions, density: Density): Int` in `WorkoutPhotoStrip.kt` dynamically mapping dimensions to pixel target (`256..1200px`).
  2. Update `PhotoThumbnailCard` to compute `targetMaxDim` via `LocalDensity.current` and key `remember(photo.filePath, targetMaxDim)` and `LaunchedEffect(photo.filePath, targetMaxDim)`.
  3. Pass `targetMaxDim` to `PhotoTrackOverlayComposer.decodeSampledBitmap(context, photo.uri, maxDim = targetMaxDim)`.
  4. Extend `PhotoThumbnailSizeTest.kt` with unit and contract tests verifying density scaling and key binding.
* **Out-of-Scope**: Full-screen photo viewer (`WorkoutPhotoPreviewBottomSheet.kt`), database schema, settings DataStore preferences.
* **Complexity Rating**: **1 SP (Trivial)**. Localized helper and Composable LaunchedEffect parameterization.

---

## 4. Blast Radius & Side-Effect Audit (Rule 68)
| Affected Component | Impact / Verification |
| :--- | :--- |
| `WorkoutPhotoStrip.kt` | Dynamic `maxDim` calculation and LaunchedEffect re-keying; zero layout regression. |
| `PhotoThumbnailSizeTest.kt` | Unit tests for `calculatePhotoDecodeMaxDim` across densities (1.0x, 2.0x, 3.0x). |
| Memory Footprint | Bounded by `coerceIn(256, 1200)` to ensure zero OOM risk across scrollable lists. |

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: OOM protection preserved; asynchronous decoding on `Dispatchers.IO`; layout dimensions unchanged.
* **Risk Rating**: **LOW** (Safe downsampling parameterization within a single UI composable).
