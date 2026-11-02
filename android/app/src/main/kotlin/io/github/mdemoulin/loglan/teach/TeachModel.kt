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

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mdemoulin.loglan.drill.Direction
import io.github.mdemoulin.loglan.drill.Drill
import io.github.mdemoulin.loglan.drill.Library
import io.github.mdemoulin.loglan.drill.Options
import io.github.mdemoulin.loglan.drill.Outcome
import io.github.mdemoulin.loglan.drill.Progress
import io.github.mdemoulin.loglan.drill.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A list on the first screen: how big it is and how far along. */
data class ListRow(val name: String, val cards: Int, val seen: Int, val graduated: Int, val errorBox: Int)

/** A drill under way, as the screen shows it. */
data class DrillView(
    val drill: Drill,
    val question: Question,
    /** The card before, graded. */
    val last: Outcome.Graded? = null,
    /** What a command said (:s, :t, ...). */
    val said: List<String> = emptyList(),
    /** The lessons still to be read before the card. */
    val lessons: List<Pair<Int, String>> = question.lessons,
)

/** What goes for every list: loglan-teach's --rungs, --escape and --no-derivs. */
data class Settings(val rungs: Int = 5, val escape: Int = 2, val derivs: Boolean = true)

class TeachModel(app: Application) : AndroidViewModel(app) {
    private var library: Library? = null
    private val prefs = app.getSharedPreferences("options", Context.MODE_PRIVATE)

    var lists by mutableStateOf<List<ListRow>?>(null)
        private set
    var settings by mutableStateOf(Settings(
        rungs = prefs.getInt("rungs", Settings().rungs),
        escape = prefs.getInt("escape", Settings().escape),
        derivs = prefs.getBoolean("derivs", Settings().derivs),
    ))
        private set
    var drill by mutableStateOf<DrillView?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        val rungs = settings.rungs
        viewModelScope.launch {
            lists = withContext(Dispatchers.IO) {
                val lib = library ?: installLists(getApplication()).also { library = it }
                lib.tables().filter { it.entries != null }.map { t ->
                    val cards = lib.cards(t.name)
                    val p = Progress.load(lib.stateFile(t.name), t.name)
                    val items = cards.mapNotNull { p.items[it.key] }
                    ListRow(t.name, cards.size, items.size,
                            items.count { it.inError == 0 && it.rung > rungs },
                            items.count { it.inError != 0 })
                }.filter { it.cards > 0 }
            }
        }
    }

    private val syncStore = SyncStore(app)
    var sync by mutableStateOf(syncStore.config())
        private set
    var syncKey by mutableStateOf(syncStore.key())
        private set
    var hostKey by mutableStateOf(syncStore.hostKey(sync))
        private set

    // Changed over adb (AdbProvider) while the app is open: shown at once.
    private val syncChanged = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        sync = syncStore.config()
        syncKey = syncStore.key()
        hostKey = syncStore.hostKey(sync)
    }.also(syncStore.prefs::registerOnSharedPreferenceChangeListener)

    fun changeSync(c: SyncConfig) {
        sync = c
        syncStore.save(c)
        hostKey = syncStore.hostKey(c)
    }

    fun makeSyncKey() {
        syncKey = syncStore.newKey(sync.device)
    }

    fun forgetHostKey() {
        syncStore.forgetHostKey()
        hostKey = null
    }

    fun changeSettings(s: Settings) {
        settings = s
        prefs.edit()
            .putInt("rungs", s.rungs)
            .putInt("escape", s.escape)
            .putBoolean("derivs", s.derivs)
            .apply()
        refresh()
    }

    /** The options last used for a list, with the settings. */
    fun options(name: String) = Options(
        direction = Direction.valueOf(prefs.getString("$name.direction", null) ?: Direction.RECOGNITION.name),
        rungs = settings.rungs,
        escape = settings.escape,
        field = prefs.getString("$name.field", null) ?: "gloss",
        random = prefs.getBoolean("$name.random", false),
        derivs = settings.derivs,
    )

    fun start(name: String, options: Options) {
        prefs.edit()
            .putString("$name.direction", options.direction.name)
            .putString("$name.field", options.field)
            .putBoolean("$name.random", options.random)
            .apply()
        viewModelScope.launch {
            drill = withContext(Dispatchers.IO) {
                val lib = library ?: return@withContext null
                val file = lib.stateFile(name)
                val d = Drill(name, lib.cards(name, options.field), Progress.load(file, name), options, lib,
                              save = { it.save(file) })
                DrillView(d, d.question)
            }
        }
    }

    fun reply(text: String) {
        val v = drill ?: return
        // :l shows the lesson on a page of its own, as the lessons before a card are.
        if (text.trim().lowercase() in setOf(":l", ":1")) {
            v.drill.lessonsFor(v.question.card)?.takeIf { it.isNotEmpty() }?.let {
                drill = v.copy(lessons = it, said = emptyList())
                return
            }
        }
        when (val o = v.drill.reply(text)) {
            is Outcome.Graded -> drill = DrillView(v.drill, v.drill.question, last = o)
            // :r (no answer) says nothing, and asks the card before: shown as
            // a new card, with the one graded before it as it was.
            is Outcome.Said -> drill = if (o.lines.isEmpty()) DrillView(v.drill, v.drill.question, v.last)
                                       else v.copy(said = o.lines)
            Outcome.Quit -> quit()
        }
    }

    fun lessonRead() {
        drill = drill?.let { it.copy(lessons = it.lessons.drop(1)) }
    }

    fun dismissSaid() {
        drill = drill?.copy(said = emptyList())
    }

    fun quit() {
        drill?.drill?.reply(":q")
        drill = null
        refresh()
    }
}
