# kotlinx.serialization ships its own consumer rules; keep our @Serializable models and
# type-safe navigation routes (their serializers are looked up reflectively by Navigation).
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep,includedescriptorclasses class com.solarpulse.**$$serializer { *; }
-keepclassmembers class com.solarpulse.** {
    *** Companion;
}
-keepclasseswithmembers class com.solarpulse.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.solarpulse.app.ui.nav.** { *; }

# Ktor / supabase-kt optional dependencies
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn java.lang.management.**
-dontwarn org.osmdroid.**
