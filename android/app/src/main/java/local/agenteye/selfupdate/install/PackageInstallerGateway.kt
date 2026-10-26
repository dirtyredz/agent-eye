package local.agenteye.selfupdate.install

import java.io.File
import local.agenteye.selfupdate.Candidate
import local.agenteye.selfupdate.UpdateFailure
import local.agenteye.selfupdate.UpdateProblem
import local.agenteye.selfupdate.storage.InstallRecord
import local.agenteye.selfupdate.storage.InstallStage
import local.agenteye.selfupdate.storage.UpdateStore

/** Installer outcome reported by the session callback. */
enum class InstallStatus {
    PendingUserAction,
    Success,
    Aborted,
    Blocked,
    Storage,
    Incompatible,
    Invalid,
    Failure
}

/** The platform side of PackageInstaller, faked in tests. */
interface InstallerPort {
    /** Per-app install permission (API 26+) or the legacy unknown-sources setting (API 24-25). */
    fun allowed(): Boolean

    fun create(size: Long): Int

    /** Streams [file] into the session as `base.apk`, fsyncs, and returns (bytes written, SHA-256). */
    fun write(sessionId: Int, file: File): Pair<Long, String>

    fun commit(sessionId: Int, nonce: String)

    fun abandon(sessionId: Int)

    fun state(sessionId: Int): SessionState
}

/**
 * Session policy: one session at a time, its identity and random callback nonce persisted
 * before each irreversible transition, the written bytes re-hashed against the candidate, and
 * callbacks accepted only for the stored session and nonce.
 */
class PackageInstallerGateway(
    private val port: InstallerPort,
    private val store: UpdateStore,
    private val nonce: () -> String
) {
    fun allowed() = port.allowed()

    fun stage(apk: File, candidate: Candidate) {
        if (!port.allowed()) throw UpdateFailure(UpdateProblem.InstallDenied)
        store.readInstall()?.let { existing ->
            if (existing.stage != InstallStage.Staging &&
                port.state(existing.sessionId) == SessionState.Committed
            ) {
                // Never create a second installation while Android is still completing one.
                throw UpdateFailure(UpdateProblem.InstallInterrupted)
            }
            runCatching { port.abandon(existing.sessionId) }
            store.clearInstall()
        }
        val id = try {
            port.create(apk.length())
        } catch (_: SecurityException) {
            throw UpdateFailure(UpdateProblem.InstallDenied)
        } catch (_: Exception) {
            throw UpdateFailure(UpdateProblem.InstallFailed)
        }
        val record = InstallRecord(candidate, id, nonce(), InstallStage.Staging)
        try {
            store.saveInstall(record)
            val (written, hash) = port.write(id, apk)
            if (written != candidate.apkSize ||
                hash != candidate.apkSha256
            ) {
                throw UpdateFailure(UpdateProblem.Corrupt)
            }
            // Commit intent first: process death between here and commit() leaves an open
            // session that recovery abandons, never one that looks like a running install.
            store.saveInstall(record.copy(stage = InstallStage.Committing))
            port.commit(id, record.nonce)
            // Keep any flags a fast callback already stored; only upgrade the stage.
            store.readInstall()?.takeIf { it.sessionId == id }?.let {
                store.saveInstall(it.copy(stage = InstallStage.Committed))
            }
        } catch (e: Exception) {
            runCatching { port.abandon(id) }
            store.clearInstall()
            throw when (e) {
                is UpdateFailure -> e
                is SecurityException -> UpdateFailure(UpdateProblem.InstallDenied)
                is java.io.IOException -> UpdateFailure(UpdateProblem.NoSpace)
                else -> UpdateFailure(UpdateProblem.InstallFailed)
            }
        }
    }

    /** Validates a callback; returns the status only for the stored committed session and nonce. */
    fun accept(sessionId: Int, nonce: String?, status: InstallStatus): InstallStatus? {
        val record = store.readInstall() ?: return null
        if (record.stage == InstallStage.Staging || record.sessionId != sessionId ||
            record.nonce != nonce
        ) {
            return null
        }
        when (status) {
            InstallStatus.PendingUserAction -> store.saveInstall(
                record.copy(pendingUserAction = true)
            )

            InstallStatus.Success -> Unit

            // the installed version, not the callback, proves replacement
            else -> store.clearInstall()
        }
        return status
    }

    /** Local reconciliation with its side effects applied; performs no network access. */
    fun reconcile(
        installedCode: Long,
        installedEligible: Boolean,
        haveConfirmation: Boolean
    ): InstallRecovery.Outcome {
        val record = store.readInstall()
        val session =
            record?.let {
                runCatching { port.state(it.sessionId) }.getOrDefault(SessionState.Missing)
            }
                ?: SessionState.Missing
        val outcome = InstallRecovery.reconcile(
            record,
            installedCode,
            installedEligible,
            session,
            haveConfirmation
        )
        when (outcome) {
            InstallRecovery.Outcome.Replaced -> {
                store.clearInstall()
                store.clearTransfer()
            }

            InstallRecovery.Outcome.Orphan -> {
                runCatching { port.abandon(record!!.sessionId) }
                store.clearInstall()
            }

            InstallRecovery.Outcome.Interrupted -> store.clearInstall()

            else -> Unit
        }
        return outcome
    }

    /** Explicit user recovery for a lost confirmation: abandon the waiting session. */
    fun abandonPending(): Boolean {
        val record = store.readInstall() ?: return true
        if (!record.pendingUserAction &&
            port.state(record.sessionId) != SessionState.Missing
        ) {
            return false
        }
        runCatching { port.abandon(record.sessionId) }
        store.clearInstall()
        return true
    }
}
