# Keep Gson model classes
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep all DTOs
-keep class com.storelense.mobile.data.remote.dto.** { *; }

# Keep Room entities and DAOs
-keep class com.storelense.mobile.data.local.entity.** { *; }
-keep interface com.storelense.mobile.data.local.dao.** { *; }

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Hilt / Dagger
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# EMDK (if present)
-keep class com.symbol.emdk.** { *; }
-dontwarn com.symbol.emdk.**

# Timber
-dontwarn org.jetbrains.annotations.**

# Suppress warnings for missing classes
-ignorewarnings
