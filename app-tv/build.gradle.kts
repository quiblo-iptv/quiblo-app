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

import java.util.Properties

plugins {
    id("quiblo.android.application")
    alias(libs.plugins.kotlin.serialization)
}

/** Same signing arrangement as `:app`, and for the same reasons. See that file. */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

val releaseStoreFile: String? = signingValue("storeFile", "QUIBLO_KEYSTORE_FILE")
val releaseStorePassword: String? = signingValue("storePassword", "QUIBLO_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = signingValue("keyAlias", "QUIBLO_KEY_ALIAS")
val releaseKeyPassword: String? = signingValue("keyPassword", "QUIBLO_KEY_PASSWORD")
val hasReleaseSigning: Boolean = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "dev.quiblo.tv"

    defaultConfig {
        // A separate application id, not a flavour of the phone app. They are two installs
        // with two databases, and a user may well have both — a phone and a television.
        applicationId = "dev.quiblo.tv"
        versionCode = 8
        versionName = "0.2.5"
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric renders the real Compose UI, which needs the module's resources
            // and a working `android.*` implementation rather than stubbed methods.
            isIncludeAndroidResources = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(projects.core.model)
    implementation(projects.core.media)

    // For their ViewModels and Koin wiring only. None of their composables are referenced,
    // and R8 strips what is unreachable — worth checking on the first release build rather
    // than assumed, because one stray reference drags the whole phone UI in.
    implementation(projects.feature.browse)
    implementation(projects.feature.sources)
    implementation(projects.feature.vod)
    implementation(projects.feature.series)
    implementation(projects.feature.player)
    implementation(projects.feature.settings)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.tv.material)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.compose.ui.tooling)

    // A screen that moves when it should be still cannot be caught by reading code — four
    // confident diagnoses of #008 were wrong before one was measured. Robolectric drives the
    // real composables frame by frame on the JVM, so the movement is a number in CI rather
    // than something to be argued about. See `TvBrowseScrollStabilityTest`.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
    debugImplementation(libs.compose.ui.test.manifest)
}
