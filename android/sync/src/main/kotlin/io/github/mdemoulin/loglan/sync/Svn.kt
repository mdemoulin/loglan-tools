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

package io.github.mdemoulin.loglan.sync

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

// The little of Subversion's own protocol (ra_svn, what svnserve speaks,
// over ssh as svnserve -t) that keeping a few files in step takes: what the
// latest revision is, whether a file is there and when it last changed,
// its contents, and a commit of whole files.  See subversion's
// libsvn_ra_svn/protocol.

/** An item of the protocol: a number, a word, a string, or a list of items. */
sealed interface Item {
    data class Num(val n: Long) : Item
    data class Word(val w: String) : Item
    class Str(val b: ByteArray) : Item {
        override fun toString() = "Str(${String(b)})"
    }
    data class Lst(val items: List<Item>) : Item
}

private fun Item.list() = (this as? Item.Lst)?.items ?: throw SvnException("protocol: expected a list, got $this")
private fun Item.num() = (this as? Item.Num)?.n ?: throw SvnException("protocol: expected a number, got $this")
private fun Item.word() = (this as? Item.Word)?.w ?: throw SvnException("protocol: expected a word, got $this")
private fun Item.str() = (this as? Item.Str)?.b ?: throw SvnException("protocol: expected a string, got $this")

/** What svn said went wrong: its error number (as E160028, out of date) and message. */
class SvnException(message: String, val code: Long = 0) : Exception(message) {
    /** Somebody committed the file since the revision we had -- or added it, when we would have. */
    val outOfDate get() = code in OUT_OF_DATE

    private companion object {
        // E155011 (entry out of date), E160028 (txn out of date), E160024 (conflict),
        // E160020 (already exists), E170004 (item out of date)
        val OUT_OF_DATE = setOf(155011L, 160028L, 160024L, 160020L, 170004L)
    }
}

internal class Reader(input: InputStream) {
    private val input = BufferedInputStream(input)

    private fun byte(): Int = input.read().also { if (it < 0) throw EOFException("svnserve closed the connection") }

    private fun ws(c: Int) = c == ' '.code || c == '\n'.code

    fun item(): Item {
        var c = byte()
        while (ws(c)) c = byte()
        return item(c)
    }

    private fun item(first: Int): Item {
        var c = first
        return when {
            c == '('.code -> {
                val items = mutableListOf<Item>()
                while (true) {
                    c = byte()
                    while (ws(c)) c = byte()
                    if (c == ')'.code) break
                    items += item(c)
                }
                byte().let { if (!ws(it)) throw SvnException("protocol: no space after a list") }
                Item.Lst(items)
            }
            c in '0'.code..'9'.code -> {
                var n = 0L
                while (c in '0'.code..'9'.code) {
                    n = n * 10 + (c - '0'.code)
                    c = byte()
                }
                if (c == ':'.code) {
                    val b = ByteArray(n.toInt())
                    var off = 0
                    while (off < b.size) {
                        val k = input.read(b, off, b.size - off)
                        if (k < 0) throw EOFException("svnserve closed the connection")
                        off += k
                    }
                    byte().let { if (!ws(it)) throw SvnException("protocol: no space after a string") }
                    Item.Str(b)
                } else {
                    if (!ws(c)) throw SvnException("protocol: bad number")
                    Item.Num(n)
                }
            }
            c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code -> {
                val w = StringBuilder()
                while (!ws(c)) {
                    w.append(c.toChar())
                    c = byte()
                }
                Item.Word(w.toString())
            }
            else -> throw SvnException("protocol: unexpected byte $c")
        }
    }
}

/** Writes items as the protocol has them; [flush] sends them. */
internal class Writer(private val out: OutputStream) {
    private val buf = ByteArrayOutputStream()

    fun open() = apply { buf.write("( ".toByteArray()) }
    fun close() = apply { buf.write(") ".toByteArray()) }
    fun word(w: String) = apply { buf.write("$w ".toByteArray()) }
    fun num(n: Long) = apply { buf.write("$n ".toByteArray()) }
    fun str(b: ByteArray) = apply { buf.write("${b.size}:".toByteArray()); buf.write(b); buf.write(' '.code) }
    fun str(s: String) = str(s.toByteArray())
    fun bool(b: Boolean) = word(if (b) "true" else "false")
    fun list(build: Writer.() -> Unit) = apply { open(); build(); close() }

    fun flush() {
        buf.writeTo(out)
        buf.reset()
        out.flush()
    }
}

/** A file in the repository: its contents, and the revision it last changed in. */
class SvnFile(val contents: ByteArray, val rev: Long)

/**
 * A session with svnserve over [input] and [output] -- the two ends of
 * `ssh server svnserve -t`, or of svnserve -t itself -- at [url], the
 * repository directory the files are in.  Paths are relative to it.
 */
class SvnSession(input: InputStream, output: OutputStream, val url: String, client: String = "loglan-teach-app") {
    private val r = Reader(input)
    private val w = Writer(output)

    init {
        // The greeting: ( success ( minver maxver ( mechs ) ( caps ) ) ).
        val greeting = success(r.item())
        if (greeting[0].num() > 2 || greeting[1].num() < 2) throw SvnException("svnserve does not speak protocol 2")
        w.list {
            num(2)
            list { word("edit-pipeline"); word("svndiff1"); word("absent-entries"); word("depth"); word("mergeinfo") }
            str(url)
            str(client)
            list { }
        }.flush()
        auth()
        success(r.item()) // repos-info: ( uuid root-url ( caps ) )
    }

    /** The answer to a command: ( success ( params ) ), or what the failure said. */
    private fun success(item: Item): List<Item> {
        val l = item.list()
        when (l.getOrNull(0)?.word()) {
            "success" -> return l.getOrNull(1)?.list() ?: emptyList()
            "failure" -> {
                val errs = l.getOrNull(1)?.list().orEmpty().map { it.list() }
                val first = errs.firstOrNull()
                throw SvnException(
                    errs.joinToString("; ") { e -> e.getOrNull(1)?.let { String(it.str()) }.orEmpty() }
                        .ifEmpty { "svnserve failed" },
                    first?.getOrNull(0)?.num() ?: 0,
                )
            }
            else -> throw SvnException("protocol: expected success or failure, got $item")
        }
    }

    /**
     * An auth request: ( success ( ( mechs ) realm ) ).  Empty is no need;
     * over ssh it is EXTERNAL, ssh having said who we are.
     */
    private fun auth() {
        val mechs = success(r.item())[0].list().map { it.word() }
        if (mechs.isEmpty()) return
        if ("EXTERNAL" !in mechs) throw SvnException("svnserve wants a password (${mechs.joinToString()}); reach it over ssh")
        w.list { word("EXTERNAL"); list { str("") } }.flush()
        val reply = r.item().list()
        if (reply[0].word() != "success") throw SvnException("svnserve did not let us in: $reply")
    }

    private fun command(name: String, params: Writer.() -> Unit): List<Item> {
        w.list { word(name); list(params) }.flush()
        auth()
        return success(r.item())
    }

    fun latestRev(): Long = command("get-latest-rev") { }[0].num()

    /** "none", "file" or "dir": what [path] is at [rev] (the latest, by default). */
    fun kind(path: String, rev: Long? = null): String =
        command("check-path") { str(path); list { rev?.let(::num) } }[0].word()

    /** The revision [path] last changed in, at [rev]; null if it is not there. */
    fun changedRev(path: String, rev: Long? = null): Long? {
        val entry = command("stat") { str(path); list { rev?.let(::num) } }[0].list()
        // ( ? ( kind size has-props created-rev ( date ) ( author ) ) )
        return entry.firstOrNull()?.list()?.get(3)?.num()
    }

    /** [path]'s contents at [rev] (the latest, by default). */
    fun file(path: String, rev: Long? = null): SvnFile {
        val head = command("get-file") { str(path); list { rev?.let(::num) }; bool(false); bool(true) }
        val changed = head[1].num()
        val contents = ByteArrayOutputStream()
        while (true) {
            val chunk = r.item().str()
            if (chunk.isEmpty()) break
            contents.write(chunk)
        }
        success(r.item())
        return SvnFile(contents.toByteArray(), changed)
    }

    /**
     * Commits [files], each path's whole new contents, in one revision:
     * a file changed from the revision given with it (it is out of date if
     * it has changed since, and the commit fails), or added (rev null).
     * Returns the new revision.
     */
    fun commit(message: String, files: Map<String, Pair<ByteArray, Long?>>): Long {
        // The message goes in the revision's properties too: given any,
        // svnserve takes svn:log from them, not from the message.
        command("commit") { str(message); list { }; bool(false); list { list { str("svn:log"); str(message) } } }
        w.list { word("open-root"); list { list { }; str("d0") } }
        files.entries.forEachIndexed { i, (path, change) ->
            val (contents, rev) = change
            val token = "f$i"
            if (rev != null) {
                w.list { word("open-file"); list { str(path); str("d0"); str(token); list { num(rev) } } }
            } else {
                w.list { word("add-file"); list { str(path); str("d0"); str(token); list { } } }
            }
            w.list { word("apply-textdelta"); list { str(token); list { } } }
            for (chunk in svndiff(contents)) w.list { word("textdelta-chunk"); list { str(token); str(chunk) } }
            w.list { word("textdelta-end"); list { str(token) } }
            w.list { word("close-file"); list { str(token); list { str(md5(contents)) } } }
        }
        w.list { word("close-dir"); list { str("d0") } }
        w.list { word("close-edit"); list { } }
        w.flush()
        try {
            success(r.item())
        } catch (e: SvnException) {
            // The edit failed (out of date, say): the server reads what was
            // sent of it until it is given up, which it does not answer.
            w.list { word("abort-edit"); list { } }.flush()
            throw e
        }
        auth()
        // commit-info: ( new-rev ( date ) ( author ) ( ? post-commit-err ) )
        return r.item().list()[0].num()
    }

    private fun md5(b: ByteArray) = MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02x".format(it) }
}

/**
 * [contents] as svndiff0 against nothing: the header, then windows of new
 * text, each one instruction copying all of its new data.
 */
internal fun svndiff(contents: ByteArray, window: Int = 64 * 1024): List<ByteArray> {
    val out = mutableListOf("SVN\u0000".toByteArray())
    var off = 0
    while (off < contents.size) {
        val n = minOf(window, contents.size - off)
        val ins = ByteArrayOutputStream().apply {
            if (n in 1..63) write(0x80 or n) else { write(0x80); write(varint(n.toLong())) }
        }.toByteArray()
        out += ByteArrayOutputStream().apply {
            write(varint(0)); write(varint(0)) // no source view
            write(varint(n.toLong()))           // target view length
            write(varint(ins.size.toLong()))
            write(varint(n.toLong()))           // new data length
            write(ins)
            write(contents, off, n)
        }.toByteArray()
        off += n
    }
    return out
}

/** svndiff's integers: seven bits a byte, most significant first, all but the last with the top bit set. */
internal fun varint(v: Long): ByteArray {
    var n = v
    val bytes = mutableListOf((n and 0x7f).toByte())
    n = n ushr 7
    while (n > 0) {
        bytes.add(0, ((n and 0x7f) or 0x80).toByte())
        n = n ushr 7
    }
    return bytes.toByteArray()
}
