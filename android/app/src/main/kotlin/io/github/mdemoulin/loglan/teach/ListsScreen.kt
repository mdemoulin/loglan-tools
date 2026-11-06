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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mdemoulin.loglan.drill.Direction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(model: TeachModel, openSettings: () -> Unit) {
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var about by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Loglan Teach") },
                actions = {
                    TextButton(onClick = openSettings) { Text("Settings") }
                    TextButton(onClick = { about = true }) { Text("About") }
                },
            )
        },
    ) { padding ->
        val lists = model.lists
        if (lists == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val grouped = listGroups.map { g -> g to g.lists.mapNotNull { n -> lists.find { it.name == n } } }
            .filter { (_, rows) -> rows.isNotEmpty() }
        val rest = lists.filter { row -> listGroups.none { row.name in it.lists } }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            grouped.forEach { (g, rows) ->
                val open = g.title !in model.collapsed
                item(key = "group:${g.title}") { GroupHeading(g, open) { model.toggleGroup(g.title) } }
                if (open) items(rows, key = { it.name }) { row -> ListRowItem(row) { chosen = row.name } }
            }
            if (rest.isNotEmpty()) item(key = "rest") { Spacer(Modifier.height(16.dp)) }
            items(rest, key = { it.name }) { row -> ListRowItem(row) { chosen = row.name } }
        }
    }
    chosen?.let { name -> StartDialog(model, name) { chosen = null } }
    if (about) AboutDialog { about = false }
}

@Composable
private fun GroupHeading(g: ListGroup, open: Boolean, toggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = toggle)
                   .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                 color = MaterialTheme.colorScheme.primary)
            Text(if (open) "\u25BE" else "\u25B8", style = MaterialTheme.typography.titleLarge,
                 color = MaterialTheme.colorScheme.primary)
        }
        Text(g.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

@Composable
private fun ListRowItem(row: ListRow, choose: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = choose),
        headlineContent = { Text(row.name) },
        supportingContent = {
            Column {
                listAbout[row.name]?.let { Text(it) }
                Text(progressLine(row), style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.primary)
            }
        },
    )
    HorizontalDivider()
}

private fun progressLine(row: ListRow) = buildString {
    append("${row.cards} cards")
    if (row.seen > 0) append(" · ${row.seen} seen, ${row.graduated} graduated, ${row.errorBox} in the error box")
}

/** How to drill the list: which way, and for some lists what. */
@Composable
private fun StartDialog(model: TeachModel, name: String, dismiss: () -> Unit) {
    val last = remember(name) { model.options(name) }
    var direction by remember(name) { mutableStateOf(last.direction) }
    var field by remember(name) { mutableStateOf(last.field) }
    var random by remember(name) { mutableStateOf(last.random) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Choice("Show the card in", listOf(Direction.RECOGNITION to "Loglan", Direction.RECALL to "English",
                                                  Direction.BOTH to "Either"), direction) { direction = it }
                if (name.startsWith("aff")) {
                    Choice("Answer with", listOf("gloss" to "Meaning", "affix" to "Affixes"), field) { field = it }
                }
                if (name == "utts") {
                    Choice("Utterances", listOf(false to "In order", true to "At random"), random) { random = it }
                    if (!random) Text("In order, with each lesson of MacTeach 1 before the utterances it goes with; " +
                                      "one you miss is asked again before the next lesson.",
                                      style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                dismiss()
                model.start(name, last.copy(direction = direction, field = field, random = random))
            }) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(label: String, choices: List<Pair<T, String>>, value: T, set: (T) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            choices.forEachIndexed { i, (v, text) ->
                SegmentedButton(selected = v == value, onClick = { set(v) },
                                shape = SegmentedButtonDefaults.itemShape(i, choices.size)) { Text(text) }
            }
        }
    }
}

@Composable
private fun AboutDialog(dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Loglan Teach") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("A flashcard drill for Loglan, the logical language James Cooke Brown began in 1955: " +
                     "a ladder of rungs a card climbs as you answer it, and an error box it must answer " +
                     "its way out of when you miss.  From loglan-tools, " +
                     "https://github.com/mdemoulin/loglan-tools.")
                Text("The program is free software, under the GNU General Public License, version 3 or later.  " +
                     "Copyright (C) 2026 Michael A. Demoulin.")
                Text("The word lists, the utterances and the lessons are Loglan material: all rights are " +
                     "reserved by The Loglan Center, Inc. (the Idaho corporation which has superseded the " +
                     "Florida corporation), which allows and encourages noncommercial use of it by others.  " +
                     "They are drawn from Lavinia Randall Holmes's Loglan-to-English dictionary, Loglan 1, " +
                     "and the Loglan Institute's LoglanTeach and MacTeach programs, published at " +
                     "https://randall-holmes.github.io/Loglan/cefli.html.")
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("OK") } },
    )
}
