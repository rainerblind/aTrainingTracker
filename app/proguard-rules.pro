# --- Legacy Dependency Fixes ---
# These libraries reference old Apache or internal classes that R8 can't find.
-dontwarn org.apache.http.**
-dontwarn android.net.http.AndroidHttpClient
-dontwarn com.getpebble.android.kit.**
-dontwarn com.dsi.ant.**

# --- Jetpack Compose & Google Maps ---
# Ensure the UI and Maps are not broken by over-aggressive optimization.
-keep class com.google.android.gms.maps.** { *; }
-keep class androidx.compose.** { *; }

# --- Kotlin Serialization ---
# Since we use the serialization plugin, we keep the descriptors.
-keepattributes *Annotation*, EnclosingMethod, InnerClasses, Signature
-keepclassmembers class ** {
    *** Companion;
    *** $serializer;
}