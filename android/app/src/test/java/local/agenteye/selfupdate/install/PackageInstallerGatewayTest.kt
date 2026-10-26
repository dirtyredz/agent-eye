package local.agenteye.selfupdate.install

import local.agenteye.selfupdate.FakeInstallerPort
import local.agenteye.selfupdate.UpdateFailure
import local.agenteye.selfupdate.UpdateFixtures
import local.agenteye.selfupdate.UpdateProblem
import local.agenteye.selfupdate.replacingRename
import local.agenteye.selfupdate.storage.InstallStage
import local.agenteye.selfupdate.storage.UpdateStore
import local.agenteye.selfupdate.tempDir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageInstallerGatewayTest {
    private val port = FakeInstallerPort()
    private val store = UpdateStore(tempDir(), replacingRename)
    private val nonce = "b".repeat(32)
    private val gateway = PackageInstallerGateway(port, store) { nonce }
    private val candidate = UpdateFixtures.candidate()
    private val apk = store.verified.apply { writeBytes(UpdateFixtures.apk) }

    private fun problem(block: () -> Unit) = assertThrows(UpdateFailure::class.java) {
        block()
    }.problem

    @Test fun stagesPersistsBeforeCommitAndCommitsOnce() {
        gateway.stage(apk, candidate)
        assertEquals(listOf(41 to nonce), port.commits)
        val record = store.readInstall()!!
        assertEquals(InstallStage.Committed, record.stage)
        assertEquals(41, record.sessionId)
        // A live committed session blocks a duplicate installation.
        assertEquals(UpdateProblem.InstallInterrupted, problem { gateway.stage(apk, candidate) })
        assertEquals(1, port.commits.size)
    }

    @Test fun permissionDenialCreatesNoSession() {
        port.allowed = false
        assertEquals(UpdateProblem.InstallDenied, problem { gateway.stage(apk, candidate) })
        assertTrue(port.sessions.isEmpty())
    }

    @Test fun bytesChangedBetweenVerificationAndSessionWriteAbandon() {
        port.tamper = { it.copyOf().also { b -> b[0] = 1 } }
        assertEquals(UpdateProblem.Corrupt, problem { gateway.stage(apk, candidate) })
        assertEquals(listOf(41), port.abandoned)
        assertNull(store.readInstall())
        assertTrue(port.commits.isEmpty())
    }

    @Test fun commitFailureAbandonsAndClears() {
        port.failCommit = true
        assertEquals(UpdateProblem.NoSpace, problem { gateway.stage(apk, candidate) })
        assertEquals(listOf(41), port.abandoned)
        assertNull(store.readInstall())
    }

    @Test fun forgedCallbacksAreIgnored() {
        gateway.stage(apk, candidate)
        assertNull(gateway.accept(41, "c".repeat(32), InstallStatus.Success))
        assertNull(gateway.accept(40, nonce, InstallStatus.Success))
        assertNull(gateway.accept(41, null, InstallStatus.Aborted))
        assertEquals(
            InstallStatus.PendingUserAction,
            gateway.accept(41, nonce, InstallStatus.PendingUserAction)
        )
        assertTrue(store.readInstall()!!.pendingUserAction)
        assertEquals(InstallStatus.Aborted, gateway.accept(41, nonce, InstallStatus.Aborted))
        assertNull(store.readInstall())
    }

    @Test fun reconcileAbandonsOrphansAndDetectsReplacementAndInterruption() {
        // Process death after session creation but before commit.
        port.failCommit = false
        val id = port.create(1)
        store.saveInstall(
            local.agenteye.selfupdate.storage.InstallRecord(
                candidate,
                id,
                nonce,
                InstallStage.Staging
            )
        )
        assertEquals(InstallRecovery.Outcome.Orphan, gateway.reconcile(2, true, false))
        assertTrue(id in port.abandoned)
        assertNull(store.readInstall())

        gateway.stage(apk, candidate)
        assertEquals(InstallRecovery.Outcome.StillRunning, gateway.reconcile(2, true, false))
        gateway.accept(42, nonce, InstallStatus.PendingUserAction)
        assertEquals(
            InstallRecovery.Outcome.ConfirmationLost,
            gateway.reconcile(2, true, haveConfirmation = false)
        )
        assertTrue(gateway.abandonPending())
        assertNull(store.readInstall())

        gateway.stage(apk, candidate)
        port.sessions.clear()
        assertEquals(InstallRecovery.Outcome.Interrupted, gateway.reconcile(2, true, false))

        gateway.stage(apk, candidate)
        assertEquals(InstallRecovery.Outcome.Replaced, gateway.reconcile(3, true, false))
        assertNull(store.readInstall())
        assertTrue(!store.verified.exists())
    }
}
