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

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The sentence notation of the Institute's MacTeach, which the utterance
 * list keeps (see "MacTeach sentence notation" in loglan-teach):
 *
 *     space      ends a word group;  '-' joins words within one ("brother-of")
 *     a/b        alternatives
 *     + and &    close a set of alternatives, '+' with a space after it and '&'
 *                without:  "is/s+a/the+brother-of" is (is|'s) (a|the) brother of
 *     = and :    open one after fixed words, '=' with a space and ':' without
 *     (x)        optional;  (*x) optional, but shown in the sentence as displayed
 *     \c         c taken literally
 *     |note      the rest of an English line is a note;  [note] in the Loglan
 *
 * A sentence compiles to a pattern rather than a list of wordings.  Answers
 * are compared with case, spaces, hyphens and punctuation ignored, Loglan
 * commas excepted.
 */
object MacTeach {
    /** A sentence compiled: the pattern a folded reply must match, how it is shown, and its note. */
    class Sentence(val pattern: String, val show: String, val note: String) {
        val regex = Regex(pattern)
    }

    private val loglanNote = Regex("""\s*\[(.*?)\]\s*""")
    private val englishNote = Regex("""(?<!\\)\|(.*)\z""", RegexOption.DOT_MATCHES_ALL)
    private val escaped = Regex("""\\(.)""")

    /** The text without its note, and the note. */
    fun splitNote(text: String): Pair<String, String> {
        val m = loglanNote.find(text) ?: englishNote.find(text)
            ?: return text to ""
        val note = escaped.replace(m.groupValues[1]) { it.groupValues[1] }
        return text.removeRange(m.range) to note
    }

    /** What an answer is compared on: letters and digits, and in Loglan the pause commas of its grammar. */
    fun fold(s: String, loglan: Boolean): String =
        s.lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' || (loglan && it == ',') }

    /** The pattern and the displayed text of a sentence (without its note). */
    fun compile(text: String, loglan: Boolean): Pair<String, String> {
        val (re, show0) = Parser(text, loglan).sentence()
        val show = show0.replace(Regex("""\s+"""), " ")
            .replace(Regex(""" ([,.!?;:])"""), "$1")
            .removePrefix(" ").removeSuffix(" ")
        return re to show
    }

    private val cache = ConcurrentHashMap<Pair<String, Boolean>, Sentence>()

    fun sentence(text: String, loglan: Boolean): Sentence = cache.getOrPut(text to loglan) {
        val (body, note) = splitNote(text)
        val (re, show) = compile(body, loglan)
        Sentence(re, show, note)
    }

    fun matches(reply: String, text: String, loglan: Boolean): Boolean =
        sentence(text, loglan).regex.matches(fold(reply, loglan))

    // Recursive descent over the sentence; each part returns (pattern, displayed text).
    private class Parser(val c: String, val loglan: Boolean) {
        var i = 0

        fun peek(): String = if (i < c.length) c[i].toString() else ""

        // Groups separated by spaces.
        fun sentence(): Pair<String, String> {
            var (re, show) = group()
            while (peek() == " ") {
                while (peek() == " ") i++
                if (peek() == "" || peek() == ")") break
                val (r, s) = group()
                re += r
                show += " $s"
            }
            return re to show
        }

        // Sets joined by + (with a space) and & (without).
        fun group(): Pair<String, String> {
            var (re, show) = seg()
            while (peek() == "+" || peek() == "&") {
                val sep = if (c[i++] == '+') " " else ""
                val (r, s) = seg()
                re += r
                show += sep + s
            }
            return re to show
        }

        // A set of alternatives.
        fun seg(): Pair<String, String> {
            val alts = mutableListOf(alt())
            while (peek() == "/") {
                i++
                alts += alt()
            }
            return if (alts.size == 1) alts[0]
                   else alts.joinToString("|", "(?:", ")") { it.first } to alts[0].second
        }

        // Fixed text, then perhaps = or : and a set.
        fun alt(): Pair<String, String> {
            var re = ""
            var show = ""
            while (true) {
                var ch = peek()
                if (ch == "" || ch in listOf(" ", "+", "&", "/", ")")) break
                if (ch == "=" || ch == ":") {
                    i++
                    val (r, s) = seg()
                    return (re + r) to (show + (if (ch == "=") " " else "") + s)
                }
                i++
                if (ch == "(") {
                    val star = peek() == "*"
                    if (star) i++
                    val (r, s) = sentence()
                    if (peek() == ")") i++
                    re += "(?:$r)?"
                    if (star) show += s
                    continue
                }
                if (ch == "\\") ch = if (i < c.length) c[i++].toString() else "".also { i++ }
                if (ch == "-") ch = " "
                show += ch
                re += fold(ch, loglan).map { if (it.isLetterOrDigit()) "$it" else "\\$it" }.joinToString("")
            }
            return re to show
        }
    }
}
