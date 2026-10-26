package local.agenteye.selfupdate

import java.util.concurrent.Executor
import local.agenteye.selfupdate.download.ApkDownloader
import local.agenteye.selfupdate.download.ApkVerifier
import local.agenteye.selfupdate.install.InstallStatus
import local.agenteye.selfupdate.install.PackageInstallerGateway
import local.agenteye.selfupdate.release.GitHubReleaseSource
import local.agenteye.selfupdate.storage.UpdateStore
import local.agenteye.selfupdate.transport.UpdateHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCoordinatorTest {
    private val connector = FakeConnector().healthy()
    private val inspector = FakeInspector()
    private val port = FakeInstallerPort()
    private val dir = tempDir()
    private var clock = 50_000L
    private val queue = ArrayDeque<Runnable>()
    private var immediate = true
    private var cameraStops = 0
    private var cameraAvailable = true

    private fun coordinator(): UpdateCoordinator {
        val store = UpdateStore(dir, replacingRename)
        val http = UpdateHttpClient(
            connector,
            object : local.agenteye.selfupdate.transport.Backoff {
                override var until: Long
                    get() = store.backoffUntil
                    set(value) {
                        store.backoffUntil = value
                    }
            },
            now = { clock }
        )
        return UpdateCoordinator(
            source = GitHubReleaseSource(http, UpdateFixtures.PIN),
            http = http,
            downloader = ApkDownloader(store, http) { clock },
            verifier = ApkVerifier(inspector, UpdateFixtures.PIN) { 34 },
            installer = PackageInstallerGateway(port, store) { "d".repeat(32) },
            inspector = inspector,
            store = store,
            worker = Executor { if (immediate) it.run() else queue.addLast(it) },
            deliver = { it.run() },
            sdk = 34,
            now = { clock }
        ).also {
            it.handoff = {
                if (cameraAvailable) cameraStops++
                cameraAvailable
            }
        }
    }

    private val updates = coordinator()

    @Test fun happyPathIsCheckUpdateThenAndroidInstall() {
        updates.foreground(false)
        assertEquals(0, connector.requests.size)
        updates.check()
        assertEquals(UpdatePhase.Available, updates.state.phase)
        assertEquals("1.1.1", updates.state.candidate?.versionName)
        assertEquals(0, cameraStops)
        updates.update()
        assertEquals(UpdatePhase.Installing, updates.state.phase)
        assertEquals(1, cameraStops)
        assertEquals(1, port.commits.size)
        updates.installResult(41, "d".repeat(32), InstallStatus.PendingUserAction)
        assertEquals(UpdatePhase.AwaitingConfirmation, updates.state.phase)
        // Android replaced the app: the next launch reconciles locally.
        inspector.installed = FakeInspector.release(3, "1.1.1")
        val requests = connector.requests.size
        val relaunched = coordinator()
        relaunched.foreground(false)
        assertEquals(UpdatePhase.Installed, relaunched.state.phase)
        assertEquals(requests, connector.requests.size)
    }

    @Test fun launchForegroundAndRecoveryNeverTouchTheNetwork() {
        updates.foreground(false)
        updates.foreground(true)
        updates.background()
        updates.installResult(1, null, InstallStatus.Success)
        coordinator().foreground(false)
        assertEquals(0, connector.requests.size)
        assertEquals(UpdatePhase.Idle, updates.state.phase)
    }

    @Test fun duplicateTapsRunOneOperation() {
        immediate = false
        updates.foreground(false)
        updates.check()
        updates.check()
        updates.update()
        assertEquals(1, queue.size)
        queue.removeFirst().run()
        assertEquals(UpdatePhase.Available, updates.state.phase)
        updates.update()
        updates.update()
        updates.install()
        assertEquals(1, queue.size)
    }

    @Test fun backgroundCancelsNetworkWorkAndStaleResultsAreIgnored() {
        immediate = false
        updates.foreground(false)
        updates.check()
        updates.background()
        assertEquals(UpdatePhase.Idle, updates.state.phase)
        queue.removeFirst().run()
        assertEquals("cancelled result is discarded", UpdatePhase.Idle, updates.state.phase)
        // Rotation is not backgrounding: the Activity simply re-attaches and the operation continues.
        updates.check()
        updates.foreground(false)
        queue.removeFirst().run()
        assertEquals(UpdatePhase.Available, updates.state.phase)
    }

    @Test fun cameraStopsOnlyAtVerifiedHandoff() {
        updates.foreground(false)
        connector.routes["https://release-assets.githubusercontent.com/x/apk?sig=secret"] =
            { FakeResponse(200, ByteArray(24)) }
        updates.check()
        updates.update()
        assertEquals(UpdateProblem.Corrupt, updates.state.problem)
        assertEquals("check/download failures never stop capture", 0, cameraStops)
        connector.healthy()
        cameraAvailable = false
        updates.update()
        assertEquals(UpdatePhase.Ready, updates.state.phase)
        assertTrue(port.commits.isEmpty())
        cameraAvailable = true
        updates.install()
        assertEquals(1, cameraStops)
        assertEquals(1, port.commits.size)
    }

    @Test fun permissionDenialWaitsForAnExplicitInstallAfterReturning() {
        port.allowed = false
        updates.foreground(false)
        updates.check()
        updates.update()
        assertEquals(UpdatePhase.Permission, updates.state.phase)
        port.allowed = true
        val requests = connector.requests.size
        updates.foreground(false)
        assertEquals(UpdatePhase.Ready, updates.state.phase)
        assertEquals("returning from settings starts nothing", requests, connector.requests.size)
        assertTrue(port.commits.isEmpty())
        updates.install()
        assertEquals(UpdatePhase.Installing, updates.state.phase)
        assertEquals(requests, connector.requests.size)
    }

    @Test fun installCancellationNeedsAnExplicitRestart() {
        updates.foreground(false)
        updates.check()
        updates.update()
        updates.installResult(41, "d".repeat(32), InstallStatus.Aborted)
        assertEquals(UpdateProblem.InstallCancelled, updates.state.problem)
        assertEquals(1, cameraStops)
        updates.install()
        assertEquals(2, port.commits.size)
    }

    @Test fun lostConfirmationOffersAbandonAndRetry() {
        updates.foreground(false)
        updates.check()
        updates.update()
        updates.installResult(41, "d".repeat(32), InstallStatus.PendingUserAction)
        val restarted = coordinator()
        restarted.foreground(haveConfirmation = false)
        assertEquals(UpdateProblem.ConfirmationLost, restarted.state.problem)
        restarted.abandonAndRetry()
        assertEquals(UpdatePhase.Ready, restarted.state.phase)
        assertTrue(41 in port.abandoned)
    }

    @Test fun rateLimitIsDistinctAndRespectsPersistedBackoff() {
        updates.foreground(false)
        connector.routes["https://api.github.com/repos/dirtyredz/agent-eye/releases/latest"] = {
            FakeResponse(429, headers = mapOf("Retry-After" to "300"))
        }
        updates.check()
        assertEquals(UpdateProblem.RateLimited, updates.state.problem)
        val requests = connector.requests.size
        val restarted = coordinator()
        restarted.foreground(false)
        restarted.check()
        assertEquals(UpdateProblem.RateLimited, restarted.state.problem)
        assertEquals(clock + 300_000, restarted.state.retryAt)
        assertEquals(requests, connector.requests.size)
    }

    @Test fun debugBuildsExplainTheChannelWithoutNetwork() {
        inspector.installed = FakeInspector.release(2, debuggable = true)
        updates.foreground(false)
        updates.check()
        assertEquals(UpdateProblem.Channel, updates.state.problem)
        assertEquals(0, connector.requests.size)
    }

    @Test fun upToDateAndChangedCandidates() {
        inspector.installed = FakeInspector.release(3, "1.1.1")
        updates.foreground(false)
        updates.check()
        assertEquals(UpdatePhase.Current, updates.state.phase)
        assertNull(updates.state.candidate)
        inspector.installed = FakeInspector.release(2)
        updates.foreground(false)
        updates.check()
        connector.routes["https://api.github.com/repos/dirtyredz/agent-eye/releases/77"] =
            { FakeResponse(404) }
        updates.update()
        assertEquals(UpdateProblem.Changed, updates.state.problem)
        assertNull(updates.state.candidate)
        assertFalse(port.commits.isNotEmpty())
    }
}
