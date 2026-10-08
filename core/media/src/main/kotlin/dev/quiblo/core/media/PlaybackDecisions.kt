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

package dev.quiblo.core.media

import androidx.media3.common.PlaybackException

/*
 * What the controller decides when the engine reports a failure, as plain functions (`BUG-034`).
 *
 * These two decisions — what kind of failure this is, and whether to try again — are the code
 * most likely to be behind "sometimes it doesn't play", and they lived inside an ExoPlayer
 * listener where nothing could test them. Here they take integers and answer values, so a table
 * of cases can be checked without an engine, a device or a stream.
 *
 * The engine's own types stop at `Media3PlayerController`, which reduces a `PlaybackException`
 * to an [EngineFailure] and hands it in. The error-code constants are read from Media3 here only
 * as numbers; they are compile-time constants, so nothing of the engine runs in a test.
 */

/**
 * The parts of an engine failure the decisions read.
 *
 * @property errorCode Media3's `PlaybackException.errorCode`.
 * @property httpStatus the status the server answered with, when the failure was a bad status.
 * @property hostUnreachable whether the cause chain says the host could not be resolved or
 *   refused the connection, rather than accepting it and going quiet.
 */
internal data class EngineFailure(
    val errorCode: Int,
    val httpStatus: Int? = null,
    val hostUnreachable: Boolean = false,
)

/**
 * The typed error for [failure].
 *
 * **A bad HTTP status is not one thing, and treating it as one was most of `BUG-034`.** Every
 * status used to become [PlaybackError.SOURCE_GONE] — "no longer available at that address" —
 * and that error is terminal, so a 403 from a panel whose connection slot had not yet been freed
 * was never asked again. The status is now read: only 404 and 410 say the stream is gone.
 */
internal fun classify(failure: EngineFailure): PlaybackError = when (failure.errorCode) {
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> classifyStatus(failure.httpStatus)

    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackError.SOURCE_GONE

    // The engine reports an unknown host and a refused connection as a failed connection, the
    // same code as a socket that opened and then died. Only the cause can tell them apart, and
    // the difference is the difference between "nobody is there" and "it stopped answering".
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
        if (failure.hostUnreachable) PlaybackError.UNREACHABLE else PlaybackError.TIMEOUT

    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackError.TIMEOUT

    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    -> PlaybackError.UNSUPPORTED_FORMAT

    // v1 ships no DRM at all (docs/FREEZE.md §3), so an encrypted stream is a clear,
    // expected failure rather than a bug to chase.
    PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
    -> PlaybackError.DRM_UNSUPPORTED

    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    -> PlaybackError.NETWORK

    else -> PlaybackError.UNKNOWN
}

/**
 * What a status says about whose side refused.
 *
 * A status this does not name is [PlaybackError.UNKNOWN] rather than a guess. "Gone" for a 400
 * would send a viewer to give up on a channel that may only need a different request.
 */
private fun classifyStatus(status: Int?): PlaybackError = when (status) {
    null -> PlaybackError.UNKNOWN
    in GONE_STATUSES -> PlaybackError.SOURCE_GONE
    in AUTH_STATUSES -> PlaybackError.AUTH_REJECTED
    in REFUSED_STATUSES, in SERVER_ERROR_STATUSES -> PlaybackError.PROVIDER_REFUSED
    else -> PlaybackError.UNKNOWN
}

/** What to do after a failure. */
internal sealed interface NextStep {

    /** Prepare the same item again after [delayMillis]; this is retry number [attempt]. */
    data class RetryAfter(val delayMillis: Long, val attempt: Int) : NextStep

    /** Report [PlaybackStatus.ERROR] now. */
    data object GiveUp : NextStep
}

/**
 * Whether to try again, and when.
 *
 * Two situations, and they used to be given opposite answers with nothing in between:
 *
 * - **A stream that was playing and dropped** gets AC-PLAY-06's three backed-off attempts —
 *   unchanged.
 * - **A stream that has not yet played** was never retried at all, whatever went wrong. That is
 *   right for a 404 and wrong for the commonest refusal there is: an Xtream panel answering
 *   403, 458 or 509 because the account's one connection slot has not been released yet — right
 *   after zapping from another channel, or while another screen still holds it. A second try
 *   two seconds later usually works. [PlaybackError.PROVIDER_REFUSED] and
 *   [PlaybackError.TIMEOUT] now get up to [INITIAL_LOAD_RETRIES] attempts, and only while there
 *   is still time for one inside the AC-PLAY-05 budget the watchdog enforces.
 *
 * Terminal errors are never retried in either situation: a 404 will still be a 404, a rejected
 * password will still be rejected, and v1 will still not support DRM.
 *
 * @param retriesSoFar automatic retries already made for this item.
 * @param elapsedMillis time since the item was handed to the engine.
 */
internal fun nextStep(
    error: PlaybackError,
    hasEverBeenReady: Boolean,
    retriesSoFar: Int,
    elapsedMillis: Long,
): NextStep {
    if (error in TERMINAL_ERRORS) return NextStep.GiveUp

    val attempt = retriesSoFar + 1
    return if (hasEverBeenReady) {
        if (attempt > MAX_RETRIES) NextStep.GiveUp else NextStep.RetryAfter(RETRY_BASE_DELAY_MILLIS * attempt, attempt)
    } else {
        val delayMillis = INITIAL_RETRY_BASE_DELAY_MILLIS * attempt
        val worthTrying = error in RETRIED_BEFORE_FIRST_FRAME &&
            attempt <= INITIAL_LOAD_RETRIES &&
            elapsedMillis + delayMillis < INITIAL_LOAD_TIMEOUT_MILLIS
        if (worthTrying) NextStep.RetryAfter(delayMillis, attempt) else NextStep.GiveUp
    }
}

/** AC-PLAY-05: a dead stream must surface an error inside this budget. */
internal const val INITIAL_LOAD_TIMEOUT_MILLIS = 12_000L

/** What to do when the initial load has run out of time without a playable frame. */
internal sealed interface LoadTimeoutStep {

    /** Data is still arriving: look again in [delayMillis]. */
    data class WaitMore(val delayMillis: Long) : LoadTimeoutStep

    /** The server sent data and then went quiet: close the connection and load the item again. */
    data object Reload : LoadTimeoutStep

    /** Report the timeout now. */
    data object GiveUp : LoadTimeoutStep
}

/**
 * Whether a film or an episode that has not started in time is dead, or only slow (`BUG-062`).
 *
 * The watchdog used to fail everything at [INITIAL_LOAD_TIMEOUT_MILLIS]. For a film that is the
 * wrong answer more often than not: the panel answered and sent data, the engine was still reading
 * — the header, then the index a Matroska or MP4 file keeps at its far end, each one more request
 * to a panel that is slow to answer it — and the viewer was told *Not sure* about a film that would
 * have played. The engine never got to report anything, because its own read timeout is longer than
 * the budget, so there was no error to retry on either.
 *
 * AC-PLAY-05 is about a dead or unreachable stream, and one that has sent data is neither:
 *
 * - **Live, or nothing received** — the error at the budget, as before.
 * - **Data still arriving** — wait, in steps, up to [VOD_LOAD_LIMIT_MILLIS].
 * - **Data received, then nothing for [VOD_STALL_MILLIS]** — one fresh load, given a full budget of
 *   its own. A request that a panel left hanging is usually answered the second time.
 * - **Stalled again, or out of time** — the error.
 *
 * @param bytesReceived media bytes received for this item so far.
 * @param millisSinceLastByte time since the last of them arrived.
 * @param elapsedMillis time since the item was handed to the engine.
 * @param reloadsSoFar reloads this decision has already asked for, for this item.
 */
internal fun afterLoadTimeout(
    isLive: Boolean,
    bytesReceived: Long,
    millisSinceLastByte: Long,
    elapsedMillis: Long,
    reloadsSoFar: Int,
): LoadTimeoutStep {
    val timeLeft = VOD_LOAD_LIMIT_MILLIS - elapsedMillis
    return when {
        isLive || bytesReceived == 0L || timeLeft <= 0L -> LoadTimeoutStep.GiveUp
        millisSinceLastByte < VOD_STALL_MILLIS -> LoadTimeoutStep.WaitMore(minOf(VOD_PROGRESS_CHECK_MILLIS, timeLeft))
        reloadsSoFar < VOD_STALL_RELOADS && timeLeft >= INITIAL_LOAD_TIMEOUT_MILLIS -> LoadTimeoutStep.Reload
        else -> LoadTimeoutStep.GiveUp
    }
}

/** The longest a film or an episode that is receiving data is waited for before it starts. */
internal const val VOD_LOAD_LIMIT_MILLIS = 40_000L

/** No data for this long, after some arrived, is a stall rather than a slow server. */
internal const val VOD_STALL_MILLIS = 4_000L

/** How often a film still receiving data is looked at again. */
internal const val VOD_PROGRESS_CHECK_MILLIS = 2_000L

/** Fresh loads after a stall, per item. */
internal const val VOD_STALL_RELOADS = 1

/** AC-PLAY-06: attempts for a stream that dropped after it had played. */
internal const val MAX_RETRIES = 3
internal const val RETRY_BASE_DELAY_MILLIS = 1_500L

/** Attempts for a stream that has not played yet, at 2 s then 4 s, inside the budget above. */
internal const val INITIAL_LOAD_RETRIES = 2
internal const val INITIAL_RETRY_BASE_DELAY_MILLIS = 2_000L

private val TERMINAL_ERRORS = setOf(
    PlaybackError.SOURCE_GONE,
    PlaybackError.AUTH_REJECTED,
    PlaybackError.UNSUPPORTED_FORMAT,
    PlaybackError.DRM_UNSUPPORTED,
)

private val RETRIED_BEFORE_FIRST_FRAME = setOf(
    PlaybackError.PROVIDER_REFUSED,
    PlaybackError.TIMEOUT,
)

private val GONE_STATUSES = setOf(404, 410)

private val AUTH_STATUSES = setOf(401)

/**
 * Statuses a provider answers with when it is refusing *this request* rather than saying the
 * stream does not exist.
 *
 * 403 is the usual connection-limit answer; 458 and 509 are the private ones some Xtream panels
 * use for "too many connections" and "bandwidth exceeded"; 429 and the XC_VM firewall's 46x
 * family are the anti-flood answers `XtreamClient` already names for the API.
 */
private val REFUSED_STATUSES = setOf(403, 429, 458, 460, 461, 462, 463, 469, 509)

private val SERVER_ERROR_STATUSES = 500..599

/**
 * Whether [failure] is a live stream fallen behind its window, to be rejoined at the live edge
 * rather than retried (`BUG-036`).
 *
 * `BEHIND_LIVE_WINDOW` is what the engine reports after a stall, a pause or a return from the
 * background on a live HLS channel: the position it was asked to resume from has scrolled out of
 * the playlist. It used to be classified `UNKNOWN` and retried by preparing at that same position
 * — which failed the same way, three times, and then showed an error on a channel that was fine.
 * The remedy is to jump to the live edge, and it is not a failure, so it does not spend a retry.
 *
 * Bounded at [MAX_LIVE_EDGE_REJOINS] between plays, so a server whose window is broken still ends
 * in an error rather than a loop.
 */
internal fun rejoinsLiveEdge(failure: EngineFailure, rejoinsSoFar: Int): Boolean =
    failure.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && rejoinsSoFar < MAX_LIVE_EDGE_REJOINS

internal const val MAX_LIVE_EDGE_REJOINS = 3

/** The HLS playlist type, as Media3 names it (`MimeTypes.APPLICATION_M3U8`). */
internal const val HLS_MIME_TYPE = "application/x-mpegURL"

/**
 * HLS when the URL says so anywhere, not only at the end of its path (`BUG-044`).
 *
 * The engine decides how to read a stream from its path's extension alone. A playlist served from
 * `…/index.m3u8` is recognised; one served from `play.php?file=index.m3u8`, `…/stream?output=m3u8`
 * or `…/hls/` is not, and goes down the progressive path, where no extractor recognises a
 * playlist and the load fails as an unsupported container. Null leaves the engine to decide.
 */
internal fun hlsMimeTypeFor(url: String): String? {
    val lower = url.lowercase()
    val path = lower.substringBefore('?').substringBefore('#')
    val query = lower.substringAfter('?', missingDelimiterValue = "")
    return HLS_MIME_TYPE.takeIf { path.endsWith(".m3u8") || HLS_IN_QUERY.containsMatchIn(query) }
}

/**
 * Whether a failed load is worth one more try as HLS (`BUG-044`).
 *
 * Only when nobody said what the stream was, the path gives no recognisable extension, the engine
 * read data and recognised no container in it — and only once. A probe request would answer the
 * question up front, but costs a connection, and on an account allowed one that is the connection.
 */
internal fun retriesAsHls(errorCode: Int, declaredMimeType: String?, url: String, alreadyTried: Boolean): Boolean =
    !alreadyTried &&
        declaredMimeType == null &&
        errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED &&
        hasNoKnownExtension(url)

private fun hasNoKnownExtension(url: String): Boolean {
    val name = url.lowercase().substringBefore('?').substringBefore('#').substringAfterLast('/')
    val extension = name.substringAfterLast('.', missingDelimiterValue = "")
    return extension !in KNOWN_EXTENSIONS
}

/** `m3u8` as the value of any query parameter, or as a file named in one. */
private val HLS_IN_QUERY = Regex("""(^|[&=.])m3u8($|&)""")

/** Containers the engine already recognises by name; a stream ending in one of these is what it says. */
private val KNOWN_EXTENSIONS = setOf(
    "m3u8", "mpd", "ism", "isml", "ts", "mp4", "m4v", "m4a", "mkv", "webm", "mov", "avi", "flv",
    "mp3", "aac", "ac3", "ogg", "wav",
)
