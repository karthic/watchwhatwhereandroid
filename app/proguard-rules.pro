# ─────────────────────────────────────────────
# General
# ─────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontwarn kotlinx.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# ─────────────────────────────────────────────
# Retrofit
# ─────────────────────────────────────────────
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# ─────────────────────────────────────────────
# Kotlinx Serialization
# ─────────────────────────────────────────────
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static ** INSTANCE;
}

# Keep all data model classes
-keepclassmembers class com.watchwhatwhere.app.data.model.** {
    *;
}
-keep class com.watchwhatwhere.app.data.model.** { *; }

# ─────────────────────────────────────────────
# OkHttp
# ─────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ─────────────────────────────────────────────
# Coil
# ─────────────────────────────────────────────
-keep class coil.** { *; }

# ─────────────────────────────────────────────
# AVIF/HEIF decoder (awxkee/avif-coder-coil)
# ─────────────────────────────────────────────
-keep class com.github.awxkee.** { *; }
-keep class com.radzivon.bartoshyk.** { *; }

# ─────────────────────────────────────────────
# Hilt / Dagger
# ─────────────────────────────────────────────
-dontwarn dagger.**
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ─────────────────────────────────────────────
# Firebase
# ─────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ─────────────────────────────────────────────
# Google Sign-In
# ─────────────────────────────────────────────
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ─────────────────────────────────────────────
# Room
# ─────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ─────────────────────────────────────────────
# Compose (keeps for reflection)
# ─────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ─────────────────────────────────────────────
# Retrofit interface (WatchWhatWhereApi)
# ─────────────────────────────────────────────
-keep interface com.watchwhatwhere.app.data.api.WatchWhatWhereApi { *; }
