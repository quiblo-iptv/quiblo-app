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

package dev.quiblo.tv.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.quiblo.core.data.CategoryRepository
import dev.quiblo.core.data.ChannelLogoRepository
import dev.quiblo.core.data.ChannelRepository
import dev.quiblo.core.data.GuideRepository
import dev.quiblo.core.data.PlayerSettingsRepository
import dev.quiblo.core.data.ProfileRepository
import dev.quiblo.core.data.SearchRepository
import dev.quiblo.core.data.SourceRepository
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.data.TitleMetadataScanner
import dev.quiblo.core.data.WatchHistoryRepository
import dev.quiblo.core.data.backup.BackupRepository
import dev.quiblo.core.datastore.ConsentStore
import org.junit.After
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatform.getKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

/** The newest Android Robolectric 4.16 ships an image for. See `TvBrowseScrollStabilityTest`. */
private const val ROBOLECTRIC_SDK = 34

/**
 * Does the television app's object graph actually build?
 *
 * **This test exists because a Koin module is not type-checked.** `single { Thing(get(), get()) }`
 * hands Kotlin a lambda whose argument types are inferred from the constructor, so *any* number
 * of `get()`s compiles as long as the arity matches — and every `get()` is only resolved when
 * something first asks for the object, which is to say on a device, in front of a viewer.
 *
 * That is not a hypothetical. `ChannelRepository` gained a `profiles` parameter in the middle of
 * its list, the module gained one more `get()` at the end rather than in the middle, and the
 * sixth argument landed on `now: () -> Long` — a clock with a perfectly good default, which Koin
 * had no definition for and never would. It compiled. Detekt passed. Lint passed. Every unit test
 * passed, because every one of them builds its subject by hand. The first thing that noticed was
 * the Live tab, by taking the whole app down with it.
 *
 * So the assertion is the crude one, and deliberately: **ask the real container for the real
 * objects.** Nothing here is mocked, because a mock is precisely the thing that hid the fault.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    // A bare Application, not QuibloTvApplication: that one starts Koin itself, and this test
    // is about starting it deliberately and reading the result.
    application = Application::class,
)
class TvModuleWiringTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    /**
     * Every singleton the app declares, resolved for real.
     *
     * Listed by hand rather than swept out of the module, because a sweep would resolve
     * whatever is declared — including nothing at all, if a module were dropped from the list.
     * A name written here has to keep resolving, and deleting one is a deliberate act.
     */
    @Test
    fun `every repository in the graph can be built`() {
        startKoin {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(tvModules)
        }

        val failures = REPOSITORIES.mapNotNull { type ->
            runCatching { getKoin().get<Any>(type) }
                .exceptionOrNull()
                ?.let { "  ${type.simpleName}: ${it.rootCause().message}" }
        }

        if (failures.isNotEmpty()) {
            fail(
                "${failures.size} of ${REPOSITORIES.size} definitions could not be built. Koin " +
                    "resolves a definition's `get()` calls only when something asks for it, so " +
                    "this is a crash on a device rather than a compile error:\n" +
                    failures.joinToString("\n"),
            )
        }
    }

    /**
     * The innermost cause, which is the one naming the type that has no definition.
     *
     * Koin wraps each failure in an `InstanceCreationException` per level of nesting, so the
     * outermost message says only that a ViewModel could not be built — true, and useless.
     */
    private fun Throwable.rootCause(): Throwable = cause?.rootCause() ?: this

    private companion object {
        val REPOSITORIES: List<KClass<*>> = listOf(
            SourceRepository::class,
            ProfileRepository::class,
            ChannelRepository::class,
            WatchHistoryRepository::class,
            CategoryRepository::class,
            SearchRepository::class,
            TitleMetadataScanner::class,
            GuideRepository::class,
            BackupRepository::class,
            PlayerSettingsRepository::class,
            TitleMetadataRepository::class,
            ChannelLogoRepository::class,
            // Not a repository, and here anyway: `TvApp` resolves it before it draws anything,
            // so a missing definition is not a broken screen — it is an app that cannot start.
            ConsentStore::class,
        )
    }
}
