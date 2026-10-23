# AYNAMusic release rules
-keep class com.ayush.aynamusic.data.model.** { *; }
-keepattributes *Annotation*
-keep class androidx.media3.** { *; }
-keep class org.json.** { *; }
-dontwarn org.conscrypt.**
-dontwarn okhttp3.internal.platform.**
