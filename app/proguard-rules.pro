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

# ── Gson ────────────────────────────────────────────────────────────────────
# Gson instantiates these reflectively, so R8 cannot see the constructors or
# fields being used and would otherwise strip or rename them.
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class com.dgraciano.breathe.data.remote.** { *; }

# ── Room entities ───────────────────────────────────────────────────────────
# Column names are derived from field names; renaming them breaks the mapping
# against a database that already exists on the user's device.
-keep class com.dgraciano.breathe.data.model.** { *; }

# ── Retrofit / OkHttp ───────────────────────────────────────────────────────
# Both ship consumer rules; these cover the suspend-function return types that
# older R8 versions could still discard.
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep,allowobfuscation interface <1>
-dontwarn okhttp3.**
-dontwarn okio.**

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
