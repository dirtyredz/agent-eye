package local.agenteye.selfupdate.download

import java.io.File
import local.agenteye.selfupdate.Candidate
import local.agenteye.selfupdate.UpdateFailure
import local.agenteye.selfupdate.UpdateProblem
import local.agenteye.selfupdate.release.ReleasePolicy

/** What PackageManager reports about an installed package or an APK archive. */
data class PackageIdentity(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    /** SHA-256 of each current signing certificate. */
    val signers: List<String>,
    val debuggable: Boolean
)

interface ApkInspector {
    fun installed(): PackageIdentity

    /** Null when the archive cannot be parsed. */
    fun archive(file: File): PackageIdentity?
}

/**
 * Pre-install decision (Command Deck's model): exact length and SHA-256, then PackageManager
 * identity and signer preflight. This is not an independent verification of every signature
 * byte; Android's installer performs the authoritative cryptographic check before replacement.
 */
class ApkVerifier(
    private val inspector: ApkInspector,
    private val pin: String,
    private val sdk: () -> Int
) {
    /** Whether this installation can self-update at all (release package, single pinned signer). */
    fun eligible(): Boolean {
        if (!ReleasePolicy.isPin(pin)) return false
        val installed = inspector.installed()
        return installed.packageName == ReleasePolicy.APPLICATION_ID && !installed.debuggable &&
            installed.signers == listOf(pin)
    }

    fun verify(file: File, candidate: Candidate) {
        if (!file.isFile || file.length() != candidate.apkSize ||
            ApkDownloader.sha256(file) != candidate.apkSha256
        ) {
            throw UpdateFailure(UpdateProblem.Corrupt)
        }
        if (!eligible() || candidate.signerSha256 != pin) throw UpdateFailure(UpdateProblem.Channel)
        val installed = inspector.installed()
        val archive = inspector.archive(file) ?: throw UpdateFailure(UpdateProblem.Corrupt)
        val identity = archive.packageName == installed.packageName &&
            archive.versionName == candidate.versionName &&
            archive.versionCode == candidate.versionCode &&
            archive.minSdk == candidate.minSdk
        if (!identity || archive.debuggable) throw UpdateFailure(UpdateProblem.Corrupt)
        if (archive.signers != listOf(pin) || archive.signers != installed.signers) {
            throw UpdateFailure(UpdateProblem.Channel)
        }
        if (archive.versionCode <= installed.versionCode || archive.minSdk > sdk()) {
            throw UpdateFailure(UpdateProblem.Incompatible)
        }
    }
}
