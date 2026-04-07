# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Room
-keep class com.impulsive.app.data.db.** { *; }

# Koin
-keep class org.koin.** { *; }

# Vico
-keep class com.patrykandpatrick.vico.** { *; }

# Kotlin serialization (if used for JSON export, though we used manual JSONObjects)
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
