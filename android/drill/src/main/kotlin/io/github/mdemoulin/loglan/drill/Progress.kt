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

package io.github.mdemoulin.loglan.drill

import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// A list's progress: the <list>.state.json loglan-teach keeps, in the same
// form, so a file can go from one to the other.

/** Where one card stands: on a rung of the ladder, or in the error box. */
@Serializable
data class Item(
    var correct: Int = 0,
    @SerialName("in_error") var inError: Int = 0,
    var rung: Int = 1,
    var seen: Int = 0,
    var streak: Int = 0,
)

/** The place in a list taught in order (utts, with its lessons). */
@Serializable
data class Order(
    var next: Int = 0,
    var pass: Int = 1,
    var retry: MutableList<String> = mutableListOf(),
    var retrying: Int? = null,
)

/** The shorter ladder of a list being brushed up, once every card has graduated. */
@Serializable
data class Brushup(val rungs: Int = 2, val escape: Int = 1)

@Serializable
data class Progress(
    @SerialName("data_file") var dataFile: String? = null,
    var items: MutableMap<String, Item> = mutableMapOf(),
    @SerialName("lessons_shown") var lessonsShown: MutableMap<String, Int>? = null,
    var order: Order? = null,
    /** How many times the cards have been wiped (a reset, a brush-up begun): the copy wiped more often wins a merge. */
    var resets: Int? = null,
    var brushup: Brushup? = null,
    var version: Int = 1,
) {
    fun encode(): String = json.encodeToString(
        serializer(),
        copy(items = items.toSortedMap(), lessonsShown = lessonsShown?.toSortedMap()),
    ) + "\n"

    /** Written whole and then put in place, so a file is never left half written. */
    fun save(file: File) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(encode())
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "cannot rename $tmp to $file" }
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val json = Json {
            prettyPrint = true
            prettyPrintIndent = "   "
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun decode(text: String): Progress =
            runCatching { json.decodeFromString(serializer(), text) }.getOrDefault(Progress())

        fun load(file: File, dataFile: String): Progress =
            if (file.isFile) decode(file.readText()) else Progress(dataFile = dataFile)

        /**
         * Two copies of one list's progress, as one: each card as the copy that
         * has seen it more often has it ([mine], on a tie); the lessons either
         * has shown; and the place in an in-order list that is further on.
         * A copy wiped more often than the other is taken whole, with the
         * lessons either has shown, so the cards the other still has do not
         * come back.
         */
        fun merge(mine: Progress, theirs: Progress): Progress {
            val (rm, rt) = (mine.resets ?: 0) to (theirs.resets ?: 0)
            if (rm != rt) {
                val (newer, older) = if (rm > rt) mine to theirs else theirs to mine
                val shown = if (newer.lessonsShown == null && older.lessonsShown == null) null
                            else ((older.lessonsShown ?: emptyMap()) + (newer.lessonsShown ?: emptyMap())).toMutableMap()
                return newer.copy(dataFile = mine.dataFile ?: theirs.dataFile, items = newer.items.toMutableMap(),
                                  lessonsShown = shown, version = mine.version)
            }
            val items = theirs.items.toMutableMap()
            for ((k, a) in mine.items) {
                val t = theirs.items[k]
                if (t == null || a.seen >= t.seen) items[k] = a
            }
            val shown = if (mine.lessonsShown == null && theirs.lessonsShown == null) null
                        else ((theirs.lessonsShown ?: emptyMap()) + (mine.lessonsShown ?: emptyMap())).toMutableMap()
            val om = mine.order
            val ot = theirs.order
            val further = om != null && ot != null &&
                compareValuesBy(ot, om, { it.pass }, { it.next }) > 0
            return Progress(
                dataFile = mine.dataFile ?: theirs.dataFile,
                items = items,
                lessonsShown = shown,
                order = if (further) ot else om ?: ot,
                resets = mine.resets,
                brushup = mine.brushup ?: theirs.brushup,
                version = mine.version,
            )
        }
    }
}
