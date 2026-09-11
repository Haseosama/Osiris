# Add project specific ProGuard rules here.
-keep class com.osiris.app.data.** { *; }
-keepattributes InnerClasses
-keepattributes *Annotation*, Signature, Exceptions
-keep,includedescriptorclasses class com.osiris.app.**$$serializer { *; }
-keepclassmembers class com.osiris.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.osiris.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
