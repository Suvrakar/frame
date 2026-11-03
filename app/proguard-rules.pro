# Project JSON is (de)serialized with kotlinx.serialization; keep the model and generated serializers.
-keep class app.frame.studio.domain.** { *; }
-keepclassmembers class app.frame.studio.domain.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Media3 and Google Mobile Ads ship their own consumer rules; these keep reflective extension lookups safe.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.google.android.gms.ads.** { *; }

# Readable crash reports in Play Console (upload the generated mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
