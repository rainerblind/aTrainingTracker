# AndroidX Core 1.15.0-patched

## Purpose & Traceability
* **Issues**:
  * [ATT-1347](https://github.com/rainerblind/aTrainingTracker) (`[Bug] AccessibilityEventCompat$Api34Impl.setAccessibilityDataSensitive`)
  * [ATT-2302](https://github.com/rainerblind/aTrainingTracker) (`[Bug] AccessibilityNodeInfoCompat$Api34Impl.setAccessibilityDataSensitive`)
  * [ATT-2584](https://github.com/rainerblind/aTrainingTracker) (`[Bug] AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool`)
  * [ATT-2767](https://github.com/rainerblind/aTrainingTracker) (`[Bug] AccessibilityNodeInfoCompat$Api34Impl.getActionScrollInDirection`)
* **Requirement**: `REQ-UI-164` (Accessibility Event, NodeInfo & Manager Compatibility & Defective Platform Resilience) in `docs/requirements.md`
* **Upstream Bug**: OEM platform framework omission on specific Android 14 (API 34) builds where `Build.VERSION.SDK_INT == 34` but `AccessibilityEvent.setAccessibilityDataSensitive(boolean)`, `AccessibilityNodeInfo.setAccessibilityDataSensitive(boolean)`, `AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_IN_DIRECTION`, or `AccessibilityManager.isRequestFromAccessibilityTool()` is missing from `framework.jar`.
* **Re-Upgrade Milestone**: Tracked in `ATT-1091` to remove this patch once upstream Google AndroidX Core safely catches or guards this method.

## Modification Details
This AAR is identical to the official `androidx.core:core:1.15.0.aar` published under Apache-2.0, with targeted hotfixes in `classes.jar`:
1. **Class**: `androidx/core/view/accessibility/AccessibilityEventCompat$Api34Impl.class` (ATT-1347)
   * Methods: `setAccessibilityDataSensitive` and `isAccessibilityDataSensitive`
   * Modification: Platform invocations are wrapped in defensive `try ... catch (LinkageError e)` blocks to absorb `NoSuchMethodError` on broken OEM ROMs without swallowing critical non-linkage JVM errors (like `OutOfMemoryError`).
2. **Class**: `androidx/core/view/accessibility/AccessibilityNodeInfoCompat$Api34Impl.class` (ATT-2302, ATT-2767)
   * Methods:
     * `getActionScrollInDirection` (ATT-2767): Wrapped in defensive `try ... catch (LinkageError e)` returning safe `null` fallback and logging diagnostic warning.
     * `setAccessibilityDataSensitive` and `isAccessibilityDataSensitive` (ATT-2302): Wrapped in defensive `try ... catch (LinkageError e)` blocks.
     * Proactive blanket shield (ATT-2767): `getContainerTitle`, `setContainerTitle`, `getBoundsInWindow`, `setBoundsInWindow`, `hasRequestInitialAccessibilityFocus`, `setRequestInitialAccessibilityFocus`, `getMinDurationBetweenContentChangeMillis`, `setMinDurationBetweenContentChangeMillis`, `setQueryFromAppProcessEnabled` wrapped in `try ... catch (LinkageError e)`.
3. **Class**: `androidx/core/view/accessibility/AccessibilityManagerCompat$Api34Impl.class` (ATT-2584)
   * Method: `isRequestFromAccessibilityTool`
   * Modification: Platform invocation wrapped in defensive `try ... catch (LinkageError e)` returning safe `false` fallback and logging diagnostic warning.
4. **Class**: `androidx/core/view/accessibility/AccessibilityWindowInfoCompat$Api34Impl.class` (ATT-2584 proactive shield)
   * Methods: `getTransitionTimeMillis` (returns `0L`) and `getLocales` (returns `null`)
   * Modification: Guarded against missing platform methods with safe fallbacks and warning logs.

### Source Code of Patched Class (`AccessibilityEventCompat.java`)
```java
package androidx.core.view.accessibility;

import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

public final class AccessibilityEventCompat {
    private static final String TAG = "AccessibilityEventCompat";

    // ... standard constants and methods from androidx.core:core:1.15.0 ...

    public static boolean isAccessibilityDataSensitive(@NonNull AccessibilityEvent event) {
        if (Build.VERSION.SDK_INT >= 34) {
            return Api34Impl.isAccessibilityDataSensitive(event);
        }
        return false;
    }

    public static void setAccessibilityDataSensitive(@NonNull AccessibilityEvent event,
            boolean accessibilityDataSensitive) {
        if (Build.VERSION.SDK_INT >= 34) {
            Api34Impl.setAccessibilityDataSensitive(event, accessibilityDataSensitive);
        }
    }

    @RequiresApi(34)
    static class Api34Impl {
        private Api34Impl() {}

        static boolean isAccessibilityDataSensitive(AccessibilityEvent event) {
            try {
                return event.isAccessibilityDataSensitive();
            } catch (LinkageError e) {
                Log.w(TAG, "isAccessibilityDataSensitive failed on platform; suppressing error", e);
                return false;
            }
        }

        static void setAccessibilityDataSensitive(AccessibilityEvent event,
                boolean accessibilityDataSensitive) {
            try {
                event.setAccessibilityDataSensitive(accessibilityDataSensitive);
            } catch (LinkageError e) {
                Log.w(TAG, "setAccessibilityDataSensitive missing on platform framework; suppressing error", e);
            }
        }
    }
}
```

## Reproducible Compilation
```bash
javac -source 1.8 -target 1.8 \
  -cp "$ANDROID_HOME/platforms/android-34/android.jar:$GRADLE_CACHE/annotation-jvm-1.9.1.jar:$ORIGINAL_CORE_CLASSES_JAR" \
  AccessibilityEventCompat.java
```
