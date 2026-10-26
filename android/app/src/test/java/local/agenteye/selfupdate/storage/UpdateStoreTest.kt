package local.agenteye.selfupdate.storage

import java.io.File
import local.agenteye.selfupdate.UpdateFixtures
import local.agenteye.selfupdate.replacingRename
import local.agenteye.selfupdate.tempDir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateStoreTest {
    private val parent = tempDir()
    private val dir = File(parent, "self-update")
    private val store = UpdateStore(dir, replacingRename)
    private val candidate = UpdateFixtures.candidate()

    @Test fun recordsRoundTripAtomically() {
        store.saveTransfer(TransferRecord(candidate, 5))
        assertEquals(TransferRecord(candidate, 5), UpdateStore(dir, replacingRename).readTransfer())
        val install =
            InstallRecord(
                candidate,
                7,
                "a".repeat(32),
                InstallStage.Committed,
                pendingUserAction = true
            )
        store.saveInstall(install)
        assertEquals(install, store.readInstall())
        store.until = 99
        assertEquals(99L, store.until)
        assertFalse(dir.listFiles()!!.any { it.name.endsWith(".tmp") })
    }

    @Test fun corruptOrOldSchemaRecordsAreDiscarded() {
        File(dir, "transfer.json").writeText("{\"schema\":1,\"candidate\":")
        assertNull(store.readTransfer())
        assertFalse(File(dir, "transfer.json").exists())
        File(dir, "install.json").writeText("{\"schema\":0}")
        assertNull(store.readInstall())
        File(dir, "install.json").writeText(
            "{\"schema\":1,\"candidate\":${StrictJsonCandidate.json},\"sessionId\":1,\"nonce\":\"short\",\"stage\":\"Committed\",\"pendingUserAction\":false}"
        )
        assertNull("bad nonce", store.readInstall())
        File(dir, "transfer.json").writeBytes(ByteArray(20_000))
        assertNull(store.readTransfer())
    }

    @Test fun orphansAreRemovedButCleanupStaysInsideTheUpdaterDirectory() {
        val outside = File(parent, "keep.txt").apply { writeText("x") }
        File(dir, "transfer.json.tmp").writeText("x")
        File(dir, "stray.bin").writeText("x")
        store.partial.writeText("x")
        store.removeOrphans()
        assertEquals(emptyList<String>(), dir.listFiles()!!.map { it.name })
        assertTrue(outside.exists())
    }

    @Test fun candidateMismatchKeepsRecordsSeparate() {
        store.saveTransfer(TransferRecord(candidate))
        store.verified.writeText("x")
        store.clearInstall()
        assertTrue(store.verified.exists())
        store.clearTransfer()
        assertFalse(store.verified.exists())
        assertNull(store.readTransfer())
    }

    private object StrictJsonCandidate {
        val json: String = local.agenteye.json.StrictJson.write(
            RecordCodec.encode(UpdateFixtures.candidate())
        )
    }
}
