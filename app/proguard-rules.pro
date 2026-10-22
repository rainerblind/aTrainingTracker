# --- Legacy Dependency Fixes ---
# These libraries reference old Apache or internal classes that R8 can't find.
-dontwarn android.net.http.AndroidHttpClient
-dontwarn com.getpebble.android.kit.**
-dontwarn com.dsi.ant.**

# --- Google Maps & Location ---
-keep class com.google.android.gms.maps.** { *; }
-dontwarn com.google.android.gms.maps.**

# --- Kotlin Serialization ---
# Keep descriptors and serializer companions
-keepattributes *Annotation*, EnclosingMethod, InnerClasses, Signature
-keepclassmembers class ** {
    *** Companion;
    *** $serializer;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# --- Domain Enums (SQLite & DataStore Name Resolution) ---
# Preserve all domain enum names, values, and resolution methods for database persistence
-keepclassmembers enum com.atrainingtracker.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static ** safeValueOf(java.lang.String);
    <fields>;
}

# --- Garmin FIT SDK (ATT-1827 / ATT-1117) ---
-keep class com.garmin.fit.** { *; }
-dontwarn com.garmin.fit.**

# --- ANT+ Plugin Library ---
-keep class com.dsi.ant.** { *; }
-dontwarn com.dsi.ant.**

# --- PebbleKit ---
-keep class com.getpebble.android.kit.** { *; }
-dontwarn com.getpebble.android.kit.**

# --- Dropbox SDK ---
-keep class com.dropbox.core.** { *; }
-dontwarn com.dropbox.core.**

# --- GPX Parser ---
-keep class io.ticofab.androidgpxparser.** { *; }

# --- AndroidX Lifecycle ViewModels ---
# Preserve constructors accessed via ViewModelProvider reflection
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# --- Source Line Number & Stacktrace Preservation ---
# Preserve file names and line numbers for Firebase Crashlytics & Play Console deobfuscation
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile