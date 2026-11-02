// Part of loglan-tools <https://github.com/mdemoulin/loglan-tools>.
// Copyright (C) 2026  Michael A. Demoulin <35350967+mdemoulin@users.noreply.github.com>
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

package io.github.mdemoulin.loglan.teach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The settings that go for every list; each takes effect from the next drill started. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(model: TeachModel, done: () -> Unit) {
    val s = model.settings
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = done) { Text("Done") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text("Rungs") },
                supportingContent = {
                    Text("How many rungs a card climbs before it has graduated.  Fewer, and the cards " +
                         "already past the new top rung count as graduated; more, and they come back.")
                },
                trailingContent = { Stepper(s.rungs, 1..20) { model.changeSettings(s.copy(rungs = it)) } },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Error box") },
                supportingContent = {
                    Text("How many right in a row take a missed card out of the error box, back to the " +
                         "first rung.")
                },
                trailingContent = { Stepper(s.escape, 1..10) { model.changeSettings(s.copy(escape = it)) } },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Derivations") },
                supportingContent = { Text("After each answer, show where the word came from.") },
                trailingContent = {
                    Switch(checked = s.derivs, onCheckedChange = { model.changeSettings(s.copy(derivs = it)) })
                },
            )
            HorizontalDivider()
            TextButton(onClick = { model.changeSettings(Settings()) }, enabled = s != Settings(),
                       modifier = Modifier.padding(8.dp)) { Text("Back to the defaults") }
        }
    }
}

@Composable
private fun Stepper(value: Int, range: IntRange, set: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { set(value - 1) }, enabled = value > range.first) { Text("−") }
        Text("$value", Modifier.widthIn(min = 28.dp), style = MaterialTheme.typography.titleMedium,
             textAlign = TextAlign.Center)
        OutlinedButton(onClick = { set(value + 1) }, enabled = value < range.last) { Text("+") }
    }
}
