# kotlinx.serialization keeps generated serializers via companion objects.
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Room entities are constructed reflectively by the generated DAO code.
-keep class com.yuvraj.resumescreener.data.local.** { *; }

# --- Optional dependencies referenced but not bundled ---------------------
# PDFBox's JPX/JBIG2 image filters need the optional Jai imageio decoder.
# This app only reads text, so the filters are never invoked.
-dontwarn com.gemalto.jp2.**

# Tink, pulled in by androidx.security-crypto, references error-prone
# annotations that are compile-time only and never present at runtime.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
