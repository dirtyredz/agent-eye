package local.agenteye.selfupdate.install

import local.agenteye.selfupdate.storage.InstallRecord
import local.agenteye.selfupdate.storage.InstallStage

enum class SessionState { Missing, Open, Committed }

/**
 * Pure launch/foreground reconciliation of a persisted installer session. Local only: no
 * network. A success callback alone never proves replacement; the installed version does.
 */
object InstallRecovery {
    enum class Outcome {
        /** No installer record. */
        None,

        /** Installed package (pinned signer) is at least the target: clear files and records. */
        Replaced,

        /** Session created but never committed (or commit never took effect): abandon it. */
        Orphan,

        /** A committed session is still alive; observe it, never start a second one. */
        StillRunning,

        /** Android wants confirmation but the confirmation intent was lost (process death). */
        ConfirmationLost,

        /** The session is gone and the app was not replaced. */
        Interrupted
    }

    /** Staging, or commit intent with the session still open (commit() never took effect), is an orphan. */
    fun reconcile(
        record: InstallRecord?,
        installedCode: Long,
        installedEligible: Boolean,
        session: SessionState,
        haveConfirmation: Boolean
    ): Outcome = when {
        record == null -> Outcome.None
        installedEligible && installedCode >= record.candidate.versionCode -> Outcome.Replaced
        record.stage == InstallStage.Staging || session == SessionState.Open -> Outcome.Orphan
        session == SessionState.Missing -> Outcome.Interrupted
        record.pendingUserAction && !haveConfirmation -> Outcome.ConfirmationLost
        else -> Outcome.StillRunning
    }
}
