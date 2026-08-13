# Quiblo — a free, open source IPTV player.
# Copyright (C) 2026 The Quiblo Authors
# Licensed under the GNU General Public License v3.0 or later. See LICENSE.
#
# R8 IS enabled for release: app/build.gradle.kts sets isMinifyEnabled and isShrinkResources,
# because AC-NFR-02 caps the APK at 25 MB and it is ~62 MB unminified.
#
# This comment used to say R8 was off "until M6" and was left behind when it was switched on.
# That was not cosmetic: it is why nothing checked whether release code still worked when class
# names change, and XtreamClient.mapThrowable was matching exception types by `simpleName` —
# so every timeout in a shipped build mapped to Unknown("a") instead of Timeout. Anything that
# depends on a name surviving minification needs a keep rule here or, better, not to depend on it.

# kotlinx.serialization keeps generated serializers reachable via reflection.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.quiblo.** {
    *** Companion;
}
-keepclasseswithmembers class dev.quiblo.** {
    kotlinx.serialization.KSerializer serializer(...);
}
