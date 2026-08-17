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

plugins {
    id("quiblo.android.core")
    // The update check reads GitHub's release payload into a four-field model. The dependency
    // was already here; without the plugin `@Serializable` compiles and generates nothing, and
    // every decode fails at runtime as if the payload were malformed.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.quiblo.core.network"
}

dependencies {
    api(projects.core.model)
    api(projects.source.api)
    implementation(projects.core.common)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.ktor.client.mock)
}
