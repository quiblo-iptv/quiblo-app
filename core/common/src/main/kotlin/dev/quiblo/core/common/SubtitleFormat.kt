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

package dev.quiblo.core.common

import java.util.Locale

/**
 * A sidecar subtitle format the player can render (INC-F10).
 *
 * [mimeType] is the string the media engine expects. It is written out rather than taken from
 * Media3's `MimeTypes`, because `:core:common` is plain Kotlin and importing an engine type here
 * would put the engine behind the seam `FREEZE.md` §4.4 exists to keep it out of. The values are
 * the same strings; a test in `:core:media` is the wrong place to guard that and the engine
 * rejecting the item on a device is how a drift would show.
 */
enum class SubtitleFormat(val mimeType: String, val extension: String) {
    SubRip("application/x-subrip", "srt"),
    WebVtt("text/vtt", "vtt"),
    SubStationAlpha("text/x-ssa", "ass"),
    Ttml("application/ttml+xml", "ttml"),
}

/**
 * The format a subtitle file's *name* claims, or null when the name says nothing.
 *
 * A name is a claim and not a fact — a panel serving `sub.srt` that is really WebVTT is a thing
 * that happens — so a caller with the bytes to hand should prefer [sniffSubtitleFormat] and fall
 * back to this. A caller with only a URL has nothing else.
 *
 * Query strings and fragments are stripped first: `…/sub.srt?token=abc` is an SRT, and reading
 * the extension off the raw string would find `srt?token=abc`.
 */
fun subtitleFormatOfName(name: String): SubtitleFormat? {
    val path = name.substringBefore('?').substringBefore('#')
    val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (extension) {
        "srt" -> SubtitleFormat.SubRip
        "vtt", "webvtt" -> SubtitleFormat.WebVtt
        "ass", "ssa" -> SubtitleFormat.SubStationAlpha
        "ttml", "dfxp" -> SubtitleFormat.Ttml
        else -> null
    }
}

/**
 * The format the file's *content* is in, or null when nothing in it is recognisable.
 *
 * Only the head of the file is needed and only the head should be read: a three-hour film's
 * subtitles are a megabyte of text, and the answer is in the first few lines of every format
 * that exists.
 *
 * SubRip and WebVTT are told apart by the separator inside a cue time — SubRip writes
 * `00:00:01,000`, WebVTT writes `00:00:01.000` — which is the only difference between them that
 * survives a file with no header.
 */
fun sniffSubtitleFormat(head: String): SubtitleFormat? {
    val text = head.removePrefix(UTF8_BOM).trimStart()
    return when {
        text.startsWith("WEBVTT") -> SubtitleFormat.WebVtt
        text.contains("[Script Info]", ignoreCase = true) -> SubtitleFormat.SubStationAlpha
        text.contains("<tt", ignoreCase = true) -> SubtitleFormat.Ttml
        SUBRIP_CUE.containsMatchIn(text) -> SubtitleFormat.SubRip
        WEBVTT_CUE.containsMatchIn(text) -> SubtitleFormat.WebVtt
        else -> null
    }
}

/**
 * The language a subtitle file's name declares, as a lowercase ISO 639 code, or null.
 *
 * `Dune.2021.ar.srt` is Arabic and `Dune.2021.HD.srt` is not "hd", so the segment before the
 * extension is accepted only when the platform recognises it as a language. That check is the
 * whole feature: without it every second file picks up a label that is a codec, a resolution or
 * a release group, and a subtitle menu full of those is worse than one with no labels at all.
 */
fun subtitleLanguageOfName(name: String): String? {
    val path = name.substringBefore('?').substringBefore('#')
    val withoutExtension = path.substringBeforeLast('.', "")
    val candidate = withoutExtension
        .substringAfterLast('.')
        .substringAfterLast('_')
        .substringAfterLast('-')
        .lowercase(Locale.ROOT)

    if (!candidate.all { it in 'a'..'z' }) return null
    return languageCodeOf(candidate)
}

/**
 * A lowercase ISO 639 code for [text], or null when it does not name a language.
 *
 * Panels label a subtitle every way there is — `ar`, `ara`, `Arabic` — and a menu that shows all
 * three for the same language is a menu that has not decided anything. The English names are
 * derived from the platform's own language list rather than typed out, so the list cannot drift
 * from the codes it maps to.
 */
fun languageCodeOf(text: String): String? {
    val trimmed = text.trim().lowercase(Locale.ROOT)
    return when {
        trimmed.isEmpty() -> null
        trimmed in ISO_LANGUAGES -> trimmed
        trimmed in ISO3_LANGUAGES -> trimmed
        else -> ENGLISH_NAMES[trimmed]
    }
}

/** How much of a file [sniffSubtitleFormat] needs. Generous; every format declares itself sooner. */
const val SUBTITLE_SNIFF_BYTES = 4096

/** The two-letter codes, as the platform knows them. */
private val ISO_LANGUAGES: Set<String> = Locale.getISOLanguages().toSet()

/**
 * The three-letter codes for the same languages.
 *
 * Derived from the two-letter list rather than typed out, and each lookup guarded: a platform
 * that has no three-letter form for a language throws rather than returning nothing, and one
 * missing entry is not a reason for a subtitle file to lose its label.
 */
private val ISO3_LANGUAGES: Set<String> = ISO_LANGUAGES
    .mapNotNull { runCatching { Locale.forLanguageTag(it).isO3Language }.getOrNull() }
    .filter { it.isNotEmpty() }
    .toSet()

/** The English name of each language, back to its code. */
private val ENGLISH_NAMES: Map<String, String> = ISO_LANGUAGES.associateBy {
    Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT)
}

private val SUBRIP_CUE = Regex("""\d{1,2}:\d{2}:\d{2},\d{1,3}\s*-->""")
private val WEBVTT_CUE = Regex("""\d{1,2}:\d{2}[:.]\d{2}[.,]\d{1,3}\s*-->""")

private const val UTF8_BOM = "﻿"
