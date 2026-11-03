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
import java.security.MessageDigest

/**
 * A file kept in step with the repository: [name] there (and here, in the
 * directory being synced), and how a copy changed here and one changed
 * there are made one -- [merge] of mine and theirs.
 */
class Synced(val name: String, val merge: (mine: ByteArray, theirs: ByteArray) -> ByteArray)

/** What a sync did: the files brought here, and the ones committed (in [rev]). */
data class SyncResult(val fetched: List<String>, val committed: List<String>, val rev: Long?)

/**
 * Keeps the files in [dir] in step with the repository, as loglan-teach
 * keeps ~/.loglan's: one changed only there is brought here; one changed
 * only here is committed; one changed in both is merged, and the merge
 * committed.  What each file was when last in step -- the revision it last
 * changed in there, and what it held -- is kept in [dir]/sync.base, which
 * is how a change made here is told from one made there.
 */
class Syncer(private val dir: File) {
    private val baseFile = File(dir, "sync.base")

    private class Base(val rev: Long, val sha: String)

    private fun loadBase(): MutableMap<String, Base> =
        if (!baseFile.isFile) mutableMapOf()
        else baseFile.readLines().mapNotNull { line ->
            val f = line.split('\t')
            if (f.size == 3) f[0] to Base(f[1].toLong(), f[2]) else null
        }.toMap(mutableMapOf())

    private fun saveBase(base: Map<String, Base>) {
        val tmp = File(baseFile.path + ".tmp")
        tmp.writeText(base.entries.sortedBy { it.key }.joinToString("") { (n, b) -> "$n\t${b.rev}\t${b.sha}\n" })
        if (!tmp.renameTo(baseFile)) {
            baseFile.delete()
            check(tmp.renameTo(baseFile)) { "cannot rename $tmp to $baseFile" }
        }
    }

    private fun write(file: File, contents: ByteArray) {
        val tmp = File(file.path + ".sync")
        tmp.writeBytes(contents)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "cannot rename $tmp to $file" }
        }
    }

    /**
     * Brings [files] into step over [svn]; [message] makes the log message
     * from the names of the files committed.  Committed from somewhere else
     * meanwhile, it starts again, once.
     */
    fun sync(svn: SvnSession, files: List<Synced>, message: (List<String>) -> String): SyncResult {
        for (attempt in 1..2) {
            try {
                return once(svn, files, message)
            } catch (e: SvnException) {
                if (!e.outOfDate || attempt == 2) throw e
            }
        }
        error("not reached")
    }

    private fun once(svn: SvnSession, files: List<Synced>, message: (List<String>) -> String): SyncResult {
        val base = loadBase()
        val latest = svn.latestRev()
        val fetched = mutableListOf<String>()
        val commits = linkedMapOf<String, Pair<ByteArray, Long?>>()
        for (f in files) {
            val file = File(dir, f.name)
            val mine = if (file.isFile) file.readBytes() else null
            val was = base[f.name]
            val changedHere = mine != null && (was == null || sha(mine) != was.sha)
            val theirRev = svn.changedRev(f.name, latest)
            when {
                theirRev == null -> if (mine != null) commits[f.name] = mine to null
                was != null && was.rev == theirRev -> if (changedHere) commits[f.name] = mine!! to theirRev
                else -> {
                    val theirs = svn.file(f.name, latest).contents
                    val merged = if (changedHere) f.merge(mine!!, theirs) else theirs
                    if (mine == null || !merged.contentEquals(mine)) {
                        write(file, merged)
                        fetched += f.name
                    }
                    if (merged.contentEquals(theirs)) base[f.name] = Base(theirRev, sha(merged))
                    else commits[f.name] = merged to theirRev
                }
            }
        }
        var rev: Long? = null
        if (commits.isNotEmpty()) {
            rev = svn.commit(message(commits.keys.toList()), commits)
            for ((name, change) in commits) base[name] = Base(rev, sha(change.first))
        }
        saveBase(base)
        return SyncResult(fetched, commits.keys.toList(), rev)
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
