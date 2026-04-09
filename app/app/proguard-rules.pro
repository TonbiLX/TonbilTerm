# ─── kotlinx.serialization ───
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable classes
-keep,includedescriptorclasses class com.tonbil.termostat.data.model.**$$serializer { *; }
-keepclassmembers class com.tonbil.termostat.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.tonbil.termostat.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ─── Ktor ───
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-keep class io.ktor.client.engine.okhttp.** { *; }
-dontwarn io.ktor.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ─── Koin ───
-keep class org.koin.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
}

# ─── Coroutines ───
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ─── AndroidX / Compose ───
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ─── EncryptedSharedPreferences ───
-keep class androidx.security.crypto.** { *; }

# ─── Navigation type-safe routes ───
-keep class com.tonbil.termostat.ui.navigation.*Route { *; }

# ─── General ───
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
