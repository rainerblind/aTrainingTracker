# Stage 1 Analysis: ATT-3926

## 1. Problem Statement & Observable Symptoms
- **Ticket**: `ATT-3926` — Make inner dial of `bsport_other.xml` stopwatch icon transparent to match legacy grey/active rendering
- **Parent Epic**: `ATT-431` (Clean Code)
- **Symptom**:
  - During the Sprint 2026-42.7 review of `ATT-2859` (`REQ-UI-375`), it was observed on device (`stopwatch_other_grey_inner.png`) that the unselected/grey state of the stopwatch sport icon (`R.drawable.bsport_other`, used for `BSportType.UNKNOWN` / `Other`) renders with an opaque grey inner circle dial instead of a transparent interior as in the legacy `bsport_other.png` / `bsport_other_gray.png` drawables.
  - Furthermore, on Dark and AMOLED surfaces, the `#F0F0F0` fill creates a bright opaque disc inside the gold stopwatch ring rather than letting the underlying card or button surface show through.

---

## 2. Forensic Root Cause Analysis (RCA)
1. **Vector Path Inspection (`app/src/main/res/drawable/bsport_other.xml`)**:
   - In `app/src/main/res/drawable/bsport_other.xml` (lines 8–12):
     ```xml
     <path
         android:pathData="M 31.63,110.42 A 63.87,63.87 0 1,0 159.37,110.42 A 63.87,63.87 0 1,0 31.63,110.42 Z"
         android:fillColor="#F0F0F0"
         android:strokeColor="#FFC700"
         android:strokeWidth="8.81" />
     ```
   - When `bsport_other.xml` was compiled from `/home/rainer/Dropbox/workspace/TrainingTracker_with_BT_and_Map5/Bilder/logo.fig` in `ATT-2859`, the main stopwatch circle object (`depth 70`) in `logo.fig` carried Xfig fill color `#F0F0F0` (used as the backdrop behind the runner and bike in the composite app launcher logo).
   - However, in the standalone legacy `bsport_other.png` and `bsport_other_gray.png` icons, the interior of the stopwatch ring was transparent (`#00000000`), leaving only the gold ring (`strokeColor="#FFC700"`), the three gold stems (`#FFC700`), and the three colored crown caps (`#000090`, `#FF0000`, `#009000`).
2. **Interaction with `SportTypeSelector.kt` Desaturation**:
   - In `SportTypeSelector.kt`, unselected sport icons are rendered with `ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })` and `alpha = 0.5f`.
   - Because the inner circle had `android:fillColor="#F0F0F0"`, the `0.5f` alpha desaturated fill rendered as a visible translucent grey disc inside the ring, contrasting against the card container instead of remaining transparent.
3. **Contract Test Coupling (`VectorAssetResolutionContractTest.kt`)**:
   - In `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt` (line 146), `testMultiColorAndProtocolBadgeVisualFidelityContracts()` currently asserts:
     `assertTrue("bsport_other.xml must preserve light gray stopwatch dial (#F0F0F0)", otherXml.contains("#F0F0F0"))`.

---

## 3. Chesterton's Fence Requirement Archaeology (`REQ-PRO-022`)
- **Original Requirement ID & Target**:
  - Amends `REQ-UI-375` (*Mathematical VectorDrawable Migration from Xfig/SVG/XCF Masters & Legacy Raster Purge*, Sprint 2026-42.7, `ATT-2859`) and introduces `REQ-UI-406` (*Transparent Stopwatch Inner Dial in `bsport_other.xml`*).
- **Historical Origin & Commit Trace**:
  - `REQ-UI-375` (`ATT-2859`, commit `15c74685`) migrated the 16 sport and sensor raster drawables to mathematical XML `VectorDrawable`s.
- **Root Reason for Existing Formulation**:
  - The Xfig master `Bilder/logo.fig` defined an interior fill (`#F0F0F0`) on the stopwatch circle for the composite launcher icon, whereas the exported standalone `bsport_other.png` / `bsport_other_gray.png` sport icons had a transparent interior (`#00000000`).
- **Preservation of Core Invariants**:
  - All Rule 37 mathematical curve primitives (`A 63.87,63.87 ...`), viewport dimensions (`192x192`, `48dp x 48dp`), gold ring and stem strokes (`#FFC700`), and multi-color button caps (`#000090`, `#FF0000`, `#009000`) in `bsport_other.xml` are 100% preserved.

---

## 4. Scope Boundaries (`ATT-1250`)
- **In Scope**:
  1. Change `android:fillColor="#F0F0F0"` to `android:fillColor="#00000000"` on the stopwatch dial circle `<path>` (line 10) in `app/src/main/res/drawable/bsport_other.xml`.
  2. Update `VectorAssetResolutionContractTest.kt` to verify that the dial circle `<path>` in `bsport_other.xml` uses transparent fill (`#00000000`), that `#F0F0F0` is absent, and that all stroke colors (`#FFC700`, `#000090`, `#FF0000`, `#009000`) remain intact.
  3. Synchronize `docs/requirements.md` (`REQ-UI-375` / `REQ-UI-406`) and `docs/tests.md` (`TST-UI-338` / `TST-UI-369`).
- **Out of Scope**:
  - Any changes to `bsport_bike.xml`, `bsport_run.xml`, sensor badge drawables, or `SportTypeSelector.kt`.
