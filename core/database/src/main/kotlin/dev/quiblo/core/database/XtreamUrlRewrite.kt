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

package dev.quiblo.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * The one-off rewrite behind MIGRATION_24_25 (`BUG-041`): stored Xtream stream URLs, which carry the
 * account's username and password in their path, become locators that carry neither.
 *
 * The locator format is `:source:xtream`'s — `XtreamUrl.liveLocator` and friends — restated here
 * because this module cannot depend on a source module. `XtreamUrlRewriteTest` pins the two to the
 * same strings.
 */

/** `http://host/live/user/pass/101.ts` and its film and episode equivalents. */
private val CREDENTIALED_STREAM = Regex("""^https?://[^/?#]+/(live|movie|series)/[^/?#]+/[^/?#]+/([^/?#]+)$""")

private val LIVE_EXTENSION = Regex("""\.(ts|m3u8)$""")

/**
 * The locator for a stored Xtream stream [url], or null when [url] is not one.
 *
 * Live drops its extension, because which container to ask for is decided at play time. Films and
 * episodes keep theirs: it is the panel's own `container_extension` and the file it serves.
 */
internal fun xtreamLocatorOf(url: String): String? {
    val match = CREDENTIALED_STREAM.matchEntire(url.trim()) ?: return null
    val (type, file) = match.destructured
    val name = if (type == "live") file.replace(LIVE_EXTENSION, "") else file
    return name.takeIf { it.isNotBlank() }?.let { "xtream:$type/$it" }
}

/**
 * The lower-cased host of a URL or of a bare `host:port` as a source URL may be typed, or null.
 *
 * Only ever compared with another host, to recognise which rows came from an Xtream panel.
 */
internal fun hostOfUrl(raw: String): String? {
    val withScheme = if ("://" in raw) raw.trim() else "http://${raw.trim()}"
    val authority = withScheme.substringAfter("://").substringBefore('/').substringBefore('?')
    val host = authority.substringAfterLast('@').let { hostPort ->
        if (hostPort.startsWith('[')) hostPort.substringBefore(']') + "]" else hostPort.substringBefore(':')
    }
    return host.lowercase().takeIf { it.isNotBlank() }
}

/** Which rows of one table to rewrite. */
internal data class StoredUrlColumn(val table: String, val column: String, val hasSourceId: Boolean)

/**
 * Every place a credentialed Xtream URL could have been stored.
 *
 * A channel's stream URL; and an episode's, which is its identity in every history table — so the
 * resume point, the watch log, a picked subtitle, a remembered row, an opinion or a favourite keyed
 * by one carries the password too.
 */
internal val STORED_URL_COLUMNS = listOf(
    StoredUrlColumn("channels", "streamUrl", hasSourceId = true),
    StoredUrlColumn("resume_positions", "stableKey", hasSourceId = true),
    StoredUrlColumn("watch_events", "stableKey", hasSourceId = true),
    StoredUrlColumn("favorites", "stableKey", hasSourceId = true),
    StoredUrlColumn("feed_rows", "stableKey", hasSourceId = true),
    StoredUrlColumn("picked_subtitles", "stableKey", hasSourceId = false),
    StoredUrlColumn("title_opinions", "titleKey", hasSourceId = false),
)

/**
 * Rewrites [target] in place.
 *
 * A **channel** is rewritten only when its source is an Xtream source: a playlist exported from the
 * same panel stores the same shape of URL, and a playlist row must keep a URL it can play.
 * A **history key** is rewritten when its row's source is an Xtream source, or — for the tables
 * that do not record a source, and history written before they did — when its host is an Xtream
 * source's host.
 *
 * `UPDATE OR REPLACE`, because a password that changed over time leaves two old URLs for one
 * episode; both become the same locator, and the later row wins rather than the upgrade failing.
 *
 * **Behind a temporary index on both columns of the update's `WHERE`** (`BUG-061`). Nothing indexes `streamUrl`, so
 * in 0.27.0 each update read every row of its source and a catalogue of n titles cost n² — hours on
 * a television, inside the transaction every query waits on, so the profile chooser sat empty. The
 * index is dropped before returning: Room checks the indices at 25 against the schema, and an extra
 * one is an upgrade that fails.
 */
internal fun SupportSQLiteDatabase.rewriteStoredXtreamUrls(
    target: StoredUrlColumn,
    xtreamSourceIds: Set<Long>,
    xtreamHosts: Set<String>,
) {
    val source = if (target.hasSourceId) "`sourceId`" else "0"
    val stored = mutableListOf<Pair<Long, String>>()
    query("SELECT DISTINCT $source, `${target.column}` FROM `${target.table}` WHERE `${target.column}` LIKE 'http%'")
        .use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(1)?.let { stored += cursor.getLong(0) to it }
            }
        }
    val found = stored.mapNotNull { (sourceId, url) ->
        val fromXtream = sourceId in xtreamSourceIds ||
            (target.table != "channels" && hostOfUrl(url) in xtreamHosts)
        xtreamLocatorOf(url)?.takeIf { fromXtream }?.let { Triple(sourceId, url, it) }
    }
    if (found.isEmpty()) return

    // Both columns of the WHERE, not just the URL. Given a one-column index on the URL beside
    // `index_channels_sourceId`, SQLite has no statistics to tell them apart and picks the source
    // index — which matches the whole catalogue, and is the quadratic plan all over again.
    val indexed = if (target.hasSourceId) "`${target.column}`, `sourceId`" else "`${target.column}`"
    execSQL("CREATE INDEX IF NOT EXISTS `$REWRITE_INDEX` ON `${target.table}` ($indexed)")
    val update = compileStatement(
        "UPDATE OR REPLACE `${target.table}` SET `${target.column}` = ? WHERE `${target.column}` = ?" +
            if (target.hasSourceId) " AND `sourceId` = ?" else "",
    )
    found.forEach { (sourceId, url, locator) ->
        update.bindString(1, locator)
        update.bindString(2, url)
        if (target.hasSourceId) update.bindLong(SOURCE_ID_ARGUMENT, sourceId)
        update.executeUpdateDelete()
        update.clearBindings()
    }
    update.close()
    execSQL("DROP INDEX IF EXISTS `$REWRITE_INDEX`")
}

/** Lives only for the length of one table's rewrite; see [rewriteStoredXtreamUrls]. */
private const val REWRITE_INDEX = "index_bug061_xtream_rewrite"

/** The third `?` of the update, after the locator and the URL it replaces. */
private const val SOURCE_ID_ARGUMENT = 3
