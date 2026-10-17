# Stage 2 Requirement & Test Specification: ATT-3926

## 1. Requirement Specification (`REQ-UI-406`)

### `REQ-UI-406`: Transparent Inner Dial Fill (`#00000000`) on Stopwatch Vector Icon (`bsport_other.xml`) Across Active and Desaturated States
- **Parent Epic**: `ATT-431` (Clean Code)
- **Amends**: `REQ-UI-375.1` (`ATT-2859`)
- **Statement**:
  1. **Transparent Dial Interior (`REQ-UI-406.1`)**:
     - Every `<path>` element in `app/src/main/res/drawable/bsport_other.xml`, including the primary stopwatch circle `<path>` (`M 31.63,110.42 A 63.87,63.87 0 1,0 159.37,110.42 A 63.87,63.87 0 1,0 31.63,110.42 Z`), SHALL declare `android:fillColor="#00000000"`.
     - `bsport_other.xml` SHALL NOT contain `#F0F0F0` or any opaque fill color on the dial face.
  2. **Preservation of Multi-Color Stroke & Rule 37 Geometry (`REQ-UI-406.2`)**:
     - `bsport_other.xml` SHALL preserve its `48dp x 48dp` dimensions, `192x192` viewport, analytical `A` arc primitives, `#FFC700` gold ring/stems, `#000090` left button cap, `#FF0000` center button cap, and `#009000` right button cap.

### Acceptance Criteria (Given-When-Then)
1. **Given** `app/src/main/res/drawable/bsport_other.xml`,
   **When** parsed as an XML `<vector>` DOM document,
   **Then** every `<path>` element has `android:fillColor="#00000000"` and `#F0F0F0` is absent.
2. **Given** `app/src/main/res/drawable/bsport_other.xml`,
   **When** inspected for multi-color stroke fidelity,
   **Then** `#FFC700` (gold ring & stems), `#000090` (blue left button cap), `#FF0000` (red center button cap), and `#009000` (green right button cap) are present.

---

## 2. Test Specification (`TST-UI-369`)

| Test ID | Target Test Class | Requirement | Verification Procedure |
| :--- | :--- | :--- | :--- |
| `TST-UI-369.1` | `VectorAssetResolutionContractTest` (`testMultiColorAndProtocolBadgeVisualFidelityContracts`) | `REQ-UI-406.1`, `REQ-UI-406.2` | Assert `bsport_other.xml` contains `android:fillColor="#00000000"` on all `<path>` elements (including `path[0]`), does not contain `#F0F0F0`, and preserves `#FFC700`, `#000090`, `#FF0000`, and `#009000`. |
| `TST-UI-369.2` | `VectorAssetResolutionContractTest` (`testAll16MigratedVectorXmlsAreWellFormedAndUseTrueGeometricPrimitives`) | `REQ-UI-406.2`, `REQ-UI-375.1` | Assert `bsport_other.xml` parses as a valid `<vector>` DOM with `48dp x 48dp` dimensions, `192x192` viewport, and analytical `A` arc primitives. |

---

## 3. Localization Audit
- No user-facing string resources (`strings.xml`) are added or modified by `ATT-3926`. Existing 9-locale parity remains 100% intact.
