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

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import java.io.Closeable

/** Where the repository is: svn+ssh://[user]@[host]:[port][repo], [repo] under svnserve's root. */
data class Remote(val host: String, val port: Int, val user: String, val repo: String) {
    val url: String get() = "svn+ssh://$user@$host${if (port == 22) "" else ":$port"}$repo"
}

/** The sync could not be made: why, in a line, and whether it was because the server was not there. */
class SyncException(message: String, val unreachable: Boolean = false, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * ssh to the server as svn+ssh does, with the app's [SshKey], running
 * svnserve -t there (which the key's authorized_keys line makes it run in
 * any case), and an svn session over it.  The server must have the ed25519
 * key [hostKey]; with none known yet, the one it shows is taken, and is
 * [seenHostKey] for the caller to keep.
 */
class SvnOverSsh private constructor(
    private val ssh: Session,
    private val channel: ChannelExec,
    val svn: SvnSession,
    val seenHostKey: ByteArray,
) : Closeable {
    override fun close() {
        channel.disconnect()
        ssh.disconnect()
    }

    companion object {
        init {
            // BouncyCastle's ed25519 and X25519, which every JVM and Android
            // version has with it, rather than whatever the platform has.
            JSch.setConfig("ssh-ed25519", "com.jcraft.jsch.bc.SignatureEd25519")
            JSch.setConfig("keypairgen.eddsa", "com.jcraft.jsch.bc.KeyPairGenEdDSA")
            JSch.setConfig("xdh", "com.jcraft.jsch.bc.XDH")
        }

        fun connect(remote: Remote, key: SshKey, hostKey: ByteArray?, timeoutMs: Int = 5000): SvnOverSsh {
            val jsch = JSch()
            jsch.addIdentity("loglan-sync", key.privateFile().toByteArray(), null, null)
            val pinned = Pinned(hostKey)
            jsch.hostKeyRepository = pinned
            val ssh = jsch.getSession(remote.user, remote.host, remote.port)
            ssh.setConfig("server_host_key", SshKey.TYPE)
            ssh.setConfig("PreferredAuthentications", "publickey")
            ssh.userInfo = NoQuestions
            try {
                ssh.connect(timeoutMs)
            } catch (e: JSchException) {
                throw when {
                    pinned.refused -> SyncException("the server's key is not the one the app knows " +
                                                    "(Settings > Sync, to forget it)", cause = e)
                    e.message.orEmpty().contains("Auth fail", ignoreCase = true) ->
                        SyncException("the server did not take the phone's key: is it in authorized_keys?", cause = e)
                    else -> SyncException("${remote.host} cannot be reached: ${e.message}", unreachable = true, cause = e)
                }
            }
            try {
                val channel = ssh.openChannel("exec") as ChannelExec
                channel.setCommand("svnserve -t")
                val input = channel.inputStream
                val output = channel.outputStream
                channel.connect(timeoutMs)
                ssh.timeout = 30_000
                val svn = SvnSession(input, output, "svn+ssh://${remote.host}${remote.repo}")
                return SvnOverSsh(ssh, channel, svn, pinned.seen!!)
            } catch (e: Exception) {
                ssh.disconnect()
                throw e as? SvnException ?: SyncException("svnserve on ${remote.host}: ${e.message}", cause = e)
            }
        }
    }

    /** The one key the server may have: [known], or with none, whichever it shows. */
    private class Pinned(private val known: ByteArray?) : HostKeyRepository {
        var seen: ByteArray? = null
        var refused = false

        override fun check(host: String?, key: ByteArray): Int {
            if (known == null || known.contentEquals(key)) {
                seen = key
                return HostKeyRepository.OK
            }
            refused = true
            return HostKeyRepository.CHANGED
        }

        override fun add(hostkey: HostKey?, ui: UserInfo?) {}
        override fun remove(host: String?, type: String?) {}
        override fun remove(host: String?, type: String?, key: ByteArray?) {}
        override fun getKnownHostsRepositoryID() = "loglan-sync"
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    }

    /** ssh is never answered: no passwords, no prompts. */
    private object NoQuestions : UserInfo {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String? = null
        override fun promptPassword(message: String?) = false
        override fun promptPassphrase(message: String?) = false
        override fun promptYesNo(message: String?) = false
        override fun showMessage(message: String?) {}
    }
}
