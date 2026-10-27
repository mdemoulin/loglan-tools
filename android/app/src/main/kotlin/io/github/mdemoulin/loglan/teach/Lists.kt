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

import android.content.Context
import io.github.mdemoulin.loglan.drill.Library
import java.io.File

/**
 * The list directory in the app's own storage, laid out as ~/.loglan is.
 * The lists the app ships (built by loglan-update when the app was built)
 * are put there when it is first run, and again when a new version of the
 * app ships changed ones; progress and troublesome are never touched.
 */
fun installLists(context: Context): Library {
    val dir = File(context.filesDir, "loglan")
    val assets = context.assets
    val shipped = assets.open("loglan/VERSION").bufferedReader().use { it.readText() }
    val version = File(dir, "VERSION")
    if (!version.isFile || version.readText() != shipped) {
        fun copy(path: String) {
            val children = assets.list(path).orEmpty()
            if (children.isNotEmpty()) {
                children.forEach { copy("$path/$it") }
                return
            }
            if (path == "loglan/VERSION") return
            val file = File(context.filesDir, path)
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            assets.open(path).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(file)
        }
        copy("loglan")
        // Last, so a copy cut short is made again next time.
        version.writeText(shipped)
    }
    return Library(dir)
}

/** What each list is, as the README has it. */
val listAbout = mapOf(
    "core" to "the everyday primitives, and the set members formed by rule",
    "prims" to "every primitive in the dictionary",
    "affs" to "every primitive or little word with affixes",
    "affsets" to "prims and affs together",
    "utts" to "sentences from the Institute's LoglanTeach and MacTeach, with lessons",
    "animals" to "the animal declension: -u generic, -a female, -o male, -i young, -e -like",
    "ethnic" to "the ethnic declension: -a language, -e territory, -i person, -o culture",
    "l1prims" to "the primitives of Loglan 1, chapters 1-7",
    "troublesome" to "the cards you have put aside to work on",
)
