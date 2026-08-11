# Room entities are constructed reflectively by generated code.
-keep class com.dgraciano.breathe.data.model.** { *; }

# Room's generated database implementation is discovered by reflection from
# Room.databaseBuilder().
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Coroutines loads its main dispatcher through a service loader.
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-dontwarn kotlinx.coroutines.**

# The accessibility service is instantiated by the system, not by our code.
-keep class com.dgraciano.breathe.service.BreatheAccessibilityService { *; }
