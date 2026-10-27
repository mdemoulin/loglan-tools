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

class TeachModel(app: Application) : AndroidViewModel(app) {
    private var library: Library? = null
    private val prefs = app.getSharedPreferences("options", Context.MODE_PRIVATE)

    var lists by mutableStateOf<List<ListRow>?>(null)
        private set
    var drill by mutableStateOf<DrillView?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            lists = withContext(Dispatchers.IO) {
                val lib = library ?: installLists(getApplication()).also { library = it }
                lib.tables().filter { it.entries != null }.map { t ->
                    val cards = lib.cards(t.name)
                    val p = Progress.load(lib.stateFile(t.name), t.name)
                    val items = cards.mapNotNull { p.items[it.key] }
                    ListRow(t.name, cards.size, items.size,
                            items.count { it.inError == 0 && it.rung > 5 }, items.count { it.inError != 0 })
                }.filter { it.cards > 0 }
            }
        }
    }

    /** The options last used for a list. */
    fun options(name: String) = Options(
        direction = Direction.valueOf(prefs.getString("$name.direction", null) ?: Direction.RECOGNITION.name),
        field = prefs.getString("$name.field", null) ?: "gloss",
        random = prefs.getBoolean("$name.random", false),
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
