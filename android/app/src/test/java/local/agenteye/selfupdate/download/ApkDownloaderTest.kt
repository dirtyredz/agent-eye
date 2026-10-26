package local.agenteye.selfupdate.download

import local.agenteye.selfupdate.FakeConnector
import local.agenteye.selfupdate.FakeResponse
import local.agenteye.selfupdate.MemoryBackoff
import local.agenteye.selfupdate.UpdateFailure
import local.agenteye.selfupdate.UpdateFixtures
import local.agenteye.selfupdate.UpdateProblem
import local.agenteye.selfupdate.replacingRename
import local.agenteye.selfupdate.storage.UpdateStore
import local.agenteye.selfupdate.tempDir
import local.agenteye.selfupdate.transport.UpdateHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ApkDownloaderTest {
    private val connector = FakeConnector().healthy()
    private var clock = 10_000L
    private var space = Long.MAX_VALUE
    private val store = UpdateStore(tempDir(), replacingRename) { space }
    private val downloader = ApkDownloader(
        store,
        UpdateHttpClient(connector, MemoryBackoff(), now = { clock }),
        now = { clock }
    )
    private val candidate = UpdateFixtures.candidate()
    private val cdn = "https://release-assets.githubusercontent.com/x/apk?sig=secret"

    private fun problem(block: () -> Unit) = assertThrows(UpdateFailure::class.java) {
        block()
    }.problem

    @Test fun streamsVerifiesAndRetainsTheCandidateApk() {
        var seen = 0L
        val file = downloader.download(candidate) { seen = it }
        assertArrayEquals(UpdateFixtures.apk, file.readBytes())
        assertEquals(candidate.apkSize, seen)
        assertFalse(store.partial.exists())
        assertEquals(file, downloader.retained(candidate))
        val requests = connector.requests.size
        downloader.download(candidate) {}
        assertEquals("retained file is reused", requests, connector.requests.size)
        // Signed CDN query strings never reach durable state.
        assertFalse(
            store.directory.listFiles()!!.any {
                it.name.endsWith(".json") &&
                    it.readText().contains("sig=")
            }
        )
    }

    @Test fun corruptBytesAreDeletedAndNeverPromoted() {
        connector.routes[cdn] =
            { FakeResponse(200, UpdateFixtures.apk.copyOf().also { it[0] = 'X'.code.toByte() }) }
        assertEquals(UpdateProblem.Corrupt, problem { downloader.download(candidate) {} })
        assertFalse(store.partial.exists())
        assertFalse(store.verified.exists())
    }

    @Test fun interruptedTransferCleansThePartialAndRestartsFromZero() {
        connector.routes[cdn] = { FakeResponse(200, UpdateFixtures.apk, failAfter = 5) }
        assertEquals(UpdateProblem.Network, problem { downloader.download(candidate) {} })
        assertFalse(store.partial.exists())
        connector.healthy()
        assertArrayEquals(UpdateFixtures.apk, downloader.download(candidate) {}.readBytes())
    }

    @Test fun insufficientSpaceFailsBeforeAnyTransfer() {
        space = candidate.apkSize
        val requests = connector.requests.size
        assertEquals(UpdateProblem.NoSpace, problem { downloader.download(candidate) {} })
        assertEquals(requests, connector.requests.size)
    }

    @Test fun retainedFilesExpireAndAreCandidateBound() {
        downloader.download(candidate) {}
        assertNull(downloader.retained(candidate.copy(apkAssetId = 5)))
        assertFalse(store.verified.exists())
        downloader.download(candidate) {}
        clock += ApkDownloader.RETAIN_MS + 1
        assertNull(downloader.retained(candidate))
        assertFalse(store.verified.exists())
    }
}
