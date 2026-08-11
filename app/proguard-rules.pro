# ── Crash reporting ─────────────────────────────────────────────────────────
# Without these, a stack trace from the store is a list of obfuscated names with
# no line numbers. Keep them, and rename the source attribute so the original
# file names still are not exposed.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Reflection metadata ─────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# ── Room entities ───────────────────────────────────────────────────────────
# Column names are derived from field names; renaming them breaks the mapping
# against a database that already exists on the user's device.
-keep class com.dgraciano.breathe.data.model.** { *; }

# ── Accessibility service ───────────────────────────────────────────────────
# Instantiated by the system from the manifest name, never from our code.
-keep class com.dgraciano.breathe.service.BreatheAccessibilityService { *; }

# ── App widget ──────────────────────────────────────────────────────────────
# Instantiated by the system from the manifest name, never from our code.
-keep class com.dgraciano.breathe.widget.PauseCountWidget { *; }

# ── Logging ─────────────────────────────────────────────────────────────────
# Strip debug and verbose logging from release builds. Warnings and errors stay,
# because they are what makes a field failure diagnosable at all.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
