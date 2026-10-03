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

package dev.quiblo.feature.player

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.quiblo.core.data.diagnostics.Diagnosis
import dev.quiblo.core.data.diagnostics.Side
import dev.quiblo.core.data.diagnostics.Verdict
import dev.quiblo.source.api.AccountHealth
import java.text.DateFormat
import java.util.Date

/*
 * The words for a verdict (`FEAT-035`), one set for both apps — the same rule as [messageRes],
 * for the same reason: a second copy of these sentences is a second thing to translate and a
 * second place for the phone and the television to disagree.
 *
 * Every sentence names a side, and only the side the evidence supports. See `verdict()`.
 */

/** Whose side, as a heading a viewer can read from across the room. */
@StringRes
fun Side.labelRes(): Int = when (this) {
    Side.CONNECTION -> R.string.player_side_connection
    Side.SUBSCRIPTION -> R.string.player_side_subscription
    Side.PROVIDER -> R.string.player_side_provider
    Side.QUIBLO -> R.string.player_side_quiblo
    Side.UNKNOWN -> R.string.player_side_unknown
}

/** One icon per side, so the heading is readable at a glance before a word of it is. */
fun Side.icon(): ImageVector = when (this) {
    Side.CONNECTION -> Icons.Filled.WifiOff
    Side.SUBSCRIPTION -> Icons.Filled.CardMembership
    Side.PROVIDER -> Icons.Filled.CloudOff
    Side.QUIBLO -> Icons.Filled.BugReport
    Side.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
}

/**
 * The one sentence that says what went wrong.
 *
 * Two verdicts carry figures from the account check — the date a subscription ended, the screens
 * in use — and fall back to a sentence without them when the panel did not send them.
 */
@Composable
fun Diagnosis.headline(): String {
    val ok = account as? AccountHealth.Ok
    return when (verdict) {
        Verdict.SUBSCRIPTION_EXPIRED ->
            (account as? AccountHealth.Expired)?.atEpochMillis
                ?.let { stringResource(R.string.player_verdict_expired_on, formatDate(it)) }
                ?: stringResource(R.string.player_verdict_expired)

        Verdict.CONNECTION_LIMIT -> {
            val active = ok?.activeConnections
            val allowed = ok?.maxConnections
            if (active != null && allowed != null) {
                stringResource(R.string.player_verdict_limit_count, active, allowed)
            } else {
                stringResource(R.string.player_verdict_limit)
            }
        }

        // A playlist has no account to vouch for, so the sentence does not vouch for one.
        Verdict.CHANNEL_OFFLINE -> stringResource(
            if (ok != null) R.string.player_verdict_channel_offline else R.string.player_verdict_channel_missing,
        )

        else -> stringResource(verdict.headlineRes())
    }
}

@StringRes
private fun Verdict.headlineRes(): Int = when (this) {
    Verdict.DEVICE_OFFLINE -> R.string.player_verdict_offline
    Verdict.SUBSCRIPTION_EXPIRED -> R.string.player_verdict_expired
    Verdict.ACCOUNT_DISABLED -> R.string.player_verdict_disabled
    Verdict.CREDENTIALS_REJECTED -> R.string.player_verdict_credentials
    Verdict.CONNECTION_LIMIT -> R.string.player_verdict_limit
    Verdict.PROVIDER_DOWN -> R.string.player_verdict_provider_down
    Verdict.PROVIDER_BLOCKING -> R.string.player_verdict_blocking
    Verdict.CHANNEL_OFFLINE -> R.string.player_verdict_channel_missing
    Verdict.FORMAT_UNSUPPORTED -> R.string.player_verdict_format
    Verdict.APP_ERROR -> R.string.player_verdict_app
    Verdict.UNDETERMINED -> R.string.player_verdict_undetermined
}

/** The one thing worth doing about it. */
@StringRes
fun Verdict.adviceRes(): Int = when (this) {
    Verdict.DEVICE_OFFLINE -> R.string.player_advice_offline
    Verdict.SUBSCRIPTION_EXPIRED -> R.string.player_advice_expired
    Verdict.ACCOUNT_DISABLED -> R.string.player_advice_disabled
    Verdict.CREDENTIALS_REJECTED -> R.string.player_advice_credentials
    Verdict.CONNECTION_LIMIT -> R.string.player_advice_limit
    Verdict.PROVIDER_DOWN -> R.string.player_advice_provider_down
    Verdict.PROVIDER_BLOCKING -> R.string.player_advice_blocking
    Verdict.CHANNEL_OFFLINE -> R.string.player_advice_channel_offline
    Verdict.FORMAT_UNSUPPORTED, Verdict.APP_ERROR -> R.string.player_advice_report
    Verdict.UNDETERMINED -> R.string.player_advice_undetermined
}

/** In the viewer's own date format, because a date is read, not parsed. */
private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
