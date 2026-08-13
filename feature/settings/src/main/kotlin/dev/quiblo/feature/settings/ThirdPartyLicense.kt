/*
 * Quiblo — a free, open source IPTV player.
 * Copyright (C) 2026 The Quiblo Authors
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

package dev.quiblo.feature.settings

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
 * Written by hand and **checked by machine**: `./gradlew licenceCheck` resolves the release
 * runtime classpath of both applications and fails when a module that ships is claimed by no
 * entry here. The wording stays hand-written because saying what a component is *for* takes
 * judgement; the set does not, and a set kept by memory is a set that quietly stops being true.
 * The first run of that check found **118 shipped modules listed nowhere** — the entries below
 * marked as families are what closed it.
 *
 * Deliberately limited to what actually ships. Build-time and test dependencies — AGP, KSP,
 * detekt, JUnit, MockK, Turbine, Robolectric — are not listed, because they are not distributed
 * to anyone and listing them would imply the opposite.
 *
 * **Nearly all of these are Apache 2.0**, which is compatible with distributing this app under
 * GPLv3. The exception is SLF4J, which is MIT and also compatible. A dependency under a licence
 * that is neither has to be answered for before it lands, not here.
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
        name = "Compose for TV",
        coordinates = "androidx.tv:tv-material",
        license = "Apache License 2.0",
        url = "https://developer.android.com/tv/compose",
        notes = "Focus-aware surfaces and lists built for a D-pad. Television build only.",
    ),
    ThirdPartyLicense(
        name = "The rest of AndroidX",
        coordinates = "androidx",
        license = "Apache License 2.0",
        url = "https://developer.android.com/jetpack/androidx",
        notes = "Roughly sixty further Jetpack libraries that the ones above depend on — " +
            "annotations, collections, SQLite, saved state, fragments and tracing. Listed as " +
            "a family because naming each would fill this screen with libraries no part of " +
            "Quiblo calls directly.",
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
        name = "Ktor",
        coordinates = "io.ktor",
        license = "Apache License 2.0",
        url = "https://ktor.io",
        notes = "HTTP requests to the hosts you configure, and nothing else. The client " +
            "pulls in Ktor's own HTTP, serialisation and networking pieces, which ship with it.",
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
    ThirdPartyLicense(
        name = "Okio",
        coordinates = "com.squareup.okio:*",
        license = "Apache License 2.0",
        url = "https://square.github.io/okio/",
        notes = "Reads and writes the bytes underneath OkHttp and the settings store.",
    ),
    ThirdPartyLicense(
        name = "Compose Multiplatform runtime",
        coordinates = "org.jetbrains.compose, org.jetbrains.androidx",
        license = "Apache License 2.0",
        url = "https://www.jetbrains.com/lp/compose-multiplatform/",
        notes = "The common half of Compose, which the Android half is built on.",
    ),
    ThirdPartyLicense(
        name = "Tink",
        coordinates = "com.google.crypto.tink:tink-android",
        license = "Apache License 2.0",
        url = "https://developers.google.com/tink",
        notes = "The cryptography that encrypts your stored Xtream password.",
    ),
    ThirdPartyLicense(
        name = "Gson",
        coordinates = "com.google.code.gson:gson",
        license = "Apache License 2.0",
        url = "https://github.com/google/gson",
        notes = "Arrives with the encrypted store above. Quiblo's own JSON is kotlinx.",
    ),
    ThirdPartyLicense(
        name = "Guava",
        coordinates = "com.google.guava",
        license = "Apache License 2.0",
        url = "https://github.com/google/guava",
        notes = "Collections and futures used by libraries above, not by Quiblo directly.",
    ),
    ThirdPartyLicense(
        name = "Accompanist Drawable Painter",
        coordinates = "com.google.accompanist:accompanist-drawablepainter",
        license = "Apache License 2.0",
        url = "https://github.com/google/accompanist",
        notes = "Lets Coil hand an Android drawable to Compose.",
    ),
    ThirdPartyLicense(
        name = "Stately",
        coordinates = "co.touchlab",
        license = "Apache License 2.0",
        url = "https://github.com/touchlab/Stately",
        notes = "Thread-safe collections used by the dependency wiring above.",
    ),
    ThirdPartyLicense(
        name = "JetBrains and JSpecify annotations",
        coordinates = "org.jetbrains:annotations, org.jspecify:jspecify",
        license = "Apache License 2.0",
        url = "https://github.com/jspecify/jspecify",
        notes = "Nullability annotations the compiler reads. They do nothing at runtime.",
    ),
    ThirdPartyLicense(
        name = "SLF4J API",
        coordinates = "org.slf4j:slf4j-api",
        license = "MIT License",
        url = "https://www.slf4j.org",
        notes = "A logging interface the HTTP client compiles against, with no " +
            "implementation behind it here — so it writes nothing. The one component in this " +
            "list that is not Apache 2.0; MIT is equally compatible with GPLv3.",
    ),
    ThirdPartyLicense(
        name = "boring-avatars",
        // Deliberately not a Maven coordinate, because there is no artefact. `licenceCheck`
        // resolves the shipped dependency graph and would never ask about this entry — which is
        // precisely why it is written down by hand: the obligation is real and the machine
        // cannot see it.
        coordinates = "(ported source, no artefact)",
        license = "MIT License",
        url = "https://github.com/boringdesigners/boring-avatars",
        notes = "The generated profile pictures. Their \"bauhaus\" design is ported into " +
            "Compose rather than depended on, because the original is a React component — so " +
            "no code of theirs ships, but the arithmetic that makes each face is theirs. MIT " +
            "permits this and is compatible with GPLv3 in this direction.",
    ),
)
