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

/**
 * Release signing material, from `keystore.properties` or the environment.
 *
 * Both are gitignored and neither is required to build: a developer or a fork can run
 * `assembleRelease` without a keystore and get an unsigned APK, rather than a build that
 * cannot be configured at all. CI supplies the same four values as secrets.
 */
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
    namespace = "dev.quiblo.player"

    defaultConfig {
        applicationId = "dev.quiblo.player"
        versionCode = 6
        versionName = "0.2.4"
    }

    buildFeatures {
        // Needed for BuildConfig.DEBUG in QuibloApplication. Disabled by default
        // project-wide in gradle.properties; :app opts back in deliberately.
        buildConfig = true
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
            // AC-NFR-02 caps the release APK at 25 MB. Unminified it is around 62 MB,
            // nearly all of it dex, so both code and resource shrinking are required
            // rather than optional.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // Left unsigned when no keystore is configured. An unsigned release APK is an
            // obvious, recoverable state; one silently signed with the debug key is not,
            // and cannot be upgraded over a properly signed install.
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

    implementation(projects.feature.browse)
    implementation(projects.feature.sources)
    implementation(projects.feature.live)
    implementation(projects.feature.vod)
    implementation(projects.feature.series)
    implementation(projects.feature.player)
    implementation(projects.feature.favorites)
    implementation(projects.feature.settings)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
}
