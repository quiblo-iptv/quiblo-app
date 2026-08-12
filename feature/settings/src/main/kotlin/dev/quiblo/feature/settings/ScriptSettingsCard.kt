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

package dev.quiblo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quiblo.core.common.TitleScript

/**
 * Which writing systems the viewer does not read (INC-F14).
 *
 * A subtraction, and it says so. The alternative design — "show only English" — promises the
 * data cannot keep: an Arabic film released under a transliterated Latin title is
 * indistinguishable from an English one by anything a playlist carries, so a filter phrased
 * as a selection quietly loses titles the viewer wanted. Phrased as a subtraction, the filter
 * only ever removes what it can positively read, which is a promise it keeps every time.
 *
 * The reset is here rather than only in the chips because a viewer who has hidden something
 * and cannot find a film needs one control that undoes all of it without remembering what
 * they picked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScriptSettingsCard(
    hidden: Set<TitleScript>,
    onSetHidden: (TitleScript, Boolean) -> Unit,
    onShowEverything: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_scripts_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_scripts_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TitleScript.offered.forEach { script ->
                    FilterChip(
                        selected = script in hidden,
                        onClick = { onSetHidden(script, script !in hidden) },
                        label = { Text(stringResource(script.labelRes())) },
                    )
                }
            }

            if (hidden.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.settings_scripts_hidden_count, hidden.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                TextButton(onClick = onShowEverything) {
                    Text(stringResource(R.string.settings_scripts_show_all))
                }
            }
        }
    }
}

/**
 * Named for what a viewer would call it, not for the Unicode block.
 *
 * "Chinese" and "Japanese" and "Korean" are the words people use for Han, Kana and Hangul, and
 * a settings screen that says "Hangul" is asking the viewer to know the answer already. The
 * exceptions are Devanagari and Cyrillic, where the script genuinely spans several languages
 * and naming one of them would be wrong.
 */
private fun TitleScript.labelRes(): Int = when (this) {
    TitleScript.Latin -> R.string.settings_script_latin
    TitleScript.Arabic -> R.string.settings_script_arabic
    TitleScript.Hebrew -> R.string.settings_script_hebrew
    TitleScript.Cyrillic -> R.string.settings_script_cyrillic
    TitleScript.Greek -> R.string.settings_script_greek
    TitleScript.Han -> R.string.settings_script_han
    TitleScript.Kana -> R.string.settings_script_kana
    TitleScript.Hangul -> R.string.settings_script_hangul
    TitleScript.Devanagari -> R.string.settings_script_devanagari
    TitleScript.Thai -> R.string.settings_script_thai
}
