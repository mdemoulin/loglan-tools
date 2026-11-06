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

/**
 * The list directory, laid out as ~/.loglan is: the lists, the auxiliary
 * ones in aux/, derivs and m1-text beside them, and each list's progress in
 * <list>.state.json.
 */
class Library(val dir: File) {
    /** A list in the directory, with its size and whether it has progress saved. */
    data class Table(val name: String, val entries: Int?, val progressSaved: Boolean)

    /**
     * What lists there are: the ones loglan-update builds first, in the order
     * one learns them; then any other auxiliary list, any other list that
     * has progress filed here, and each troublesome list once :t has made it
     * (which loglan-teach --tables leaves out until it has progress).
     */
    fun tables(): List<Table> {
        val own = sortedSetOf<String>()
        dir.list()?.forEach { f -> f.removeSuffix(".state.json").takeIf { it != f }?.let(own::add) }
        File(dir, "aux").listFiles()?.forEach { f ->
            if (f.isFile && !f.name.startsWith(".") && !f.name.endsWith(".old")) own += f.name
        }
        TROUBLESOME.filter { File(dir, it).isFile }.forEach(own::add)
        own.removeAll(KNOWN.toSet())
        return (KNOWN + own).mapNotNull { t ->
            val file = listFile(t)
            val saved = stateFile(t).isFile
            when {
                file != null -> Table(t, file.readLines().count { !blankOrComment.containsMatchIn(it) }, saved)
                saved -> Table(t, null, true)
                else -> null
            }
        }
    }

    fun listFile(name: String): File? =
        listOf(File(dir, name), File(dir, "aux/$name")).firstOrNull { it.isFile }

    fun stateFile(name: String) = File(dir, "$name.state.json")

    /** The troublesome list a list's cards go on: one each for the utts, the words and the affixes. */
    fun troublesome(list: String) = File(dir, troublesomeFor(list))

    val derivs: Map<String, Derivation> by lazy {
        File(dir, "derivs").takeIf { it.isFile }?.useLines(block = ::parseDerivs) ?: emptyMap()
    }

    val lessons: Map<Int, String>? by lazy {
        File(dir, "m1-text").takeIf { it.isFile }?.let { parseLessons(it.readLines()) }
    }

    fun cards(name: String, field: String = "gloss"): List<Card> =
        listFile(name)?.let { parseWords(it.readLines(), field) } ?: emptyList()

    /** What :t did: the list it used, the rows it put on it, and the ones already there. */
    data class Troubled(val list: String, val added: List<String>, val already: List<String>)

    /**
     * The card, from [list], goes on that list's troublesome list, to be
     * drilled on its own -- an animal or a people with the rest of its set
     * (cervu cerva cervo cervi cerve), as aux/animals and aux/ethnic have it.
     */
    fun addTroublesome(card: Card, list: String): Troubled {
        var rows = listOf(card.line)
        val stem = Regex("[aeiou]\\z").replaceFirst(card.loglan, "")
        val member = Regex(Regex.escape(stem) + "[aeiou]")
        if (!card.sentence) {
            for (set in listOf("animals", "ethnic").map { File(dir, "aux/$it") }) {
                if (!set.isFile) continue
                val members = listLines(set.readLines()).filter { member.matches(it.split('\t')[0]) }
                if (members.any { it.split('\t')[0] == card.loglan }) {
                    rows = members
                    break
                }
            }
        }
        val file = troublesome(list)
        val added = appendRows(file, rows)
        val new = added.map(::rowKey).toSet()
        return Troubled(file.name, added.map { it.split('\t')[0] },
                        rows.filter { rowKey(it) !in new }.map { it.split('\t')[0] })
    }

    /** Adds to a list the rows it has not got yet, making it if need be; returns the ones added. */
    private fun appendRows(file: File, rows: List<String>): List<String> {
        val have = (if (file.isFile) listLines(file.readLines()) else emptyList()).map(::rowKey).toMutableSet()
        val add = rows.filter { have.add(rowKey(it)) }
        if (add.isEmpty()) return add
        val text = StringBuilder()
        if (!file.exists()) {
            text.append("# Cards that keep tripping you up, drilled on their own: loglan-teach ${file.name}\n")
                .append("# Put here by :t in a drill; an animal or a people comes with its whole set.\n")
        }
        add.forEach { text.append(it).append('\n') }
        file.appendText(text.toString())
        return add
    }

    /**
     * The one troublesome list there was before there were three: its
     * utterances go to utts-troublesome and its words to words-troublesome,
     * each with the progress it had, and it is kept as troublesome.old.
     */
    fun splitTroublesome() {
        val old = File(dir, "troublesome")
        if (!old.isFile) return
        val (utts, words) = listLines(old.readLines()).partition { splitTabs(it).size < 3 }
        val state = stateFile("troublesome")
        for ((name, rows) in listOf("utts-troublesome" to utts, "words-troublesome" to words)) {
            if (rows.isEmpty()) continue
            appendRows(File(dir, name), rows)
            if (state.isFile && !stateFile(name).isFile) state.copyTo(stateFile(name))
        }
        state.delete()
        old.renameTo(File(dir, "troublesome.old"))
    }

    companion object {
        val TROUBLESOME = listOf("utts-troublesome", "words-troublesome", "affixes-troublesome", "lws-troublesome")

        /**
         * Two copies of a list, as one: theirs, with the rows mine has that
         * it has not on the end -- as loglan-teach merges a troublesome list
         * added to on two machines.
         */
        fun mergeLists(mine: String, theirs: String): String {
            val have = listLines(theirs.lines()).map(::rowKey).toMutableSet()
            val add = listLines(mine.lines()).filter { have.add(rowKey(it)) }
            if (add.isEmpty()) return theirs
            val text = StringBuilder(theirs)
            if (text.isNotEmpty() && !text.endsWith("\n")) text.append('\n')
            add.forEach { text.append(it).append('\n') }
            return text.toString()
        }

        /** utts-troublesome for utts, affixes-troublesome for affs and affsets, lws-troublesome for lws, words-troublesome for the rest. */
        fun troublesomeFor(list: String) = when {
            list.startsWith("utts") -> "utts-troublesome"
            list.startsWith("aff") -> "affixes-troublesome"
            list.startsWith("lws") -> "lws-troublesome"
            else -> "words-troublesome"
        }

        val KNOWN = listOf("core", "prims", "affs", "affsets", "utts", "lws", "animals", "ethnic", "coined", "l1prims",
                         "lws-variables", "lws-discursives", "lws-indicators", "lws-punctuation", "lws-connectives", "lws-causal", "lws-tense", "lws-location", "lws-descriptors", "lws-quantifiers", "lws-modals", "lws-case-tags", "lws-alphabet", "lws-register-markers", "lws-aux-verbs")
        private val blankOrComment = Regex("""^\s*(?:#|\z)""")
    }
}
