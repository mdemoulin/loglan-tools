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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

// The app's directory kept in step with a repository that another machine
// -- an svn working copy, as loglan-teach has -- commits to as well.
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class SyncerTest {
    @TempDir
    lateinit var dir: File
    private val servers = mutableListOf<Process>()
    private lateinit var app: File
    private lateinit var wc: File

    private fun run(vararg cmd: String, cwd: File? = null): String {
        val p = ProcessBuilder(*cmd).directory(cwd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        assertEquals(0, p.waitFor(), out)
        return out
    }

    @BeforeTest
    fun repository() {
        File(dir, "repos").mkdirs()
        run("svnadmin", "create", File(dir, "repos/state").path)
        run("svn", "mkdir", "-q", "-m", "trunk", "file://$dir/repos/state/trunk")
        wc = File(dir, "wc")
        run("svn", "checkout", "-q", "file://$dir/repos/state/trunk", wc.path)
        app = File(dir, "app").apply { mkdirs() }
    }

    @AfterTest
    fun stop() = servers.forEach { it.destroy() }

    private fun session(): SvnSession {
        val p = ProcessBuilder("svnserve", "-t", "-r", File(dir, "repos").path).start()
        servers += p
        return SvnSession(p.inputStream, p.outputStream, "svn+ssh://server/state/trunk")
    }

    /** Lines of either, theirs first: as the troublesome lists are merged. */
    private val union = { mine: ByteArray, theirs: ByteArray ->
        val t = String(theirs).lines().filter { it.isNotEmpty() }
        (t + String(mine).lines().filter { it.isNotEmpty() && it !in t }).joinToString("") { "$it\n" }.toByteArray()
    }
    private val files = listOf(Synced("a.state.json", union), Synced("words-troublesome", union))

    private fun sync() = Syncer(app).sync(session(), files) { "${it.joinToString(", ")} from test" }

    /** Another machine: changes [name] in its working copy and commits it. */
    private fun elsewhere(name: String, text: String) {
        run("svn", "update", "-q", cwd = wc)
        val f = File(wc, name)
        val add = !f.exists()
        f.writeText(text)
        if (add) run("svn", "add", "-q", name, cwd = wc)
        run("svn", "commit", "-q", "-m", "$name from elsewhere", cwd = wc)
    }

    private fun there(name: String): String {
        run("svn", "update", "-q", cwd = wc)
        return File(wc, name).readText()
    }

    @Test
    fun aFileMadeHereIsAdded() {
        File(app, "a.state.json").writeText("one\n")
        val r = sync()
        assertEquals(listOf("a.state.json"), r.committed)
        assertEquals("one\n", there("a.state.json"))
        assertTrue(run("svn", "log", "-q", "-l", "1", "-v", wc.path).isNotEmpty())
        // and then there is nothing to do
        assertEquals(SyncResult(emptyList(), emptyList(), null), sync())
    }

    @Test
    fun aFileChangedThereIsBroughtHere() {
        File(app, "a.state.json").writeText("one\n")
        sync()
        elsewhere("a.state.json", "one\ntwo\n")
        val r = sync()
        assertEquals(listOf("a.state.json"), r.fetched)
        assertEquals(emptyList(), r.committed)
        assertEquals("one\ntwo\n", File(app, "a.state.json").readText())
    }

    @Test
    fun aFileMadeThereIsBroughtHere() {
        elsewhere("words-troublesome", "helva\n")
        assertEquals(listOf("words-troublesome"), sync().fetched)
        assertEquals("helva\n", File(app, "words-troublesome").readText())
    }

    @Test
    fun aFileChangedHereIsCommitted() {
        File(app, "a.state.json").writeText("one\n")
        sync()
        File(app, "a.state.json").writeText("one\nmine\n")
        assertEquals(listOf("a.state.json"), sync().committed)
        assertEquals("one\nmine\n", there("a.state.json"))
    }

    @Test
    fun aFileChangedInBothIsMergedAndTheMergeCommitted() {
        File(app, "a.state.json").writeText("one\n")
        sync()
        elsewhere("a.state.json", "one\ntheirs\n")
        File(app, "a.state.json").writeText("one\nmine\n")
        val r = sync()
        assertEquals(listOf("a.state.json"), r.fetched)
        assertEquals(listOf("a.state.json"), r.committed)
        assertEquals("one\ntheirs\nmine\n", File(app, "a.state.json").readText())
        assertEquals("one\ntheirs\nmine\n", there("a.state.json"))
    }

    @Test
    fun withNothingKnownOfEitherTheyAreMerged() {
        elsewhere("a.state.json", "theirs\n")
        File(app, "a.state.json").writeText("mine\n")
        sync()
        assertEquals("theirs\nmine\n", there("a.state.json"))
    }

    @Test
    fun addedElsewhereMeanwhileItIsMerged() {
        File(app, "words-troublesome").writeText("mine\n")
        var once = false
        val r = Syncer(app).sync(session(), files) {
            if (!once) { once = true; elsewhere("words-troublesome", "theirs\n") }
            "from test"
        }
        assertEquals(listOf("words-troublesome"), r.committed)
        assertEquals("theirs\nmine\n", there("words-troublesome"))
    }

    @Test
    fun committedElsewhereMeanwhileItStartsAgain() {
        File(app, "a.state.json").writeText("one\n")
        sync()
        File(app, "a.state.json").writeText("one\nmine\n")
        // Committed after this sync has looked, before it commits.
        val svn = session()
        var once = false
        val r = Syncer(app).sync(svn, files) {
            if (!once) { once = true; elsewhere("a.state.json", "one\ntheirs\n") }
            "from test"
        }
        assertEquals(listOf("a.state.json"), r.committed)
        assertEquals("one\ntheirs\nmine\n", there("a.state.json"))
    }
}
