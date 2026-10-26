package local.agenteye.selfupdate

import java.util.concurrent.Executor
import local.agenteye.selfupdate.download.ApkDownloader
import local.agenteye.selfupdate.download.ApkInspector
import local.agenteye.selfupdate.download.ApkVerifier
import local.agenteye.selfupdate.install.InstallRecovery
import local.agenteye.selfupdate.install.InstallStatus
import local.agenteye.selfupdate.install.PackageInstallerGateway
import local.agenteye.selfupdate.release.GitHubReleaseSource
import local.agenteye.selfupdate.storage.UpdateStore
import local.agenteye.selfupdate.transport.UpdateHttpClient

/**
 * Check -> download -> verify -> install state machine. Public methods run on the main thread;
 * work runs on [worker] and results come back through [deliver]. Exactly one operation runs
 * at a time (duplicate taps are ignored). Only [check] and [update] reach the network; launch,
 * [foreground], recovery and install callbacks are local.
 */
class UpdateCoordinator(
    private val source: GitHubReleaseSource,
    private val http: UpdateHttpClient,
    private val downloader: ApkDownloader,
    private val verifier: ApkVerifier,
    private val installer: PackageInstallerGateway,
    private val inspector: ApkInspector,
    private val store: UpdateStore,
    private val worker: Executor,
    private val deliver: (Runnable) -> Unit,
    private val sdk: Int,
    private val now: () -> Long = System::currentTimeMillis
) {
    var state = UpdateState()
        private set
    private val listeners = mutableListOf<(UpdateState) -> Unit>()
    private var generation = 0
    private var running = false

    /**
     * Stops the camera session at the verified install handoff. Set by the visible Activity;
     * returns false when no Activity can take the handoff (the install then waits).
     */
    var handoff: (() -> Boolean)? = null

    fun observe(listener: (UpdateState) -> Unit) {
        listeners.add(listener)
        listener(state)
    }

    fun forget(listener: (UpdateState) -> Unit) {
        listeners.remove(listener)
    }

    /** Local reconciliation on launch/foreground. Never touches the network. */
    fun foreground(haveConfirmation: Boolean) {
        val installed = inspector.installed()
        val eligible = verifier.eligible()
        var next = state.copy(
            installedVersion = installed.versionName.orEmpty(),
            installedCode = installed.versionCode,
            installAllowed = installer.allowed()
        )
        if (!running) {
            store.removeOrphans()
            when (installer.reconcile(installed.versionCode, eligible, haveConfirmation)) {
                InstallRecovery.Outcome.Replaced ->
                    next =
                        next.copy(phase = UpdatePhase.Installed, candidate = null, problem = null)

                InstallRecovery.Outcome.ConfirmationLost ->
                    next =
                        failed(next, UpdateProblem.ConfirmationLost, keep = true)

                InstallRecovery.Outcome.Interrupted ->
                    next =
                        failed(next, UpdateProblem.InstallInterrupted, keep = true)

                InstallRecovery.Outcome.StillRunning ->
                    next =
                        next.copy(
                            phase = if (haveConfirmation) AWAITING else UpdatePhase.Installing
                        )

                else -> if (next.phase == UpdatePhase.Idle) next = restore(next)
            }
            if (next.phase == UpdatePhase.Permission &&
                next.installAllowed
            ) {
                next = next.copy(phase = UpdatePhase.Ready)
            }
        }
        publish(next)
    }

    /** Real backgrounding (not rotation): cancel network work; installer sessions continue. */
    fun background() {
        if (state.phase == UpdatePhase.Checking || state.phase == UpdatePhase.Downloading) cancel()
    }

    fun check() {
        if (running || state.phase == UpdatePhase.Installing ||
            state.phase == UpdatePhase.AwaitingConfirmation
        ) {
            return
        }
        if (!verifier.eligible()) return publish(failed(state, UpdateProblem.Channel))
        val until = store.backoffUntil
        if (now() <
            until
        ) {
            return publish(failed(state, UpdateProblem.RateLimited).copy(retryAt = until))
        }
        publish(state.copy(phase = UpdatePhase.Checking, problem = null, newerIncompatible = null))
        run({ source.check(state.installedCode, sdk) }) { result ->
            publish(
                when (result) {
                    is GitHubReleaseSource.Check.Available ->
                        state.copy(
                            phase = UpdatePhase.Available,
                            candidate = result.candidate,
                            downloaded = 0
                        )

                    is GitHubReleaseSource.Check.Incompatible -> state.copy(
                        phase = UpdatePhase.Current,
                        candidate = null,
                        newerIncompatible =
                            "${result.versionName} needs Android API ${result.minSdk}"
                    )

                    else -> state.copy(phase = UpdatePhase.Current, candidate = null)
                }
            )
        }
    }

    /** Download (or reuse a retained verified APK), verify, then hand off to the installer. */
    fun update() {
        val candidate = state.candidate ?: return
        if (running || state.busy || state.phase == UpdatePhase.AwaitingConfirmation) return
        publish(state.copy(phase = UpdatePhase.Downloading, problem = null, downloaded = 0))
        run({
            val file = downloader.retained(candidate) ?: run {
                source.revalidate(candidate)
                downloader.download(candidate) { bytes -> deliver(Runnable { progress(bytes) }) }
            }
            verifier.verify(file, candidate)
        }) {
            publish(state.copy(phase = UpdatePhase.Ready, downloaded = candidate.apkSize))
            install()
        }
    }

    /** Explicit install of the retained verified APK (also after granting permission). */
    fun install() {
        val candidate = state.candidate ?: return
        if (running || state.busy) return
        if (!installer.allowed()) {
            return publish(
                state.copy(phase = UpdatePhase.Permission, installAllowed = false)
            )
        }
        if (handoff?.invoke() != true) return publish(state.copy(phase = UpdatePhase.Ready))
        publish(state.copy(phase = UpdatePhase.Installing, problem = null, installAllowed = true))
        run({
            // Re-verify the retained bytes and the installed identity immediately before staging.
            val file = downloader.retained(candidate) ?: throw UpdateFailure(UpdateProblem.Corrupt)
            verifier.verify(file, candidate)
            installer.stage(file, candidate)
        }) { /* Android now owns the session; the callback moves the state on. */ }
    }

    fun cancel() {
        if (!running) return
        generation++
        running = false
        http.cancel()
        val phase = if (state.candidate != null) UpdatePhase.Available else UpdatePhase.Idle
        publish(state.copy(phase = phase, downloaded = 0))
    }

    /** Recovery for a lost confirmation: abandon the waiting session, then allow a retry. */
    fun abandonAndRetry() {
        if (running || !installer.abandonPending()) return
        publish(restore(state.copy(phase = UpdatePhase.Idle, problem = null)))
    }

    /** Returns false for a callback that does not match the stored session and nonce. */
    fun installResult(sessionId: Int, nonce: String?, status: InstallStatus): Boolean {
        val accepted = installer.accept(sessionId, nonce, status) ?: return false
        val problem = when (accepted) {
            InstallStatus.PendingUserAction -> {
                publish(state.copy(phase = UpdatePhase.AwaitingConfirmation))
                return true
            }

            InstallStatus.Success -> {
                foreground(haveConfirmation = false)
                return true
            }

            InstallStatus.Aborted -> UpdateProblem.InstallCancelled

            InstallStatus.Blocked -> UpdateProblem.InstallBlocked

            InstallStatus.Storage -> UpdateProblem.NoSpace

            InstallStatus.Incompatible -> UpdateProblem.Channel

            InstallStatus.Invalid -> UpdateProblem.Corrupt

            InstallStatus.Failure -> UpdateProblem.InstallFailed
        }
        if (problem == UpdateProblem.Corrupt) store.clearTransfer()
        publish(failed(state, problem, keep = problem != UpdateProblem.Corrupt))
        return true
    }

    private fun progress(bytes: Long) {
        if (state.phase == UpdatePhase.Downloading) publish(state.copy(downloaded = bytes))
    }

    /** Restores a candidate persisted by an earlier process (retained download), locally. */
    private fun restore(base: UpdateState): UpdateState {
        val record = store.readTransfer() ?: return base
        if (record.candidate.versionCode <= base.installedCode) {
            store.clearTransfer()
            return base
        }
        val ready = downloader.retained(record.candidate) != null
        return base.copy(
            phase = if (ready) UpdatePhase.Ready else UpdatePhase.Available,
            candidate = record.candidate,
            downloaded = if (ready) record.candidate.apkSize else 0
        )
    }

    private fun failed(base: UpdateState, problem: UpdateProblem, keep: Boolean = true) = base.copy(
        phase = UpdatePhase.Failed,
        problem = problem,
        candidate = if (keep &&
            problem != UpdateProblem.Changed
        ) {
            base.candidate ?: store.readTransfer()?.candidate
        } else {
            null
        }
    )

    private fun <T> run(work: () -> T, done: (T) -> Unit) {
        running = true
        val token = ++generation
        http.reset()
        worker.execute {
            val outcome = try {
                Result.success(work())
            } catch (e: Exception) {
                Result.failure(e)
            }
            deliver(
                Runnable {
                    if (token != generation) return@Runnable
                    running = false
                    outcome.fold(done) { error ->
                        when (error) {
                            is UpdateCancelled -> Unit

                            is UpdateFailure -> {
                                if (error.problem == UpdateProblem.Corrupt) store.clearTransfer()
                                publish(failed(state, error.problem).copy(retryAt = error.retryAt))
                            }

                            else -> publish(
                                failed(
                                    state,
                                    if (state.phase ==
                                        UpdatePhase.Installing
                                    ) {
                                        UpdateProblem.InstallFailed
                                    } else {
                                        UpdateProblem.Network
                                    }
                                )
                            )
                        }
                    }
                }
            )
        }
    }

    private fun publish(next: UpdateState) {
        state = next
        listeners.toList().forEach { it(next) }
    }

    private companion object {
        val AWAITING = UpdatePhase.AwaitingConfirmation
    }
}
