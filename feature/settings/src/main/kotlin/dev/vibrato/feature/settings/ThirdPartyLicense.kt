/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.vibrato.feature.settings

/**
 * One third-party component shipped inside the APK.
 *
 * @property notes what the component is used for, so the list reads as an explanation
 *   rather than a wall of coordinates.
 */
data class ThirdPartyLicense(
    val name: String,
    val coordinates: String,
    val license: String,
    val url: String,
    val notes: String,
)

/**
 * Everything third-party that reaches a release build (AC-LEGAL-03).
 *
 * Maintained by hand and deliberately limited to what actually ships. Build-time and test
 * dependencies — AGP, KSP, detekt, JUnit, MockK, Turbine, Robolectric — are not listed,
 * because they are not distributed to anyone and listing them would imply the opposite.
 *
 * All of these are Apache 2.0, which is compatible with distributing this app under
 * GPLv3. If a dependency under a different licence is ever added, the compatibility
 * question has to be answered before it lands, not here.
 */
val THIRD_PARTY_LICENSES: List<ThirdPartyLicense> = listOf(
    ThirdPartyLicense(
        name = "Kotlin standard library and coroutines",
        coordinates = "org.jetbrains.kotlin, org.jetbrains.kotlinx:kotlinx-coroutines",
        license = "Apache License 2.0",
        url = "https://github.com/JetBrains/kotlin",
        notes = "The language runtime and its concurrency primitives.",
    ),
    ThirdPartyLicense(
        name = "kotlinx.serialization",
        coordinates = "org.jetbrains.kotlinx:kotlinx-serialization-json",
        license = "Apache License 2.0",
        url = "https://github.com/Kotlin/kotlinx.serialization",
        notes = "Parses Xtream API responses and reads and writes export files.",
    ),
    ThirdPartyLicense(
        name = "Jetpack Compose",
        coordinates = "androidx.compose:*",
        license = "Apache License 2.0",
        url = "https://developer.android.com/jetpack/compose",
        notes = "The entire user interface, including Material 3.",
    ),
    ThirdPartyLicense(
        name = "AndroidX Core, Activity, Lifecycle and Navigation",
        coordinates = "androidx.core, androidx.activity, androidx.lifecycle, androidx.navigation",
        license = "Apache License 2.0",
        url = "https://developer.android.com/jetpack/androidx",
        notes = "Application scaffolding, screen state and navigation between screens.",
    ),
    ThirdPartyLicense(
        name = "AndroidX Room",
        coordinates = "androidx.room:*",
        license = "Apache License 2.0",
        url = "https://developer.android.com/training/data-storage/room",
        notes = "The on-device database holding sources, channels, favourites and the guide.",
    ),
    ThirdPartyLicense(
        name = "AndroidX DataStore",
        coordinates = "androidx.datastore:datastore-preferences",
        license = "Apache License 2.0",
        url = "https://developer.android.com/topic/libraries/architecture/datastore",
        notes = "Small persisted preferences.",
    ),
    ThirdPartyLicense(
        name = "AndroidX Security Crypto",
        coordinates = "androidx.security:security-crypto",
        license = "Apache License 2.0",
        url = "https://developer.android.com/topic/security/data",
        notes = "Encrypts stored Xtream credentials at rest.",
    ),
    ThirdPartyLicense(
        name = "AndroidX Media3 / ExoPlayer",
        coordinates = "androidx.media3:*",
        license = "Apache License 2.0",
        url = "https://github.com/androidx/media",
        notes = "Decodes and plays every stream.",
    ),
    ThirdPartyLicense(
        name = "Ktor Client",
        coordinates = "io.ktor:ktor-client-*",
        license = "Apache License 2.0",
        url = "https://ktor.io",
        notes = "HTTP requests to the hosts you configure, and nothing else.",
    ),
    ThirdPartyLicense(
        name = "OkHttp",
        coordinates = "com.squareup.okhttp3:okhttp",
        license = "Apache License 2.0",
        url = "https://square.github.io/okhttp/",
        notes = "The HTTP engine underneath Ktor and Coil.",
    ),
    ThirdPartyLicense(
        name = "Koin",
        coordinates = "io.insert-koin:*",
        license = "Apache License 2.0",
        url = "https://insert-koin.io",
        notes = "Wires the app's components together.",
    ),
    ThirdPartyLicense(
        name = "Coil",
        coordinates = "io.coil-kt.coil3:*",
        license = "Apache License 2.0",
        url = "https://coil-kt.github.io/coil/",
        notes = "Loads channel logos and artwork from your provider.",
    ),
)
