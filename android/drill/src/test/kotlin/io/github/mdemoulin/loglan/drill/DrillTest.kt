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
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrillTest {
    private val words = parseWords(listOf(
        "bakso\tbao\tbox/crate/carton\tof/containing..",
        "kangu\tkan\tdog\tof breed..",
        "kange\t-\tdog-like\tis canine..",
    ))

    private fun drill(cards: List<Card> = words, progress: Progress = Progress(dataFile = "t"),
                      options: Options = Options(), library: Library? = null,
                      lessons: Map<Int, String>? = null, seed: Int = 1) =
        Drill("t", cards, progress, options, library, lessons, Random(seed))

    private fun Drill.answerRight(): Outcome {
        val q = question
        return reply(if (q.recognition) acceptedAnswers(q.card.answer).first() else q.card.loglan)
    }

    @Test
    fun aRightAnswerClimbsAndAWrongOneGoesToTheErrorBox() {
        val d = drill()
        val key = d.question.card.key
        val ok = assertIs<Outcome.Graded>(d.answerRight())
        assertEquals(true, ok.right)
        assertEquals("rung 1->2", ok.ladder)
        assertEquals(Item(correct = 1, rung = 2, seen = 1), d.progress.items[key])

        val q = d.question
        val no = assertIs<Outcome.Graded>(d.reply("not it"))
        assertEquals(false, no.right)
        assertEquals(Item(inError = 1, rung = 0, seen = d.progress.items[q.card.key]!!.seen,
                          correct = d.progress.items[q.card.key]!!.correct), d.progress.items[q.card.key])
    }

    @Test
    fun twoRightLeaveTheErrorBox() {
        val one = words.take(1)
        val d = drill(one)
        d.reply("")
        assertEquals("error-box 1/2", assertIs<Outcome.Graded>(d.reply("crate")).ladder)
        assertEquals("error-box 2/2", assertIs<Outcome.Graded>(d.reply("Carton")).ladder)
        assertEquals(Item(correct = 2, inError = 0, rung = 1, seen = 3, streak = 0), d.progress.items["bakso"])
    }

    @Test
    fun aBlankShowsTheAnswerAndCountsAsAMiss() {
        val d = drill(words.take(1))
        val g = assertIs<Outcome.Graded>(d.reply("  "))
        assertNull(g.right)
        assertEquals(listOf("box / crate / carton"), g.answer)
        assertEquals(1, d.progress.items["bakso"]!!.inError)
    }

    @Test
    fun anXLikeTakesXish() {
        val d = drill(words.drop(2))
        val g = assertIs<Outcome.Graded>(d.reply("Doggish"))
        assertEquals(true, g.right)
        assertEquals(listOf("dog like / doggish"), g.answer)
    }

    @Test
    fun colonRAsksTheCardBeforeAgainThenThisOne() {
        val d = drill()
        assertIs<Outcome.Said>(d.reply(":r")).also { assertEquals(listOf("(nothing asked before this one yet)"), it.lines) }
        val first = d.question.card
        d.reply("wrong")
        val second = d.question.card
        d.reply(":R")
        assertEquals(first, d.question.card)
        d.answerRight()
        assertEquals(second, d.question.card)
    }

    @Test
    fun colonRWithAnAnswerGradesTheCardBeforeOnIt() {
        val d = drill()
        val first = d.question
        d.reply("wrong")
        val second = d.question.card
        val g = assertIs<Outcome.Graded>(d.reply(":r " + acceptedAnswers(first.card.answer).last()))
        assertEquals(first.card, g.question.card)
        assertEquals(true, g.right)
        assertEquals("error-box 1/2", g.ladder)
        assertEquals(second, d.question.card)
    }

    @Test
    fun commandsAreNotAnswers() {
        val d = drill()
        val q = d.question
        assertIs<Outcome.Said>(d.reply(":x")).also { assertEquals("(not a command:", it.lines[0]) }
        assertIs<Outcome.Said>(d.reply(":S"))
        assertEquals(q, d.question)
        assertTrue(d.progress.items.isEmpty())
        assertIs<Outcome.Quit>(d.reply(":q"))
    }

    @Test
    fun aBackslashIsASlip() {
        val d = drill(words.take(1))
        assertEquals(true, assertIs<Outcome.Graded>(d.reply("box\\")).right)
    }

    @Test
    fun graduatedCardsAreNotAskedUntilAllHave() {
        val p = Progress(items = mutableMapOf("bakso" to Item(rung = 6), "kangu" to Item(rung = 6)))
        repeat(20) { seed -> assertEquals("kange", drill(progress = p, seed = seed).question.card.loglan) }
        p.items["kange"] = Item(rung = 6)
        assertEquals(3, (0 until 40).map { drill(progress = p, seed = it).question.card.loglan }.toSet().size)
    }

    private val utts = parseWords(listOf(
        "# {1}",
        "Mi godzi\tI go.",
        "Tu godzi\tYou go/are-going.",
        "# {2}",
        "Mi hapci\tI am-happy.",
        "Tu nirne ho\t-|(ungrammatical)",
    ))
    private val lessonText = mapOf(1 to "Lesson one.\n", 2 to "Lesson two.\n")

    @Test
    fun aListWithLessonsIsTaughtInOrderWithMissesGoneOverBeforeTheNextLesson() {
        val d = drill(utts, lessons = lessonText)
        assertTrue(d.inOrder)
        assertEquals("Mi godzi", d.question.prompt)
        assertEquals(listOf(1 to "Lesson one.\n"), d.question.lessons)
        d.reply("I go")
        assertEquals("Tu godzi", d.question.card.loglan)
        assertEquals(emptyList(), d.question.lessons)
        d.reply("you went")
        // Missed: gone over before lesson 2 begins, until it is right.
        assertEquals("Tu godzi", d.question.card.loglan)
        assertEquals("retry", d.question.kind)
        assertEquals("(going over the ones missed, before going on)", d.question.notice)
        d.reply("nope")
        assertEquals("Tu godzi", d.question.card.loglan)
        assertNull(d.question.notice)
        d.reply("you are going")
        assertEquals("Mi hapci", d.question.card.loglan)
        assertEquals(listOf(2 to "Lesson two.\n"), d.question.lessons)
        d.reply("I am happy")
        assertEquals("Tu nirne ho", d.question.card.loglan)
        val g = assertIs<Outcome.Graded>(d.reply("-"))
        assertEquals(true, g.right)
        assertEquals(listOf("- (no translation)", "(ungrammatical)"), g.answer)
        assertEquals("---- That was all 4; from the top again (time 2) ----", d.question.notice)
        assertEquals(Order(next = 0, pass = 2), d.progress.order)
    }

    @Test
    fun randomDrillsALessonListOnTheLadder() {
        assertFalse(drill(utts, lessons = lessonText, options = Options(random = true)).inOrder)
        assertFalse(drill(utts, lessons = null).inOrder)
    }

    @Test
    fun colonTPutsTheCardBeforeOnTroublesomeWithItsSet() {
        val dir = Files.createTempDirectory("loglan").toFile()
        File(dir, "aux").mkdirs()
        File(dir, "aux/animals").writeText(
            "kangu\t-\tdog\t..\nkanga\t-\tbitch\t..\nkango\t-\tmale-dog\t..\nkangi\t-\tpuppy\t..\nkange\t-\tdog-like\t..\nkanguru\t-\tkangaroo\t..\n")
        val lib = Library(dir)
        val d = drill(words.drop(1).take(1), library = lib)
        assertEquals(listOf("(nothing asked before this one yet)"), assertIs<Outcome.Said>(d.reply(":t")).lines)
        d.reply("dog")
        assertEquals(listOf("(on troublesome now: kangu kanga kango kangi kange)"),
                     assertIs<Outcome.Said>(d.reply(":t")).lines)
        assertEquals(listOf("(on troublesome already: kangu kanga kango kangi kange)"),
                     assertIs<Outcome.Said>(d.reply(":t")).lines)
        val rows = listLines(lib.troublesome.readLines())
        assertEquals(5, rows.size)
        assertTrue(lib.troublesome.readText().startsWith("# Cards that keep tripping you up"))
        assertEquals(Library.Table("troublesome", 5, false), lib.tables().single { it.name == "troublesome" })
        dir.deleteRecursively()
    }

    @Test
    fun progressReadsAndWritesWhatLoglanTeachWrites() {
        val perl = """
            {
               "data_file" : "animals",
               "items" : {
                  "ansi" : {
                     "correct" : 0,
                     "in_error" : 1,
                     "rung" : 0,
                     "seen" : 3,
                     "streak" : 0
                  },
                  "ansa" : {
                     "correct" : 2,
                     "in_error" : 0,
                     "rung" : 1,
                     "seen" : 3,
                     "streak" : 0
                  }
               },
               "lessons_shown" : {
                  "1" : 1
               },
               "order" : {
                  "next" : 4,
                  "pass" : 1,
                  "retry" : [
                     "Tu godzi\tYou go."
                  ],
                  "retrying" : 1
               },
               "version" : 1
            }
        """.trimIndent()
        val p = Progress.decode(perl)
        assertEquals(Item(correct = 2, rung = 1, seen = 3), p.items["ansa"])
        assertEquals(Order(next = 4, retry = mutableListOf("Tu godzi\tYou go."), retrying = 1), p.order)
        val back = Progress.decode(p.encode())
        assertEquals(p, back)
        assertTrue(p.encode().indexOf("\"ansa\"") < p.encode().indexOf("\"ansi\""), "keys sorted, as JSON::PP canonical")
        assertEquals(Progress(dataFile = "x"), Progress.decode("not json").copy(dataFile = "x"))
    }

    @Test
    fun mergeKeepsTheCopyThatHasSeenACardMore() {
        val mine = Progress(items = mutableMapOf("a" to Item(seen = 3, rung = 4), "b" to Item(seen = 1)),
                            order = Order(next = 2))
        val theirs = Progress(items = mutableMapOf("a" to Item(seen = 2), "b" to Item(seen = 5, rung = 3), "c" to Item(seen = 1)),
                              order = Order(next = 7), lessonsShown = mutableMapOf("3" to 1))
        val m = Progress.merge(mine, theirs)
        assertEquals(mapOf("a" to Item(seen = 3, rung = 4), "b" to Item(seen = 5, rung = 3), "c" to Item(seen = 1)), m.items)
        assertEquals(7, m.order!!.next)
        assertEquals<Map<String, Int>?>(mapOf("3" to 1), m.lessonsShown)
    }
}
