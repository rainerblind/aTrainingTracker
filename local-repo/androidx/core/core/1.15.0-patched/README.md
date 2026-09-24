# AndroidX Core 1.15.0-patched

## Purpose & Traceability
* **Issue**: [ATT-1347](https://github.com/rainerblind/aTrainingTracker) (`[Bug] AccessibilityEventCompat$Api34Impl.setAccessibilityDataSensitive`)
* **Requirement**: `REQ-UI-164` (Accessibility Event Dispatch Compatibility & Defective Platform Resilience) in `docs/requirements.md`
* **Upstream Bug**: OEM platform framework omission on specific Android 14 (API 34) builds where `Build.VERSION.SDK_INT == 34` but `AccessibilityEvent.setAccessibilityDataSensitive(boolean)` is missing from `framework.jar`.
* **Re-Upgrade Milestone**: Tracked in `ATT-1091` to remove this patch once upstream Google AndroidX Core safely catches or guards this method.

## Modification Details
This AAR is identical to the official `androidx.core:core:1.15.0.aar` published under Apache-2.0, with exactly one targeted hotfix in `classes.jar`:
* Class: `androidx/core/view/accessibility/AccessibilityEventCompat$Api34Impl.class`
* Methods: `setAccessibilityDataSensitive` and `isAccessibilityDataSensitive`
* Modification: Platform invocations are wrapped in defensive `try ... catch (LinkageError e)` blocks to absorb `NoSuchMethodError` on broken OEM ROMs without swallowing critical non-linkage JVM errors (like `OutOfMemoryError`).

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
