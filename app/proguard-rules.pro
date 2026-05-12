# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# Retrofit + Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.cellomsai.steplog.data.weather.** { *; }
-keep class retrofit2.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Health Connect
-keep class androidx.health.connect.** { *; }
