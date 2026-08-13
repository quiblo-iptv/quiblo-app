# Quiblo — a free, open source IPTV player.
# Copyright (C) 2026 The Quiblo Authors
# Licensed under the GNU General Public License v3.0 or later. See LICENSE.
#
# R8 IS enabled for release: app-tv/build.gradle.kts sets isMinifyEnabled and isShrinkResources.
#
# This comment used to say R8 was off "until M6" and was left behind when it was switched on.
# See app/proguard-rules.pro for what that cost. Anything that depends on a class or method name
# surviving minification needs a keep rule here or, better, not to depend on it.

# kotlinx.serialization keeps generated serializers reachable via reflection.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.quiblo.** {
    *** Companion;
}
-keepclasseswithmembers class dev.quiblo.** {
    kotlinx.serialization.KSerializer serializer(...);
}
