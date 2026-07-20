# PDFBox-Android uses reflection and bundled resources: keep it whole.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.**
-dontwarn javax.**

# Room entities/relations and domain enums are referenced by generated code
# and converted by name through TypeConverters.
-keep class com.aesthetic.gym.data.db.** { *; }
-keep class com.aesthetic.gym.domain.model.** { *; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Coil / OkIO
-dontwarn okio.**
-dontwarn okhttp3.**

# AdMob (play-services-ads). Estas clases son de la plataforma en Android 15 (API 35);
# con compileSdk 34 no están en el classpath de compilación, pero sí existen en los
# dispositivos que las usan, así que basta con silenciar el aviso.
-dontwarn android.media.LoudnessCodecController
-dontwarn android.media.LoudnessCodecController$OnLoudnessCodecUpdateListener
