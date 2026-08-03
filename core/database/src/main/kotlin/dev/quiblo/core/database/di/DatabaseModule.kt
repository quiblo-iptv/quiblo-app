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

package dev.quiblo.core.database.di

import android.content.Context
import dev.quiblo.core.database.QuibloDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wiring owned by `:core:database`.
 *
 * The database is constructed here rather than in `:core:data` so that Room stays an
 * implementation detail of this module and never leaks onto a consumer's classpath.
 */
val databaseModule: Module = module {
    single { QuibloDatabase.create(get<Context>()) }
    single { get<QuibloDatabase>().sourceDao() }
    single { get<QuibloDatabase>().movieMetadataDao() }
    single { get<QuibloDatabase>().categoryOverrideDao() }
    single { get<QuibloDatabase>().channelDao() }
    single { get<QuibloDatabase>().resumePositionDao() }
    single { get<QuibloDatabase>().favoriteDao() }
    single { get<QuibloDatabase>().programmeDao() }
}
