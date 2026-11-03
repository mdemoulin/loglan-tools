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
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

// Against a real server, read only, when told where:
//   LOGLAN_LIVE=user@host:port/repo LOGLAN_LIVE_KEY=keyfile LOGLAN_LIVE_HOSTKEY=AAAA... ./gradlew :sync:test
class SshLiveTest {
    private val live = System.getenv("LOGLAN_LIVE")
    private fun remote(): Remote {
        val (who, rest) = live.split('@', limit = 2)
        val hostPort = rest.substringBefore('/')
        return Remote(hostPort.substringBefore(':'), hostPort.substringAfter(':', "22").toInt(), who,
                      "/" + rest.substringAfter('/'))
    }

    @Test
    fun readsTheRepositoryOverSsh() {
        assumeTrue(live != null)
        val key = SshKey.parse(File(System.getenv("LOGLAN_LIVE_KEY")).readText())
        val hostKey = Base64.getDecoder().decode(System.getenv("LOGLAN_LIVE_HOSTKEY"))
        SvnOverSsh.connect(remote(), key, hostKey).use { c ->
            assertContentEquals(hostKey, c.seenHostKey)
            val rev = c.svn.latestRev()
            assertTrue(rev > 0)
            println("latest r$rev; prims.state.json: ${c.svn.kind("prims.state.json")}, " +
                    "last changed r${c.svn.changedRev("prims.state.json")}")
        }
        // Some other key for the server is refused.
        val e = assertFailsWith<SyncException> { SvnOverSsh.connect(remote(), key, ByteArray(51)) }
        assertTrue(e.message!!.contains("not the one"), e.message)
    }
}
