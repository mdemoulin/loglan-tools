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

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import java.util.Base64
import kotlin.concurrent.thread

/**
 * What install.sh --app asks of the app over adb, to set up its sync:
 *
 *     adb shell content read --uri content://io.github.mdemoulin.loglanteach.sync/key
 *
 * prints the line for the server's authorized_keys (making the key first if
 * there is none), and
 *
 *     adb shell content call --uri content://io.github.mdemoulin.loglanteach.sync \
 *         --method configure --extra host:s:192.168.0.245 --extra port:i:22 \
 *         --extra user:s:mad --extra repo:s:/loglan-state/trunk --extra hostkey:s:AAAA...
 *
 * fills in the sync settings, the server's key with them (base64, as in
 * known_hosts).  Only adb's shell user, or root, is answered: no other app
 * on the phone can read the key from here or send the sync elsewhere.
 */
class AdbProvider : ContentProvider() {
    private val store by lazy { SyncStore(context!!) }

    override fun onCreate() = true

    private fun fromAdb() {
        val uid = Binder.getCallingUid()
        if (uid != SHELL_UID && uid != ROOT_UID && uid != Process.myUid())
            throw SecurityException("only adb may ask this")
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        fromAdb()
        require(uri.path == "/key" && mode == "r") { "only key can be read" }
        val key = store.key() ?: store.newKey(store.config().device)
        val (read, write) = ParcelFileDescriptor.createPipe()
        thread {
            ParcelFileDescriptor.AutoCloseOutputStream(write).use { it.write((key.publicLine + "\n").toByteArray()) }
        }
        return read
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        fromAdb()
        require(method == "configure") { "no such method: $method" }
        val e = extras ?: Bundle()
        // Checked before anything is saved, so a bad call changes nothing.
        val hostKey = e.getString("hostkey")?.let { Base64.getDecoder().decode(it) }
        var c = store.config()
        e.getString("host")?.let { c = c.copy(host = it) }
        if (e.containsKey("port")) c = c.copy(port = e.getInt("port"))
        e.getString("user")?.let { c = c.copy(user = it) }
        e.getString("repo")?.let { c = c.copy(repo = it) }
        e.getString("device")?.let { c = c.copy(device = it) }
        store.save(c)
        hostKey?.let { store.saveHostKey(c, it) }
        return Bundle().apply { putString("url", c.url) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private companion object {
        // android.os.Process.SHELL_UID and ROOT_UID, which the SDK hides.
        const val SHELL_UID = 2000
        const val ROOT_UID = 0
    }
}
