# Conserver les sérialiseurs kotlinx.serialization utilisés par réflexion
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class net.chevillot.raintoday.data.** {
    *** Companion;
}
-keepclasseswithmembers class net.chevillot.raintoday.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class net.chevillot.raintoday.data.**$$serializer { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
