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

package dev.quiblo.core.data.diagnostics

import dev.quiblo.source.api.AccountHealth

/**
 * Whose side a playback failure is on (`FEAT-035`).
 *
 * The owner's requirement, in his words: when a stream fails, the message must say whether *my
 * subscription ended*, *the provider is down*, or *the app is broken*. A viewer whose subscription
 * expired yesterday used to be told "this stream is no longer available at that address" — the
 * same words as a dead channel or a Quiblo bug — and sent to argue with the wrong party.
 */
enum class Side {
    CONNECTION,
    SUBSCRIPTION,
    PROVIDER,
    QUIBLO,
    UNKNOWN,
}

/** What the evidence says, one case per sentence the viewer can be told. */
enum class Verdict(val side: Side) {
    DEVICE_OFFLINE(Side.CONNECTION),
    SUBSCRIPTION_EXPIRED(Side.SUBSCRIPTION),
    ACCOUNT_DISABLED(Side.SUBSCRIPTION),
    CREDENTIALS_REJECTED(Side.SUBSCRIPTION),
    CONNECTION_LIMIT(Side.SUBSCRIPTION),
    PROVIDER_DOWN(Side.PROVIDER),
    PROVIDER_BLOCKING(Side.PROVIDER),
    CHANNEL_OFFLINE(Side.PROVIDER),
    FORMAT_UNSUPPORTED(Side.QUIBLO),
    APP_ERROR(Side.QUIBLO),
    UNDETERMINED(Side.UNKNOWN),
}

/**
 * The kind of failure the stream itself reported, without any engine type in it.
 *
 * `:core:data` does not know the player exists, so the player's own error is translated into
 * this at the boundary and nothing here depends on how the engine names things.
 */
enum class StreamFault {
    /** The server answered with an error status; see [StreamEvidence.httpStatus]. */
    BAD_STATUS,

    /** The host could not be resolved, or refused the connection. */
    UNREACHABLE,

    /** Connected — or tried to — and nothing usable arrived in time. */
    TIMEOUT,

    /** The connection broke for some other transport reason. */
    NETWORK,

    /** Data arrived and could not be parsed or decoded. */
    FORMAT,

    /** The stream is encrypted. */
    DRM,

    /** Anything the engine reported that is none of the above. */
    OTHER,
}

/**
 * What the stream said when it failed.
 *
 * @property bytesReceived media bytes that arrived from the network for this item.
 * @property hadPlayed whether the item played at all before failing.
 */
data class StreamEvidence(
    val fault: StreamFault,
    val httpStatus: Int? = null,
    val engineCode: String? = null,
    val bytesReceived: Long = 0L,
    val hadPlayed: Boolean = false,
    val retries: Int = 0,
) {
    /** True when the server demonstrably sent something. */
    val receivedData: Boolean get() = bytesReceived > 0L || hadPlayed
}

/** What is known about the account behind the stream. */
sealed interface AccountEvidence {

    /** The source has no account to ask about — a playlist is a file of URLs. */
    data object NotApplicable : AccountEvidence

    /** It has one, and the question produced no answer. */
    data object Unavailable : AccountEvidence

    /** The provider answered. */
    data class Answered(val health: AccountHealth) : AccountEvidence
}

/**
 * The verdict the evidence supports, and nothing beyond it.
 *
 * **Never blame a side without evidence.** A verdict naming the subscription or the provider needs
 * a positive answer from the account check. A verdict naming Quiblo needs proof that the account
 * is fine *and* that the stream answered with data. Anything less is [Verdict.UNDETERMINED], with
 * the raw details shown beside it. A wrong verdict is worse than none: it sends a viewer to argue
 * with the wrong party.
 *
 * A playlist has no account to ask, so only what the stream itself said can stand: a 401 is a
 * rejected credential whoever asks, a host that cannot be reached while the device is online is
 * the provider's, and a 404 is a channel that is not there. Everything else is undetermined.
 */
fun verdict(deviceOnline: Boolean, account: AccountEvidence, stream: StreamEvidence): Verdict = when {
    !deviceOnline -> Verdict.DEVICE_OFFLINE
    account is AccountEvidence.Answered -> accountVerdict(account.health, stream)
    account == AccountEvidence.NotApplicable -> playlistVerdict(stream)
    else -> Verdict.UNDETERMINED
}

/** The account check outranks the stream: an expired account explains whatever the stream said. */
private fun accountVerdict(health: AccountHealth, stream: StreamEvidence): Verdict = when (health) {
    is AccountHealth.Expired -> Verdict.SUBSCRIPTION_EXPIRED
    AccountHealth.Disabled -> Verdict.ACCOUNT_DISABLED
    AccountHealth.CredentialsRejected -> Verdict.CREDENTIALS_REJECTED
    AccountHealth.Unreachable, is AccountHealth.ServerError -> Verdict.PROVIDER_DOWN
    AccountHealth.Blocked -> Verdict.PROVIDER_BLOCKING
    is AccountHealth.Ok -> healthyAccountVerdict(health, stream)
}

/**
 * The account is fine, so the stream's own answer decides — and only some answers decide anything.
 *
 * A refusal is a connection limit only when the panel *also* says every screen is in use; a
 * refusal from an account with screens to spare is not explained by anything known, and is left
 * undetermined rather than guessed at.
 */
private fun healthyAccountVerdict(health: AccountHealth.Ok, stream: StreamEvidence): Verdict = when {
    health.isAtConnectionLimit && stream.httpStatus in CONNECTION_LIMIT_STATUSES -> Verdict.CONNECTION_LIMIT
    stream.httpStatus in GONE_STATUSES -> Verdict.CHANNEL_OFFLINE
    stream.fault == StreamFault.TIMEOUT && !stream.receivedData -> Verdict.CHANNEL_OFFLINE
    stream.receivedData && stream.fault in FORMAT_FAULTS -> Verdict.FORMAT_UNSUPPORTED
    stream.receivedData && stream.fault == StreamFault.OTHER -> Verdict.APP_ERROR
    else -> Verdict.UNDETERMINED
}

private fun playlistVerdict(stream: StreamEvidence): Verdict = when {
    stream.httpStatus == HTTP_UNAUTHORIZED -> Verdict.CREDENTIALS_REJECTED
    stream.fault == StreamFault.UNREACHABLE -> Verdict.PROVIDER_DOWN
    stream.httpStatus in GONE_STATUSES -> Verdict.CHANNEL_OFFLINE
    else -> Verdict.UNDETERMINED
}

private const val HTTP_UNAUTHORIZED = 401

private val GONE_STATUSES = setOf(404, 410)

/** What a panel answers a stream request with when the account has no screen left. */
private val CONNECTION_LIMIT_STATUSES = setOf(403, 429, 458, 509)

private val FORMAT_FAULTS = setOf(StreamFault.FORMAT, StreamFault.DRM)
