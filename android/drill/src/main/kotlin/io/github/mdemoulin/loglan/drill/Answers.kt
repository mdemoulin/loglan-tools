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

// Grading a word's gloss, as loglan-teach does (accepted_answers and the
// subs after it there).

private val like = Regex("""(.+) like""", RegexOption.IGNORE_CASE)

/**
 * Every spelling the drill will take for [expected], which is also what it
 * lists back after each card: what the list says, and, for an "X-like" (the
 * -e of an animal), "Xish" -- nothing else is made up here.
 */
fun acceptedAnswers(expected: String): List<String> {
    val gloss = mutableListOf<String>()
    val seen = HashSet<String>()
    for (alt in expected.split('/', '|')) {
        val a = perlTrim(alt)
        if (a.isEmpty()) continue
        for (v in expandOptionals(a)) {
            val n = normalize(v)
            if (n.isEmpty()) continue
            // "sheep like", "sheeplike" and "sheep-like" are one answer.
            if (seen.add(matchKey(n))) gloss += n
            val m = like.matchEntire(n) ?: continue
            val ish = ish(m.groupValues[1])
            if (seen.add(matchKey(ish))) gloss += ish
        }
    }
    return gloss
}

fun matchAnswer(reply: String, expected: String): Boolean {
    val k = matchKey(normalize(reply))
    return acceptedAnswers(expected).any { matchKey(it) == k }
}

/** Neither case nor spacing counts, so "fish-like" takes "Fishlike". */
internal fun matchKey(s: String): String = s.lowercase(Locale.ROOT).replace(" ", "")

private val silentE = Regex("""(?<=[^e])e\z""", RegexOption.IGNORE_CASE)
private val doubled = Regex("""((?:^| )[^aeiou ]*[aeiou])([b-df-hj-np-tvz])\z""", RegexOption.IGNORE_CASE)

/**
 * "X" + "ish", spelled as English spells it: a silent e goes ("horsish"),
 * and the last consonant of a one-syllable word ending in one vowel and one
 * consonant is doubled ("rattish", "guinea piggish"), but not w, x or y
 * ("cowish").
 */
fun ish(x: String): String {
    val stem = if (silentE.containsMatchIn(x)) silentE.replaceFirst(x, "")
               else doubled.find(x)?.let { x.replaceRange(it.range, it.value + it.groupValues[2]) } ?: x
    return "${stem}ish"
}

private val breaks = Regex("""[-_\s]+""")

/** Hyphens, underscores and runs of whitespace all count as one word break. */
internal fun normalize(s: String): String =
    breaks.replace(s, " ").removePrefix(" ").removeSuffix(" ")

private val optional = Regex("""(.*?)\(([^()]*)\)(.*)""", RegexOption.DOT_MATCHES_ALL)

/**
 * "vapo(u)r" -> ("vapor", "vapour"): parenthesised letters are optional.  A
 * dash left dangling at either end after removal ("Loglan-(language)" ->
 * "Loglan-", "(house)-fly" -> "-fly") is dropped too.
 */
internal fun expandOptionals(s: String): List<String> {
    var out = listOf(s)
    while (true) {
        var changed = false
        out = out.flatMap { v ->
            val m = optional.matchEntire(v) ?: return@flatMap listOf(v)
            changed = true
            val (pre, inside, post) = m.destructured
            var without = pre + post
            if (post.isEmpty()) without = without.removeSuffix("-")
            if (pre.isEmpty()) without = without.removePrefix("-")
            listOf(without, pre + inside + post)
        }
        if (!changed) return out
    }
}
