# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line number information for readable Crashlytics stack traces.
-keepattributes SourceFile,LineNumberTable

# Preserve annotations, generics and reflection-related attributes. Required by
# Moshi (reflective), Retrofit and Kotlin reflection metadata.
-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod

# Keep the no-argument constructor for Firestore models
-keepclassmembers class com.alpha.spendtracker.data.** {
    !static <fields>;
    public <init>();
}

# Alternatively, keep the entire data class package (Room entities, DAOs,
# Firestore models, Moshi-serialized request/response models).
-keep class com.alpha.spendtracker.data.** { *; }

# ---------------------------------------------------------------------------
# Moshi (reflective via KotlinJsonAdapterFactory)
# ---------------------------------------------------------------------------
# Keep all classes annotated with @JsonClass and all of their members so the
# reflective adapter can read/write every field.
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
# Moshi's own generated adapters and internal classes.
-keep class com.squareup.moshi.** { *; }
-keep class **JsonAdapter { *; }
-keepnames @com.squareup.moshi.JsonClass class *
-dontwarn com.squareup.moshi.**
# Kotlin reflection metadata used by KotlinJsonAdapterFactory.
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.jvm.internal.** { *; }
-dontwarn kotlin.reflect.jvm.internal.**

# ---------------------------------------------------------------------------
# Retrofit / OkHttp
# ---------------------------------------------------------------------------
# Keep Retrofit service interfaces and their annotated methods.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep interface com.alpha.spendtracker.data.GroqApiService { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
# With R8 full mode generic signatures are stripped; retain those for Retrofit.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
# Room ships consumer ProGuard rules, but keep entities/DAOs explicitly to be safe.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.**

# ---------------------------------------------------------------------------
# Google Generative AI (Gemini) SDK
# ---------------------------------------------------------------------------
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# ---------------------------------------------------------------------------
# Jetpack Glance widget
# ---------------------------------------------------------------------------
-keep class com.alpha.spendtracker.widget.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-dontwarn androidx.glance.**

# ---------------------------------------------------------------------------
# Firebase (Auth / Firestore / RemoteConfig / Analytics / Crashlytics)
# ---------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
# Crashlytics: preserve exceptions and source/line info (handled above).
-keepattributes *Annotation*

# ---------------------------------------------------------------------------
# Hilt / Dagger
# ---------------------------------------------------------------------------
-dontwarn dagger.hilt.**
-dontwarn javax.annotation.**

# ---------------------------------------------------------------------------
# Kotlin coroutines
# ---------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ---------------------------------------------------------------------------
# DataStore (uses protobuf-lite internally)
# ---------------------------------------------------------------------------
-keep class androidx.datastore.*.** { *; }
-dontwarn androidx.datastore.**

# Suppress warnings for optional dependencies that R8 may flag.
-dontwarn javax.lang.model.element.Modifier
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Transitive deps pulled in by the Gemini SDK (Ktor / SLF4J / kotlinx.serialization).
# These emit benign R8 "missing class" warnings; suppress so a warnings-as-errors
# release build can't be broken by them.
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn kotlinx.serialization.**
