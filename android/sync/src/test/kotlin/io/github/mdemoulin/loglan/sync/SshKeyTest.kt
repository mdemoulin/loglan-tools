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
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

// The key is held to ssh-keygen itself: what it reads in our files, and
// what we read in its.
class SshKeyTest {
    @TempDir
    lateinit var dir: File

    private fun run(vararg cmd: String, input: String? = null): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        p.outputStream.use { o -> input?.let { o.write(it.toByteArray()) } }
        val out = p.inputStream.bufferedReader().readText()
        assertEquals(0, p.waitFor(), out)
        return out.trim()
    }

    private fun keyFile(name: String, text: String) = File(dir, name).apply {
        writeText(text)
        Files.setPosixFilePermissions(toPath(), PosixFilePermissions.fromString("rw-------"))
    }

    @Test
    fun sshKeygenReadsTheKeyFile() {
        val key = SshKey.generate("loglan-sync test")
        val file = keyFile("id_ed25519", key.privateFile())
        assertEquals(key.publicLine, run("ssh-keygen", "-y", "-f", file.path))
        val pub = File(dir, "id_ed25519.pub").apply { writeText(key.publicLine + "\n") }
        assertEquals("256 ${key.fingerprint} loglan-sync test (ED25519)", run("ssh-keygen", "-l", "-f", pub.path))
    }

    @Test
    fun readsWhatSshKeygenMakes() {
        val file = File(dir, "k")
        run("ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-C", "made by ssh-keygen", "-f", file.path)
        val key = SshKey.parse(file.readText())
        assertEquals(File(dir, "k.pub").readText().trim(), key.publicLine)
        assertEquals("made by ssh-keygen", key.comment)
    }

    @Test
    fun readsItsOwnFileBack() {
        val key = SshKey.generate("round trip")
        val again = SshKey.parse(key.privateFile())
        assertContentEquals(key.publicBlob, again.publicBlob)
        assertEquals(key.publicLine, again.publicLine)
    }

    @Test
    fun signaturesVerifyWithThePublicKey() {
        val key = SshKey.generate("signer")
        val data = "some data to sign".toByteArray()
        val verifier = org.bouncycastle.crypto.signers.Ed25519Signer()
        verifier.init(false, org.bouncycastle.crypto.params.Ed25519PublicKeyParameters(key.publicKey, 0))
        verifier.update(data, 0, data.size)
        assertTrue(verifier.verifySignature(key.sign(data)))
    }

    @Test
    fun aKeyWithAPassphraseIsRefused() {
        val file = File(dir, "p")
        run("ssh-keygen", "-q", "-t", "ed25519", "-N", "secret", "-f", file.path)
        assertFailsWith<IllegalArgumentException> { SshKey.parse(file.readText()) }
        assertFailsWith<IllegalArgumentException> { SshKey.parse("not a key") }
    }
}
