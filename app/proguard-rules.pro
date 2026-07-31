# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ---- Gson DTOs: keep all fields used for JSON deserialization ----
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.saucedplussytv.androidtv.models.** { *; }
-keep class com.saucedplussytv.androidtv.creator.** { *; }
-keep class com.saucedplussytv.androidtv.post.** { *; }
-keep class com.saucedplussytv.androidtv.subscription.** { *; }
# SyncEvent.kt and UserSync.kt each declare several top-level classes (Data, Target,
# Video, Post, Metadata, Body). Naming only SyncEvent/UserSync left those siblings
# obfuscated, so Gson could not bind their fields — keep every socket DTO by listing
# the nested-payload types explicitly.
-keep class com.saucedplussytv.androidtv.client.SyncEvent { *; }
-keep class com.saucedplussytv.androidtv.client.Data { *; }
-keep class com.saucedplussytv.androidtv.client.Target { *; }
-keep class com.saucedplussytv.androidtv.client.Video { *; }
-keep class com.saucedplussytv.androidtv.client.Post { *; }
-keep class com.saucedplussytv.androidtv.client.Metadata { *; }
-keep class com.saucedplussytv.androidtv.client.UserSync { *; }
-keep class com.saucedplussytv.androidtv.client.Body { *; }

# Gson uses the generic type argument of TypeToken at runtime (getVideoProgress uses
# TypeToken<List<VideoProgress>>); without this the erased signature breaks binding.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# github.Release is deserialized by the update check but sits outside the kept packages.
-keep class com.saucedplussytv.androidtv.github.** { *; }
-dontwarn com.google.gson.**

# ---- socket.io-client / engine.io ----
# io.socket.** covers both socket.io and engine.io-client (io.socket.engineio.**)
-keep class io.socket.** { *; }
-dontwarn io.socket.**

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**

# ---- Glide ----
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.AppGlideModule { *; }

# ---- Stack traces: preserve line numbers ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- WebView JS bridge ----
# WebLoginActivity.LoginBridge is called only from JavaScript, so R8 sees no callers and
# would strip it — silently breaking the authoritative login-detection probe.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}