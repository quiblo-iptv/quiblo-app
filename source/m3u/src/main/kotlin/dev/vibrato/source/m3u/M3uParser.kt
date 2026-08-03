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

package dev.vibrato.source.m3u

import dev.vibrato.core.model.Category
import dev.vibrato.core.model.Channel
import dev.vibrato.core.model.MediaKind
import dev.vibrato.core.model.Source
import dev.vibrato.source.api.SourceReport
import java.io.BufferedReader
import java.io.Reader

/**
 * A streaming M3U/M3U8 parser.
 *
 * Real playlists are far dirtier than the format suggests, so the parser is written to
 * survive rather than to validate: anything it cannot use is counted and skipped, and it
 * never throws on malformed content (AC-PL-04). Only I/O failures propagate.
 *
 * It reads line by line and emits each entry as soon as it is complete, so a 20,000-entry
 * playlist never needs to exist twice in memory (AC-PL-05).
 *
 * Deliberate decisions:
 * - A missing `#EXTM3U` header is tolerated; it is recorded in the report, not rejected.
 * - A URL line only produces an entry when it follows an `#EXTINF`. Bare URL lists are
 *   not treated as playlists, which keeps stray text in a broken file from becoming
 *   channels.
 * - `group-title` beats a preceding `#EXTGRP` directive when both are present.
 * - An entry with an empty display name is unusable in a list, so it is skipped and
 *   counted rather than shown as a blank row.
 */
object M3uParser {

    private const val HEADER = "#EXTM3U"
    private const val EXTINF = "#EXTINF:"
    private const val EXTGRP = "#EXTGRP:"
    private const val BYTE_ORDER_MARK = '﻿'

    private val ATTRIBUTE_REGEX =
        Regex("""([A-Za-z0-9_.-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

    /** Capture group holding a double-quoted attribute value in [ATTRIBUTE_REGEX]. */
    private const val DOUBLE_QUOTED_VALUE = 2

    /** Capture group holding a single-quoted attribute value in [ATTRIBUTE_REGEX]. */
    private const val SINGLE_QUOTED_VALUE = 3

    /**
     * Parses [reader], invoking [onChannel] for each usable entry as it is completed.
     *
     * The reader is consumed but not closed; the caller owns it.
     *
     * @param sourceId stamped onto every emitted [Channel].
     * @return counts of what was parsed and skipped.
     */
    @Suppress("NestedBlockDepth")
    fun parse(
        reader: Reader,
        sourceId: Long = Source.UNSAVED_ID,
        onChannel: (Channel) -> Unit,
    ): SourceReport {
        val buffered = reader as? BufferedReader ?: BufferedReader(reader)

        var hadHeader = false
        var parsed = 0
        var skipped = 0
        var isFirstLine = true

        var pending: PendingEntry? = null
        var pendingGroup: String? = null

        buffered.forEachLine { rawLine ->
            var line = rawLine
            if (isFirstLine) {
                line = line.removePrefix(BYTE_ORDER_MARK.toString())
                isFirstLine = false
            }
            // BufferedReader splits on \r, \n and \r\n, but a stray \r can still survive
            // inside a line when the file mixes conventions.
            line = line.trim().trimEnd('\r')

            when {
                line.isEmpty() -> Unit

                line.startsWith(HEADER) -> hadHeader = true

                line.startsWith(EXTINF, ignoreCase = true) -> {
                    // An #EXTINF arriving while one is already pending means the previous
                    // entry never got its URL.
                    if (pending != null) skipped++
                    pending = parseExtInf(line)
                    if (pending == null) skipped++
                    pendingGroup = null
                }

                line.startsWith(EXTGRP, ignoreCase = true) -> {
                    pendingGroup = line.removePrefix(EXTGRP).trim().takeIf { it.isNotEmpty() }
                }

                // Any other directive (#EXTVLCOPT, #EXTVLCOPT, comments) is not ours.
                line.startsWith("#") -> Unit

                else -> {
                    val entry = pending
                    if (entry == null) {
                        // A URL with no preceding #EXTINF, or plain junk text. Neither is
                        // an entry, so neither is counted as a skipped one.
                        Unit
                    } else {
                        pending = null
                        val channel = entry.toChannel(
                            sourceId = sourceId,
                            streamUrl = line,
                            fallbackGroup = pendingGroup,
                        )
                        pendingGroup = null
                        if (channel == null) {
                            skipped++
                        } else {
                            parsed++
                            onChannel(channel)
                        }
                    }
                }
            }
        }

        // A trailing #EXTINF at end of file never received its URL.
        if (pending != null) skipped++

        return SourceReport(parsedEntries = parsed, skippedEntries = skipped, hadHeader = hadHeader)
    }

    /** Convenience wrapper that collects every entry into a list. */
    fun parseToList(reader: Reader, sourceId: Long = Source.UNSAVED_ID): M3uParseResult {
        val channels = ArrayList<Channel>()
        val report = parse(reader, sourceId) { channels += it }
        return M3uParseResult(channels = channels, report = report)
    }

    /**
     * Splits an `#EXTINF:` line into its attributes and display name.
     *
     * The name is everything after the first comma that is *not* inside quotes, which is
     * what makes unescaped commas in display names parse correctly (AC-PL-04).
     * Returns null when the line has no comma at all and so carries no name.
     */
    private fun parseExtInf(line: String): PendingEntry? {
        val body = line.substring(EXTINF.length)
        val separator = indexOfUnquotedComma(body) ?: return null

        val head = body.substring(0, separator)
        val name = body.substring(separator + 1).trim()

        val attributes = ATTRIBUTE_REGEX.findAll(head).associate { match ->
            val key = match.groupValues[1].lowercase()
            val value = match.groups[DOUBLE_QUOTED_VALUE]?.value
                ?: match.groups[SINGLE_QUOTED_VALUE]?.value.orEmpty()
            key to value
        }

        return PendingEntry(name = name, attributes = attributes)
    }

    /** Index of the first comma outside any quoted region, or null if there is none. */
    private fun indexOfUnquotedComma(text: String): Int? {
        var quote: Char? = null
        text.forEachIndexed { index, char ->
            when {
                quote == null && (char == '"' || char == '\'') -> quote = char
                quote == char -> quote = null
                char == ',' && quote == null -> return index
            }
        }
        return null
    }

    private data class PendingEntry(
        val name: String,
        val attributes: Map<String, String>,
    ) {
        fun toChannel(sourceId: Long, streamUrl: String, fallbackGroup: String?): Channel? {
            if (name.isBlank() || streamUrl.isBlank()) return null

            val group = attributes["group-title"]?.takeIf { it.isNotBlank() }
                ?: fallbackGroup?.takeIf { it.isNotBlank() }
                ?: Category.UNGROUPED_TITLE

            return Channel(
                id = 0L,
                sourceId = sourceId,
                name = name,
                streamUrl = streamUrl,
                kind = MediaKind.LIVE,
                tvgId = attributes["tvg-id"]?.takeIf { it.isNotBlank() },
                logoUrl = attributes["tvg-logo"]?.takeIf { it.isNotBlank() },
                groupTitle = group,
            )
        }
    }
}

/** Everything a full parse produced. */
data class M3uParseResult(
    val channels: List<Channel>,
    val report: SourceReport,
)
