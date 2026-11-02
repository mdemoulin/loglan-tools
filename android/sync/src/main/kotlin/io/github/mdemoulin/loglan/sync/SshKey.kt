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

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * An ed25519 key for ssh, with no passphrase -- as `ssh-keygen -t ed25519
 * -N ''` makes one, and in the forms it keeps one in: the private key file,
 * and the line for authorized_keys.
 */
class SshKey private constructor(private val seed: ByteArray, val comment: String) {
    private val private = Ed25519PrivateKeyParameters(seed, 0)

    /** The 32 bytes of the public key. */
    val publicKey: ByteArray = private.generatePublicKey().encoded

    /** The public key as ssh sends it: its type and its bytes. */
    val publicBlob: ByteArray = sshBytes { string(TYPE); string(publicKey) }

    /** "ssh-ed25519 AAAA... comment", as in id_ed25519.pub. */
    val publicLine: String get() = "$TYPE ${Base64.getEncoder().encodeToString(publicBlob)} $comment".trimEnd()

    /** "SHA256:...", as ssh-keygen -l shows it. */
    val fingerprint: String get() = fingerprint(publicBlob)

    /** A signature of [data], as ssh-ed25519 makes one. */
    fun sign(data: ByteArray): ByteArray =
        Ed25519Signer().run { init(true, private); update(data, 0, data.size); generateSignature() }

    /** The private key file, in OpenSSH's own form, unencrypted, as id_ed25519 is. */
    fun privateFile(random: SecureRandom = SecureRandom()): String {
        val check = random.nextInt()
        val inner = ByteArrayOutputStream()
        DataOutputStream(inner).run {
            writeInt(check); writeInt(check)
            string(TYPE); string(publicKey); string(seed + publicKey); string(comment.toByteArray())
            var pad = 1
            while (inner.size() % 8 != 0) writeByte(pad++)
        }
        val body = sshBytes {
            write(MAGIC)
            string("none"); string("none"); string(ByteArray(0))
            writeInt(1)
            string(publicBlob)
            string(inner.toByteArray())
        }
        val b64 = Base64.getEncoder().encodeToString(body).chunked(70).joinToString("\n")
        return "$BEGIN\n$b64\n$END\n"
    }

    companion object {
        const val TYPE = "ssh-ed25519"
        private const val BEGIN = "-----BEGIN OPENSSH PRIVATE KEY-----"
        private const val END = "-----END OPENSSH PRIVATE KEY-----"
        private val MAGIC = "openssh-key-v1\u0000".toByteArray()

        fun generate(comment: String, random: SecureRandom = SecureRandom()): SshKey =
            SshKey(ByteArray(32).also(random::nextBytes), comment)

        /** Reads a private key file: one [privateFile] wrote, or ssh-keygen, if it has no passphrase. */
        fun parse(text: String): SshKey {
            val b64 = text.substringAfter(BEGIN, "").substringBefore(END, "")
            require(b64.isNotBlank()) { "not an OpenSSH private key" }
            val body = DataInputStream(Base64.getMimeDecoder().decode(b64).inputStream())
            val magic = ByteArray(MAGIC.size).also(body::readFully)
            require(magic.contentEquals(MAGIC)) { "not an OpenSSH private key" }
            require(String(body.string()) == "none" && String(body.string()) == "none") {
                "the key has a passphrase"
            }
            body.string()
            require(body.readInt() == 1) { "more than one key in the file" }
            body.string()
            val inner = DataInputStream(body.string().inputStream())
            require(inner.readInt() == inner.readInt()) { "the key file is damaged" }
            require(String(inner.string()) == TYPE) { "not an ed25519 key" }
            inner.string()
            val priv = inner.string()
            require(priv.size == 64) { "the key file is damaged" }
            return SshKey(priv.copyOf(32), String(inner.string()))
        }

        /** "SHA256:...", the fingerprint of any public key as ssh sends it. */
        fun fingerprint(blob: ByteArray): String =
            "SHA256:" + Base64.getEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))
    }
}

/** ssh's string: its length, then its bytes. */
internal fun DataOutputStream.string(b: ByteArray) { writeInt(b.size); write(b) }
internal fun DataOutputStream.string(s: String) = string(s.toByteArray())
internal fun DataInputStream.string(): ByteArray = ByteArray(readInt()).also(::readFully)

internal fun sshBytes(write: DataOutputStream.() -> Unit): ByteArray =
    ByteArrayOutputStream().also { DataOutputStream(it).write() }.toByteArray()
