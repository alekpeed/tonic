# Stage 10 (docs/09-BUILD-PLAN.md): revisited now that release builds are actually exercised.
#
# Room and Hilt/Dagger ship their own consumer ProGuard rules in their AARs (keeping generated
# `_Impl`/`Hilt_*`/`*_Factory`/`*_MembersInjector` classes as needed) - nothing extra required here for
# either. kotlinx.serialization's own Gradle plugin has bundled consumer rules for a while now, but the
# project keeps its own explicit copy of the standard rules below (per the library's own documentation)
# rather than relying on that silently: every `@Serializable` type here backs a Room JSON-blob column
# (docs/05-DATA-MODEL.md §2) or the diagnostic/session-resume state - if R8 ever stripped a generated
# `$serializer`, existing persisted rows would fail to decode on the very next app launch, which is
# exactly the kind of failure this stage exists to rule out.

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Every @Serializable type in this codebase, plus the generated `$serializer` companion R8 would
# otherwise be free to strip since it's only reached through kotlinx.serialization's own reflection.
-keep,includedescriptorclasses class com.tonic.**$$serializer { *; }
-keepclassmembers class com.tonic.** {
    *** Companion;
}
-keepclasseswithmembers class com.tonic.** {
    kotlinx.serialization.KSerializer serializer(...);
}
