# Khatiyan proguard rules
# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.shohan.khatiyan.data.backup.** {
    *** Companion;
}
-keepclasseswithmembers class com.shohan.khatiyan.data.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Room entities are kept implicitly by generated code; nothing app-specific required.
