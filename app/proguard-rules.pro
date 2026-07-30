# Decision #85: shrink dead code but never obfuscate, so PostHog stack traces stay readable.
-dontobfuscate

# Retrofit / OkHttp / Moshi keep rules for reflection-based (de)serialization.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations
-keep class ch.rhosys.email.data.remote.dto.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
