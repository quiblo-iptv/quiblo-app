# Vibrato — a free, open source IPTV player.
# Copyright (C) 2026 The Vibrato Authors
# Licensed under the GNU General Public License v3.0 or later. See LICENSE.
#
# R8 is not enabled until M6 (docs/PLAN.md §3). Rules accumulate here as modules land.

# kotlinx.serialization keeps generated serializers reachable via reflection.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.vibrato.** {
    *** Companion;
}
-keepclasseswithmembers class dev.vibrato.** {
    kotlinx.serialization.KSerializer serializer(...);
}
