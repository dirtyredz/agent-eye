package local.agenteye.selfupdate.storage

import java.io.File
import local.agenteye.selfupdate.replacingRename
import local.agenteye.selfupdate.tempDir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackoffStoreTest {
    private val dir = File(tempDir(), "self-update")

    @Test fun deadlineRoundTripsAcrossInstancesInTheSameFile() {
        BackoffStore(dir, replacingRename).until = 99
        assertEquals(99L, BackoffStore(dir, replacingRename).until)
        assertEquals("{\"schema\":1,\"until\":99}", File(dir, "backoff.json").readText())
        assertFalse(dir.listFiles()!!.any { it.name.endsWith(".tmp") })
    }

    @Test fun missingOrCorruptRecordReadsAsNoBackoff() {
        val store = BackoffStore(dir, replacingRename)
        assertEquals(0L, store.until)
        File(dir, "backoff.json").writeText("{\"schema\":1,\"until\":")
        assertEquals(0L, store.until)
        assertFalse(File(dir, "backoff.json").exists())
    }
}
