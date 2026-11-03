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

import java.io.File
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

// The session held to svnserve itself, run as ssh runs it (svnserve -t),
// on a repository made for the test; and what it commits, to svn.
@Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class SvnTest {
    @TempDir
    lateinit var dir: File
    private val servers = mutableListOf<Process>()

    private fun run(vararg cmd: String): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        assertEquals(0, p.waitFor(), out)
        return out
    }

    private val repo get() = File(dir, "repos/state")

    private fun session(): SvnSession {
        if (!repo.exists()) {
            repo.parentFile.mkdirs()
            run("svnadmin", "create", repo.path)
            run("svn", "mkdir", "-q", "-m", "trunk", "file://${repo.path}/trunk")
        }
        val p = ProcessBuilder("svnserve", "-t", "-r", File(dir, "repos").path).start()
        servers += p
        return SvnSession(p.inputStream, p.outputStream, "svn+ssh://server/state/trunk")
    }

    @AfterTest
    fun stop() = servers.forEach { it.destroy() }

    private fun cat(path: String) = run("svn", "cat", "file://${repo.path}/trunk/$path")

    @Test
    fun addsAFileReadsItAndChangesIt() {
        val s = session()
        assertEquals(1, s.latestRev())
        assertEquals("none", s.kind("prims.state.json"))
        assertNull(s.changedRev("prims.state.json"))
        val rev = s.commit("prims.state.json from test", mapOf("prims.state.json" to ("{ \"a\": 1 }\n".toByteArray() to null)))
        assertEquals(2, rev)
        assertEquals("file", s.kind("prims.state.json"))
        assertEquals(2, s.changedRev("prims.state.json"))
        assertEquals("{ \"a\": 1 }\n", String(s.file("prims.state.json").contents))
        assertEquals("{ \"a\": 1 }\n", cat("prims.state.json"))

        val rev3 = s.commit("again", mapOf("prims.state.json" to ("{ \"a\": 2 }\n".toByteArray() to 2L)))
        assertEquals(3, rev3)
        assertEquals("{ \"a\": 2 }\n", cat("prims.state.json"))
        assertEquals(3, s.file("prims.state.json").rev)
        assertTrue(run("svn", "log", "-q", "-v", "file://${repo.path}/trunk").contains("M /trunk/prims.state.json"))
        assertEquals("prims.state.json from test", run("svn", "propget", "--revprop", "-r", "2", "svn:log", "file://${repo.path}").trim())
        assertEquals("again", run("svn", "propget", "--revprop", "-r", "3", "svn:log", "file://${repo.path}").trim())
    }

    @Test
    fun aCommitFromAnOldRevisionIsOutOfDate() {
        val s = session()
        s.commit("add", mapOf("t" to ("one\n".toByteArray() to null)))
        session().commit("elsewhere", mapOf("t" to ("two\n".toByteArray() to 2L)))
        val e = assertFailsWith<SvnException> { s.commit("stale", mapOf("t" to ("three\n".toByteArray() to 2L))) }
        assertTrue(e.outOfDate, "${e.code}: ${e.message}")
        assertEquals("two\n", cat("t"))
        // and the session goes on
        assertEquals(3, s.latestRev())
        assertEquals("two\n", String(s.file("t").contents))
    }

    @Test
    fun severalFilesInOneRevisionAndBigOnesAndEmptyOnes() {
        val s = session()
        val big = Random(7).nextBytes(300_000)
        val rev = s.commit("three", mapOf(
            "big" to (big to null),
            "empty" to (ByteArray(0) to null),
            "words-troublesome" to ("helva\t-\tSwiss\n".toByteArray() to null),
        ))
        assertEquals(2, rev)
        assertContentEquals(big, s.file("big").contents)
        assertEquals(0, s.file("empty").contents.size)
        assertEquals("helva\t-\tSwiss\n", cat("words-troublesome"))
        val out = File(dir, "big.out")
        ProcessBuilder("svn", "cat", "file://${repo.path}/trunk/big").redirectOutput(out).start().waitFor()
        assertContentEquals(big, out.readBytes())
    }

    @Test
    fun aMissingFileIsAnError() {
        assertFailsWith<SvnException> { session().file("nothing") }
    }

    @Test
    fun varints() {
        assertContentEquals(byteArrayOf(0), varint(0))
        assertContentEquals(byteArrayOf(0x7f), varint(127))
        assertContentEquals(byteArrayOf(0x81.toByte(), 0x00), varint(128))
        assertContentEquals(byteArrayOf(0x84.toByte(), 0x80.toByte(), 0x00), varint(65536))
    }
}
