# App has no reflection-based serialization; defaults are enough for our own code.

# ML Kit (barcode scanner) discovers its component registrars by reflection; R8 removed their constructors.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# Glance widgets run through WorkManager (Room, reflection) and DataStore protobuf-lite; with R8 full mode the
# widget never left its loading layout in release builds.
# shortcut: keeps these three libraries whole (a few hundred KB); narrow to exact classes if APK size matters.
-keep class androidx.glance.** { *; }
-keep class androidx.work.** { *; }
-keep class androidx.datastore.** { *; }
