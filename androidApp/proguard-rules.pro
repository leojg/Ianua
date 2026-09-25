# kotlinx.serialization: keep generated serializers for the rule-pack model.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class me.lgcode.ianua.rules.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class me.lgcode.ianua.rules.**$$serializer { *; }
