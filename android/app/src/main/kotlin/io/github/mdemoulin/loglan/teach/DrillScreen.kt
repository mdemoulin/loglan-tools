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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.mdemoulin.loglan.drill.Drill
import io.github.mdemoulin.loglan.drill.Outcome

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillScreen(model: TeachModel, view: DrillView) {
    var status by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    val drill = view.drill
    val q = view.question
    view.lessons.firstOrNull()?.let { (n, text) ->
        LessonPage(n, text, model::lessonRead)
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(drill.name) },
                navigationIcon = { TextButton(onClick = model::quit) { Text("Done") } },
                actions = {
                    TextButton(onClick = { status = true }) { Text("Status") }
                    TextButton(onClick = { help = true }) { Text("Help") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            view.last?.let { Graded(it) }
            q.notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }
            SelectionContainer {
                Text(q.prompt, style = MaterialTheme.typography.headlineMedium)
            }
            Answer(view, loglan = !q.recognition, submit = model::reply)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { model.reply("") }) { Text("Show answer") }
                OutlinedButton(onClick = { model.reply(":r") }, enabled = drill.hasPrevious) { Text("Ask the last again") }
                OutlinedButton(onClick = { model.reply(":t") }, enabled = drill.hasPrevious) { Text("Last is troublesome") }
                if (drill.inOrder) OutlinedButton(onClick = { model.reply(":l") }) { Text("Lesson") }
            }
            if (view.said.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        view.said.forEach { Text(it, fontFamily = FontFamily.Monospace) }
                        TextButton(onClick = model::dismissSaid) { Text("OK") }
                    }
                }
            }
        }
    }
    if (status) StatusDialog(drill) { status = false }
    if (help) HelpDialog { help = false }
}

/** The answer box: Enter grades what is in it; a blank Enter shows the answer. */
@Composable
private fun Answer(card: Any, loglan: Boolean, submit: (String) -> Unit) {
    var text by remember(card) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(card) { focus.requestFocus() }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        label = { Text(if (loglan) "Loglan" else "English") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None,
                                          autoCorrectEnabled = false, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { submit(text) }),
    )
}

/** The card before, graded: ok and where it went on the ladder, or "no" in reverse video; then the answer. */
@Composable
private fun Graded(g: Outcome.Graded) {
    val colors = when (g.right) {
        true -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        false -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        null -> CardDefaults.cardColors()
    }
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(g.question.prompt, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (g.right) {
                    true -> Text("ok", fontWeight = FontWeight.Bold)
                    false -> Text(" no ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.surface,
                                  modifier = Modifier.background(MaterialTheme.colorScheme.onSurface))
                    null -> Text("(shown)")
                }
                if (g.reply.isNotEmpty()) Text("“${g.reply}”")
                g.ladder?.let { Text("[$it]", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            SelectionContainer {
                Column { g.answer.forEach { Text(it, style = MaterialTheme.typography.titleMedium) } }
            }
        }
    }
}

@Composable
private fun StatusDialog(drill: Drill, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Status") },
        text = { Column { drill.statusLines().drop(1).forEach { Text(it.trimStart(), fontFamily = FontFamily.Monospace) } } },
        confirmButton = { TextButton(onClick = dismiss) { Text("OK") } },
    )
}

@Composable
private fun HelpDialog(dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Help") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type the answer and press Send.  Any one of a gloss's alternatives counts " +
                     "(\"box/crate/carton\"), and an X-like takes \"Xish\" as well.  Send with nothing " +
                     "typed, or Show answer, to see the answer (it counts as a miss).")
                Text("A card climbs a rung each time you have it; one you miss goes to the error box, " +
                     "which it leaves after two right in a row, back on the first rung.  Past the top " +
                     "rung it has graduated.")
                Text("The commands of loglan-teach can be typed too:")
                Drill.COMMANDS.dropLast(1).forEach { Text(it.trim(), fontFamily = FontFamily.Monospace) }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("OK") } },
    )
}
