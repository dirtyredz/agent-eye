package local.agenteye.selfupdate.install

import local.agenteye.selfupdate.UpdateFixtures
import local.agenteye.selfupdate.install.InstallRecovery.Outcome
import local.agenteye.selfupdate.storage.InstallRecord
import local.agenteye.selfupdate.storage.InstallStage
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallRecoveryTest {
    private val committed =
        InstallRecord(UpdateFixtures.candidate(), 9, "a".repeat(32), InstallStage.Committed)

    private fun outcome(
        record: InstallRecord? = committed,
        code: Long = 2,
        eligible: Boolean = true,
        session: SessionState = SessionState.Committed,
        confirmation: Boolean = false
    ) = InstallRecovery.reconcile(record, code, eligible, session, confirmation)

    @Test fun installedVersionNotTheCallbackProvesReplacement() {
        assertEquals(Outcome.None, outcome(record = null))
        assertEquals(Outcome.Replaced, outcome(code = 3))
        assertEquals(Outcome.Replaced, outcome(code = 4))
        // A higher code from an ineligible (e.g. debug) install is not our replacement.
        assertEquals(Outcome.StillRunning, outcome(code = 3, eligible = false))
    }

    @Test fun processDeathAroundCommit() {
        assertEquals(
            Outcome.Orphan,
            outcome(
                record = committed.copy(stage = InstallStage.Staging),
                session = SessionState.Open
            )
        )
        // Commit intent saved but commit() never took effect: abandon, never wait forever.
        val committing = committed.copy(stage = InstallStage.Committing)
        assertEquals(Outcome.Orphan, outcome(record = committing, session = SessionState.Open))
        assertEquals(Outcome.Orphan, outcome(session = SessionState.Open))
        assertEquals(Outcome.StillRunning, outcome(record = committing))
        assertEquals(Outcome.StillRunning, outcome())
        assertEquals(Outcome.Interrupted, outcome(session = SessionState.Missing))
    }

    @Test fun lostConfirmation() {
        val pending = committed.copy(pendingUserAction = true)
        assertEquals(Outcome.ConfirmationLost, outcome(record = pending))
        assertEquals(Outcome.StillRunning, outcome(record = pending, confirmation = true))
    }
}
