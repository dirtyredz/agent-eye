package local.agenteye.ui.update

import local.agenteye.selfupdate.UpdateFixtures
import local.agenteye.selfupdate.UpdatePhase
import local.agenteye.selfupdate.UpdateProblem
import local.agenteye.selfupdate.UpdateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePresenterTest {
    private val base = UpdateState(installedVersion = "1.1.0", installedCode = 2)
    private val candidate = UpdateFixtures.candidate().copy(apkSize = 8 * 1_048_576L)

    private fun view(state: UpdateState) = UpdatePresenter.present(state, now = 0)

    @Test fun showsInstalledVersionAndCheckButtonByDefault() {
        val v = view(base)
        assertEquals("Installed: 1.1.0 (2)", v.version)
        assertEquals(UpdateAction.Check, v.primary?.action)
        assertNull(v.progress)
    }

    @Test fun availableShowsVersionSizeAndCameraWarning() {
        val v = view(base.copy(phase = UpdatePhase.Available, candidate = candidate))
        assertTrue(
            v.status,
            v.status.contains("1.1.1") && v.status.contains("8.0 MB") &&
                v.status.contains("stops the camera")
        )
        assertEquals(UpdateAction.Update, v.primary?.action)
    }

    @Test fun downloadingShowsProgressAndCancel() {
        val v = view(
            base.copy(
                phase = UpdatePhase.Downloading,
                candidate = candidate,
                downloaded =
                    2 * 1_048_576L
            )
        )
        assertEquals(25, v.progress)
        assertNull(v.primary)
        assertEquals(UpdateAction.Cancel, v.secondary?.action)
        assertEquals(
            UpdateAction.Cancel,
            view(base.copy(phase = UpdatePhase.Checking)).secondary?.action
        )
    }

    @Test fun permissionAndConfirmationFlows() {
        assertEquals(
            UpdateAction.GrantPermission,
            view(base.copy(phase = UpdatePhase.Permission, candidate = candidate)).primary?.action
        )
        assertEquals(
            UpdateAction.Install,
            view(base.copy(phase = UpdatePhase.Ready, candidate = candidate)).primary?.action
        )
        assertNull(view(base.copy(phase = UpdatePhase.Installing)).primary)
        assertEquals(
            UpdateAction.Continue,
            view(base.copy(phase = UpdatePhase.AwaitingConfirmation)).primary?.action
        )
    }

    @Test fun actionableErrors() {
        fun failed(problem: UpdateProblem, downloaded: Long = 0) = view(
            base.copy(
                phase = UpdatePhase.Failed,
                problem = problem,
                candidate = candidate,
                downloaded = downloaded
            )
        )
        assertEquals(UpdateAction.Check, failed(UpdateProblem.Network).primary?.action)
        assertTrue(
            view(
                base.copy(
                    phase = UpdatePhase.Failed,
                    problem = UpdateProblem.RateLimited,
                    retryAt = 120_000
                )
            ).status.contains("2 min")
        )
        assertNull("no recovery that uninstalls", failed(UpdateProblem.Channel).primary)
        assertEquals(
            UpdateAction.Install,
            failed(UpdateProblem.InstallCancelled, candidate.apkSize).primary?.action
        )
        assertTrue(
            failed(UpdateProblem.InstallCancelled, candidate.apkSize).status.contains("press Start")
        )
        assertEquals(UpdateAction.Update, failed(UpdateProblem.Corrupt).primary?.action)
        assertEquals(
            UpdateAction.AbandonRetry,
            failed(UpdateProblem.ConfirmationLost).primary?.action
        )
        for (p in UpdateProblem.values()) assertTrue(p.name, failed(p).status.isNotBlank())
    }

    @Test fun currentExplainsIncompatibleNewerRelease() {
        val v =
            view(
                base.copy(
                    phase = UpdatePhase.Current,
                    newerIncompatible = "1.2.0 needs Android API 30"
                )
            )
        assertTrue(v.status.contains("up to date") && v.status.contains("API 30"))
    }
}
