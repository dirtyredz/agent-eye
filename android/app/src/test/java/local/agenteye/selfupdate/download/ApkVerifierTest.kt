package local.agenteye.selfupdate.download

import java.io.File
import local.agenteye.selfupdate.FakeInspector
import local.agenteye.selfupdate.UpdateFailure
import local.agenteye.selfupdate.UpdateFixtures
import local.agenteye.selfupdate.UpdateProblem
import local.agenteye.selfupdate.tempDir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkVerifierTest {
    private val inspector = FakeInspector()
    private var sdk = 34
    private val verifier = ApkVerifier(inspector, UpdateFixtures.PIN) { sdk }
    private val candidate = UpdateFixtures.candidate()
    private val file = File(tempDir(), "update.apk").apply { writeBytes(UpdateFixtures.apk) }
    private val other = "e".repeat(64)

    private fun problem() = assertThrows(UpdateFailure::class.java) {
        verifier.verify(file, candidate)
    }.problem

    @Test fun acceptsMatchingBytesIdentityAndSigner() {
        assertTrue(verifier.eligible())
        verifier.verify(file, candidate)
    }

    @Test fun byteCorruptionOrWrongLengthIsCorrupt() {
        file.writeBytes(UpdateFixtures.apk.copyOf().also { it[3] = 0 })
        assertEquals(UpdateProblem.Corrupt, problem())
        file.writeBytes(UpdateFixtures.apk + 1)
        assertEquals(UpdateProblem.Corrupt, problem())
    }

    @Test fun forgedMatchingChecksumMetadataWithWrongSignerIsRefused() {
        // Metadata and bytes agree, but the archive is signed by someone else.
        inspector.archive = FakeInspector.release(3, "1.1.1", signers = listOf(other))
        assertEquals(UpdateProblem.Channel, problem())
        inspector.archive =
            FakeInspector.release(3, "1.1.1", signers = listOf(UpdateFixtures.PIN, other))
        assertEquals(UpdateProblem.Channel, problem())
        assertEquals(
            UpdateProblem.Channel,
            assertThrows(UpdateFailure::class.java) {
                verifier.verify(file, candidate.copy(signerSha256 = other))
            }.problem
        )
    }

    @Test fun debugOrDifferentlySignedInstallationsCannotSelfUpdate() {
        inspector.installed = FakeInspector.release(2, debuggable = true)
        assertFalse(verifier.eligible())
        assertEquals(UpdateProblem.Channel, problem())
        inspector.installed = FakeInspector.release(2, signers = listOf(other))
        assertEquals(UpdateProblem.Channel, problem())
        inspector.installed = FakeInspector.release(2).copy(packageName = "local.agenteye.debug")
        assertEquals(UpdateProblem.Channel, problem())
        assertFalse(ApkVerifier(FakeInspector(), "") { 34 }.eligible())
    }

    @Test fun packageVersionAndSdkMismatchesAreRefused() {
        inspector.archive = FakeInspector.release(3, "1.1.1").copy(packageName = "evil")
        assertEquals(UpdateProblem.Corrupt, problem())
        inspector.archive = FakeInspector.release(4, "1.1.1")
        assertEquals(UpdateProblem.Corrupt, problem())
        inspector.archive = FakeInspector.release(3, "1.1.2")
        assertEquals(UpdateProblem.Corrupt, problem())
        inspector.archive = FakeInspector.release(3, "1.1.1", debuggable = true)
        assertEquals(UpdateProblem.Corrupt, problem())
        inspector.archive = null
        assertEquals(UpdateProblem.Corrupt, problem())
        inspector.archive = FakeInspector.release(3, "1.1.1")
        inspector.installed = FakeInspector.release(3)
        assertEquals(UpdateProblem.Incompatible, problem())
        inspector.installed = FakeInspector.release(2)
        sdk = 23
        assertEquals(UpdateProblem.Incompatible, problem())
    }
}
