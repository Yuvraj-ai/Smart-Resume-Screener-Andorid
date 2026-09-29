# kotlinx.serialization keeps generated serializers via companion objects.
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Room entities are constructed reflectively by the generated DAO code.
-keep class com.yuvraj.resumescreener.data.local.** { *; }
