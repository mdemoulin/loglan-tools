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
import kotlin.random.Random

// The drill: loglan-teach's drill_loop, taken one reply at a time.

enum class Direction { RECOGNITION, RECALL, BOTH }

data class Options(
    val direction: Direction = Direction.RECOGNITION,
    val rungs: Int = 5,
    val escape: Int = 2,
    /** For affs and affsets: "gloss", or "affix" to drill the affixes. */
    val field: String = "gloss",
    /** A list with lessons (utts) drilled on the ladder, at random, without them. */
    val random: Boolean = false,
    val derivs: Boolean = true,
)

/** A card being asked. */
data class Question(
    val card: Card,
    /** "new" or "retry" in a list taught in order; "" otherwise. */
    val kind: String,
    /** True for Loglan-to-English. */
    val recognition: Boolean,
    val prompt: String,
    /** The lessons to read before it, each shown once. */
    val lessons: List<Pair<Int, String>> = emptyList(),
    /** Said before it: "going over the ones missed", "from the top again". */
    val notice: String? = null,
)

/** What a reply came to. */
sealed interface Outcome {
    /** A card graded; [right] is null for a blank reply, which shows the answer and counts as a miss. */
    data class Graded(
        val question: Question,
        val reply: String,
        val right: Boolean?,
        /** "rung 1->2", "error-box 1/2" */
        val ladder: String?,
        /** The answer, and what goes with it: its note, or where the word came from. */
        val answer: List<String>,
    ) : Outcome

    /** A command's say (:s, :l, :h, :t, a slip); the same card is asked again. */
    data class Said(val lines: List<String>) : Outcome

    data object Quit : Outcome
}

class Drill(
    val name: String,
    private val cards: List<Card>,
    val progress: Progress,
    private val options: Options = Options(),
    private val library: Library? = null,
    private val lessonText: Map<Int, String>? = library?.lessons,
    private val random: Random = Random.Default,
    private val save: (Progress) -> Unit = {},
) {
    init {
        require(cards.isNotEmpty()) { "No usable records in $name" }
    }

    /**
     * A list with lessons marked in it is taught as MacTeach 1 taught it: the
     * new sentences in order, each lesson shown before the first new sentence
     * it goes with.
     */
    val inOrder = !options.random && lessonText != null && cards.any { it.lessons.isNotEmpty() }

    private data class Entry(val card: Card, val kind: String, val recognition: Boolean, val given: String? = null)

    // ':r' asks the card before this one again, then this one: both wait
    // here, each as it was asked.
    private val ahead = ArrayDeque<Entry>()
    private var prev: Entry? = null
    private var current: Entry? = null

    /** The card being asked. */
    var question: Question = next()
        private set

    /**
     * A reply to the card being asked: an answer, a blank (show the answer),
     * or a command (":s", ":l", ":r", ":r ANSWER", ":t", ":h", ":q").
     */
    fun reply(text: String): Outcome {
        // A backslash is never part of an answer, only a slip of the finger
        // beside Enter.  The commands are taken however they are
        // capitalised; an answer after ':r' is kept as typed.
        var reply = perlTrim(text.replace("\\", ""))
        reply = Regex("""\A(:\S*)""").replace(reply) { it.value.lowercase(Locale.ROOT) }
        val again = Regex("""\A:r\s+([^:].*)\z""").find(reply)?.groupValues?.get(1)
        if (again != null) reply = ":r"
        if (!reply.startsWith(":")) return grade(current!!, reply)
        val p = prev
        return when (reply) {
            ":q" -> {
                save(progress)
                Outcome.Quit
            }
            ":r" -> if (p == null) nothingBefore() else askAgain(p, again)
            ":l", ":1" -> Outcome.Said(cardLessons(current!!.card))
            ":s" -> Outcome.Said(statusLines())
            ":t" -> if (p == null) nothingBefore() else Outcome.Said(troublesome(p.card))
            ":h" -> Outcome.Said(listOf("(commands:") + COMMANDS)
            // Anything else after a ':' is a slip, not an answer.
            else -> Outcome.Said(listOf("(not a command:") + COMMANDS)
        }
    }

    /** Is there a card before this one, for :r and :t? */
    val hasPrevious get() = prev != null

    private fun nothingBefore() = Outcome.Said(listOf("(nothing asked before this one yet)"))

    // The card before is asked again (graded at once on [given], when there
    // is one), then this one.
    private fun askAgain(p: Entry, given: String?): Outcome {
        ahead.addFirst(current!!.copy(given = null))
        ahead.addFirst(p.copy(given = given))
        prev = null
        question = next()
        val c = current!!
        return if (c.given != null) grade(c, c.given) else Outcome.Said(emptyList())
    }

    private fun grade(e: Entry, reply: String): Outcome {
        val q = question
        val r = e.card
        val expected = if (e.recognition) r.answer else r.loglan
        val s = progress.items.getOrPut(r.key) { Item() }
        s.seen++

        // A sentence with no translation -- ungrammatical, its English just
        // "-" and a note why -- is answered with a "-".
        val none = r.sentence && MacTeach.splitNote(expected).first.isBlankDash()
        val right = when {
            none -> reply == "-"
            r.sentence -> MacTeach.matches(reply, expected, !e.recognition)
            else -> matchAnswer(reply, expected)
        }
        var ladder: String? = null
        when {
            reply.isEmpty() -> demote(s)
            right -> {
                ladder = if (s.inError != 0) "error-box ${s.streak + 1}/${options.escape}"
                         else "rung ${s.rung}->${s.rung + 1}"
                s.correct++
                promote(s)
            }
            else -> demote(s)
        }
        if (inOrder) afterInOrder(r, e.kind, right && reply.isNotEmpty())
        // Asked again, it is graded again, as one gone over: right takes it
        // off the in-order retry list, wrong puts it at the back.
        prev = e.copy(kind = if (e.kind == "new") "retry" else e.kind, given = null)

        val answer = mutableListOf<String>()
        if (none) {
            val why = MacTeach.splitNote(expected).second
                .let { Regex("""\A\s*\((.*)\)\s*\z""", RegexOption.DOT_MATCHES_ALL).replace(it, "$1") }
            answer += "- (no translation)"
            if (why.isNotEmpty()) answer += "($why)"
        } else if (r.sentence) {
            val x = MacTeach.sentence(expected, !e.recognition)
            answer += x.show
            if (x.note.isNotEmpty() && !e.recognition) answer += "(${x.note})"
        } else {
            answer += acceptedAnswers(expected).joinToString(" / ")
            if (options.derivs) library?.derivs?.get(r.loglan)?.line()?.let { answer += "from: $it" }
        }
        save(progress)
        question = next()
        return Outcome.Graded(q, reply, if (reply.isEmpty()) null else right, ladder, answer)
    }

    private fun String.isBlankDash() = perlTrim(this) == "-"

    private fun next(): Question {
        var notice: String? = null
        var lessons = emptyList<Pair<Int, String>>()
        val e = ahead.removeFirstOrNull() ?: run {
            val (card, kind) = if (inOrder) {
                val (c, k, n) = nextInOrder()
                notice = n
                c to k
            } else pick() to ""
            if (kind == "new") lessons = dueLessons(card)
            Entry(card, kind, when (options.direction) {
                Direction.RECOGNITION -> true
                Direction.RECALL -> false
                Direction.BOTH -> random.nextDouble() < 0.5
            })
        }
        current = e
        val r = e.card
        var prompt = if (e.recognition) r.loglan else r.answer
        // A sentence is shown as MacTeach showed it: the main wording, without
        // the alternatives; a Loglan note goes with the prompt.
        if (r.sentence) {
            val p = MacTeach.sentence(prompt, e.recognition)
            prompt = p.show + if (e.recognition && p.note.isNotEmpty()) "   [${p.note}]" else ""
        }
        return Question(r, e.kind, e.recognition, prompt, lessons, notice)
    }

    // Error-box cards get top weight; ladder cards weighted toward low
    // rungs; a card past the top rung has graduated (weight 0).  When every
    // card has, all are open again, for lifetime review.
    private fun pick(): Card {
        val weights = cards.map { c ->
            val s = progress.items[c.key]
            when {
                s == null -> options.rungs + 1
                s.inError != 0 -> options.rungs + 2
                s.rung > options.rungs -> 0
                else -> options.rungs - s.rung + 2
            }
        }.let { w -> if (w.sum() == 0) w.map { 1 } else w }
        val roll = random.nextDouble() * weights.sum()
        var acc = 0
        for ((i, w) in weights.withIndex()) {
            acc += w
            if (roll < acc) return cards[i]
        }
        return cards.last()
    }

    private fun promote(s: Item) {
        if (s.inError != 0) {
            s.streak++
            if (s.streak >= options.escape) {
                s.inError = 0
                s.streak = 0
                s.rung = 1
            }
        } else {
            s.rung++
        }
    }

    private fun demote(s: Item) {
        s.inError = 1
        s.streak = 0
        s.rung = 0
    }

    // In order, as MacTeach 1 went: every sentence in its turn.  One you
    // miss goes on a retry list, which is gone through -- until each is
    // right -- before the next lesson begins and at the end of the list;
    // after the end, it starts again from the top.
    private fun nextInOrder(): Triple<Card, String, String?> {
        val q = progress.order ?: Order().also { progress.order = it }
        val byKey = cards.associateBy { it.key }
        q.retry.retainAll { it in byKey }     // the list may have changed
        var n = q.next
        if (q.retry.isNotEmpty() && (n > cards.lastIndex || cards[n].lessons.isNotEmpty())) {
            val first = (q.retrying ?: 0) == 0
            q.retrying = (q.retrying ?: 0) + 1
            return Triple(byKey.getValue(q.retry[0]), "retry",
                          if (first) "(going over the ones missed, before going on)" else null)
        }
        q.retrying = null
        var notice: String? = null
        if (n > cards.lastIndex) {
            n = 0
            q.next = 0
            q.pass++
            notice = "---- That was all ${cards.size}; from the top again (time ${q.pass}) ----"
        }
        return Triple(cards[n], "new", notice)
    }

    private fun afterInOrder(r: Card, kind: String, right: Boolean) {
        val q = progress.order ?: return
        val retry = q.retry.filterTo(mutableListOf()) { it != r.key }
        if (kind == "new") {
            q.next++
            if (!right) retry += r.key
        } else if (!right) {
            retry += r.key          // to the back of the line
        }
        q.retry = retry
    }

    // Before the next sentence in order, every lesson up to it not yet
    // shown, in the order the list has them -- for a new learner, just the
    // ones that go with it.  Each is shown once.
    private fun dueLessons(r: Card): List<Pair<Int, String>> {
        val text = lessonText ?: return emptyList()
        val shown = progress.lessonsShown ?: mutableMapOf<String, Int>().also { progress.lessonsShown = it }
        val due = cards.subList(0, r.index + 1).flatMap { it.lessons }.filter { shown["$it"] == null }
        if (due.isEmpty()) return emptyList()
        due.forEach { shown["$it"] = 1 }
        save(progress)
        return due.map { it to (text[it] ?: "(not in the lessons file)\n") }
    }

    /** The lesson a card goes with: the nearest one marked at or before it. */
    fun cardLessons(r: Card): List<String> {
        val l = lessonsFor(r) ?: return listOf("(this list has no lessons)")
        if (l.isEmpty()) return listOf("(no lesson comes before this card)")
        return l.flatMap { (n, text) -> listOf("", "---- Lesson $n ----") + text.trimEnd('\n').split('\n') }
    }

    /** The lessons a card goes with, with their text: none before it, or null when the list has none. */
    fun lessonsFor(r: Card): List<Pair<Int, String>>? {
        val text = lessonText?.takeIf { cards.any { it.lessons.isNotEmpty() } } ?: return null
        val l = cards.subList(0, r.index + 1).lastOrNull { it.lessons.isNotEmpty() }?.lessons ?: return emptyList()
        return l.map { it to (text[it] ?: "(not in the lessons file)\n") }
    }

    private fun troublesome(r: Card): List<String> {
        val lib = library ?: return listOf("(no list directory to keep troublesome in)")
        val t = lib.addTroublesome(r)
        return buildList {
            if (t.added.isNotEmpty()) add("(on troublesome now: ${t.added.joinToString(" ")})")
            if (t.already.isNotEmpty()) add("(on troublesome already: ${t.already.joinToString(" ")})")
        }
    }

    /** How far along the list is. */
    data class Status(
        val new: Int,
        val errorBox: Int,
        val rungs: List<Int>,
        val graduated: Int,
        val total: Int,
        /** In order: the next card (from 1), the time through, and how many to go over again. */
        val inOrder: Triple<Int, Int, Int>?,
    )

    fun status(): Status {
        var new = 0
        var err = 0
        var grad = 0
        val hist = IntArray(options.rungs + 1)
        for (r in cards) {
            val s = progress.items[r.key]
            when {
                s == null -> new++
                s.inError != 0 -> err++
                s.rung > options.rungs -> grad++
                else -> if (s.rung in hist.indices) hist[s.rung]++
            }
        }
        val q = progress.order ?: Order()
        return Status(new, err, hist.drop(1), grad, cards.size,
                      if (inOrder) Triple(q.next + 1, q.pass, q.retry.size) else null)
    }

    fun statusLines(): List<String> {
        val s = status()
        return buildList {
            add("status:")
            add("  new       %5d".format(s.new))
            add("  error-box %5d".format(s.errorBox))
            s.rungs.forEachIndexed { i, n -> add("  rung %d    %5d".format(i + 1, n)) }
            add("  graduated %5d".format(s.graduated))
            add("  total     %5d".format(s.total))
            s.inOrder?.let { (next, pass, retry) ->
                add("  in order: next is %d of %d (time %d through); %d to go over again"
                        .format(next, s.total, pass, retry))
            }
        }
    }

    companion object {
        val COMMANDS = listOf(
            "  :s  status", "  :l  the lesson this card goes with",
            "  :r  ask the card before this one again", "  :r ANSWER  answer it again with ANSWER",
            "  :t  put the card before this one on troublesome",
            "  :h  these commands", "  :q  quit and save)",
        )
    }
}
