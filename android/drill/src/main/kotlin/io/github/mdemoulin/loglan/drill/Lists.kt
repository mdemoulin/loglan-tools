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

// Reading the lists, as loglan-teach reads them (parse_words, load_lessons,
// load_derivs, read_lines, row_key there).

/**
 * One card of a list.  Progress is kept by [key]: the Loglan word -- or, for
 * a sentence, the Loglan and the English together, since some sentences come
 * twice, with a literal and an idiomatic English, and each is a card of its
 * own.  [lessons] are the lessons of m1-text marked just before it ("# {7}").
 */
data class Card(
    val loglan: String,
    val answer: String,
    val sentence: Boolean,
    val key: String,
    val line: String,
    val index: Int,
    val lessons: List<Int> = emptyList(),
)

/** Perl's split on tabs: trailing empty fields are dropped. */
internal fun splitTabs(line: String): List<String> =
    line.split('\t').dropLastWhile { it.isEmpty() }

internal fun perlTrim(s: String): String = s.trim { it.isWhitespace() }

private val lessonMark = Regex("""\s*#\s*\{(\d+)\}\s*""")
private val comment = Regex("""^\s*#""")
private val number = Regex("""\d+""")

/**
 * Row layout: <loglan> \t <affix> \t <gloss> \t <place-notes>, or, for
 * sentences, just <loglan> \t <english>.  A line starting with '#' is
 * commented out; "# {7}" puts lesson 7 before the next sentence.  [field] is
 * "gloss", or "affix" to drill a list's affixes.
 */
fun parseWords(lines: List<String>, field: String = "gloss"): List<Card> {
    val out = mutableListOf<Card>()
    val lessons = mutableListOf<Int>()
    for (line in lines) {
        if (line.isEmpty()) continue
        val mark = lessonMark.matchEntire(line)
        if (mark != null) {
            lessons += mark.groupValues[1].toInt()
            continue
        }
        if (comment.containsMatchIn(line)) continue
        val f = splitTabs(line)
        val loglan = f.getOrNull(0)?.let(::perlTrim) ?: continue
        val affix = if (f.size >= 3) perlTrim(f[1]) else null
        val gloss = (if (f.size >= 3) f[2] else f.getOrNull(1))?.let(::perlTrim)
        if (loglan.isEmpty() || number.matches(loglan)) continue
        val answer = (if (field == "affix") affix else gloss) ?: continue
        if (answer.isEmpty() || answer == "-") continue
        val sentence = f.size < 3
        out += Card(loglan, answer, sentence,
                    key = if (sentence) "$loglan\t$answer" else loglan,
                    line = line, index = out.size, lessons = lessons.toList())
        lessons.clear()
    }
    return out
}

/** m1-text: "{n}" on a line of its own starts lesson n; the '#' lines at the top are its header. */
fun parseLessons(lines: List<String>): Map<Int, String> {
    val text = linkedMapOf<Int, StringBuilder>()
    var n: Int? = null
    val start = Regex("""\{(\d+)\}\s*""")
    for (line in lines) {
        val m = start.matchEntire(line)
        if (m != null) {
            n = m.groupValues[1].toInt()
            text[n] = StringBuilder()
            continue
        }
        text[n ?: continue]!!.append(line).append('\n')
    }
    val trailing = Regex("""\s+\z""")
    return text.mapValues { (_, t) -> trailing.replaceFirst(t.toString(), "\n") }
}

/** Where a word came from, as mk-utility writes the derivs table. */
data class Derivation(val base: String, val from: String) {
    /** "ladzo  [L. ...]", or just the one there is; null when there is neither. */
    fun line(): String? {
        var line = if (base == "-") "" else base
        if (from != "-") line += if (line.isNotEmpty()) "  [$from]" else from
        return line.ifEmpty { null }
    }
}

/** <word> \t <the word it came from, or '-'> \t <the words that one was built from, or '-'> */
fun parseDerivs(lines: Sequence<String>): Map<String, Derivation> {
    val d = HashMap<String, Derivation>()
    for (line in lines) {
        val f = splitTabs(line)
        if (f.size < 3 || f[0].isEmpty()) continue
        d[f[0]] = Derivation(f[1], f[2])
    }
    return d
}

/** A list's lines, the comments and empty ones left out. */
fun listLines(lines: List<String>): List<String> =
    lines.filter { it.isNotEmpty() && !comment.containsMatchIn(it) }

/** What a row is known by: the Loglan word, or a sentence with its English. */
fun rowKey(line: String): String {
    val f = splitTabs(line).map(::perlTrim)
    return if (f.size < 3) "${f.getOrElse(0) { "" }}\t${f.getOrElse(1) { "" }}" else f[0]
}
