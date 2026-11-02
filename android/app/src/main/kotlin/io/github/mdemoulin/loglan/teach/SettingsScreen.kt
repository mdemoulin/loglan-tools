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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mdemoulin.loglan.sync.SshKey

/** The settings that go for every list (each takes effect from the next drill started), and the sync. */
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
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())) {
            Heading("Drill")
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
            Heading("Sync")
            SyncSection(model)
        }
    }
}

@Composable
private fun Heading(title: String) {
    Text(title, Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
         style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider()
}

/**
 * Where the progress is kept in step with loglan-teach's: the Subversion
 * repository, reached over ssh with the app's own key.
 */
@Composable
private fun SyncSection(model: TeachModel) {
    val c = model.sync
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Progress is kept in step with loglan-teach's through the Subversion repository it syncs " +
             "with, reached over ssh: svnserve on the server, as for every other machine.",
             style = MaterialTheme.typography.bodyMedium)
        Field("Server", c.host, "its address, as 192.168.0.245 (a .local name is not looked up)",
              KeyboardType.Uri) { model.changeSync(c.copy(host = it)) }
        // Kept as typed, so it can be emptied on the way to another number.
        var port by rememberSaveable { mutableStateOf(c.port.toString()) }
        Field("Port", port, "ssh's port on the server; empty is 22", KeyboardType.Number) { v ->
            port = v.filter(Char::isDigit).take(5)
            val n = if (port.isEmpty()) 22 else port.toInt()
            if (n in 1..65535) model.changeSync(c.copy(port = n))
        }
        Field("User", c.user, "who you are on the server", KeyboardType.Ascii) { model.changeSync(c.copy(user = it)) }
        Field("Repository", c.repo, "the path under svnserve's root, as /loglan-state/trunk",
              KeyboardType.Uri) { model.changeSync(c.copy(repo = it)) }
        Field("This phone", c.device, "its name in commit messages, and on its key",
              KeyboardType.Text) { model.changeSync(c.copy(device = it)) }
        if (c.complete) {
            SelectionContainer {
                Text(c.url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    HorizontalDivider()
    KeyItem(model)
    HorizontalDivider()
    HostKeyItem(model)
    HorizontalDivider()
}

@Composable
private fun Field(label: String, value: String, about: String, type: KeyboardType, set: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = set,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text(about) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = type,
        ),
    )
}

/** The app's key: made here, and given to the server for its authorized_keys. */
@Composable
private fun KeyItem(model: TeachModel) {
    val key = model.syncKey
    val context = LocalContext.current
    var replacing by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("This phone's key", style = MaterialTheme.typography.titleMedium)
        if (key == null) {
            Text("The app reaches the server with an ssh key of its own, with no passphrase, which " +
                 "does nothing there but svn.")
            OutlinedButton(onClick = model::makeSyncKey) { Text("Make a key") }
            return@Column
        }
        val line = authorizedKeysLine(key)
        Text(key.fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        Text("On the server, add this line to ~/.ssh/authorized_keys of the user above (-r is the " +
             "directory your repositories are in, if it is not /svn):")
        SelectionContainer {
            Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("authorized_keys line", line))
            }) { Text("Copy") }
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, line)
                    .putExtra(Intent.EXTRA_SUBJECT, "loglan-sync key: ${key.comment}")
                context.startActivity(Intent.createChooser(send, "Send the key line"))
            }) { Text("Share") }
            OutlinedButton(onClick = { replacing = true }) { Text("New key") }
        }
    }
    if (replacing) {
        AlertDialog(
            onDismissRequest = { replacing = false },
            title = { Text("Make a new key?") },
            text = { Text("The key there is now is put away for good: the server has to be given the new " +
                          "one before the app can sync again, and the old one's line taken out.") },
            confirmButton = { TextButton(onClick = { replacing = false; model.makeSyncKey() }) { Text("Make it") } },
            dismissButton = { TextButton(onClick = { replacing = false }) { Text("Cancel") } },
        )
    }
}

/** The server's own key, which the first sync records, and every one after holds the server to. */
@Composable
private fun HostKeyItem(model: TeachModel) {
    val host = model.hostKey
    ListItem(
        headlineContent = { Text("The server's key") },
        supportingContent = {
            if (host == null) {
                Text("Not seen yet: the first sync records it, and after that the app syncs only with the " +
                     "server that has it.")
            } else {
                Text(SshKey.fingerprint(host), fontFamily = FontFamily.Monospace)
            }
        },
        trailingContent = { if (host != null) TextButton(onClick = model::forgetHostKey) { Text("Forget") } },
    )
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
