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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// The Kotlin drill held to what loglan-teach itself makes of every list
// (golden/make-golden, run by the build before the tests).
class GoldenTest {
    private val golden: JsonObject = Json.parseToJsonElement(File(System.getProperty("golden")).readText()).jsonObject
    private val lists = File(System.getProperty("lists"))

    private val JsonObject.str get() = { k: String -> getValue(k).jsonPrimitive.content }

    /** Every mismatch, not just the first, so one run shows the whole of a difference. */
    private class Misses {
        val list = mutableListOf<String>()
        fun <T> check(what: String, perl: T, kotlin: T) {
            if (perl != kotlin) list += "$what\n    perl:   $perl\n    kotlin: $kotlin"
        }
        fun assertNone() = assertTrue(list.isEmpty(), "${list.size} differences:\n" + list.take(40).joinToString("\n"))
    }

    @Test
    fun listsAreReadAlike() {
        val m = Misses()
        for ((which, recs) in golden.getValue("records").jsonObject) {
            val (name, field) = which.split('/')
            val file = listOf(File(lists, name), File(lists, "aux/$name")).first { it.isFile }
            val cards = parseWords(file.readLines(), field)
            val perl = recs.jsonArray.map { it.jsonObject }
            m.check("$which: how many", perl.size, cards.size)
            for ((p, c) in perl.zip(cards)) {
                val s = p.str
                m.check("$which ${c.index}", listOf(s("loglan"), s("answer"), s("key"), s("line"),
                                                     p.getValue("sentence").jsonPrimitive.boolean, p.getValue("index").jsonPrimitive.int,
                                                     p.getValue("lessons").jsonArray.map { it.jsonPrimitive.int }),
                        listOf(c.loglan, c.answer, c.key, c.line, c.sentence, c.index, c.lessons))
            }
        }
        m.assertNone()
    }

    @Test
    fun glossesTakeTheSameAnswers() {
        val m = Misses()
        val answers = golden.getValue("answers").jsonArray.map { it.jsonObject }
        assertTrue(answers.size > 1000)
        for (a in answers) {
            val expected = a.str("expected")
            m.check("accepted for '$expected'", a.getValue("accepted").jsonArray.map { it.jsonPrimitive.content },
                    acceptedAnswers(expected))
            for ((reply, ok) in a.getValue("replies").jsonObject) {
                m.check("'$reply' for '$expected'", ok.jsonPrimitive.boolean, matchAnswer(reply, expected))
            }
        }
        m.assertNone()
    }

    @Test
    fun sentencesCompileAlike() {
        val m = Misses()
        val sentences = golden.getValue("sentences").jsonArray.map { it.jsonObject }
        assertTrue(sentences.size > 300)
        for (s in sentences) {
            for ((side, loglan) in listOf("loglan" to true, "english" to false)) {
                val g = s.getValue(side).jsonObject
                val text = g.str("text")
                val (body, note) = MacTeach.splitNote(text)
                m.check("note of '$text'", g.str("body") to g.str("note"), body to note)
                val (pattern, show) = MacTeach.compile(body, loglan)
                m.check("pattern of '$text'", g.str("pattern"), pattern)
                m.check("shown for '$text'", g.str("show"), show)
                m.check("fold of '$show'", g.str("fold"), MacTeach.fold(show, loglan))
                for ((reply, ok) in g.getValue("replies").jsonObject) {
                    m.check("'$reply' for '$text'", ok.jsonPrimitive.boolean, MacTeach.matches(reply, text, loglan))
                }
            }
        }
        m.assertNone()
    }

    @Test
    fun ishIsSpelledAlike() {
        for ((x, perl) in golden.getValue("ish").jsonObject) assertEquals(perl.jsonPrimitive.content, ish(x), x)
    }
}
