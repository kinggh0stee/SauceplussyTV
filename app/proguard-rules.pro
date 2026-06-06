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
-keep class com.saucedplussytv.androidtv.client.SyncEvent { *; }
-keep class com.saucedplussytv.androidtv.client.UserSync { *; }
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

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}