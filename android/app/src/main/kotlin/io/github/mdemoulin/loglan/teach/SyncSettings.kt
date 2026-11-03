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

package io.github.mdemoulin.loglan.teach

import android.content.Context
import android.os.Build
import android.provider.Settings
import io.github.mdemoulin.loglan.sync.SshKey
import java.io.File

/**
 * Where progress is kept in step: the Subversion repository loglan-teach
 * syncs with, reached as svn+ssh://[user]@[host]:[port][repo] -- [repo]
 * under the root svnserve -r gives on the server.  [device] names this
 * phone in commit messages and on its key.
 */
data class SyncConfig(
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val repo: String = "",
    val device: String = "",
    /** Minutes between syncs while a drill runs; 0 for only at its start and end. */
    val every: Int = 5,
) {
    val url: String get() = "svn+ssh://$user@$host${if (port == 22) "" else ":$port"}$repo"
    val complete: Boolean get() = host.isNotBlank() && user.isNotBlank() && repo.startsWith("/")
}

/**
 * The sync settings, and the key the app reaches the server with: an
 * ed25519 key of its own, with no passphrase, kept where Android's backups
 * do not take it.  The server's own key is kept once a sync has seen it,
 * for the server it came from.
 */
class SyncStore(private val context: Context) {
    val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val keyFile = File(context.noBackupFilesDir, "sync/id_ed25519")

    fun config() = SyncConfig(
        host = prefs.getString("host", null) ?: "",
        port = prefs.getInt("port", 22),
        user = prefs.getString("user", null) ?: "",
        repo = prefs.getString("repo", null) ?: "",
        device = prefs.getString("device", null) ?: defaultDevice(),
        every = prefs.getInt("every", 5),
    )

    fun save(c: SyncConfig) {
        prefs.edit()
            .putString("host", c.host.trim())
            .putInt("port", c.port)
            .putString("user", c.user.trim())
            .putString("repo", c.repo.trim())
            .putString("device", c.device)
            .putInt("every", c.every)
            .apply()
    }

    fun key(): SshKey? = keyFile.takeIf { it.isFile }?.let { runCatching { SshKey.parse(it.readText()) }.getOrNull() }

    fun newKey(device: String): SshKey {
        val key = SshKey.generate("loglan-sync $device".trim())
        keyFile.parentFile?.mkdirs()
        val tmp = File(keyFile.path + ".tmp")
        tmp.writeText(key.privateFile())
        tmp.setReadable(false, false); tmp.setReadable(true, true)
        check(tmp.renameTo(keyFile)) { "cannot put the key in place" }
        // So the app, if it is open, sees a key made over adb.
        prefs.edit().putString("key", key.fingerprint).apply()
        return key
    }

    /** The server's key, as ssh sent it, if a sync with this [host] and [port] has recorded one. */
    fun hostKey(c: SyncConfig): ByteArray? {
        val (at, blob) = (prefs.getString("hostKey", null) ?: return null).split(' ', limit = 2)
            .takeIf { it.size == 2 } ?: return null
        return if (at == "${c.host}:${c.port}") java.util.Base64.getDecoder().decode(blob) else null
    }

    fun saveHostKey(c: SyncConfig, blob: ByteArray) {
        prefs.edit().putString("hostKey", "${c.host}:${c.port} ${java.util.Base64.getEncoder().encodeToString(blob)}")
            .apply()
    }

    fun forgetHostKey() {
        prefs.edit().remove("hostKey").apply()
    }

    private fun defaultDevice(): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
            ?: Build.MODEL
}

/** The line that lets the key in on the server: svn, and nothing else, from the repositories under [root]. */
fun authorizedKeysLine(key: SshKey, root: String = "/svn") =
    "command=\"svnserve -t -r $root\",restrict ${key.publicLine}"
